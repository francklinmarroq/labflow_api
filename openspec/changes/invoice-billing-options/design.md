## Context

See proposal.md for motivation. The relevant current state:

- `InvoiceServiceImp.createInvoice` takes the laboratory row with `findWithLockById`, resolves everything that can fail in `buildDraft` *before* calling `CaiNumberService.next`, then snapshots the CAI and the issuer onto the `Invoice` and calls `postIssueEntry`.
- The journal is touched in four places: `postIssueEntry` (issue), `applyPayment` (each payment, including the initial one), `annulInvoice` (reverses each payment's `PAGO` entry with `findSourceEntry`, then the `FACTURA` entry with `findSourceEntryIfExists`), and `annulPayment` (reverses `PAGO` with `findSourceEntry`). `findSourceEntry` throws when the entry is missing. Closed periods are enforced inside `JournalService.post`/`reverse`.
- `invoices` was created by `ddl-auto` with `cai NOT NULL`; prod has no DDL rights for Hibernate, so every column change goes to `schema.sql` as an idempotent statement.
- `LaboratoryServiceImp.update` uses `modelMapper.map(dto, laboratory)`, which copies nulls; `showReportRangeFlags` is preserved by saving it before the map and restoring it when the DTO carries null.

## Goals / Non-Goals

**Goals:**
- One switch, read on the server, decides the document type; the request shape does not change.
- A non-fiscal document is indistinguishable from a fiscal one for everything operational (lines, discounts, payments, balance, annulment, receivables).
- Zero behavioural change for laboratories that never touch the switch.

**Non-Goals:**
- Per-sale choice of document type, or a separate endpoint/screen.
- Converting a document from one type to the other after issue.
- Editing or resetting the internal correlative from the API (it starts at 1 and only moves forward; an editable start number can come later if a laboratory migrates from another system).
- Any layout logic for paper sizes; the API only stores the preference.
- Accounting for non-fiscal documents in any form (not even memo accounts).

## Decisions

### D1. Server-side mode, not a request field

`createInvoice` reads `laboratory.getInvoiceWithoutCai()` from the row it already locks. `InvoiceRequest` gains nothing. Alternative considered: a `fiscal` flag in the request defaulted from the setting by the frontend — rejected because it lets a crafted request issue a non-fiscal document in a laboratory that has the mode off (the exact fiscal risk the switch is meant to control), and because the user explicitly wants no per-sale choice.

### D2. A frozen `fiscal` column on `Invoice`

`invoices.fiscal boolean not null default true`, set at issue and never updated. Alternatives: infer from `cai IS NULL` (rejected: implicit, and every reader of `cai` would become a type check), or an enum `documentType` (rejected for now: two states, a boolean reads better at the ~5 decision points; an enum can replace it if a third kind ever appears). The default + backfill makes every existing row fiscal.

### D3. Numbering: a separate correlative on the laboratory

`laboratory.non_fiscal_next_number bigint` (null ⇒ 1), advanced under the same `findWithLockById` lock, so concurrent issues are serialized exactly like the CAI. The number is `String.format("%08d", n)`. It is stored in the same `invoice_number` column: the unique constraint `(laboratory_id, invoice_number)` still holds because a CAI number always contains dashes and this never does. Like the CAI, it is taken *after* `buildDraft` and the issue-date check, so a failed request never burns a number (and in any case the whole method is one transaction, so a later failure rolls the counter back).

The numbering choice lives in a small method next to `CaiNumberService` usage (e.g. `issueNumber(laboratory, invoice)`) that either calls `caiNumberService.next` and copies the snapshot, or takes the internal number and leaves the CAI fields null. `CaiNumberService` itself does not change.

### D4. Accounting guard at the four touchpoints

A single predicate, `invoice.isFiscal()`, gates `postIssueEntry`, the `journalService.post` inside `applyPayment`, both reversal loops in `annulInvoice`, and the reversal in `annulPayment`. Gating at each call site (instead of making `findSourceEntry` lenient) keeps the strict lookup as a guard for fiscal invoices: a fiscal payment without its entry is still a data error and must still fail loudly. Closed-period checks disappear for non-fiscal documents as a natural consequence, which is the intended behaviour (spec: "Closed period does not apply").

### D5. Laboratory settings follow the `showReportRangeFlags` pattern

- `Laboratory.invoiceWithoutCai Boolean = FALSE`; `Laboratory.invoicePaperSize` as a Java enum `InvoicePaperSize { A5_LANDSCAPE, THERMAL_80, THERMAL_58 }` with `@Enumerated(STRING)`, default `A5_LANDSCAPE`. Null in the DB is reported as the default in `toDto`.
- `LaboratoryDTO` carries `invoicePaperSize` as a `String`; `update` validates it against the enum before mapping and throws `APIException("Tamaño de papel no válido…")` on anything else, so an unknown value never reaches modelMapper.
- Both are saved before `modelMapper.map` and restored when the DTO carries null. `nonFiscalNextNumber` is not in the DTO and is saved/restored like `seedStatus`, so no update can reset it.

### D6. Reports

- `registerInvoiceRows` and the item/order row queries used by `getSalesDetail` gain `and i.fiscal = true`, so the register, its allocation and its totals only see fiscal invoices.
- `getSales` and `getCollections` keep their queries; their `Summary` gains `nonFiscalCount`/`nonFiscalTotal` (sales) and `nonFiscalTotal` (collections), from one extra aggregate query each, filtered by `fiscal = false` with the same date and status rules the summary already uses.
- Receivables and statements are untouched apart from `InvoiceDTO.fiscal` being populated.

## Risks / Trade-offs

- [Operational receivables and the CxC ledger account stop matching for laboratories that use the mode on credit] → Expected and stated in the switch's help text (frontend). The receivables listing exposes `fiscal`, so the difference can be explained per document.
- [A laboratory turns the mode on by mistake and hands out non-fiscal documents] → Only `LAB_SETTINGS_EDIT` can change it, it defaults to off, and the frontend shows a fiscal warning next to the switch. Documents already issued keep their type (D2), so turning it off fixes the future without rewriting history.
- [Existing tests assume every invoice posts entries] → `InvoiceAccountingTest` keeps covering fiscal invoices unchanged; new tests cover the non-fiscal path.
- [Native image: new enum and column] → `InvoicePaperSize` is a plain enum used by Hibernate and Jackson; verify the native build like any other enum field (no proxies, no reflection beyond what enums already get).
- [`invoices.cai` drops NOT NULL in prod] → A fiscal invoice can no longer be guaranteed to have a CAI by the DB. Mitigated by the service always setting it on the fiscal path and by a test asserting it.

## Migration Plan

1. `schema.sql` (idempotent, appended with an explanatory comment as the file does today):
   - `alter table if exists laboratory add column if not exists invoice_without_cai boolean default false;` + `update … set … = false where … is null;`
   - `alter table if exists laboratory add column if not exists invoice_paper_size varchar(32);`
   - `alter table if exists laboratory add column if not exists non_fiscal_next_number bigint;`
   - `alter table if exists invoices add column if not exists fiscal boolean default true;` + `update invoices set fiscal = true where fiscal is null;` + `alter table if exists invoices alter column fiscal set not null;`
   - `alter table if exists invoices alter column cai drop not null;`
2. Deploy the API before the frontend. The old frontend keeps working: it never sends the new fields (preserved by D5) and ignores `fiscal`.
3. Rollback: the previous image still reads every row (new columns are ignored); only non-fiscal documents issued meanwhile would have a null `cai`, which the previous image maps without error but would print with an empty CAI. Turn the switch off before rolling back if any laboratory used it.
