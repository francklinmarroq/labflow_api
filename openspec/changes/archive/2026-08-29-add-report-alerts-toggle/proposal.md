## Why

The results report always prints an out-of-range marker next to each value — `(Alto)`, `(Bajo)`, `(¡Crítico!)` — plus bold emphasis on the value itself. Some laboratories do not want the report to interpret the result for the patient; they want the number and the reference range only, and today they have no way to turn that off.

The marker is computed and rendered in the frontend, but the preference belongs to the laboratory and must be stored and served by the API before any client can honor it.

## What Changes

- New per-laboratory setting `showReportRangeFlags`, persisted on the `laboratory` table.
- `GET /api/v1/laboratory` returns it; `PUT /api/v1/laboratory/{id}` accepts it (both already gated by `LAB_SETTINGS_VIEW` / `LAB_SETTINGS_EDIT`).
- `GET /api/v1/public/orders/{token}` returns it inside `laboratory`, so the patient's public report honors the same preference as the printed one.
- Default is **on**: a laboratory with no stored value keeps today's behavior. The setting is opt-out, so the change is not breaking.
- A `PUT` body that omits the field must preserve the stored value rather than clear it — `updateLaboratory` maps the DTO over the entity with nulls included, so this must be handled explicitly.
- No new endpoint, no new permission, no change to how the flag itself is computed (the API still ships only raw values and reference ranges).

## Capabilities

### New Capabilities
- `laboratory-settings`: the laboratory's own configuration — how it is read, how it is updated, and which parts of it reach the public patient report. This change introduces the report-alerts switch as its first requirement set.

### Modified Capabilities
<!-- None: openspec/specs/ is empty, so there is no existing capability to amend. -->

## Impact

- `model/Laboratory.java` — new nullable `Boolean` column (`show_report_range_flags`); the first boolean column on this entity.
- `payload/LaboratoryDTO.java` — new field, copied both ways by ModelMapper.
- `service/LaboratoryServiceImp.java` — null normalization on read, explicit preservation on update.
- `payload/PublicReportDTO.java` (`Lab`) and `service/PublicReportServiceImp.java` — carry the value to the public report.
- `src/main/resources/schema.sql` — idempotent `alter table` plus backfill; must be run by hand in prod, or every query against `laboratory` breaks there.
- Tests under `src/test/java/marroquinsoftware/labflowapi/`.
- No AOT/reflection change: `payload/**` is already registered in `config/AppConfig.NativeRuntimeHints`.
- Out of scope: the frontend (`../labflow_frontend`) work that actually hides the labels and the bold styling.
