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
- **No lazy to-one associations.** `@ManyToOne(fetch = LAZY)` (and `@OneToOne(fetch = LAZY)`) needs a Hibernate proxy class that must exist from build time; the native image cannot produce one at runtime. Every to-one in `model/` is EAGER and fetch-joined where a page of rows is read (`BillingSpecifications`, the repositories' `@EntityGraph`). `Invoice.billingClient` was the one exception, for a page or two of saved joins, and it took invoice reading down on develop the moment the first invoice was issued to a company: every row with a non-null FK returned 500, rows with a null FK were fine, and the whole suite stayed green because it runs on H2 on the JVM. `NativeImageLazyAssociationTest` now fails the build on any lazy to-one; if one is genuinely needed, register its proxy here, verify it **in the native image** — the suite structurally cannot — and add the field to that test's allowlist.

## Branches & promotion

`develop` is where work lands; `main` is production. The flow is one-way and has no shortcuts:

1. Work on `develop` (or a branch off it) and push.
2. Prove the change on the **develop** environment: `wrangler deploy --env develop`, exercised against the develop database. Green tests are not proof — both outages this repo has had were things the H2 suite structurally cannot catch.
3. Merge `develop` into `main` once it holds up there.

Nothing is committed straight to `main`.

**Merging to `main` deploys nothing here.** Unlike the frontend, which Cloudflare Pages builds on push, this repo's Worker and container go out by hand: `wrangler deploy` for production (top level, no `--env`), `wrangler deploy --env develop` for develop. So `main` records what production is *meant* to be running, not what it is. `v1.6.0` was pinned and never deployed, and the gap only surfaced as an outage two releases later.

**Order when promoting a change that touches the database:**

1. Run the new `schema.sql` statements against the production database with admin credentials, **before** the image that maps the new column reaches production. An entity mapping a column the database lacks breaks *every* query on that table, login included — that is how the exam editor went down, and how `app_user` nearly took the whole app with it.
2. Merge to `main`, cut the release, push the image, `wrangler deploy` — in that order, see **Releases → Order of operations**.
3. Deploy the API **before** merging the frontend. New columns, endpoints and response fields are additive, so an older frontend against a newer API degrades; the reverse breaks.

The develop environment has its own database, its own R2 bucket and its own frontend; nothing is shared with production. That is what makes it safe to test in — and useless as evidence *about* production. A column present in develop says nothing about prod: develop's app role may still hold the DDL privileges production's lost, in which case `ddl-auto` created it there and only there.

## Cloudflare Worker / deploy

- `worker/index.ts` defines `LabflowApiContainer extends Container`; its `defaultPort = 8080` must match `SERVER_PORT=8080` in `Dockerfile`.
- Deploying a new backend = cut a release (see **Releases** below, which bumps `containers[0].image` in `wrangler.jsonc` for you), then push the matching Docker image tag, then `wrangler deploy`. Do **not** edit the image tag by hand any more — the release writes it, along with `pom.xml` and `package.json`, so the three cannot drift.
- Worker secrets come from `wrangler secret put` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `RESEND_API_KEY`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`); non-secret config is in `wrangler.jsonc` `vars`. Do not hardcode secrets in `worker/index.ts` or `wrangler.jsonc`.
- Deploy worker: `wrangler deploy`. Typecheck worker: `tsc` (uses pnpm; `pnpm-workspace.yaml` allows esbuild/workerd build scripts).
- `.env.wrangler` holds real prod credentials and is **not** in `.gitignore`; never `git add` it.

## Releases

Versioning is driven by `commit-and-tag-version` (the maintained fork of the archived `standard-version`; the original's own deprecation notice points at `release-please`, which we declined because it needs CI and this repo has none). Config lives in `.versionrc.json`; releases are run locally and deliberately, never in a hook or on push.

- `pnpm release:dry` — dry run. Always read the diff before a real release.
- `pnpm release` — bumps the version, writes `CHANGELOG.md`, commits, and creates an annotated tag.
- `pnpm release -- --release-as X.Y.Z` — release as an exact version. This is the normal form here; see lockstep below.

A release writes the version to **three** places, via `bumpFiles` in `.versionrc.json`:

- `package.json` — the tool's anchor. Note this file describes the *Worker*, not the Spring Boot app; it holds the version because the tool needs a native anchor it understands.
- `pom.xml` — the project's own `<version>`, through `.release/updaters/pom-version.cjs`.
- `wrangler.jsonc` — the **prod** `containers[].image` tag only, through `.release/updaters/wrangler-image.cjs`.

Both updaters are hand-written text replacements, on purpose. The built-in `maven` updater reparses the XML and reformats the entire `pom.xml` (measured: 379 lines of diff on a 199-line file, 14 lines lost, the `<?xml?>` declaration and the inline `<!-- lookup parent from repository -->` comment destroyed). A JSON updater on `wrangler.jsonc` would delete every comment in it. Each updater throws rather than bumping the wrong line or none at all. The develop image entry is deliberately untouched — per that file's own comment, develop builds use `develop-<sha>` tags, not the `vX.Y.Z` series.

### Lockstep with the frontend

`labflow_api` and `labflow_frontend` share **one product version** and are released together, so "LabFlow X.Y.Z" names the whole app — which is what the release-notes modal announces to users. The tool derives its bump from each repo's own commits, so left alone the two would diverge (a `feat:` here and only a `fix:` there gives 1.8.0 and 1.7.1). Therefore:

1. `pnpm release:dry` in **both** repositories.
2. Take the **higher** of the two bumps.
3. `pnpm release -- --release-as X.Y.Z` in both, with that same version.

Write the frontend's user-facing release-notes entry for that version as part of the release; its release will refuse to proceed without one. `CHANGELOG.md` here is developer-facing and generated from commit subjects — it is *not* the users' release notes, and no user-facing copy is written in this repository.

### Order of operations

Release → build and push the Docker image for the new tag → `wrangler deploy`. In that order, because the release commit writes an image tag that does not exist yet; deploying between the release and the image push points the Worker at a missing image. Tagging the release before the image exists is also what makes the version series checkable in git — the absence of that is what let `v1.6.0` be pinned but never deployed.

### Commit convention

Enforced on `commit-msg` by commitlint + husky (`.commitlintrc.json`). The **type keyword is English**, the **subject stays Spanish**, as everywhere else in this repo:

`feat`, `fix`, `perf`, `refactor`, `design`, `docs`, `style`, `test`, `build`, `ci`, `chore`, `revert`

`design` is ours and is kept because it is genuinely used; it shows in the changelog. `feat` bumps the minor, `fix`/`perf`/`refactor` the patch. A breaking change is `feat!:` or a `BREAKING CHANGE:` footer — **not** `breaking-changes:`, which is not a registered type and would produce no major bump. `git commit --no-verify` bypasses the hook; it is there to catch slips, not to veto the repo owner.

## Repo boundaries

- `src/main/java/marroquinsoftware/labflowapi/` — the Spring app (`controller/v1`, `service`, `repositories`, `model`, `payload` DTOs, `security`, `tenant`, `config`).
- `worker/` — Cloudflare Worker (TypeScript) that fronts the container; separate toolchain (pnpm + wrangler).
- Sibling repos (not here): `../labflow_frontend` (Nuxt) and `../labflow_website` — see `.claude/launch.json` for their dev commands.
- Default branch is `develop`; many `claude/*` working branches exist.
