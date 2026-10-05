## Why

The laboratory wants one invoice to cover several pending orders — of one patient or of several — and to invoice exams and free concepts with no order at all. Identical exams must print as a single line with a quantity ("10 — Hemograma", not ten lines). The full planning for this change (proposal, design, the `invoice-composition` spec and its tasks) lives in the workspace change `openspec/changes/facturas-multiorden-y-libres`, which spans both repositories; this change only reconciles this repository's `invoice-billing-party` spec, which was written assuming one order per invoice.

## What Changes

- The recipient is chosen explicitly: a patient, a billing client, or a final consumer typed at issue time. The legacy `billingClientId` and "no recipient, single patient" requests keep their old meaning.
- The patient is frozen per order (in `invoice_orders`), not once per invoice: `patientName` on the invoice is filled only when all its orders belong to one patient.
- An invoice issued with no orders, or to a final consumer, records no patient and no billing client.

## Capabilities

### New Capabilities
<!-- None in this repository: invoice-composition is specified in the workspace change. -->

### Modified Capabilities
- `invoice-billing-party`: the recipient choice gains the final consumer and the case of orders of several patients; the frozen patient becomes per order.

## Impact

- Already implemented by the workspace change: `invoice_orders` table, `InvoiceRequest.recipient`, `InvoiceDTO.orders`, nullable `invoices.customer_id`/`order_id`/`discount_kind`. No further code in this change.
