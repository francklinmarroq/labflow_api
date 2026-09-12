## Why

`src/main/resources/schema.sql` cannot rebuild a working database. Five columns the entities map are absent from the script and exist in production only because `spring.jpa.hibernate.ddl-auto=update` was still allowed to alter the schema when they shipped, in June and July 2026:

| Column | Entity that maps it |
| --- | --- |
| `test_config.chart_type` | `TestConfig.chartType` |
| `test_config.result_layout` | `TestConfig.resultLayout` |
| `test_config.chart_x_axis_label` | `TestConfig.chartXAxisLabel` |
| `test_config_parameters.display_order` | `TestConfigParameter.displayOrder` |
| `test_config_parameters.chart_x_value` | `TestConfigParameter.chartXValue` |

`test_config_parameters` is not mentioned in the script at all.

That era is over, and `fix-test-attachments-toggle-persistence` is what it costs when the gap is discovered late: `test_config.allow_result_attachments` shipped the same way in `5243610`, `ddl-auto` could no longer create it in production, and the exam editor returned `500` for every exam until the DDL was run by hand. These five columns are the same defect that has not gone off yet — they are only safe because production already has them. A database restored from `schema.sql`, or any new environment built from it, would come up with a silently broken exam catalog: no chart configuration, no antibiogram layout, and no parameter ordering on the printed report.

The rule the constitution already states — an entity column implies a `schema.sql` entry — is currently unenforced, which is why the same omission has happened at least three times (`77af8f2` for the `laboratory_id` columns, `a165ed1`'s flag riding an undeployed version, and `5243610`).

## What Changes

- Add the five columns to `src/main/resources/schema.sql` as idempotent statements, written to be no-ops against production, which already has all of them.
- Add `test_config_parameters` itself, since the script never creates the table the two columns live on.
- Each statement carries the Spanish comment the file's convention calls for: which entity maps the column and what breaks without it.
- Verify idempotency the way the previous change did — run the new statements twice against a scratch PostgreSQL, and once more against a database `ddl-auto` has already built.
- No Java change, no API change, no redeploy. Production already has every column; this change only makes the script tell the truth.

## Capabilities

### New Capabilities
<!-- None: this change adds no observable behavior. -->

### Modified Capabilities
<!-- None: the exam editor's behavior is unchanged and already specified by test-catalog. -->

This is a repair to a migration script with no externally visible behavior change, so it sets `skip_specs: true`. The behavior these columns support is already specified — `test-catalog` requires that saving an exam persists `chartType`, `chartXAxisLabel` and `resultLayout` and that a later read returns them unchanged. What is missing is not a requirement but the DDL that lets a rebuilt database satisfy it.

## Impact

- `src/main/resources/schema.sql` — one new block: `create table if not exists test_config_parameters (...)`, three `alter table if exists test_config add column if not exists ...`, and two `alter table if exists test_config_parameters add column if not exists ...`.
- **Production database** — nothing to run. Unlike the previous change, this one is not a hotfix: every column already exists there. The statements are written so that applying them is a no-op.
- No entity, DTO, service or controller change. No AOT/reflection change. No image rebuild and no `wrangler.jsonc` bump.
- Out of scope: introducing Flyway or Liquibase, and removing the dependence on `ddl-auto=update`. Both are real and both are larger than this.
- Also out of scope but recorded in design.md as the durable fix: a build-time check that every mapped `@Column` has a matching statement in `schema.sql`. The H2 test suite structurally cannot catch this class of outage, because `ddl-auto` builds the H2 schema from the same entities the code reads.
