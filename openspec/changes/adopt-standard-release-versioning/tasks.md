## 1. Establish the changelog floor

- [x] 1.1 Confirm `commit-and-tag-version` is the maintained fork of the archived `standard-version` and note the version to pin — verified by checking the package's repository and its deprecation/successor notice, rather than assuming
- [x] 1.2 Create an annotated git tag `v1.7.0` on `044ba38` ("Despliega version 1.7.0") — verified by `git tag` listing it and `git show v1.7.0` pointing at that commit
- [x] 1.3 Push the tag to `origin` — verified by `git ls-remote --tags origin` listing it
- [x] 1.4 Confirm no earlier tags are fabricated — verified by `git tag` showing exactly one tag, since `v1.6.0` never shipped and earlier versions would be guesses

## 2. Install and configure the tool

- [x] 2.1 Add `commit-and-tag-version` as a dev dependency with a pinned version, and a `release` script in `package.json` — verified by `pnpm install` completing and the script being listed
- [x] 2.2 Add a `version` field to `package.json` set to `1.7.0`, with a comment nearby or in `AGENTS.md` noting this file is the tool's anchor even though it describes the Worker rather than the Spring Boot app — verified by the field being present and matching the tag
- [x] 2.3 Create the tool configuration declaring the bump targets: `package.json` natively, plus `pom.xml` and `wrangler.jsonc` through custom updaters — verified by the config being read (the tool lists the files it will bump on a dry run)
- [x] 2.4 Write the `pom.xml` updater anchored on the **project's own** `<version>` element, not any `<version>` inside `<dependencies>` or `<plugins>` — verified by a dry run showing exactly one changed line in `pom.xml`, the one currently reading `0.0.1-SNAPSHOT`
- [x] 2.5 Write the `wrangler.jsonc` updater as a **text** replacement on the image tag, never a JSON parse-and-serialise — verified by a dry run leaving every comment in the file byte-identical, which a JSON round-trip would delete
- [x] 2.6 Scope the `wrangler.jsonc` updater to the **prod** container entry only, leaving the develop entry alone — verified by the develop image reference being unchanged, per that file's own comment that develop builds should use `develop-<sha>` tags rather than the `vX.Y.Z` series
- [x] 2.7 Run a full dry run and read the complete diff of all three version files before any real release — verified by the diff touching only the intended lines

## 3. Enforce the commit convention

- [x] 3.1 Add `commitlint` and `husky` as dev dependencies and initialise the hook directory — verified by `pnpm install` completing and `.husky/` existing
- [x] 3.2 Write the commitlint configuration extending the conventional preset and registering the types this project actually uses, including `design` — verified by the config listing them
- [x] 3.3 Confirm `breaking-changes` is **not** registered as a type, since a breaking change must be expressed as `feat!:` or a `BREAKING CHANGE:` footer for the tool to produce a major bump — verified by a `breaking-changes:` subject being rejected and a `feat!:` subject being accepted
- [x] 3.4 Wire the `commit-msg` hook — verified by an attempted commit with a non-conforming subject being rejected, and one with `design: ...` being accepted
- [x] 3.5 Confirm the hook is bypassable with `--no-verify` — verified by that flag allowing a commit through, which is the intended escape hatch for the repository owner

## 4. Document the procedure

- [x] 4.1 Replace the manual image-bump instruction in `AGENTS.md` (currently "bump `containers[0].image` … `chore: bump de imagen de despliegue a vX.Y.Z`") with the release command — verified by the old instruction no longer being the documented path
- [x] 4.2 Document the lockstep rule: dry-run both repositories, take the **higher** of the two bumps, and release both explicitly as that exact version — verified by the procedure being written with the concrete commands
- [x] 4.3 Document the ordering — release, build and push the image, then `wrangler deploy` — and why: the release commit writes an image tag that does not exist yet, so deploying in between would point the Worker at a missing image — verified by the ordering and its rationale being stated
- [x] 4.4 Document the allowed commit types alongside the existing note that commit messages are Spanish, making clear the type keyword is English and the subject is Spanish — verified by the list being present

## 5. Verification

- [x] 5.1 Run a real release as a patch on a scratch branch and inspect the result: `CHANGELOG.md` generated, `package.json`, `pom.xml` and `wrangler.jsonc` all showing the same new version, one release commit, one annotated tag — verified by all six holding, then discard the branch
- [x] 5.2 Confirm the generated `CHANGELOG.md` covers only commits after `v1.7.0` — verified by its first entry not reaching back into the pre-tag history
- [x] 5.3 Confirm the changelog is developer-facing and that nothing in this repository attempts to write user-facing release-note copy — verified by no release-notes content file existing here, since that copy lives in the frontend repository
- [x] 5.4 Confirm the application is untouched: no Java source, no `schema.sql`, no endpoint and no dependency of the running app changed — verified by the diff containing only build, config, docs and changelog files
- [ ] 5.5 Coordinate the first real release with the frontend's matching change so both repositories carry the same version — verified by `git describe --tags` reporting the same version in both repositories
