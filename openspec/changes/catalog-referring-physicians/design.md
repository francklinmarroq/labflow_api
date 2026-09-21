## Context

See `proposal.md` — *Why*. What shapes the approach here is that order tags already implement this exact pattern, and that three constraints in this repository narrow the design far more than the feature itself does:

- **`OrderTag` is the reference implementation.** `OrderTagServiceImp.resolveOrCreate` (create on first use), `OrderTagRepository.countUsageByTag` (usage shown in the catalogue), `detachFromAllOrders` (delete unlinks rather than refusing), and the normalized-name uniqueness per laboratory are all in place and in production. Everything here is that pattern applied to a to-one association instead of a many-to-many.
- **No lazy to-one associations** (`AGENTS.md`, `NativeImageLazyAssociationTest`). The native image cannot build a Hibernate proxy at runtime, so `LabOrder.referringPhysician` is EAGER and has to be fetch-joined anywhere a page of orders is read, or the listing becomes N+1.
- **`schema.sql` is a hand-run, idempotent script**, and start-up data fixes are `CommandLineRunner` beans using `JdbcTemplate` that must not crash the application. The physician backfill is one of those, and dropping the old column is a manual step gated on the backfill's result, not something that happens on deploy.

Current state: `lab_orders.referring_physician varchar(150)`, written by `LabOrderServiceImp` through `trimToNull(dto.getReferringPhysician())` on create and update, read back into `LabOrderDTO.referringPhysician` and into `PublicReportDTO.Order.referringPhysician`, and printed on the report only when non-null.

## Goals / Non-Goals

**Goals:**

- The same "born on first use, maintained afterwards" behaviour tags have, for a to-one association.
- **Zero wire change**: a frontend deployed before this goes out keeps creating, reading and updating orders with physicians, unchanged. The promotion order in `AGENTS.md` is API first, frontend second, so the API must tolerate the old frontend.
- The orders listing costs the same number of queries after the change as before.
- The physicians already typed end up in the catalogue without anyone re-typing them, and the old column is retired only once that is proven in production.

**Non-Goals:**

- A `physicianId` request parameter on `GET /orders`. See `proposal.md` — *Impact*; the frontend filters in the browser as it does for tags.
- Any field on a physician beyond the name — no licence number, no speciality, no colour. The name is what the report prints and what the picker matches on.
- Merging two physicians into one. A rename onto an existing name is refused, exactly as for tags; merging would have to decide what happens to both sets of orders and nobody has asked for it.

## Decisions

### The name stays the wire format; the id is additive

`LabOrderDTO.referringPhysician` keeps being a `String` on the way in **and** on the way out, and a read-only `referringPhysicianId` is added beside it. The service resolves the name to a catalogue entry on save and reports `physician.getName()` on read.

- *Why*: it is the asymmetry tags already use (`tagNames` in, `tags` out), minus the asymmetry — a single name can be the same field both ways. It makes the change invisible to the currently deployed frontend, which matters because the API deploys first. It also keeps `PublicReportDTO.Order.referringPhysician` untouched, so the public report and its frontend page need no coordination at all.
- *Alternative rejected*: taking `referringPhysicianId` on write and dropping the name. Cleaner on paper, but it breaks the deployed frontend on the API-first deploy, forces the catalogue to be visited before an order can name a new physician (the opposite of what was asked for), and buys nothing — the name already identifies the physician uniquely within the laboratory by construction.
- *Alternative rejected*: accepting both a name and an id on write, with the id winning. Two ways to say the same thing, and a silent conflict to define when they disagree. The id is ignored on write instead.

### Resolve-or-create returns the entity, blank returns null

`ReferringPhysicianService.resolveOrCreate(String)` mirrors `OrderTagService.resolveOrCreate(List<String>)` but for one name, returning `null` for a blank one. `LabOrderServiceImp` then does `order.setReferringPhysician(referringPhysicianService.resolveOrCreate(dto.getReferringPhysician()))` in both `createOrder` and `updateOrder`, replacing the two `trimToNull(...)` calls one for one.

- *Why*: it keeps "blank clears the physician" exactly as it behaves today — the detail screen sends the whole order back on every edit, so any other rule would change observable behaviour for an operation nobody asked to change.
- *Note on tags*: `tagNames == null` means "leave the tags alone" while an empty list clears them. The physician does **not** copy that: a null name clears, as it does today. The asymmetry is deliberate and is spelled out in the spec — for tags the distinction was needed because a partial update of a collection is ambiguous; for a single scalar it is not, and copying it would be a behaviour change.

### One extra query on save, none on read

`resolveOrCreate` costs one `findByNormalizedName` per save, plus one insert the first time a name is used. Reading is a fetch join, so the listing adds no query at all: `LabOrderSpecifications.orders(...)` already fetch-joins `customer`, and `referringPhysician` joins the same way — both are to-one, so neither multiplies rows and SQL paging stays correct. The count query keeps skipping both fetches.

- *Why*: every request pays ~0.7 s of floor latency (browser → Worker → DO → container), so the thing to protect is the number of *requests*; one extra in-transaction query on save is not measurable against that, while an N+1 across a 1000-row listing page is.

### Deleting unlinks through a bulk update, not a native statement

`OrderTagRepository.detachFromAllOrders` had to be native SQL because the join table has no entity. Here the owning side is `LabOrder` itself, so unlinking is a JPQL bulk update — `update LabOrder o set o.referringPhysician = null where o.referringPhysician.id = :id` — with `@Modifying(flushAutomatically = true, clearAutomatically = true)` for the same reasons documented on the tag repository: flush so a link made earlier in the transaction is not lost, clear so orders already in the session stop reporting a physician the database no longer links them to.

- *Why JPQL over native*: it stays inside the tenant filter and needs no knowledge of the column name. The physician id has already been validated against the tenant by the service's `findById` before this runs, so both are safe; JPQL is simply the less surprising of the two.

### The backfill is a `CommandLineRunner`, and the column drop is gated on it

`ReferringPhysicianBackfill` follows `PublicTokenBackfill` exactly: `JdbcTemplate`, one `try`/`catch` around everything, a `log.warn` instead of a failure if the tables are not there yet. It reads `select id, laboratory_id, referring_physician from lab_orders where referring_physician is not null and referring_physician_id is null`, normalizes each name **in Java** (the same NFD-strip-marks-lower-case used by the service), groups by `(laboratory_id, normalized)`, inserts the missing physicians, and updates each order's FK.

- *Why normalize in Java*: it is the only way the comparison is guaranteed identical to the service's, and it is portable between PostgreSQL (prod) and H2 (tests). `unaccent` is a Postgres extension that may not be installed and does not exist in H2.
- *Why `JdbcTemplate`*: it deliberately bypasses `@TenantId`, which is what lets one run cover every laboratory. `AGENTS.md` names this as the legitimate exception to preferring repositories.
- *Why idempotent by construction*: the `referring_physician_id is null` predicate means a second run sees nothing, and an insert is only attempted for a `(laboratory_id, normalized)` pair not already present.
- *Why the drop is separate*: `LabOrder` stops mapping the old column the moment this deploys, so the column being there costs nothing and is the only copy of the data if the backfill mis-groups something. Dropping it in the same deploy would make the migration irreversible before anyone had looked at the result.

### The catalogue endpoint mirrors `/order-tags` in its permissions

`GET /api/v1/referring-physicians` carries the same `hasAnyAuthority('CATALOG_VIEW','ORDERS_VIEW','ORDERS_CREATE','INVOICES_VIEW','INVOICES_CREATE','REPORTS_VIEW')` as the tag listing; the writes carry `CATALOG_CREATE`/`CATALOG_EDIT`/`CATALOG_DELETE`.

- *Why the wide read*: the picker on the order screen needs it, and whoever takes an order holds `ORDERS_CREATE`, not `CATALOG_VIEW`. Creating a physician while saving an order does **not** go through the `CATALOG_CREATE` endpoint — it happens inside the order's own transaction, under `ORDERS_CREATE`, which is the same split tags already have.

## Risks / Trade-offs

- **The backfill mis-groups two different physicians who share a name** (two "Dr. J. López" in a laboratory) → They were already indistinguishable in the free text, so no information is lost; the catalogue can be corrected afterwards by renaming one and re-attributing its orders. Keeping the text column until the result has been reviewed is what makes that recoverable.
- **Renaming a physician changes reports already issued** → Intended, and the same thing renaming a tag already does. It is what makes a correction worth making. The spec states it, and the catalogue screen says so on the rename dialog.
- **A typo enrols a new physician silently** ("Dr. Carlso Mejía") → The catalogue screen exists for exactly this: the entry shows up with one order against it and can be renamed onto the correct spelling — except that renaming onto an existing name is refused, so the honest recovery is to correct the order and delete the stray entry. Same limitation tags have today.
- **Turning the field into an association is the kind of change H2 does not catch** — the `Invoice.billingClient` outage is the precedent: green suite, 500s in the native image. Mitigation: `referringPhysician` is EAGER like every other to-one (`NativeImageLazyAssociationTest` enforces it), the orders listing is exercised on develop against the develop database before merging, and the fetch join is verified with `show-sql` rather than assumed.
- **One more query per order save** → Bounded and measured against a ~0.7 s per-request floor; not worth avoiding with a cache that would have to be invalidated per tenant.

## Migration Plan

1. Deploy the API to develop (`wrangler deploy --env develop`) after running the new `create table` / `add column` statements from `schema.sql` against the develop database. The old text column stays.
2. On start-up the backfill fills `referring_physicians` and sets `lab_orders.referring_physician_id`. Check the log line for the number of orders relinked, and spot-check the catalogue against the names previously in the text column.
3. Exercise the orders listing, the order detail, creating an order with a new physician and with an existing one, and a public report, on develop.
4. Promote: run the same `schema.sql` statements against production, deploy the API to production, confirm the backfill log there too.
5. **Only then**, and as a deliberate separate step, run `alter table lab_orders drop column if exists referring_physician` on both databases. `schema.sql` carries it commented out with that instruction on it, so the file still describes the intended end state without dropping the column out from under a database where the backfill has not been verified.

**Rollback**: before step 5 the old column still holds every name, and the previous API image reads and writes it as it always did — redeploying the previous version is a complete rollback, with the only loss being physicians recorded through the new API in the interim (whose names are still in the catalogue table, recoverable by hand). After step 5 the rollback is no longer clean, which is precisely why it is a separate, gated step.
