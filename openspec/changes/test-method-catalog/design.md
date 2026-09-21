## Context

See `proposal.md` — *Why*. What shapes the approach:

- **`TestConfig` already owns everything else about how an exam is presented**: its parameters (`TestConfigParameter`, a `@OneToMany` with `cascade = ALL`, `orphanRemoval` and `@OrderBy`), its chart type, its layout, its attachments toggle. The methods are one more owned collection on that aggregate, and the code to manage an owned collection of a template is already there to copy.
- **The unified exam editor saves the whole aggregate in one call.** `TestFullDTO` carries the exam, its single template and its parameters through `POST/PUT /api/v1/tests/full`, and `TestFullDTO`'s own Javadoc records that the UI assumes 1 exam = 1 template. The methods ride in that payload; there is no reason for a separate methods endpoint.
- **The template is assigned to an order's exam *after* the exam is created.** Both `LabTestServiceImp.addTestToOrder` and `LabOrderServiceImp.createOrder` set `testConfig` to `null` explicitly; the frontend then calls `assignTestConfig`, automatically when the exam has exactly one template. So `assignTestConfig` is the single point where an exam acquires a template — and therefore the only sane place to stamp the default method.
- **Two in-repo precedents pull in opposite directions on deletion**: `OrderTag` deletes by unlinking from the orders, `BillingClient` refuses deletion once a fiscal document names it. Which one applies here is the main decision below.
- **No lazy to-one associations** (`AGENTS.md`, `NativeImageLazyAssociationTest`): `LabTest.method` must be EAGER.

Current state: `lab_tests.method varchar(255)`, written by `LabTestServiceImp.updateMethod` from `PATCH /orders/{orderId}/tests/{labTestId}/method`, read into `LabTestDTO.method` and printed by the report when non-null.

## Goals / Non-Goals

**Goals:**

- The method stops being retyped: it is chosen from the template, or written once and kept there.
- **Zero wire change** on the order path — `{ "method": "ELISA" }` in and `method` out, both still names — so the frontend deployed before this goes out keeps working. `AGENTS.md`'s promotion order is API first.
- What an order says was used never changes because of a later setup decision.
- The exam editor can fix what the orders taught it: rename, unmark, remove.

**Non-Goals:**

- A laboratory-wide method catalogue shared between exams. See `proposal.md` — *Impact*.
- Methods per parameter, or a method per run. The method is a property of the exam on the order, which is where it already is and where the report prints it.
- Any change to how the report lays the method out. "Better visualised" is the frontend's half of this change; the API's contribution is that the method is reliably there.

## Decisions

### Methods are an owned collection of `TestConfig`, with a boolean default

`TestMethod` gets a `@ManyToOne TestConfig testConfig` and `TestConfig` gets `@OneToMany(mappedBy = "testConfig", cascade = ALL, orphanRemoval = true) @BatchSize List<TestMethod> methods`, modelled directly on `configParameters`. Which one is the default is a **boolean on the method row**, not a `defaultMethod` association on `TestConfig`.

- *Why the boolean and not an FK on the template*: every to-one here is EAGER (the native image cannot proxy a lazy one), so `TestConfig.defaultMethod` → `TestMethod.testConfig` → `TestConfig` would be an eager cycle loaded on every read of a template — and templates are read on every order screen. The boolean has no cycle, and "at most one true per template" is a rule the service enforces in the one place that sets it.
- *Trade-off accepted*: the invariant is not expressible as a database constraint, so a direct SQL edit could leave two defaults. The service is the only writer, and a reader that meets two picks the first deterministically rather than failing.

### `LabTest.method` becomes an association; the DTO keeps the name

`LabTest.method` becomes `@ManyToOne TestMethod` on column `method_id` (EAGER). `LabTestDTO.method` stays a `String` — the method's name — on the way in **and** out, and a read-only `methodId` is added beside it.

- *Why*: identical reasoning to the referring-physician change, and for the same hard constraint: the API deploys before the frontend, so the currently deployed order screen must keep working with `{ method: "ELISA" }`. It also means the report and everything that reads `LabTestDTO.method` need no change at all.
- *Consequence*: the name printed on an order's report is the template's *current* name for that method, so a rename reaches issued reports. That is the intent — see the deletion decision for where the line is drawn.

### Renaming propagates, deleting a used method is refused

A rename is a correction and reaches every order. A deletion is refused while any `LabTest` references the method, with a message saying how many exams hold it.

- *Why not the `OrderTag` behaviour (unlink on delete)*: a tag is the laboratory's own filing — losing it costs a filter. The method states how a clinical result was produced and is printed on reports already handed to patients; silently blanking it on twenty issued reports is not a correction, it is a loss of the analytical record. `BillingClient` sets the precedent for "it is data on an issued document, so it cannot be deleted", and it is the closer analogy.
- *Why renaming is still allowed to propagate*: a misspelled technique on issued reports is already wrong; propagating the correction makes them right. The rename cannot change *which* technique a report names, only its spelling — unlike a delete, which erases the statement entirely. A rename onto a name the same template already holds would merge two techniques into one, so it is refused, as it is for tags and physicians.
- *Escape hatch if a method really must go*: change those orders' method first, then delete. The refusal message says how many there are so the size of that job is known before it starts.

### Resolve-or-create is scoped to the template, and marks the default

`TestMethodService.resolveOrCreate(TestConfig, String)` mirrors `OrderTagServiceImp.resolveOrCreate` — same `cleanName` and `normalize` (NFD, strip marks, collapse spaces, lower case) — but searches only within the given template and returns `null` for a blank name. `updateMethod` then: refuses when the exam has no template, resolves or creates within it, links the exam, and marks the resolved method as the template's default.

- *Why marking the default is part of setting it, rather than a separate call*: it is what was asked for — the last choice becomes the default — and doing it in the same transaction means a caller cannot end up with the exam set and the default not, or vice versa.
- *Trade-off accepted and deliberate*: a one-off deviation on a single order silently changes what the next orders start with. That is the requested behaviour, and it is recoverable in the exam editor, which is exactly why the editor can set the default explicitly rather than only observing it.

### The default is stamped in `assignTestConfig`, never overwriting

`assignTestConfig` sets the exam's method to the template's default when the template has one **and the exam has no method yet**. `addTestToOrder` and `createOrder` are left alone: they set `testConfig = null` on purpose and the frontend assigns immediately afterwards, so stamping there would have nothing to stamp from.

- *Why never overwrite*: re-assigning a template to an exam that already states a method (changing which profile is used, say) must not quietly rewrite what the technician said was used.
- *Why not resolve the default at read or print time instead*: a report must state what was used, not what the setup currently prefers. Stamping is what makes a later setup change harmless to orders already taken.

### The backfill leaves what it cannot determine

`TestMethodBackfill` follows `PublicTokenBackfill`: `JdbcTemplate`, one `try`/`catch`, `log.warn` rather than a failed start-up. It reads `select id, test_config_id, method from lab_tests where method is not null and method_id is null`, normalizes in Java (same rule as the service, and portable between PostgreSQL and H2 — `unaccent` is a Postgres extension and does not exist in H2), groups by `(test_config_id, normalized)`, inserts the missing methods, links each exam, and marks as each template's default the method of the highest `lab_tests.id` for that template — the most recently created exam, which is the best available stand-in for "most recently used".

Rows whose `test_config_id` is null are **not** guessed at. They are counted and logged, and they keep their text. The old column is therefore not dropped while any remain: `schema.sql` carries the drop commented out, gated on that count being zero.

- *Why not fall back to "the exam's only template"*: it is usually right and occasionally wrong, and the wrong case attributes a technique to a template that never ran it, which then becomes that template's default. Leaving a handful of rows for a human to resolve is cheaper than that.

## Risks / Trade-offs

- **A typo adds a method to the template and makes it the default** → The exam editor is where it is fixed: rename it onto the right spelling (refused if that creates a duplicate, in which case correct the order and delete the stray, which is possible precisely because it is used by one exam you just freed). The picker showing the template's existing methods on focus is the main defence.
- **A rename rewrites issued reports** → Intended, and stated in the spec. It cannot change which technique is named, only how it is spelled.
- **A method in use cannot be deleted, which will surprise someone** who expects tag-like behaviour → The refusal message says how many exams hold it, so the next step is obvious. This is the deliberate half of the trade-off above.
- **An exam that acquired its template before this change, and whose method is null, never gets stamped** → Correct: stamping happens on assignment, and its assignment already happened. Those exams are set by hand as they are today, which is no worse than before.
- **Turning a column into an association is the kind of change H2 does not catch** — `Invoice.billingClient` is the precedent: green suite, 500s in the native image. Mitigation: `LabTest.method` is EAGER like every other to-one, `NativeImageLazyAssociationTest` enforces it, the order detail and the report are exercised on develop before merging.
- **Reading a page of orders now touches methods** → `LabTest` is loaded per order detail rather than per listing row, and the method is a to-one on it. The order listing does not read exams, so the listing is unaffected; the order detail is verified with `show-sql` rather than assumed.

## Migration Plan

1. Run the new `create table` / `add column` statements from `schema.sql` against the develop database, then `wrangler deploy --env develop`. The old text column stays.
2. On start-up the backfill fills `test_methods`, links `lab_tests.method_id` and marks each template's default. Read the log: the number of exams linked, and the number left alone for want of a template.
3. Resolve the left-alone exams by hand if there are any — assign the template on the order, then set the method — until the count is zero.
4. Exercise on develop: the exam editor's methods section, adding an exam to an order and finding the default already set, changing the method and seeing the default follow, a report, and a deletion attempt on a method in use.
5. Promote: same `schema.sql` statements against production, deploy, confirm the backfill log there, resolve any leftovers.
6. **Only then**, as its own step, run `alter table lab_tests drop column if exists method`. `schema.sql` carries it commented out with that condition written on it.

**Rollback**: before step 6 the text column still holds every method and the previous image reads and writes it as before, so redeploying the previous version is a complete rollback. After step 6 it is not, which is why it is separate and gated.
