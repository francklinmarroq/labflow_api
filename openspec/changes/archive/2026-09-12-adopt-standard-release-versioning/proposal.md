## Why

**There are no git tags in this repository.** Not one. The `vX.Y.Z` series that the deploy procedure, the Docker images and the announcements all refer to exists in exactly one place: an image tag string inside `wrangler.jsonc`, edited by hand. `pom.xml` still says `0.0.1-SNAPSHOT` and never moved.

That is not a bookkeeping complaint — it is the direct cause of the outage this project just finished repairing. Production ran `v1.5.0` until a commit bumped it straight to `v1.7.0`, because **`v1.6.0` was pinned but never deployed** and nothing recorded that. There was no tag to diff against, no changelog listing what fell in the gap, and so the two data-model commits in that range went unnoticed until every query against `test_config` started failing. Reconstructing what shipped required reading a hundred commits by hand.

A version that exists only as a string in a config file cannot be checked, cannot be diffed, and cannot tell anyone what is in it. And the release-notes modal now being proposed needs one thing above all: a release identifier that reliably means something.

## What Changes

- **`commit-and-tag-version`** drives releases: it reads the conventional-commit history, decides the bump, writes `CHANGELOG.md`, commits and creates an annotated git tag. It is the maintained drop-in fork of the archived `standard-version` — same CLI, same `.versionrc` configuration.
- **One product version, shared with `labflow_frontend` in lockstep.** Both repositories carry the same `vX.Y.Z` and are released together, so "LabFlow 1.8.0" identifies the whole app — which is what the release-notes modal announces and what the Docker image series already implies.
- A release updates **every place the version is written**: `package.json`, `pom.xml`, and the `containers[].image` tags in `wrangler.jsonc`. Today those three disagree, and the only one anyone maintains is the third.
- **`CHANGELOG.md`** is generated from commit subjects and is developer-facing. It is deliberately *not* the user-facing release notes — those are hand-written Spanish copy in the frontend repository, and the only thing the two share is the version string.
- **`commitlint` + `husky`** reject a commit whose subject does not conform, so the changelog stops silently losing commits. The configuration registers the types this project actually uses, including the frontend's `design`, and the release procedure is documented in `AGENTS.md`.
- **The existing `v1.7.0` is tagged retroactively** at the commit that deployed it, so the first generated changelog has a floor and does not attempt to describe the entire history of the project.
- No change to the application, the API surface, the database, or how the Docker image is built and pushed. This is the release process only.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
<!-- None. -->

This change alters no externally observable behavior of the API: no endpoint, payload, permission or stored value changes. It is developer tooling and process, so it sets `skip_specs: true`. Writing a requirement such as "a CHANGELOG SHALL exist" would be inventing a spec to satisfy validation rather than describing behavior the system owes anyone. The one place a release *does* meet observable behavior — the version string the release-notes modal announces — is already specified by `release-notes-seen` here and `release-notes-announcement` in the frontend.

## Impact

- **New:** `CHANGELOG.md`, `.versionrc.js` (or equivalent) configuring the bump targets and custom updaters, `commitlint.config.js`, and a `.husky/` hook.
- `package.json` — gains a `version` field (it has none), the release scripts, and the three dev dependencies. Note this is the *worker's* `package.json`; it is the anchor the tool reads, which is a slight semantic stretch worth documenting in place.
- `pom.xml` — the version moves off `0.0.1-SNAPSHOT` and is thereafter written by the release, not by hand.
- `wrangler.jsonc` — the `containers[].image` tags are updated by the release. **This file is JSONC with substantial explanatory comments**, so it needs a comment-preserving updater; a naive JSON round-trip would destroy them.
- `AGENTS.md` — the release procedure, and the commit-type list. The current instruction (`chore: bump de imagen de despliegue a vX.Y.Z`, edited by hand) is replaced by the release command.
- **Git history:** one retroactive tag for `v1.7.0`. No history is rewritten.
- No `.github/` and no CI is introduced. Releases are run locally and deliberately, which suits a repository that has no CI today.
- Coordination cost, stated plainly: because lockstep means both repositories release together, a release where only one repo changed still tags both. The version number is a product fact, not a per-codebase one — that is the trade accepted in exchange for one number the modal can announce.
- Out of scope: automating the Docker image build or push, introducing CI, publishing to any registry, changing the branch model, and rewriting existing commit messages that predate the hook.
