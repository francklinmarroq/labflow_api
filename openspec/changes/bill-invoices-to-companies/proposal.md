## Why

Every invoice today goes out in the patient's name: `createInvoice` copies the name and the RTN off the record (`order.getCustomer()`) and there is no way to say anything else. But part of the laboratory's work is charged to a third party — an insurer, the company that sends its staff in for pre-employment exams, a corporate agreement — and that third party needs the invoice made out to its registered name and its RTN in order to deduct the expense. Without that, the front desk has two ways out and both are bad: issue it in the patient's name and have the company reject it, or register the insurer as though it were a patient in the roster, polluting the clinical record with an entity that has no age, no sex and no pathologies, and littering the results history.

## What Changes

- **A new billing client catalogue**, separate from the patient roster: registered name and RTN are required (RTN unique per laboratory), phone, email and address optional. Full CRUD under the `CATALOG_*` permissions that already exist.
- **Issuing an invoice picks who it is billed to**: the order's patient (what happens today, and still the default) or a client from the catalogue. `InvoiceRequest` accepts an optional `billingClientId`.
- **The invoice freezes who it was billed to.** When it goes out in a client's name, the `customerName`/`customerRtn` that already gets printed becomes the company's; the patient is frozen separately, in a new field, so the document still states whose exams these are. The link to the patient (`customer_id`) and to the order is untouched.
- **Per-company follow-up**: the invoice listing accepts a `billingClientId` filter, and there is a statement of account per billing client plus a summary of how much each one owes, mirroring what already exists per patient.
- **A client with issued invoices cannot be deleted**: it is data on a fiscal document.
- Changing the recipient of an already-issued invoice still does not exist: it is annulled and reissued, like any other fiscal correction.
- Quotes are out of scope: they stay with the patient.

## Capabilities

### New Capabilities
- `billing-clients`: the catalogue of billing clients (companies and insurers) — what data it carries, what is unique, who may administer it and when an entry may be deleted.
- `invoice-billing-party`: who an invoice is made out to — how it is chosen at issue time, what is frozen into the document, and how what was billed to a company is looked up and collected.

### Modified Capabilities
<!-- None: there is no billing spec in openspec/specs (today only laboratory-settings,
     order-composition, release-notes-seen and test-catalog). The billing behaviour
     this change touches is described in full in the new invoice-billing-party
     capability. -->

## Impact

- **Model and database**: a new `BillingClient` entity (table `billing_clients`, `@TenantId`, RTN unique per laboratory) and two new columns on `invoices` (`billing_client_id` nullable, `patient_name`). Everything here is additive, so `ddl-auto=update` creates it on startup in both environments now that the production role holds DDL privileges again. The idempotent statements are written in `schema.sql` all the same: that file is the reviewable record of the schema and what rebuilds a database from the repo.
- **API**: a new controller and service for `/api/v1/billing-clients`; `InvoiceRequest` gains `billingClientId`; `InvoiceDTO` gains the billing client and the patient name; `GET /invoices` gains the filter; two new reporting endpoints.
- **Consequence for reporting**: the sales report groups by `customerName`, so an invoice to a company will appear grouped under the company and not under the patient. That is correct — it is who paid — but it changes what anyone already using that report sees.
- **Frontend** (`labflow_frontend`, sibling change of the same name): the catalogue screen, the recipient picker when billing, the patient on the printed invoice, the filter and the statement of account.
- No new dependencies.
