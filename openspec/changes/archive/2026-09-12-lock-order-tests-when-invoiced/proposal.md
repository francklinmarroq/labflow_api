## Why

An invoice is a fiscal document (CAI, SAR Honduras) and everything it prints is frozen when it is issued — including the name and price of every exam it charges for. But the order it was issued from is not frozen: `POST /api/v1/orders/{orderId}/tests` and `DELETE /api/v1/orders/{orderId}/tests/{testId}` still work after invoicing, so an invoiced order can gain an exam that nobody was charged for, or lose one the patient already paid for. Nothing in the API prevents it and nothing detects it afterwards.

The result is a silent accounting discrepancy that surfaces in the wrong place: the printed invoice and the printed report disagree about what was done, sales-by-exam reporting counts a test the invoice never billed, and the receivable no longer corresponds to the work. The order's own screen already knows the order is invoiced — it uses that to decide whether cancelling needs the annulment permission — so the rule is simply not being applied to the one operation that changes what the invoice was computed from.

## What Changes

- Adding an exam to an order that has a **live invoice** SHALL be refused. Removing one SHALL be refused the same way. Both fail with the API's existing business-rule response — HTTP 400 and a message naming the invoice — and change nothing.
- "Live invoice" reuses the concept the codebase already has: the order's most recent invoice whose status is not `ANULADA`. `InvoiceRepository.existsByOrderIdAndStatusNot` already expresses exactly this and is currently unused; `createInvoice` and `cancelOrder` already reason in the same terms.
- **Annulling the invoice releases the lock.** Annul → fix the exams → issue a new invoice is the correction path for an order invoiced with the wrong exams, and it is the only one: no override permission and no bypass. The annulment already records who, when and why.
- `LabOrderDTO` gains a read-only boolean reporting whether the order's exams are locked, computed from the same rule, so a client can disable the affected controls instead of discovering the refusal on click.
- Every other operation on an invoiced order is **unchanged**: entering and editing results, assigning a profile, notes, sample type, method, status changes, printing, referrals, tags, cancelling. Creating an order with its exams is unchanged too — a new order cannot have an invoice yet.
- Not a breaking change in practice: the refused calls are calls that currently corrupt the invoice relationship. Any client that relies on adding exams to an invoiced order was producing the discrepancy this change exists to stop.

## Capabilities

### New Capabilities
- `order-composition`: which exams an order contains and when that set may change. This change introduces its first requirement set — the invoice-issued lock, what stays permitted while it holds, and how annulment releases it.

### Modified Capabilities
<!-- None: openspec/specs/ holds laboratory-settings and test-catalog, neither of which specifies order composition or invoicing. -->

## Impact

- `service/LabTestServiceImp.java` — `addTestToOrder` and `removeTestFromOrder` gain the guard. These are the only two methods that change an existing order's exam set; `LabOrderServiceImp.createOrder` is the only other place a `LabTest` is created and it runs before any invoice can exist.
- `service/LabOrderServiceImp.java` — `toDTO` computes the new read-only flag.
- `payload/LabOrderDTO.java` — one new read-only boolean, ignored on write like `customerName` and `publicToken` already are.
- `repositories/InvoiceRepository.java` — no change; `existsByOrderIdAndStatusNot` is already declared.
- `controller/v1/LabOrderController.java` — no change. The endpoints, their permissions and their shapes stay as they are; only the service refuses.
- No new permission, no `Permission` enum value, no role or seed change.
- No database change: no new column, no migration, nothing for `schema.sql`. The rule is derived from invoice status at read time.
- New tests under `src/test/java/marroquinsoftware/labflowapi/` for the guard and for the annulment release.
- Consumer impact: the frontend order detail screen must disable its "Agregar examen" and "Quitar examen" controls when the flag is set. Proposed separately in `labflow_frontend` as the matching change; this change is what makes the rule true regardless of what any client does.
- Out of scope: freezing anything else about an invoiced order (customer, clinical context, tags, notes), reconciling invoices against orders that already diverged before this change, and any change to how invoices are issued or annulled.
