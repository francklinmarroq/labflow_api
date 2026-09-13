## 1. Persistence

- [x] 1.1 Add a nullable column to `model/User.java` holding the newest release-notes version the user has been shown, named in English with a Spanish comment stating that null means "has seen nothing" and that the API never interprets the string — verified by the field being present and nullable
- [x] 1.2 Append the DDL to `src/main/resources/schema.sql`: a single idempotent `alter table if exists app_user add column if not exists ...`, modelled on the three statements already there for the same table (`schema.sql:129` for `name`, `:148-149` for the reset-token columns) and carrying the same kind of Spanish comment — those note that a column `User` maps but `app_user` lacks makes **every** query against that table fail, login included — verified by the statement being present, standing alone with no `DO $$` block, and naming `app_user`
- [x] 1.3 Run `schema.sql`'s new statement twice against a scratch PostgreSQL (`docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine`) — verified by the second run completing with no error, proving idempotency
- [x] 1.4 Confirm no backfill is written — verified by every existing user reading the column as null, which is the specified "has seen nothing" state

## 2. Read path

- [x] 2.1 Add the field to `payload/UserInfoResponse.java` **as the last constructor argument** — verified by the field being last in both the class and the constructor, since inserting it earlier silently shifts the existing string arguments
- [x] 2.2 Populate it in `AuthController.me` from the authenticated user's stored value, read from the database rather than from the JWT — verified by marking a version and reading `me` in the same session reporting the new value
- [x] 2.3 Confirm nothing about the marker enters the JWT or `AppUserDetails` — verified by grepping the token-building path and finding no reference to it

## 3. Write path

- [x] 3.1 Add a service method that sets the marker for a given username, ignoring an empty or absent version so the value can never be cleared — verified by a test that marking with an empty value leaves the stored value intact
- [x] 3.2 Add an endpoint under `/api/v1/auth` that marks the version seen for the caller, taking the version in the body and deriving the user from `@AuthenticationPrincipal` — verified by the endpoint having no user identifier in its path or body
- [x] 3.3 Confirm the endpoint carries no `@PreAuthorize` permission requirement beyond authentication — verified by an authenticated user with no permissions being able to call it successfully
- [x] 3.4 Confirm no new `Permission` enum value was added — verified by `Permission.java` being unchanged, so no check constraint has to be dropped

## 4. Regression tests

- [x] 4.1 Add a test class alongside the existing tests in `src/test/java/marroquinsoftware/labflowapi/`, using the `@DataJpaTest` + H2 + `TenantContext` setup the rest of the suite uses — verified by the class compiling and running
- [x] 4.2 Cover "reading identity after having seen a release" and "a user who has never been marked": the marked version comes back unchanged, and an unmarked user reports absent with no default substituted — verified by both tests passing
- [x] 4.3 Cover "the API does not interpret the version": a string in an unfamiliar format round-trips unchanged — verified by the test passing
- [x] 4.4 Cover "the mark survives a new session": the stored value is still reported after the persistence context is flushed and cleared — verified by the test passing
- [x] 4.5 Cover per-user isolation: marking one user's version leaves every other user's value untouched, including another user in the same laboratory — verified by the test passing
- [x] 4.6 Cover "a mark cannot clear the marker": an empty or absent version leaves the stored value as it was — verified by the test passing
- [x] 4.7 Assert on the **values** returned by `me`, not merely their presence, so a future reordering of the positional constructor is caught — verified by the test failing if two adjacent fields are swapped
- [x] 4.8 Run `mvn -B --settings .mvn/settings.xml test -Dtest=<NewTestClass>` — verified by the new suite passing on H2

## 5. Verification

- [x] 5.1 Run `mvn -B --settings .mvn/settings.xml test` — verified by the run being green, allowing for the two known pre-existing conditions (`PostgresQueryCompatibilityTest` self-skips without the local PostgreSQL; `LabflowapiApplicationTests` fails on `${DB_URL}` without a `.env`)
- [x] 5.2 Apply the DDL to the production database with admin credentials — verified by `information_schema.columns` listing the new column — confirmado por el equipo contra la base de producción el 2026-09-12
- [x] 5.3 Against the deployed API, call `me`, mark a version, and call `me` again — verified by the first reporting absent, the third reporting the marked version, with no re-login in between — confirmado por el equipo contra la API desplegada el 2026-09-12
