## Context

See proposal.md — Why. The specs artifact is deliberately absent (`skip_specs: true`): this change alters no observable API behavior.

Constraints that shape the approach:

- **`standard-version` is archived.** It was deprecated in 2022 and its README points at successors. `commit-and-tag-version` is the drop-in fork — same CLI, same `.versionrc`, same behavior — and is what "use standard-version" resolves to in practice. Confirm this at install time rather than taking it on faith.
- **Three files carry a version and none agree.** `pom.xml` is `0.0.1-SNAPSHOT`, `package.json` has no `version` at all, and `wrangler.jsonc` has `docker.io/luciaelabs/labflow_backend:v1.7.0` (twice — prod and develop, the develop entry currently reusing the prod image with a `-develop` suffix).
- **`wrangler.jsonc` is JSONC and heavily commented.** The comments explain the container placement, the DO migrations and the develop-image caveat. Any updater that parses and re-serialises JSON will delete all of it.
- **`pom.xml` is XML**, and the `<version>` that matters is the project's own — the third `<version>` element in the file, surrounded by dependency versions that must not be touched.
- **No CI exists.** No `.github/` in either repository. Anything requiring a runner is a new system, not a configuration change.
- **Conventional commits are ~70% adopted** in the last hundred commits (feat 28, fix 22, perf 9, chore 8), and most of the remainder are PR merge commits, which these tools skip anyway. The genuine gaps are a handful of bare Spanish subjects and one `breaking-changes:` where `feat!:` was meant.
- **Commit messages, comments and code are Spanish**, per `AGENTS.md`. Conventional-commit *types* are English keywords; the subject after the colon is Spanish. That combination is already in use and works fine.
- **No Maven on this machine** — `mvn` is not installed and `.mvn/wrapper/` is absent, so anything that shells out to Maven to set the version will not run here.

## Goals / Non-Goals

**Goals:**

- Every released version is an annotated git tag, so "what shipped between these two versions" is answerable with `git log`.
- The three version locations are written by one command and cannot drift.
- The comments in `wrangler.jsonc` survive a release.
- A commit that would vanish from the changelog is rejected before it exists.
- The version the frontend's release-notes modal announces is the same number this repository tagged.

**Non-Goals:**

- Automating the image build or push. The release writes the tag it *expects*; pushing the image stays a human step.
- CI. Releases are local and deliberate.
- Rewriting history or retro-fixing old commit subjects.
- Deriving the bump automatically across both repositories — see the lockstep decision, which makes the number an explicit input.

## Decisions

### `commit-and-tag-version`, run locally, never in a hook

A `release` script in `package.json`. It bumps, writes the changelog, commits and tags in one step, and it is run by a person who then pushes the tag and the image.

Local rather than CI because there is no CI, and introducing GitHub Actions to get a version number is a large change for a small need. Deliberate rather than automatic because a release here implies pushing a GraalVM native image and editing a deployed Worker's config — the two things whose mismatch caused the last outage. A tool that tags on every merged `feat:` would be automating the front half of a pipeline whose back half is manual, which is worse than either extreme.

*Alternative considered:* `release-please` with GitHub Actions, giving a reviewable release PR. Genuinely better for a team, and worth revisiting if this becomes one. Rejected now because it introduces CI to two repositories that have none, to solve a problem that a local command solves.

### Lockstep by explicit version, not by two independent computations

The product version is decided once and both repositories are released **as that exact version**, via the tool's explicit-release flag rather than by letting each repo compute its own bump.

This is forced by the mechanics: `commit-and-tag-version` derives the bump from the commits in *its own* repository. Two repos with different histories will compute different bumps — a release where the frontend has a `feat:` and the API has only a `fix:` would produce 1.8.0 and 1.7.1. Lockstep means the number cannot be a per-repo computation.

The procedure, documented in `AGENTS.md`: inspect what each repository would bump to (dry run in both), take the **highest** of the two severities, and release both explicitly as that version. The human doing a release is already the person deciding to deploy; deciding the number is not extra burden, and it is the only way the two stay equal.

*Alternative considered:* one repository computes and the other follows. Rejected — whichever repo is nominated becomes wrong whenever the other one has the more significant change, and the failure is silent (a `feat:` in the API released as a patch).

### Custom updaters for `wrangler.jsonc` and `pom.xml`; `package.json` as the anchor

`package.json` gets a real `version` field and is the tool's native anchor. The other two are configured as additional bump targets with **custom updaters that operate on text, not on parsed documents**:

- `wrangler.jsonc` — a regex replacing the version inside the image tags, leaving every comment and the file's formatting untouched. This is the whole reason for a custom updater; the built-in JSON updater would round-trip the file and silently delete the comments that explain the container and migration setup.
- `pom.xml` — a regex anchored on the project's own `<version>` element, not on any `<version>` inside `<dependencies>` or `<plugins>`. Anchoring it loosely would bump a dependency and break the build in a way that looks like a supply-chain problem.

The develop image entry needs deciding explicitly: it currently reuses the prod image with a `-develop` suffix, and `wrangler.jsonc`'s own comment says develop builds should use `develop-<sha>` tags rather than the `vX.Y.Z` series. The updater should therefore touch the **prod** entry only, and the task list says so.

*Alternative considered:* shelling out to `mvn versions:set` for `pom.xml`. Rejected — there is no Maven on this machine, so the release would simply fail to run; and the tool's own updater mechanism keeps everything in one config.

Putting a `version` in the worker's `package.json` is a mild semantic stretch — that file describes the Worker, not the Spring Boot application. It is the anchor because the tool needs one it understands natively, and a comment in place says so.

### Tag `v1.7.0` retroactively, and start the changelog there

An annotated tag on the commit that deployed `v1.7.0` (`044ba38 Despliega version 1.7.0`), created before the first release.

Without a floor, the first `commit-and-tag-version` run walks the entire history and produces a changelog covering everything ever committed, most of it predating any convention. With the tag, the first generated entry covers only what came after — which is the honest and useful scope.

Deliberately **not** reconstructing tags for `v1.5.0` and earlier: the changelog they would produce would be a guess, and `v1.6.0` never shipped at all. One accurate floor beats a fabricated lineage.

### `commitlint` with the project's real type list, including `design`

The configuration extends the conventional preset and adds the types this project uses in practice. `design` is registered because the frontend has twelve of them in the last hundred commits and the distinction is evidently useful; dropping it would either invalidate history or push those commits into `feat:`, where they would misreport the bump.

`breaking-changes:` is **not** registered. It appears once, and it was reaching for a breaking-change marker that conventional commits already has — `feat!:` or a `BREAKING CHANGE:` footer, which the tool reads to drive a major bump. Registering a non-standard type for it would mean a breaking change that never triggers a major.

The hook runs on `commit-msg`. It rejects the subject at commit time, which is the only moment the fix is free.

*Alternative considered:* documenting the convention without enforcing it. It would probably be adequate given ~70% adoption — but the commits that get missed are exactly the ones made in a hurry during an incident, which are the ones a changelog most needs.

## Risks / Trade-offs

- **A regex updater on `pom.xml` or `wrangler.jsonc` that matches too much silently corrupts the file.** A bumped dependency version or a mangled image tag both fail far from the cause. → Each updater is verified by a dry run that shows the diff before the first real release, and the task list makes that a discrete step rather than a hope.

- **Lockstep tags a repository that did not change.** An empty release in one repo, and a changelog entry with nothing under it. → Accepted deliberately, and stated in the proposal. The number is a product fact; a version with no changes in one half of the product is not misleading, whereas two different numbers for one release would be.

- **The husky hook is friction on a solo repository.** It will reject a commit at an inconvenient moment. → It is bypassable (`--no-verify`) by the person who owns the repo, which is the right escape hatch; the hook exists to catch inattention, not to police intent.

- **`commit-and-tag-version` is a fork, and forks can be abandoned too.** → It is a build-time dev dependency with no runtime footprint; if it stalls, the artifacts it produces (tags, `CHANGELOG.md`, a version in three files) are all plain and portable, and replacing the tool changes nothing about them.

- **The release writes an image tag before the image exists.** The tag in `wrangler.jsonc` points at something that has not been pushed yet, so a `wrangler deploy` between the release commit and the image push would deploy a broken reference. → Ordering is documented in `AGENTS.md`: release, build and push the image, then deploy. This is the same ordering the current manual procedure requires; it is now written down.

## Migration Plan

1. Tag `v1.7.0` retroactively at `044ba38` and push the tag, establishing the changelog floor.
2. Add the tooling, the configuration and the custom updaters. Dry-run the release and inspect the diff on all three version files — especially that `wrangler.jsonc` keeps every comment.
3. Add `commitlint` and the hook, then verify a bad subject is rejected and a `design:` subject is accepted.
4. Document the procedure and the type list in `AGENTS.md`, replacing the manual image-bump instruction.
5. Coordinate the first real release with the frontend: dry-run both, take the higher bump, release both explicitly as that version.

**Rollback:** delete the tag and revert the release commit. Nothing about the application changes, so there is no deployed state to undo — only repository metadata.
