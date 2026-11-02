## Context

Data source: read-only queries against production on 2026-10-28, over the four laboratories with real activity (ids 1, 121, 122, 156). Demo and empty laboratories were ignored. Laboratory 121 (Erazo) declined the seed and built its catalog from scratch, which makes it an independent sample of what a laboratory needs; 1, 122 and 156 started from the seed, so their *edits* to it are the signal.

## Decisions

**An exam is added when two or more laboratories created it independently.** One laboratory's exam is a local need; two is a pattern. Exams created by one laboratory only (influenza, COVID antigen, cortisol, panels of a single lab) stay out.

**Reuse parameters the catalog already has.** Sodium, potassium and chloride already exist inside "Electrolitos"; insulin basal and 2 h inside "Curva de Insulina"; troponin I inside "Troponina / Mioglobina"; the culture parameters inside "Urocultivo". The new exams point at those parameters, so a laboratory that corrects the range of potassium corrects it once.

**Ranges only where they are defensible.** A range is included when the laboratories agree, or when one is clearly the standard one. Where they disagree and depend on the analyser (free PSA, anti-CCP, clotting time) the parameter ships without a range; ANA ships qualitative. The values to review:

| Parameter | Range | Basis |
|---|---|---|
| TP / TPT / bleeding time | 10–14 s / 20–35 s / 1–3 min | the seed's own text, now numeric |
| ESR | M ≤15, F ≤20, F >50 ≤30 mm/h (M >50 ≤20 kept) | Westergren; Salinas, La Merced |
| Lymph# (men) | 0.8–4.0 ×10³/µL | same as women; Salinas fix |
| Fasting glucose | + prediabetes 100–125, diabetes ≥126 mg/dL | ADA; Salinas |
| Sodium / potassium / chloride | existing 135–145 / 3.5–5.1 / 98–107 | unchanged |
| B12 | 197–835 pg/mL | median of the three labs |
| Folic acid | 3–17 ng/mL | two labs agree |
| Ferritin | M 30–400, F 13–150 ng/mL | common immunoassay range |
| Serum iron / TIBC | 50–170 / 250–450 µg/dL | two labs agree |
| Transferrin | 200–360 mg/dL | Salinas |
| GGT | M 11–61, F 9–39 U/L | La Merced (the lab that orders it) |
| IgE | by age, adult ≤100 UI/mL | Erazo / La Merced pattern |
| 24 h urine protein | ≤150 mg/24 h | standard |
| CEA / AFP / CA 125 / CA 15-3 / CA 19-9 | ≤5 ng/mL / ≤10 ng/mL / ≤35 / ≤35 / ≤37 U/mL | labs + common cut-offs |

**Known defects left as they are.** Hemoglobin and hematocrit have the same range for men and women (11–15 g/dL, 37–47 %). It is wrong, but no laboratory corrected it in the data and choosing values is a clinical decision; it is reported rather than changed. Methods (`test_methods`) are not seeded: they depend on each laboratory's equipment, and the seeder does not handle them.

**The JSON is edited, not regenerated.** It was exported from a reference database that no longer matches it; a scripted edit keeps the diff reviewable and the existing ids stable.

## Risks

- A wrong range reaches every new laboratory's reports. Mitigation: the table above for professional review; existing laboratories are untouched.
- Shared parameters: editing a reused parameter changes it in both exams. This is how the catalog already works for every parameter.
