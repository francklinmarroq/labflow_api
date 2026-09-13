## Context

See proposal.md — Why, and specs/release-notes-seen/spec.md for the requirements. The current state that shapes the approach:

- **`GET /api/v1/auth/me` already exists and is already called on every app load** to refresh permissions without re-logging in. It is the natural carrier: the client needs the marker at exactly the moment it already asks this question.
- **`UserInfoResponse` is built by a positional constructor** in `AuthController.me` — seven string/collection arguments in a row. `LaboratoryReportFlagsTest` exists partly because a positional constructor silently swapped values once before.
- **A `User` row is per (email, laboratory).** The entity documents it: "el correo puede tener varias filas (una por laboratorio)", each with its own `name`. Whatever is stored on `User` is therefore per-membership, not per-person.
- **The session is a JWT** and `AppUserDetails` carries identity and permissions into the request. Anything put in the token goes stale until the next login.
- **No migration tool.** `schema.sql` is hand-run against production with admin credentials, split on `;`, no dollar-quoting. This repository has just spent an entire change recovering from a mapped column that never reached production, and a second change is open for five more columns in the same state.
- **`Permission` is a fixed enum** whose values are also written into a check constraint that `ddl-auto` cannot update — the script drops those constraints deliberately. Adding a permission is therefore not free.

## Goals / Non-Goals

**Goals:**

- One extra field on a request the client already makes, so announcing costs no additional round trip. On Cloudflare each request pays a ~0.7 s floor, which is why this matters.
- The marker cannot be read or written for anyone but the caller, by construction rather than by permission check.
- Dismissing an announcement is durable across devices and deploys.
- The DDL ships with the mapping, not after it.

**Non-Goals:**

- Understanding releases. No version parsing, no comparison, no ordering, no knowledge of what changed.
- Holding the announcement content. It lives in the frontend repository with the code that shipped the features.
- Reporting on who has read what. The data would support it later; no endpoint here does.
- Per-person (rather than per-membership) semantics. See the decision below — it is a deliberate trade.

## Decisions

### Store an opaque string, not a parsed version or a foreign key

The column holds whatever the client sends — `"1.8.0"`, `"2026-09"`, anything. The API never compares two of them.

This is what keeps the API out of the release business. The alternative is a `releases` table with an ordering column and a join, which would let the server decide whether an announcement is due — and would then need an admin surface to populate it, migrations every release, and a second source of truth that can disagree with the frontend about what shipped. The content lives next to the code that shipped it; the server only needs to remember a bookmark.

The cost is that the never-moves-backwards rule cannot be fully enforced server-side, because enforcing it means ordering the strings. The specs are deliberately written to the guarantee the API can actually keep — a mark never clears the value — and to leave the ordering to the client that defines it. Overpromising here would be worse than the honest, narrower guarantee.

*Alternative considered:* an integer sequence the API can compare. Rejected — it forces the frontend's human-readable release identity through a numeric mapping that has to be maintained in two repositories, to buy an invariant the client already maintains by only ever marking the release it just displayed.

### Put the marker on `User`, and accept per-membership semantics

One nullable column on `User`. A person in three laboratories is three rows and will see the announcement once per laboratory they switch into.

That is the wrong answer in the abstract and the right one here. The alternative is a table keyed on `username` — a new entity, its own DDL, its own repository, and a read that `me` must join on every app load — to fix a case that affects only multi-lab users and whose failure mode is seeing a "what's new" modal a second time after switching laboratories. Given that switching laboratories is itself a deliberate act of changing context, showing that context's news again is defensible rather than merely tolerable.

Recorded explicitly in the proposal's Impact rather than buried, so if it turns out to annoy the multi-lab users, the fix is a known, scoped follow-up and not a surprise.

*Alternative considered:* keying on `username` in a new `user_release_notes` table. Rejected for now, as above. Worth revisiting if the multi-lab population grows.

### Read from the database on `me`, never from the token

`me` already loads the authenticated principal; the marker is read from storage in the same request. Nothing about it goes into the JWT.

If it were in the token, marking a release seen would not take effect until the next login — so a user who dismissed the modal would be shown it again on the next app load, which is the exact defect this change exists to prevent. The specs require the read to reflect the current stored value for this reason.

### Derive the target user from the authentication, never from the request

The mark endpoint takes the version and nothing else. The user it writes to comes from `@AuthenticationPrincipal`, so there is no user identifier in the path or the body for a caller to change.

This is why the specs can say "there is no way through it for one user to change another's" as a property rather than as a permission check: the endpoint is incapable of addressing another user. Adding a `Permission` value would be both unnecessary and expensive — the enum feeds a check constraint the script has to drop by hand.

*Alternative considered:* `PATCH /api/v1/users/{id}` style, gated by a permission. Rejected — it invents an authorization question ("may this user mark that user?") that need not exist.

### Extend `UserInfoResponse` by appending, and treat the constructor as the risk

The new field goes last in both the class and the `new UserInfoResponse(...)` call in `AuthController.me`.

The positional constructor is the hazard: inserting the field anywhere but the end silently shifts every argument after it, and because they are almost all strings, nothing fails to compile and nothing fails at runtime — the user simply sees the laboratory name where their role should be. Appending is the mitigation, and a test that asserts on the field values rather than only their presence is what catches it if someone reorders later.

## Risks / Trade-offs

- **The never-moves-backwards guarantee is the client's to keep.** A client that marks an old version would move the marker back and re-announce an older release. → The API's stated guarantee is narrower and honest (a mark never clears the value); the frontend only ever marks the release it just displayed, and its own change specifies that. A server-side ordering rule would require the version parsing this design deliberately refuses.

- **A mapped column missing from `schema.sql` is this repository's known failure mode.** It has happened at least three times and once caused an outage. → The DDL is a task in the same change, not a follow-up, and it is written idempotently so it is a no-op where the column already exists.

- **Multi-lab users see an announcement once per laboratory.** → Deliberate, documented in the proposal, and reversible via the `username`-keyed table if it proves annoying.

- **A user who clears nothing and simply never dismisses will be re-announced every load.** The marker only advances when the client marks it. → That is the frontend's concern: its change specifies marking on dismissal *and* on reaching the last slide, so a user who reads the notes is never asked twice.

## Migration Plan

1. Land the entity column, the `schema.sql` statement, the `me` field and the mark endpoint together. The column without the DDL is the outage this project already had.
2. Run the idempotent `alter table` against production with admin credentials, as the script's convention requires. No backfill: every existing user reads as absent, which is the correct "has seen nothing" state.
3. Deploy the API before the frontend change. An API carrying a field no client reads is inert; a client reading a field no API sends would announce on every load.

**Rollback:** revert the code. The column is additive and nullable, so an older image simply stops mapping it and the stored markers sit unread.
