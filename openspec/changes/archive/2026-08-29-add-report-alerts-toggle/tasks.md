## 1. Persistence

- [x] 1.1 Add `private Boolean showReportRangeFlags = Boolean.TRUE;` to `model/Laboratory.java`, next to the report colors, with a Spanish comment stating that null means "on" (today's behavior); verify the app starts against the H2 test database and Hibernate maps the `show_report_range_flags` column
- [x] 1.2 Append the idempotent block to `src/main/resources/schema.sql` — `alter table if exists laboratory add column if not exists show_report_range_flags boolean default true;` followed by `update laboratory set show_report_range_flags = true where show_report_range_flags is null;` — with a Spanish comment block in the file's house style explaining what breaks without it; verify both statements are single statements (no `DO $$`) and re-running them is a no-op

## 2. Authenticated API

- [x] 2.1 Add `private Boolean showReportRangeFlags;` to `payload/LaboratoryDTO.java` with a Spanish comment; verify no validation annotation is needed and that ModelMapper picks it up by name in both directions
- [x] 2.2 Normalize on read in `LaboratoryServiceImp.toDto`: report `true` when the stored value is null; verify with the test from 4.1 that a laboratory row with a null column reads back as `true`
- [x] 2.3 Preserve on update in `LaboratoryServiceImp.updateLaboratory`: capture the stored value before `modelMapper.map(dto, laboratory)` and restore it when `dto.getShowReportRangeFlags()` is null, matching the existing `logoObjectKey` / `stampObjectKey` / `seedStatus` handling; verify with the test from 4.3 that a body omitting the field leaves a stored `false` intact

## 3. Public report

- [x] 3.1 Add `showReportRangeFlags` to the nested `Lab` class in `payload/PublicReportDTO.java`; verify the field's position matches the argument position added in 3.2 (the all-args constructor is positional)
- [x] 3.2 Pass `lab.getShowReportRangeFlags()` in the `new PublicReportDTO.Lab(...)` call in `PublicReportServiceImp.toLab`; verify it compiles and that the value is the already-normalized one coming from `LaboratoryServiceImp.toDto`

## 4. Tests

- [x] 4.1 Create `src/test/java/marroquinsoftware/labflowapi/LaboratoryReportFlagsTest.java` in the `@DataJpaTest` + `@Import` + `@MockitoBean` style of `OrderTagTest` (tenant set and cleared per test, `FileStorageService` / `CatalogSeeder` / `AccountSeeder` mocked), covering: a laboratory whose column is null reads back as `true`; verify `mvn -B --settings .mvn/settings.xml test -Dtest=LaboratoryReportFlagsTest` passes
- [x] 4.2 Add the round-trip cases to that test: updating with `false` persists and reads back `false`, updating with `true` reads back `true`; verify the same command passes
- [x] 4.3 Add the regression case: with a stored `false`, an update whose DTO leaves `showReportRangeFlags` null changes the other field it carries and leaves the preference `false`; verify the test fails if the preserve-on-null guard from 2.3 is removed
- [x] 4.4 Cover the public-report mapping — `PublicReportServiceImp.toLab` carries the value into `PublicReportDTO.Lab`, including the null-stored case reporting `true`; if wiring the full service proves heavy, assert the mapping in a focused unit test instead; verify the test passes

## 5. Verification

- [x] 5.1 Run the full suite: `mvn -B --settings .mvn/settings.xml test` — green, with no regression in `LabflowapiApplicationTests` or the existing laboratory-touching tests (`CaiNumberingTest`, `OrderTagTest`)
- [x] 5.2 Run the API locally (`.env` loaded, `spring-boot:run`) and confirm end to end: `GET /api/v1/laboratory` reports `showReportRangeFlags: true` on a fresh laboratory; `PUT /api/v1/laboratory/{id}` with `false` persists; a re-`GET` and `GET /api/v1/public/orders/{token}` both report `false`
- [x] 5.3 Confirm no AOT change was needed — the new DTO fields live under `payload/**`, already registered in `config/AppConfig.NativeRuntimeHints`; verify by checking that no file under `config/` was touched
