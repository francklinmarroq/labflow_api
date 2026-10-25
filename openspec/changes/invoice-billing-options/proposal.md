## Why

Today a laboratory cannot sell anything through LabFlow without a valid CAI: `CaiNumberService.next` throws when neither CAI is current and in range, and `invoices.cai` is NOT NULL. Several laboratories operate informally and ask to hand out what in Honduras is called a *factura sin membrete* — a sales document with the laboratory's header but no fiscal data, no title and no SAR numbering. They also print on different hardware: the invoice is fixed to A5 landscape, while many counters use 80 mm or 58 mm thermal printers.

## What Changes

- **A laboratory-level switch "Facturar sin CAI"** (`invoiceWithoutCai`, off by default). It is a mode of the laboratory, not a choice per sale: the same `POST /invoices` with the same body issues one kind of document or the other depending on the switch. The client never sends the document type.
  - **Off** — exactly today's behaviour: a fiscal invoice numbered from the CAI, and issuing is rejected when there is no current CAI with numbers left.
  - **On** — every invoice is issued as a non-fiscal document, **even when a valid CAI is configured**: no CAI number is consumed, no CAI snapshot is stored, and the document takes its number from a separate internal correlative of the laboratory.
- **The document type is frozen on each invoice** (`fiscal`, read-only in the DTO). Reprinting, the detail screen and the reports read it from the invoice, never from the current switch, so turning the switch on or off never changes a document already issued. Every existing invoice is fiscal.
- **A non-fiscal document does not touch the accounting.** Issuing it, registering a payment on it, annulling it and annulling one of its payments post no journal entries and reverse none. Consequently closed accounting periods do not restrict it.
- **Operational tracking stays.** Balance, status, payments with receipt number, annulment, orders marked as invoiced, receivables and customer/billing-client statements include non-fiscal documents; the receivables total keeps adding them up.
- **Reports.** The detailed sales register (the SAR-style list) contains only fiscal invoices. The aggregated sales and collections reports keep including every document and additionally report how much of the total came from non-fiscal documents.
- **A laboratory-level invoice paper size** (`invoicePaperSize`: `A5_LANDSCAPE` default, `THERMAL_80`, `THERMAL_58`). The API only stores and validates it; the layout is the frontend's.
- **BREAKING (database, not API)**: `invoices.cai` becomes nullable. No request or response field is removed or renamed.

## Capabilities

### New Capabilities

- `invoice-without-cai`: issuing invoices without CAI when the laboratory's switch is on — what decides the document type, its numbering, what it freezes, that it stays out of the accounting, and how it shows in receivables and reports.

### Modified Capabilities

- `laboratory-settings`: adds the two billing preferences (`invoiceWithoutCai`, `invoicePaperSize`) with their defaults, validation, and the rule that an update omitting them preserves them.

## Impact

- **Model and database** (`schema.sql`, idempotent): `laboratory.invoice_without_cai boolean default false`, `laboratory.invoice_paper_size varchar`, `laboratory.non_fiscal_next_number bigint`; `invoices.fiscal boolean not null default true` backfilled to true; `invoices.cai` drops NOT NULL.
- **Services**: `InvoiceServiceImp.createInvoice` chooses the numbering by the switch under the laboratory lock it already takes; the four journal touchpoints (`postIssueEntry`, `applyPayment`, `annulInvoice`, `annulPayment`) are skipped for non-fiscal documents. `LaboratoryServiceImp.update` preserves the new fields when omitted, like `showReportRangeFlags`. `AnalyticsReportServiceImp` filters the sales register and adds the non-fiscal subtotals.
- **DTOs**: `LaboratoryDTO` gains `invoiceWithoutCai` and `invoicePaperSize`; `InvoiceDTO` gains read-only `fiscal`; `SalesReportDTO.Summary` and `CollectionsReportDTO.Summary` gain non-fiscal totals. `InvoiceRequest` is unchanged.
- **Frontend** (`labflow_frontend`, sibling change of the same name): the switch and paper size in *Mi laboratorio*, the *factura sin membrete* print layout, the thermal layouts, and the "Sin CAI" mark.
- No new dependencies.
