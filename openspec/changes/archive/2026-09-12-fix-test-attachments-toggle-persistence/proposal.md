## Why

**Production is broken right now.** `GET /api/v1/tests/{testId}/full` returns `500` for every exam, so the exam editor cannot be opened at all. The attachments toggle that started this investigation was the early warning; deploying `v1.7.0` turned it into an outage.

The cause is confirmed. Production ran `v1.5.0` (pinned at `ebc4c2e`, 2026-08-16) until `044ba38` bumped straight to `v1.7.0` — **`v1.6.0` was pinned but never deployed**. Only two commits in that range touch the data model, and both need a migration that has not been run:

1. **`5243610`** added `test_config.allow_result_attachments` and the whole `test_run_attachments` table, and touched `src/main/resources/schema.sql` **not at all**. `v1.5.0` did not map the column, so the field was silently dropped on save — the original "the switch doesn't stick" symptom. `v1.7.0` maps it, the column does not exist, and every query against `test_config` now fails.
2. **`a165ed1`** added `laboratory.show_report_range_flags`. Its DDL *is* written (`schema.sql:209-210`), but that script is hand-run and it rode the same v1.5.0 → v1.7.0 jump. If it has not been executed, every query against `laboratory` fails too — login, settings, invoices, orders and the public report.

The Java is not at fault. `TestFullDTO.allowResultAttachments` flows through `TestBuilderServiceImp.persistProfile` into `TestConfigDTO`, is written by `TestConfigServiceImp.createTestConfig` and `updateTestConfig` onto `TestConfig.allowResultAttachments`, and is read back by `getFull`; the frontend sends and reads the same name. The defect is entirely that the database was never given the two artifacts the entity mapping requires.

The older profile columns (`chart_type`, `result_layout`, `chart_x_axis_label`, `chart_x_value`, `display_order`) are also absent from `schema.sql`, but they shipped in June/July and have been serving traffic — so `ddl-auto=update` created them back when the app still had DDL rights in production. That era is over, and `77af8f2 fix(deploy): script para crear columnas laboratory_id que ddl-auto no aplico` shows the lesson was already learned once. The migration script is now the only reliable mechanism, which is what the constitution says.

## What Changes

- **Hotfix production first**, outside the normal order: run the idempotent DDL by hand against the production database. `v1.7.0` already contains the code, so this restores the exam editor with no redeploy.
- Add that same DDL to `src/main/resources/schema.sql` so the script describes reality and the next database built from it is correct:
  - `test_config.allow_result_attachments` — boolean, default `false`, with a backfill, so every existing profile keeps behaving exactly as it does today.
  - `test_run_attachments` — `id`, `test_run_id` referencing `test_runs`, `object_key`, `content_type`, `display_order`, plus `ix_test_run_attachments_run`. No `laboratory_id`: the entity maps none, and isolation is inherited from the run that owns the row.
  - Written to be a no-op where the column and table already exist, since the hotfix will have created them.
- Add regression tests pinning the round trip through the unified exam editor: the switch saved on reads back on, saved off reads back off, a profile that predates the setting reads `false`, the switch is independent of `resultLayout`, and the other profile settings survive the same save. `LaboratoryReportFlagsTest` is the precedent.
- Confirm `laboratory.show_report_range_flags` was applied in production as well, since it shipped in the same jump.
- No API shape change: no new endpoint, no new field, no new permission. No Java change is expected.

## Capabilities

### New Capabilities
- `test-catalog`: the unified exam editor aggregate — an exam, its single profile (`TestConfig`) and its ordered parameters, saved and re-read in one payload. This change introduces its first requirement set, covering the durability of the profile's presentation and capture settings, the result-attachments switch among them.

### Modified Capabilities
<!-- None: openspec/specs/ holds only laboratory-settings, which this change does not touch. -->

## Impact

- **Production database** — two artifacts must be created by hand before anything else; until then the exam catalog is down.
- `src/main/resources/schema.sql` — new idempotent `alter table ... add column if not exists`, `create table if not exists` and index, plus a backfill.
- Tests under `src/test/java/marroquinsoftware/labflowapi/` — new coverage for the exam-editor round trip.
- No Java source change is expected: `model/TestConfig.java`, `model/TestRunAttachment.java`, `payload/TestConfigDTO.java`, `payload/TestFullDTO.java`, `service/TestConfigServiceImp.java` and `service/TestBuilderServiceImp.java` already wire the field end to end. If the new tests prove otherwise, the defect they expose is in scope.
- No AOT/reflection change: `payload/**` is already registered in `config/AppConfig.NativeRuntimeHints`.
- No image rebuild or `wrangler.jsonc` bump: `v1.7.0` is already deployed and already has the code.
- Adjacent gap surfaced but **not** fixed here: `chart_type`, `result_layout`, `chart_x_axis_label`, `chart_x_value` and `display_order` are missing from `schema.sql` even though they exist in production. A database rebuilt from the script would lack them. Called out in design.md as a follow-up.
- Out of scope: the frontend (`../labflow_frontend`), which already sends and reads the field correctly; and any change to how attachments are uploaded, stored in R2, or rendered on the report.
