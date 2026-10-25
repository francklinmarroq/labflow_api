## 1. Schema and model

- [x] 1.1 Append the idempotent statements of design.md "Migration Plan" step 1 to `schema.sql` with an explanatory Spanish comment; verify by running the file twice against the local PostgreSQL (`labflow-pg-test`) without errors and checking `\d invoices` shows `fiscal not null` and `cai` nullable
- [x] 1.2 Add `InvoicePaperSize` enum and `invoiceWithoutCai`, `invoicePaperSize`, `nonFiscalNextNumber` to `Laboratory` with their defaults; verify the app starts on H2 and `NativeImageLazyAssociationTest` stays green
- [x] 1.3 Add `fiscal` (not null, default true) to `Invoice` and make `cai` nullable in the mapping; add `fiscal` to `InvoiceDTO` and populate it in `toDTO`; verify with `mvn -B --settings .mvn/settings.xml test -Dtest=InvoiceCompositionTest`

## 2. Laboratory settings

- [x] 2.1 Expose `invoiceWithoutCai` and `invoicePaperSize` in `LaboratoryDTO`; report null as `false` / `A5_LANDSCAPE` in `toDto`; verify with a new `LaboratoryBillingOptionsTest` scenario "Default for an existing laboratory" and "Default paper size"
- [x] 2.2 In `LaboratoryServiceImp.update`, validate `invoicePaperSize` against the enum (Spanish `APIException`) before mapping, and preserve both preferences and `nonFiscalNextNumber` when omitted; verify with tests for "Turning the switch on", "Choosing a thermal printer", "Unknown paper size" and "Older client updates unrelated fields"
- [x] 2.3 Verify a laboratory created through registration reports `invoiceWithoutCai` `false` and `A5_LANDSCAPE` (test "Newly registered laboratory")

## 3. Issuing

- [x] 3.1 Extract the numbering step of `createInvoice` into a method that, under the existing lock, either takes the CAI number and snapshot (switch off) or the internal 8-digit correlative with null CAI fields and `fiscal = false` (switch on); verify with a new `InvoiceWithoutCaiTest`: "Switch on ignores a valid CAI", "Switch on without any CAI configured", "First non-fiscal document", "Correlative survives toggling the switch"
- [x] 3.2 Verify the switch-off path is unchanged: `CaiNumberingTest` passes untouched and new tests "Switch off issues a fiscal invoice" and "Switch off and no usable CAI blocks issuing" pass
- [x] 3.3 Verify a rejected request (already-invoiced order, future date, incomplete cash payment) does not advance the internal correlative ("Every other validation still applies")
- [x] 3.4 Verify the non-fiscal document freezes the laboratory header and recipient and reports null CAI fields, and that toggling the switch afterwards does not change it ("Non-fiscal document has no CAI data", "Toggling the switch does not rewrite history", "Existing invoices remain fiscal")

## 4. Accounting

- [x] 4.1 Gate `postIssueEntry`, the journal post in `applyPayment`, both reversals in `annulInvoice`, and the reversal in `annulPayment` on `invoice.isFiscal()`; verify with tests "Issuing posts nothing", "Cash sale posts nothing", "Annulling a document and a payment"
- [x] 4.2 Verify "Closed period does not apply" with a closed period and a back-dated non-fiscal document, and that `InvoiceAccountingTest` (fiscal path) still passes unchanged

## 5. Receivables and reports

- [x] 5.1 Verify receivables listing, total and statements include non-fiscal documents with `fiscal` reported ("Pending non-fiscal document is owed")
- [x] 5.2 Filter the sales-register queries (`registerInvoiceRows`, item rows, order rows, invoiced units) by `fiscal = true`; verify "Sales register is fiscal only" and run `PostgresQueryCompatibilityTest` against the local PostgreSQL
- [x] 5.3 Add `nonFiscalCount`/`nonFiscalTotal` to `SalesReportDTO.Summary` and `nonFiscalTotal` to `CollectionsReportDTO.Summary`, computed with the summary's existing date and status rules; verify "Aggregated reports break out non-fiscal sales"

## 6. Integration

- [x] 6.1 Run the full suite `mvn -B --settings .mvn/settings.xml test` and confirm it is green
- [ ] 6.2 Deploy to develop (`wrangler deploy --env develop`), apply `schema.sql`, and with the switch on issue, pay and annul a non-fiscal document; confirm in the native image that reading it, the receivables list and the reports return 200 and no journal entry was created; then switch off and confirm a CAI invoice still issues
