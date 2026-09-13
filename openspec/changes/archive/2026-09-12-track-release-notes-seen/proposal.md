## Why

Features ship and nobody tells the users. The exam-attachments switch, the report range-alert toggle, order tags — each landed in a deploy and each was discovered by whoever happened to click on it. There is no channel from a release to the people using it, so the laboratory staff learn the app changed by noticing that it changed.

The frontend is going to announce each release in a modal on login (proposed separately in `labflow_frontend`). For that to work, the API has to answer one question per user: **what is the newest release this person has already been shown?** Without it the modal either appears every single login, which trains people to dismiss it unread, or it remembers in the browser — which loses the answer on every new device and cleared cache, and means nobody can ever say whether a release note was actually read.

The state belongs on the server for the same reason every other piece of state in this system does: it must outlive the session and it must be the same answer on every device the user opens.

## What Changes

- A user record gains the newest release-notes version that user has been shown. It is per user, so one person's dismissal never speaks for anyone else.
- `GET /api/v1/auth/me` — already called by the frontend on every app load to refresh permissions — reports that value, so the client can decide whether to announce without a second request.
- A new endpoint marks the current user as having seen a given version. It is the only way the value changes, it applies only to the caller, and it never moves backwards: marking an older version seen leaves a newer one in place.
- A user who has never seen anything reports an absent value, which the client reads as "has seen nothing" and announces the current release. No backfill invents a history that did not happen.
- The API stores an **opaque version string** and does not interpret it. It does not know what a release contains, does not hold the announcement text, and does not decide whether the modal should open — the content lives in the frontend repository alongside the code that shipped the features. The API only remembers a marker per user.
- No new permission. Every authenticated user may read and mark their own state, and may not touch anyone else's.

## Capabilities

### New Capabilities
- `release-notes-seen`: the per-user record of which release announcement a user has already been shown — how it is read, how it is marked, and the guarantees that keep one user's state from affecting another's.

### Modified Capabilities
<!-- None: openspec/specs/ holds laboratory-settings, test-catalog and order-composition (pending), none of which specifies session identity or user preferences. -->

## Impact

- `model/User.java` — one new nullable column holding the version marker. Nullable is the "never seen anything" state and needs no backfill.
- `src/main/resources/schema.sql` — the `alter table if exists ... add column if not exists` for that column, idempotent, with the Spanish comment the file's convention requires. **This is not optional**: a mapped column absent from the script is exactly the defect that took the exam editor down in production, so the DDL lands with the entity, in the same change.
- `payload/UserInfoResponse.java` — one new field. Note it is built by a positional constructor in `AuthController.me`, so the call site changes with it; a field added in the wrong position would silently swap two strings.
- `controller/v1/AuthController.java` — the `me` response gains the field, and one new endpoint under `/api/v1/auth` marks the version seen for the caller.
- A service and repository method to write the marker for the authenticated user.
- New tests under `src/test/java/marroquinsoftware/labflowapi/` covering the read, the mark, the never-moves-backwards rule and the per-user isolation.
- **Multi-tenant consequence worth stating plainly**: a `User` row is per (email, laboratory) — the same person belonging to three laboratories has three rows. Storing the marker on `User` therefore announces a release once per laboratory that person switches into. That is the behavior this change specifies; storing it per email instead would mean a new table keyed on the username, and is called out in design.md as the alternative.
- No change to authentication, JWT contents, login, or the laboratory-selection flow. The marker is read from the database on `me`, not carried in the token, so it is never stale.
- Out of scope: the announcement content and the modal (both in `labflow_frontend`), any admin screen for publishing releases, any reporting on who has read what, and any notification channel other than the app itself.
