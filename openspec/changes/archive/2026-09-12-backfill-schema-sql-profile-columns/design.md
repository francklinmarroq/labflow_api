## Context

See proposal.md — Why. Five columns the entities map are missing from `src/main/resources/schema.sql` and exist in production only because `ddl-auto=update` created them when they shipped. Nothing is broken today; the exposure is a rebuild.

Constraints carried over from `fix-test-attachments-toggle-persistence`, which are properties of the repository rather than of that change:

- **No Flyway/Liquibase.** `spring.sql.init.mode=never`, `spring.jpa.hibernate.ddl-auto=update`. `schema.sql` is a hand-run, idempotent script executed against production with admin credentials; Spring never runs it.
- **Spring splits `schema.sql` on `;`** and does not understand dollar-quoting, so every statement must stand alone — no `DO $$` blocks, no PL/pgSQL.
- **`ddl-auto=update` can no longer alter the production schema.** It is what created these five columns, and it is exactly what is no longer available.
- **The script cannot run end to end on an empty database.** It already assumes `ddl-auto` created the base tables — `lab_order_tags` and `test_run_attachments` reference `lab_orders` and `test_runs`, which the script never creates. This change does not fix that; it stays scoped to the profile columns, and the statements must be verifiable in isolation.
- **Production already has every column.** So, as in the previous change, correctness against an empty database is not enough: each statement must also be a no-op against the state production is in.

## Goals / Non-Goals

**Goals:**

- `schema.sql` mentions every column the exam-profile entities map, so a database built from the script has a working exam catalog.
- The new statements are idempotent under both starting states — the column absent, and the column already present.
- The reason each statement exists is written down next to it, in the file's existing Spanish-comment convention.

**Non-Goals:**

- Making `schema.sql` able to build a database from empty. That needs the base tables `ddl-auto` still owns, and is a much larger change.
- Introducing a migration tool, or removing the dependence on `ddl-auto=update`.
- Any Java, API or deployment change. Production needs nothing run against it.
- Auditing every other entity in the project against the script by hand. The build-time check below is the answer to that, not a one-off sweep.

## Decisions

### Create `test_config_parameters` from the entity's mapping

The two missing columns live on a table the script never creates, so the table comes first:

```sql
create table if not exists test_config_parameters (
  test_config_id bigint not null references test_config(id),
  parameter_id bigint not null references parameter(id),
  display_order integer,
  chart_x_value numeric(19,2),
  primary key (test_config_id, parameter_id)
);
```

The composite primary key comes from `@IdClass(TestConfigParameterId.class)` with both `@ManyToOne` associations annotated `@Id`. `display_order` and `chart_x_value` are nullable because the entity documents them as such — rows written before the ordering existed are left NULL and sort last, and `chart_x_value` only means anything on a curve profile.

The exact referenced table names and the `numeric` precision must be read off the running database before this is written, not guessed: `Parameter`'s table name and Hibernate's default `BigDecimal` mapping both need confirming against `information_schema.columns` in production. That verification belongs in tasks.md.

### Add the three `test_config` columns as separate idempotent statements

```sql
alter table if exists test_config add column if not exists chart_type varchar(255);
alter table if exists test_config add column if not exists result_layout varchar(255);
alter table if exists test_config add column if not exists chart_x_axis_label varchar(255);
```

`chart_type` and `result_layout` are `@Enumerated(EnumType.STRING)`, so they are `varchar`, not integers — getting this wrong would produce a database that reads every profile's layout as garbage rather than failing loudly.

No `not null` and no default, matching the entities: both fields carry a Java-side initialiser (`ChartType.NONE`, `ResultLayout.STANDARD`) and `TestConfigServiceImp.toDTO` already maps a null to the default on read, so a null column is handled everywhere it is observed. This is the same reasoning that rejected `not null default false` for `allow_result_attachments`: adding `not null` to a column that may already exist as nullable needs a second statement that fails when it is already `not null`, and the script cannot branch.

**No check constraints for the two enum columns.** The file already drops such constraints on purpose — `ddl-auto` does not update a check when an enum gains a value, so guarding a saved profile with one breaks the save. The enum in Java is the validity guarantee.

*Alternative considered:* backfilling the two enum columns with their defaults, as the previous change did for `allow_result_attachments`. Rejected — production rows already hold real values, so a backfill would either be a no-op or would overwrite a laboratory's chosen layout. The `where ... is null` guard makes it safe, but it buys nothing that the read-side default does not already provide.

### Verify against a scratch PostgreSQL, not against production

The previous change's procedure applies unchanged: run the new block twice against

```
docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test \
  -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine
```

and confirm the second run is clean, then run it once more after letting the app build the schema via `ddl-auto` — which reproduces the state production is in. Because the script as a whole cannot run on an empty database, the verification is scoped to the new block, exactly as it was for `test_run_attachments`.

### The durable fix: check entity mappings against `schema.sql` at build time

This change is the third time the same omission has been repaired by hand, so the interesting decision is how to stop there being a fourth.

**The H2 test suite structurally cannot catch this class of outage.** Every test runs on H2 with `ddl-auto=create-drop`, so Hibernate builds the test schema from the very entities the code then reads. The mapped column is always present, by construction. A test can prove the Java path is correct — `fix-test-attachments-toggle-persistence` planned exactly such tests — and the production database can still be missing the column. No amount of test coverage at that layer closes this gap.

What would close it is a build-time check with a different input: enumerate every `@Column` (and `@JoinColumn`, and `@Table`) on every `@Entity`, then assert that `src/main/resources/schema.sql` contains a statement that would create it. Sketch of the shape:

- Scan the entity classes — by reflection over `marroquinsoftware.labflowapi.model`, or from the Hibernate metamodel, which already resolves implicit names and defaults and so avoids reimplementing Hibernate's naming strategy.
- Ask Hibernate for the DDL it would generate against `PostgreSQLDialect` (`SchemaExport` / `hbm2ddl` to a file, not to a database), which yields the authoritative set of tables and columns.
- Diff that set against the tables and columns `schema.sql` mentions, and fail with the missing ones named.
- Allow an explicit, commented exemption list, since the script legitimately does not create the base tables `ddl-auto` owns — otherwise the check fails on day one and gets disabled.

Run as a test, it fails the build the moment an entity gains a column the script does not describe — which is the moment it is cheap to fix, rather than during an outage. That exemption list is also the honest inventory of how much of the schema still depends on `ddl-auto`, which no one currently has.

This is deliberately **not** in scope here. It is a different kind of work — build tooling, not a migration — and this change should stay small enough to review while the previous outage is still fresh. It is recorded here so the idea is not lost with the session that produced it.

## Risks / Trade-offs

- **The column types are inferred from the entity mappings, not read from production.** A wrong type — `integer` for a `@Enumerated(STRING)` column, or the wrong `numeric` precision — produces a database that comes up and then misreads data, which is worse than one that fails. → Mitigated by making "read the live types out of `information_schema.columns` and match them" an explicit task rather than a review comment.

- **This change makes `schema.sql` look more trustworthy than it is.** After it lands, the exam-profile tables are described but the base tables still are not, so a reader may conclude the script can rebuild a database when it cannot. → The block gets a comment saying so, and the build-time check above is what would eventually make the claim true.

- **Nothing verifies the result.** There is no failing symptom to fix and no test that can cover it, so the only evidence this change is correct is the scratch-database run. → That is why the verification is three separate runs against two starting states, and why the build-time check matters more than this change does.

## Migration Plan

1. Read the live column types for the five columns and the `test_config_parameters` table out of production's `information_schema.columns`.
2. Write the block into `schema.sql` to match, with its comments.
3. Verify on a scratch PostgreSQL: twice from the post-`ddl-auto` state, once more after a fresh `ddl-auto` build.
4. Nothing to run against production, and nothing to deploy.

**Rollback:** none needed. Every statement is additive and guarded by `if not exists`, and production already satisfies all of them, so the change is inert there by construction.
