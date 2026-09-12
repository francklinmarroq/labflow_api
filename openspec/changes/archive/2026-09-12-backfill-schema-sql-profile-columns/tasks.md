## 1. Read the authoritative column types

- [x] 1.1 Start the scratch PostgreSQL the design names — `docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine` — verified by `psql` connecting on `localhost:55432`
- [x] 1.2 Let Hibernate build the schema with `ddl-auto=update` against it by running `mvn -B --settings .mvn/settings.xml test -Dtest=PostgresQueryCompatibilityTest` (the one test in the suite that points at that database) — verified by `information_schema.tables` listing `test_config` and `test_config_parameters`
- [x] 1.3 Read the live types of the five columns and of `test_config_parameters` out of `information_schema.columns` — verified by having the recorded `data_type`, `character_maximum_length`, `numeric_precision`/`numeric_scale` and `is_nullable` for each. **This substitutes for the design's "read them out of production":** this environment has no route to the production database, and the columns there were themselves created by `ddl-auto` against `PostgreSQLDialect`, which is exactly what step 1.2 reproduces. The substitution must be stated when reporting the change
- [x] 1.4 Read the primary key and foreign keys `ddl-auto` gave `test_config_parameters` out of `information_schema.table_constraints` — verified by confirming the composite `(test_config_id, parameter_id)` key the `@IdClass` implies, and the referenced tables `test_config` and `parameter`

## 2. Write the block into schema.sql

- [x] 2.1 Append `create table if not exists test_config_parameters (...)` matching what 1.3 and 1.4 recorded, with `display_order` and `chart_x_value` nullable — verified by every column and constraint matching the live table, not the design's sketch
- [x] 2.2 Append the three `alter table if exists test_config add column if not exists ...` statements for `chart_type`, `result_layout` and `chart_x_axis_label`, all `varchar` because both enums are `@Enumerated(EnumType.STRING)` — verified by the types matching 1.3
- [x] 2.3 Append the two `alter table if exists test_config_parameters add column if not exists ...` statements for `display_order` and `chart_x_value`, so the columns are added even on a database whose `test_config_parameters` predates them and the `create table` above is a no-op — verified by both statements being present after the create
- [x] 2.4 Give the block the Spanish comment the file's convention calls for: which entity maps each column, that they exist in production only because `ddl-auto` created them, that there is nothing to run against production, and that no `not null`, no default and no check constraint is used — with the reason for each — verified by the comment stating all four
- [x] 2.5 State in the same comment that the script still cannot build a database from empty, so nobody reads the new block as the script having become complete — verified by the caveat being present, which is the risk the design names
- [x] 2.6 Confirm every new statement stands alone with no `DO $$` block, since Spring splits the file on `;` — verified by grepping the new block for `$$` and finding nothing

## 3. Verify idempotency against both starting states

- [x] 3.1 Run the new block against the `ddl-auto`-built database from step 1 — verified by it completing with no error and changing nothing, which is the state production is in
- [x] 3.2 Run it a second time against that same database — verified by the second run also completing clean, proving idempotency
- [x] 3.3 Reproduce the pre-column state on the scratch database (drop the three `test_config` columns and the `test_config_parameters` table) and run the block once — verified by the columns and the table coming back
- [x] 3.4 Compare the types the block produced in 3.3 against what 1.3 recorded — verified by `information_schema.columns` reporting the same `data_type`, length, precision/scale and nullability for all five columns, which is what stops the "comes up and misreads data" risk the design names
- [x] 3.5 Run the block once more on the state 3.3 left — verified by a clean run, proving idempotency from the other starting state too
- [x] 3.6 Drop and recreate the scratch database, run `ddl-auto` again, and check that the new block still matches — verified by 3.1 passing on a freshly built schema, so the match is not an artifact of one dirty database

## 4. Full verification

- [x] 4.1 Run `mvn -B --settings .mvn/settings.xml test` — verified by the run being green, allowing for the two known pre-existing conditions (`LabflowapiApplicationTests` fails on `${DB_URL}` without a `.env`; `PostgresQueryCompatibilityTest` self-skips when the scratch database is down, and runs when it is up)
- [x] 4.2 Confirm no Java, DTO, controller or `wrangler.jsonc` file was touched — verified by `git status` showing only `src/main/resources/schema.sql` and this change's artifacts
- [x] 4.3 Stop and remove the scratch container — verified by `docker ps` no longer listing `labflow-pg-test`

## 5. Follow-up

- [x] 5.1 Confirm the build-time check that every mapped `@Column` has a matching statement in `schema.sql` is recorded in `design.md` as the durable fix and left out of scope here — verified by the section existing, since this change is the third hand repair of the same defect and nothing in it prevents a fourth
- [x] 5.2 Record, without acting on it, that `ddl-auto` also created `test_config_chart_type_check` and `test_config_result_layout_check` on `test_config` — the same kind of enum check constraint this file already drops for `app_role_permission`, `accounts`, `journal_entries` and `tests.area`. Production almost certainly carries both, so adding a value to `ChartType` or `ResultLayout` would fail every save of a profile until they are dropped. Out of scope here: this change is additive and has nothing to run against production, whereas dropping them does — verified by the finding being written down and by no `drop constraint` statement having been added
