## Why

The method — the technique a result was produced with (ELISA, quimioluminiscencia, aglutinación) — is a property of **how the laboratory runs that exam**, but it is stored as free text on each individual exam of each individual order (`lab_tests.method`, set through `PATCH /orders/{orderId}/tests/{labTestId}/method`). So the same technique is retyped on every order that includes that exam, spelled a little differently each time, and printed on the patient's report exactly as it was mistyped. A laboratory that runs one exam by one technique has to say so again on every single order, forever.

It belongs on the exam's template, where everything else about how that exam is presented and measured already lives: the parameters, the reference ranges, the layout, whether attachments are allowed. From there it can be offered rather than retyped, and the technique the laboratory actually uses can settle in as the default.

There is a second, sharper problem: the technician entering results never sees the method. It is a small icon button on the exam card of the order screen, and the results dialog does not mention it at all. Results get saved without the person saving them knowing which technique the order says they used.

## What Changes

- **Methods become a list on the exam's template** (`TestConfig` — what the unified exam editor calls "el perfil"). Each template carries its own methods, unique within that template under the same accent- and case-insensitive comparison that order tags use, and one of them may be marked as the template's default.
- **Choosing the method on an order picks from that template's list, or writes a new one.** Writing a name the template does not have adds it to the template and uses it — the same "born on first use" the order tags already have. The endpoint keeps its shape: it still takes a method *name*.
- **The last method chosen becomes the template's default.** Choosing a method on an order marks it as that template's default, so the next orders start with it already set, until somebody chooses differently on a later order. The default can also be set, renamed, reordered out or removed directly in the exam editor, so a one-off deviation is correctable where the rest of the exam's setup lives.
- **The default lands on the exam when its template is assigned** — which, for the overwhelmingly common one-template exam, is the moment the exam is added to an order. The exam keeps whatever method it was given from then on: changing the template's default later SHALL NOT alter orders already created or reports already issued.
- **BREAKING (behaviour)**: an exam whose template has not been assigned yet no longer accepts a method. The method lives on the template, so there is nowhere to put it until there is a template. In practice this is a transient state — the frontend assigns the only template automatically — and the request is refused with a message saying so rather than silently dropped.
- **BREAKING (database, not API)**: `lab_tests.method` (text) stops being the source of truth and becomes a link to the template's method. A start-up backfill turns the text already recorded into template methods and links the exams to them, marking the most recently used as each template's default. The column is dropped only after that is verified in production.
- **Renaming a method corrects it everywhere; deleting one in use is refused.** A rename is a correction and reaches every order that used that method, reports included. A deletion is not: the method is printed on reports already issued, so a method some exam used cannot be deleted — it can only be renamed or stopped being the default. This differs deliberately from order tags, whose deletion just unlinks (see design.md).
- The report prints the method exactly as it does today, when there is one.

## Capabilities

### New Capabilities

- `test-method-catalog`: the methods of an exam template — what one is, what makes two names the same method within a template, which one is the default and how it comes to be, who may administer them, and what renaming or deleting one means for the orders that used it.
- `order-test-method`: the method on an order's exam — how it is chosen, why it needs a template first, when the template's default is stamped onto it, and what an order and its report report back.

### Modified Capabilities

<!-- None. `test-catalog` under openspec/specs/ covers the exam's attachments toggle;
     `order-composition` covers which exams an order contains and when that set freezes.
     Neither says anything about the method. The behaviour here is described in full in
     the two new capabilities above. -->

## Impact

- **Model and database**: a new `TestMethod` entity (table `test_methods`, `@TenantId`, one row per method per template, normalized name unique per template, a flag marking the template's default) owned by `TestConfig` exactly as `TestConfigParameter` is. `LabTest.method` changes from `String` to a `@ManyToOne` on a new `method_id` column — EAGER, like every other to-one here, because a lazy one cannot build its proxy in the native image.
- **Data migration**: a `TestMethodBackfill` `CommandLineRunner` in `config/`, in the style of `PublicTokenBackfill`: `JdbcTemplate`, outside the tenant filter so it reaches every laboratory, idempotent, unable to crash start-up. Exams whose template cannot be determined are left alone and reported, and the column is not dropped while any remain.
- **API**: the exam template's methods ride in the payloads that already carry the template — `TestFullDTO` (the unified editor saves the exam, its template and its parameters in one atomic call) and `TestConfigDTO` (what the order screen reads). `PATCH /orders/{orderId}/tests/{labTestId}/method` keeps its request and response shape and gains the resolve-or-create, the default-marking and the refusal when there is no template. `LabTestDTO.method` stays the method's name; a read-only `methodId` is added beside it.
- **Assignment point**: `LabTestServiceImp.assignTestConfig` is where the default gets stamped — `addTestToOrder` and order creation both leave the template null and the frontend assigns it immediately after.
- **Not in scope**: methods shared across templates or a laboratory-wide method catalogue. The method is a property of how *this* exam is run; the same word under two exams is two methods, and making them one would mean a rename on one exam silently rewriting another's reports.
- **Frontend** (`labflow_frontend`, sibling change of the same name): the methods section in the exam editor, the picker replacing the free-text dialog on the order screen, and showing the method where results are actually entered.
- No new dependencies.
