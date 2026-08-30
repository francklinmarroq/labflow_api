## Context

See `proposal.md` — Why. What shapes the approach here is the existing plumbing around `Laboratory`:

- The schema is managed by Hibernate `ddl-auto=update` plus a hand-run, idempotent `src/main/resources/schema.sql` (`spring.sql.init.mode=never`). In prod the app has no DDL privileges, so a column that only exists in the entity breaks every query against `laboratory` until the script is run by hand.
- `Laboratory` has **no boolean column today**. Its convention for optional presentation config is "null means the current behavior" — see the comments on `reportPrimaryColor` / `reportSecondaryColor` and `envelopeLayout`.
- `LaboratoryServiceImp.updateLaboratory` calls `modelMapper.map(dto, laboratory)` on a default `ModelMapper` bean, with no `setSkipNullEnabled`. Nulls in the DTO **do** overwrite the entity. The method already compensates by capturing and restoring the fields that do not travel in the DTO (`logoObjectKey`, `stampObjectKey`, `seedStatus`).
- `application.properties` sets `spring.jackson.deserialization.fail-on-null-for-primitives=false`, so an explicit JSON `null` on a primitive silently becomes `false` rather than a 400.
- The public report already ships laboratory presentation data through a hand-built, positional `PublicReportDTO.Lab`. Commit `fc2f07c` (the "lema" in the public report header) is the two-file precedent for adding one more.

The `(Alto)` / `(Bajo)` marker itself is computed and rendered in the frontend (`labflow_frontend/app/utils/ranges.ts`, `app/components/OrderReport.vue`). The API never sees it.

## Goals / Non-Goals

**Goals:**
- One preference, stored once per laboratory, reaching both report surfaces through the DTOs they already consume.
- Zero behavior change for every existing laboratory on deploy.
- No way for a partial or stale `PUT` to silently reset the preference.

**Non-Goals:**
- Any frontend change. Hiding the labels and the bold styling happens in `../labflow_frontend` as a separate change.
- Per-report or per-order overrides. The preference is laboratory-wide.
- Suppressing flags on the staff-facing screens (order detail, results entry). Those are working tools, not the patient's document.
- Moving flag computation into the API.

## Decisions

**Nullable `Boolean`, not primitive `boolean`.** Existing rows will read `null` until the backfill runs, and in dev `ddl-auto=update` creates the column with no default at all. A primitive would turn those rows into `false` — silently switching alerts off for every existing laboratory, the exact opposite of the intended default. A nullable `Boolean` with a field initializer of `TRUE` (for rows this app creates) and a read-side normalization of `null → true` keeps the "null means current behavior" convention already used for the report colors and the envelope layout. Alternative considered: primitive `boolean` plus a `default true` column and a mandatory backfill — rejected because it makes correctness depend on a manual script having been run, and dev databases would be wrong.

**Normalize on read, not on write.** `toDto` resolves `null` to `true` so no client ever sees `null` and no client has to encode the default. The stored `null` is left alone rather than backfilled on read, keeping the read path free of writes. The DDL backfill still runs once for tidiness, but nothing depends on it.

**Preserve-on-null in `updateLaboratory`, following the method's existing pattern.** Capture the stored value before `modelMapper.map`, and restore it if the incoming DTO's field is `null`. This is deliberately the same shape as the existing `logoObjectKey` / `stampObjectKey` / `seedStatus` handling, so it reads as one idiom rather than a special case. Alternatives considered: (a) configuring the shared `ModelMapper` bean with `setSkipNullEnabled(true)` — rejected, it silently changes update semantics for every DTO in the app, well beyond this change; (b) a dedicated `PATCH` endpoint — rejected as more surface than a single boolean warrants. The cost is that the field can never be cleared back to `null`, which is fine: it is a two-state switch whose absent state is defined as `true`.

**Ship it to the public report via `PublicReportDTO.Lab`, not a new endpoint.** `PublicReportServiceImp.toLab` already receives the `LaboratoryDTO` produced by `toDto`, so it inherits the normalized value for free. `Lab` uses a positional all-args constructor, so the new field and the new constructor argument must be added in the same position — that is the only ordering trap in this change.

**Name: `showReportRangeFlags`.** English identifier per the constitution, matching the frontend's existing `RangeFlag` vocabulary, and broad enough to cover the whole marker family (`Alto`, `Bajo`, `¡Crítico!`, and the emphasis) rather than reading as "only Alto/Bajo".

## Risks / Trade-offs

- **`schema.sql` is not run automatically; forgetting it breaks prod.** Every query against `laboratory` fails with "column does not exist" — which takes down settings, invoices, orders and the public report link. → Append the `alter table` in the same commit as the entity change, and treat running it as part of the deploy checklist, before the new image is promoted. Note that the most recent boolean column added elsewhere (`TestConfig.allowResultAttachments`, commit `5243610`) was **not** added to `schema.sql`; do not repeat that.
- **The positional `PublicReportDTO.Lab` constructor.** Adding the field in one position and the argument in another compiles only if the neighboring types differ, and misroutes data if they do not. → The neighbors are `String`; a `Boolean` in the wrong slot will not compile. Covered by the mapping test regardless.
- **A stale frontend never sends the field.** Handled by preserve-on-null, but until the frontend ships its half, the API stores a preference that nothing honors. → Acceptable and intended: this change is the API half, and the default keeps behavior identical meanwhile.
- **`fail-on-null-for-primitives=false`** would have made a primitive-boolean version of this bug invisible. The nullable `Boolean` sidesteps it entirely.

## Migration Plan

1. Merge the code change together with the `schema.sql` block.
2. Before promoting the new image, run against prod with the admin credential:
   `alter table if exists laboratory add column if not exists show_report_range_flags boolean default true;`
   then `update laboratory set show_report_range_flags = true where show_report_range_flags is null;`
   Both are idempotent and safe to re-run.
3. Deploy the image and bump `containers[0].image` in `wrangler.jsonc`.

Rollback: revert the code. The column can stay — it is nullable, additive, and read by nothing else. No data migration to undo.
