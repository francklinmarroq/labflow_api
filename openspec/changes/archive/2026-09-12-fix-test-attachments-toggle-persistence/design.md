## Context

See proposal.md — Why. The diagnosis is settled: `v1.7.0` maps `test_config.allow_result_attachments`, production does not have the column, and so every query against `test_config` fails. The Java path is correct and needs no change.

Constraints that shape the approach:

- **No Flyway/Liquibase.** `spring.sql.init.mode=never`, `spring.jpa.hibernate.ddl-auto=update`. `src/main/resources/schema.sql` is a hand-run, idempotent migration script executed against production with admin credentials; Spring never runs it.
- **Spring splits `schema.sql` on `;`** and does not understand dollar-quoting, so every statement must stand alone — no `DO $$` blocks.
- **`ddl-auto=update` can no longer be relied on in production.** The older profile columns exist there because it *could* alter the schema when they shipped. It cannot now, which is the whole reason this outage exists.
- **The hotfix runs before the code change.** By the time `schema.sql` is edited, production will already have the column and the table, so the statements added to the script must be no-ops against that state as well as correct against an empty database.
- **`test_run_attachments` carries no `laboratory_id` and no `@TenantId`.** It is reached only through `TestRun` → `LabTest` → `LabOrder`, which are tenant-filtered, so Hibernate's tenant filter never applies to the attachment rows themselves. The DDL must not invent a tenant column the entity does not map.
- `LaboratoryReportFlagsTest` (199 lines, added with the report-alerts toggle) is the shape to copy for the new tests: H2, full HTTP round trip through the controller.

## Goals / Non-Goals

**Goals:**

- Production serves the exam editor again.
- `schema.sql` describes both database artifacts the feature needs, idempotently, so the next database built from the script is correct.
- The result-attachments switch survives a save → reload cycle, and a test fails if that stops being true.

**Non-Goals:**

- Rebuilding or redeploying the image. `v1.7.0` already has the code; the database is what is behind.
- Backfilling `schema.sql` for the older columns it is missing (`chart_type`, `result_layout`, `chart_x_axis_label`, `chart_x_value`, `display_order`). Real, but a separate change — see Risks.
- Changing how attachments are uploaded to R2, listed, deleted, or rendered on the report. Only the switch that gates the feature is in scope.
- Removing the dependence on `ddl-auto=update`, or introducing a migration tool.

## Decisions

### Repair production by hand before touching the repository

The outage is a missing column, not a missing binary, so the fastest correct fix is DDL against the production database — no build, no push, no deploy. Editing `schema.sql` afterwards is what stops the next environment from repeating it.

*Alternative considered:* roll `wrangler.jsonc` back to `v1.5.0` and redeploy. Rejected: it restores the exam editor by removing the mapping, but it also un-ships `a165ed1`, leaves the underlying gap in place, and costs a full native-image deploy cycle to undo later.

### Write the DDL to be a no-op on a database that already has it

`alter table if exists test_config add column if not exists allow_result_attachments boolean default false;` followed by `update test_config set allow_result_attachments = false where allow_result_attachments is null;`.

`default false` covers rows inserted after the migration, the `update` covers rows that predate it, and the entity's primitive `boolean` already reads a null as `false` — so the backfill is for tidiness and for anything reading the table directly, not a precondition. `if exists` on the table keeps the script safe on a brand-new database where Hibernate creates `test_config` from the entity.

*Alternative considered:* `not null default false`. Rejected — adding `not null` to a column that may already exist as nullable needs a second statement that fails if the column is already `not null`, and the script cannot branch. The nullable-with-default form is idempotent under every starting state, including the post-hotfix one.

### Create `test_run_attachments` from the entity's mapping, not from a guess

`create table if not exists test_run_attachments (id bigserial primary key, test_run_id bigint not null references test_runs(id), object_key varchar(255) not null, content_type varchar(255), display_order integer);` plus `create index if not exists ix_test_run_attachments_run on test_run_attachments (test_run_id);`.

Every column comes from `TestRunAttachment`; `test_runs` is the table `TestRun` maps to. The index matches the only access pattern — attachments are always fetched for one run — and mirrors `ix_lab_order_tags_tag`, added for the same reason. `bigserial` provides the `GenerationType.IDENTITY` the entity declares, consistent with `order_tags` in the same script.

No `laboratory_id`: the entity does not map one, and an unmapped column would be dead weight that `ddl-auto` never fills. Tenant isolation for attachments is structural, inherited from the run that owns them; the spec states it as a requirement so it stays deliberate rather than accidental.

*Alternative considered:* adding `@TenantId` to `TestRunAttachment` so the table is filtered directly. Rejected as scope creep — it changes the entity, the DDL and the upload path, to defend a row already unreachable except through a tenant-filtered parent.

### Test the round trip through the controller, not the service

The bug was invisible at the service layer — `TestConfigServiceImp` demonstrably sets the field. What failed was the whole path: JSON binding, persistence, read-back. So the tests drive `POST /api/v1/tests/full`, `PUT /api/v1/tests/{id}/full` and `GET /api/v1/tests/{id}/full` and assert on response bodies, as `LaboratoryReportFlagsTest` does.

These tests run on H2, where `ddl-auto` builds the schema from the entities — so they prove the Java path and would **not** have caught this outage. That is the honest limit of the approach, and it is why the production verification in tasks.md is a separate, explicit step rather than something the suite can cover.

## Risks / Trade-offs

- **The tests cannot catch the class of bug that caused the outage.** H2 always has every mapped column. → Mitigated procedurally, not technically: the constitution's rule (entity column ⇒ `schema.sql` entry) is the real control, and tasks.md verifies production directly. Worth a follow-up to check entity mappings against `schema.sql` in CI.

- **`schema.sql` still cannot rebuild a working database.** `chart_type`, `result_layout`, `chart_x_axis_label`, `chart_x_value` and `display_order` exist in production only because `ddl-auto` once created them; the script does not mention them. A restore from the script would silently produce a broken exam catalog. → Called out as a follow-up change so this one stays reviewable and shippable during an outage.

- **`laboratory.show_report_range_flags` may also be unapplied.** It shipped in the same v1.5.0 → v1.7.0 jump. If it was never run, `laboratory` queries are failing too and the symptom would be far broader than the exam editor. → Included in the hotfix and in tasks.md as an explicit check; the statement is idempotent, so running it when it is already applied costs nothing.

## Migration Plan

1. Run the idempotent DDL against production with admin credentials: the `test_config` column with its backfill, the `test_run_attachments` table with its index, and `laboratory.show_report_range_flags` for safety.
2. Confirm both artifacts exist, and confirm the exam editor loads.
3. Land the same statements in `schema.sql` together with the regression tests.

**Rollback:** none needed. The DDL is additive — a nullable column with a default and an empty table are inert, and an older image simply does not map them.
