## 1. Restore production

- [x] 1.1 Run against the production database, with admin credentials, `alter table if exists test_config add column if not exists allow_result_attachments boolean default false;` and the `update test_config set allow_result_attachments = false where allow_result_attachments is null;` backfill — verified by `information_schema.columns` listing `test_config.allow_result_attachments`
- [x] 1.2 Run the `create table if not exists test_run_attachments (...)` statement — verified by `information_schema.tables` listing `test_run_attachments` with the five columns `TestRunAttachment` maps. The table already existed: `ddl-auto` created it under the app role when `v1.7.0` deployed, so the statement was a no-op
- [ ] 1.2b Create `ix_test_run_attachments_run`, which the admin credential cannot: the app role owns the table and Postgres requires ownership for `CREATE INDEX` (`must be owner of table test_run_attachments`). Run it as that role (`set role`, or the `.env.wrangler` connection), or `alter table test_run_attachments owner to current_user` first — verified by `pg_indexes` listing the index. Performance only; nothing is broken without it
- [x] 1.3 Run the idempotent `laboratory.show_report_range_flags` statements from `schema.sql:209-210`, which shipped in the same v1.5.0 → v1.7.0 jump and may never have been applied — verified by the column existing and no row left null
- [x] 1.4 Open an exam in the deployed app — verified by `GET /api/v1/tests/{testId}/full` returning `200` instead of `500`
- [x] 1.5 Confirm the wider blast radius is clear: log in, open laboratory settings, list orders, open an invoice, open a public results link — verified by none of them returning `500` and no "column does not exist" in the Worker logs

## 2. Confirm the reported bug is actually fixed

- [ ] 2.1 In the deployed app, turn "Permitir adjuntar foto del reporte" on for an exam, save, leave the editor and reopen it — verified by the switch showing on, which is the behavior originally reported as broken
- [ ] 2.2 Turn it back off, save, reopen — verified by the switch showing off, so the value is genuinely stored rather than defaulting on
- [ ] 2.3 Upload a photo against a run of that exam — verified by the upload succeeding, which exercises the newly created `test_run_attachments` table

## 3. Migration script

- [x] 3.1 Append to `src/main/resources/schema.sql` the `test_config.allow_result_attachments` statements from 1.1, with a Spanish comment stating what breaks in production without them — verified by the statements being present, each a single statement with no `DO $$` block
- [x] 3.2 Append the `create table if not exists test_run_attachments (...)` statement with the columns `TestRunAttachment` maps (`id`, `test_run_id` referencing `test_runs`, `object_key`, `content_type`, `display_order`) and no `laboratory_id` — verified by each column matching the entity's `@Column` names
- [x] 3.3 Append `create index if not exists ix_test_run_attachments_run on test_run_attachments (test_run_id);` — verified by the statement being present
- [x] 3.4 Run the whole `schema.sql` twice against a scratch PostgreSQL (`docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine`) — verified by the second run completing with no error, proving idempotency. Scoped to the new block: the script as a whole cannot run on an empty database (`lab_order_tags` and `test_run_attachments` reference `lab_orders`/`test_runs`, which only `ddl-auto` creates), a pre-existing condition section 6 covers
- [x] 3.5 Run the new statements once more against that database after letting the app create the schema via `ddl-auto` — verified by no error, proving the script is a no-op on a database that already has the column and table, which is the state production is now in

## 4. Regression tests

- [x] 4.1 Add a test class for the unified exam editor round trip alongside the existing tests in `src/test/java/marroquinsoftware/labflowapi/`, modelled on `LaboratoryReportFlagsTest`, driving the controller over HTTP on H2 — `TestFullProfileSettingsTest`, `MockMvc` en `standaloneSetup` sobre `@DataJpaTest`. Se le inyecta el `JsonMapper` de la aplicación (`@ImportAutoConfiguration(JacksonAutoConfiguration.class)`) en vez del que arma `standaloneSetup` por su cuenta: Jackson 3 trae `FAIL_ON_NULL_FOR_PRIMITIVES` encendido y Spring Boot se lo apaga, así que con el mapper crudo un cuerpo que omite un boolean primitivo se rechaza con `400` — comportamiento del arnés, no de la API
- [x] 4.2 Cover "turning attachments on for an existing exam": `PUT /api/v1/tests/{testId}/full` with `allowResultAttachments` `true` returns `true`, and a following `GET` still returns `true` — verified by the test passing
- [x] 4.3 Cover "turning attachments back off": the same round trip with `false` returns and re-reads `false` — verified by the test passing
- [x] 4.4 Cover "creating an exam with attachments allowed" via `POST /api/v1/tests/full`, and "exam created without mentioning the setting" defaulting to `false` — verified by both tests passing
- [x] 4.5 Cover "the switch is independent of the antibiogram layout": `resultLayout` `ANTIBIOGRAM` with `allowResultAttachments` `true` round-trips both unchanged, and changing one in a later save leaves the other alone — verified by the test passing
- [x] 4.6 Cover "all profile settings survive one round trip": non-default `active`, `chartType`, `chartXAxisLabel`, `resultLayout` and `allowResultAttachments` in one save all read back unchanged — verified by the test passing
- [x] 4.7 Run `mvn -B --settings .mvn/settings.xml test -Dtest=TestFullProfileSettingsTest` — verified by the 6 tests passing on H2

## 5. Full verification

- [x] 5.1 Run `mvn -B --settings .mvn/settings.xml test` — verified by the run being green, allowing for the two known pre-existing conditions (`PostgresQueryCompatibilityTest` self-skips without the local PostgreSQL; `LabflowapiApplicationTests` fails on `${DB_URL}` without a `.env`) — 87 pruebas, 1 error: solo `LabflowapiApplicationTests.contextLoads`, con `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}` (no hay `.env` en este entorno), y `PostgresQueryCompatibilityTest` se saltó solo por no haber PostgreSQL en `localhost:55432`

## 6. Follow-up

- [x] 6.1 Open a separate change for the columns `schema.sql` still cannot rebuild — `test_config.chart_type`, `test_config.result_layout`, `test_config.chart_x_axis_label`, `test_config_parameters.chart_x_value`, `test_config_parameters.display_order` — which exist in production only because `ddl-auto` created them years-of-commits ago; verified by the change existing
- [x] 6.2 Note for that change the idea of a build-time check that every `@Column` on an entity has a matching statement in `schema.sql`, since the H2 test suite structurally cannot catch this class of outage — verified by the idea being recorded in its design
