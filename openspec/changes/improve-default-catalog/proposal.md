## Why

Every new laboratory starts from `default-catalog.json`, which was exported once from a reference database and never revisited. The production catalogs of the laboratories that have been working on LabFlow (Salinas, Erazo, La Merced, Santa Ana — about 1,500 orders and 4,000 ordered exams by 2026-10-28) show what that starting point gets wrong. Each laboratory corrects it by hand, and each one does it a little differently:

- **Exams that are missing.** Two or three laboratories independently created the same exams: sodium, potassium and chloride as separate exams, B12, folic acid, ferritin, serum iron, transferrin, free PSA, stool culture, KOH, fungal culture, peripheral smear, febrile agglutinins, Chagas, IgE, post-prandial insulin, GGT, ANA, the usual tumour markers, troponin I, 24-hour urine protein, bleeding and clotting time, rotavirus/adenovirus.
- **Parameters that cannot hold the real result.** The microscopic part of urinalysis and stool exam is numeric, but what laboratories write is text ("2-4 x campo", "Escasas", "No se observan"): every one of those results at Santa Ana and La Merced is non-numeric. Two of the three laboratories using urinalysis also added yeasts to it.
- **Ranges that never flag.** PT, PTT and bleeding time carry their range as text ("10-14"), so an out-of-range result is never marked; Salinas and La Merced turned them into numeric limits. ESR only has a range for men over 50. The absolute lymphocyte count for men carries the percentage range (20–40).
- **Exams with no area** (14), so they fall outside every section of the report and the filters; Salinas had already assigned them in its own copy.
- **Age groups that overlap.** The seed carries eight age groups — "Pediátrico/Niño" (0–17 years) on top of "Niño" and "Adolescente", "Adulto" with no limits at all, "Adulto Mayor", "mayores de 50" and "menores de 50" — so a patient's age can fall into several groups or, for the ranges of a group nobody meant, into the wrong one.
- **Names** with typos ("BILLIRUBINA", "post-pandrial"), a duplicate ("Glucosa en sangre", never ordered by anyone) and names that made laboratories create the exam again ("PSA" vs "PSA TOTAL", "Drogas combo" vs "Panel de drogas").

## What Changes

- `default-catalog.json` gets the 30 exams above (with prices at the median of what the laboratories charge, reusing parameters the catalog already has), the corrections above, and drops the duplicate exam. 90 → 119 exams.
- The age groups become four, contiguous and non-overlapping: **Neonatos** (0–28 days), **Niños** (29 days–11 years), **Adolescentes** (12–17), **Adultos** (18+). The reference ranges that used the removed groups are remapped so each age keeps the range it had (see design.md).
- `CatalogSeedIntegrityTest` gains rules that keep these defects from coming back for any exam, present or future: no exam without area, no repeated exam name, no numeric parameter whose range has no limits, no quantitative microscopic parameter, and exactly the four age groups above, without gaps or overlaps.
- **No effect on existing laboratories.** The seeder only runs for a laboratory with an empty catalog; nothing already stored is touched. No schema change, no API change.

## Capabilities

### New Capabilities

- `default-catalog`: what a newly created laboratory receives as its initial exam catalog and the quality rules that catalog must satisfy.

### Modified Capabilities

<!-- None. -->

## Impact

- `src/main/resources/default-catalog.json` (data only; the seeder and its record types are unchanged).
- `src/test/java/.../CatalogSeedIntegrityTest.java`.
- Clinical content: the reference ranges added or changed are listed in design.md and SHOULD be reviewed by a laboratory professional before release; where the laboratories disagreed and no standard value is clear, the parameter ships without a range so each laboratory sets its own.
