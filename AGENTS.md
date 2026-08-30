# AGENTS.md

LabFlow backend: Spring Boot 4.0.6 / Java 25, PostgreSQL, JWT auth (Spring Security), multi-tenant by laboratory. Deployed as a GraalVM native image inside Cloudflare Containers, fronted by a Cloudflare Worker. Code, comments, and commit messages are in Spanish.

## Constitution

`docs/constitution.md` holds the non-negotiable principles for this repo (stack simplicity, spec-first via OpenSpec, controllers orchestrate / services hold logic, tests with every behavior change, idempotent `schema.sql` migrations, English identifiers with Spanish comments and messages). Read it before changing code; it wins over anything below.

## Build & run

- The Maven wrapper is **incomplete** — there is no `.mvn/wrapper/`, so `./mvnw` does not work. Use a system `mvn` and always pass the settings file (Aliyun mirror to avoid Maven Central rate-limits):
  - `mvn -B --settings .mvn/settings.xml <goal>`
- Run the API locally: load `.env` (format `export KEY=value`, gitignored) then `mvn --settings .mvn/settings.xml spring-boot:run`. `.claude/run-api.ps1` does this.
- Build the native image (Cloudflare Containers only runs `linux/amd64`):
  - `docker buildx build --builder <builder> --tag luciaelabs/labflow_backend:vX.Y.Z --push .`
  - Native-image needs >7.67 GB heap. On low-RAM machines pass `--build-arg NATIVE_XMX=6g --build-arg NATIVE_PARALLELISM=4`. See comments in `Dockerfile`.

## Tests

- `mvn -B --settings .mvn/settings.xml test`
- Single test: `mvn -B --settings .mvn/settings.xml test -Dtest=ClassName`
- Most tests run on H2 (auto). Exception: `PostgresQueryCompatibilityTest` runs against a real PostgreSQL at `localhost:55432` and **skips itself** if it is not running (do not expect it to fail the build). To run it:
  - `docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine`

## Database schema / migrations (critical)

- **No Flyway/Liquibase.** `spring.sql.init.mode=never` and `spring.jpa.hibernate.ddl-auto=update`.
- `src/main/resources/schema.sql` is **not auto-run by Spring**. It is a hand-run migration script (idempotent `ALTER TABLE ... IF EXISTS`) executed manually against the prod DB with admin credentials; in prod the app has no DDL privileges.
- Rule when you add a column to an entity: add a matching idempotent `alter table <t> add column if not exists ...` in `schema.sql`, or every query on that table breaks in prod ("column does not exist"). New tables go in as `create table if not exists`.
- `@Enumerated(STRING)` columns have a DB check constraint that `ddl-auto` **never updates**. Adding an enum value requires dropping the old `..._check` constraint in `schema.sql` (see existing examples) or inserts fail with "violates check constraint".
- Statements must be single statements, no `DO $$` PL/pgSQL blocks — Spring splits `schema.sql` on `;` and does not understand dollar-quoting.
- Startup data fixes live in `config/` as `CommandLineRunner` beans (`PublicTokenBackfill`, `TestConfigOrderBackfill`, `AppUserUsernameUniqueFix`). They must stay idempotent and must not crash the app on error (they use `JdbcTemplate` and catch exceptions).

## Multi-tenancy

- Entities are annotated `@TenantId` (laboratory). `TenantContext` (a `ThreadLocal`) is set per request by `AuthTokenFilter` and cleared afterward; `TenantIdentifierResolver` makes Hibernate auto-filter by tenant.
- Direct `JdbcTemplate`/native SQL bypasses the tenant filter. Backfills use this deliberately to reach all tenants; in normal feature code prefer repositories or you will leak data across labs.
- Spring Boot 4 does not auto-detect the tenant resolver — it is registered via `HibernatePropertiesCustomizer`.

## Native image / GraalVM reflection

- Reflection hints are registered through Spring AOT in `config/AppConfig.NativeRuntimeHints`, not loose `META-INF` files (except the jjwt `reflect-config.json` it parses).
- The whole `payload/**` package is registered for Jackson binding because `ResponseEntity<?>` erases return types. New request/response DTOs must live in `payload` or be registered explicitly, or they serialize as `{}`.
- jjwt and pgjdbc SSL classes are registered there too; do not remove without reason.

## Cloudflare Worker / deploy

- `worker/index.ts` defines `LabflowApiContainer extends Container`; its `defaultPort = 8080` must match `SERVER_PORT=8080` in `Dockerfile`.
- Deploying a new backend = push a new Docker image, then bump `containers[0].image` in `wrangler.jsonc` to the new tag (`luciaelabs/labflow_backend:vX.Y.Z`). Commit convention: `chore: bump de imagen de despliegue a vX.Y.Z`.
- Worker secrets come from `wrangler secret put` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `RESEND_API_KEY`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`); non-secret config is in `wrangler.jsonc` `vars`. Do not hardcode secrets in `worker/index.ts` or `wrangler.jsonc`.
- Deploy worker: `wrangler deploy`. Typecheck worker: `tsc` (uses pnpm; `pnpm-workspace.yaml` allows esbuild/workerd build scripts).
- `.env.wrangler` holds real prod credentials and is **not** in `.gitignore`; never `git add` it.

## Repo boundaries

- `src/main/java/marroquinsoftware/labflowapi/` — the Spring app (`controller/v1`, `service`, `repositories`, `model`, `payload` DTOs, `security`, `tenant`, `config`).
- `worker/` — Cloudflare Worker (TypeScript) that fronts the container; separate toolchain (pnpm + wrangler).
- Sibling repos (not here): `../labflow_frontend` (Nuxt) and `../labflow_website` — see `.claude/launch.json` for their dev commands.
- Default branch is `develop`; many `claude/*` working branches exist.
