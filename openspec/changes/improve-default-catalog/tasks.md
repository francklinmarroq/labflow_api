## 1. Catalog data

- [x] 1.1 Apply the corrections of proposal.md to `default-catalog.json` (numeric PT/PTT/bleeding-time ranges, qualitative microscopic parameters, yeasts in urinalysis, ESR and Lymph# ranges, glucose interpretations, areas, names, removal of "Glucosa en sangre"); verify ids stay unique and every reference resolves
- [x] 1.2 Add the 30 exams of proposal.md, reusing existing parameters where design.md says so; verify 119 exams and 119 profiles

## 2. Tests

- [x] 2.1 Add `seededCatalogNeedsNoManualFixesToReport` to `CatalogSeedIntegrityTest` covering the scenarios of "The initial catalog is usable without manual fixes"; verify it fails against the previous JSON and passes against the new one
- [x] 2.2 Run `mvn -B --settings .mvn/settings.xml test -Dtest=CatalogSeedIntegrityTest`

## 3. Review and rollout

- [ ] 3.1 A laboratory professional reviews the ranges table in design.md
- [ ] 3.2 On develop, register a laboratory, accept the catalog, and open an urinalysis, a PT and an ESR result for a woman to confirm capture and flags
