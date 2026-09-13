## Context

See proposal.md — Why.

What shapes the approach:

- `Customer` in this codebase **is the patient**: it carries `ageInDays`, `sex`, `pathologies` and the national id, and it is what an order points at. The word "cliente" in the UI and in `Invoice.customerName` means "whoever this invoice was made out to", which until now has always been the patient. Those two meanings are about to stop coinciding.
- An invoice is a frozen fiscal document. It already snapshots the CAI, the issuer's letterhead, the customer name and RTN, and the name and price of every exam. Everything that gets printed is read from the snapshot, never from the live record.
- Consumers of `Invoice.customerName` today: the printed invoice, the invoice listing and its search (`BillingSpecifications.invoices`, `lower(customerName) like …`), the receivables listing, and the sales report, which groups by it (`salesInvoiceRows`).
- The PlanetScale role production connects with was granted `postgres`, so `ddl-auto=update` **does** create the new table and the new columns there, at startup, as it has always done in develop. What that privilege does not change: `ddl-auto` only ever adds — it never drops an enum's `..._check`, never removes or renames, never backfills — which is why every schema change is still written into `schema.sql`, and why that file is what gets reviewed in the diff.
- `PostgresQueryCompatibilityTest` exists because H2 accepts optional-filter and type-inference shapes that Postgres rejects. Any new query built with Criteria or with `group by` belongs in it.

## Goals / Non-Goals

**Goals:**

- One meaning for the invoice's frozen customer fields: whoever the invoice was made out to. No consumer has to choose between two names.
- Zero change in behaviour and zero extra query for the path that already exists — an invoice to the patient.
- The per-page cost of the invoice listing and of receivables stays what it is: one query per page, no N+1.

**Non-Goals:**

- No billing client on the *order*. The recipient is decided when the invoice is issued.
- No "inactive" flag on the catalogue (see Decisions).
- Quotes (`Quote`) keep pointing at the patient and are untouched.
- No accounting change: the journal entry for an issue or a payment is the same regardless of who was billed. Receivables remain one pool; what this change adds is a breakdown of that pool by client, not a second ledger.

## Decisions

### A new entity, not a flag on `Customer`

`BillingClient` (table `billing_clients`), `@TenantId`, with `name`, `rtn`, and optional `phone`, `email`, `address`.

*Alternative rejected:* a `type` column on `Customer` (`PATIENT` | `COMPANY`). It looks cheaper and is not: every patient listing, every patient search, the order form, the quote form and the clinical history would have to start excluding one type, and the day one forgets, an insurer shows up in a patient picker. The clinical fields (`ageInDays`, `sex`, `pathologies`) are meaningless for a company, and `ageInDays` in particular feeds the age-discount calculator — a company with a null age silently lands in whatever bracket the calculator treats as unknown.

### The recipient is frozen into the customer fields that already exist

At issue time, when a billing client is named:

```
invoice.customerName = client.name
invoice.customerRtn  = client.rtn
invoice.billingClient = client        // new FK, nullable
invoice.patientName  = order.customer.name   // new column
```

and when none is named, the existing three lines are untouched and `patientName` is filled with the same name that goes into `customerName`.

*Alternative rejected:* a parallel pair `billToName` / `billToRtn`, leaving `customerName` as the patient. Then printing, the listing search, receivables and the sales report each have to decide which of the two to read, and every one of them that is not updated prints the patient's name on a company's invoice. Reusing the existing pair means the fiscal snapshot keeps exactly one meaning and every consumer is already correct without being touched.

The cost of that decision is real and is accepted: **the sales report will group a company invoice under the company**, because it groups by `customerName`. That is the right answer — the company is who paid — but it is a visible change for whoever reads that report, and it is called out in the proposal's Impact.

`customer_id` (the patient) stays `not null` and keeps pointing at the patient. Nothing about the order/patient relationship changes.

### `patientName`, nullable, with the old invoices reading as patient-billed

An invoice issued before this change has `billing_client_id` null and `patient_name` null. Null `billing_client_id` already means "made out to the patient", and for those invoices `customerName` *is* the patient's name, so printing falls back to it. No backfill, no `CommandLineRunner`, nothing to run once.

### The association is `LAZY` and only its id is read

`@ManyToOne(fetch = FetchType.LAZY)`. The DTO reports `billingClientId` and `patientName`; the name to show is `customerName`, already frozen and already in the DTO. Reading `getId()` off a lazy proxy does not hit the database, so a page of 500 invoices costs **no** extra join and **no** extra query.

*Alternative rejected:* leaving it EAGER (the JPA default, which `order` and `customer` use) and adding `billingClient` to the fetch joins in `BillingSpecifications.invoices` and to the `@EntityGraph` of `findReceivables`. That works and is what the existing to-one associations do, but it is a third LEFT JOIN on every listing query to fetch a name the row already carries.

### The filter goes in the Criteria specification, next to the others

`BillingSpecifications.invoices(...)` gains a `billingClientId` parameter, added as a predicate only when non-null — the same construction, and for the same reason, as every other optional filter in that class (a `:param is null` JPQL filter cannot have its type inferred by Postgres; see the class Javadoc). It is a plain `equal` on `billingClient.id`, so it needs no join and cannot duplicate rows, which keeps the count query and the pagination correct.

### The two collection reports mirror what exists for patients

- `GET /api/v1/reports/billing-client-statement?billingClientId=` returns the same `…StatementDTO` shape as `/reports/customer-statement`, built by the same event-merge logic. The service method is extracted so patient and client statements share it rather than being copied.
- `GET /api/v1/reports/receivables-by-client` returns one row per billing client with an open balance: id, name, count, sum of balances. One aggregate query (`group by`), not a query per client, and it joins `billing_clients` to get the live name — this is a working list for chasing payment, not a fiscal document, so the current name is the right one to show.

Both require `INVOICES_VIEW`, matching `/reports/receivables` and `/reports/customer-statement`.

### Delete is refused when the client has invoices; no `active` flag

A billing client referenced by any invoice — annulled ones included — cannot be deleted, with a business-rule message that says so. Editing stays open, and editing does not rewrite issued invoices, because they carry snapshots.

*Alternative considered and deferred:* an `active` boolean to retire a client from the picker without deleting it. It is the usual answer to "then it can never be removed", but a laboratory has a handful of these; a stale entry in a dropdown is a smaller cost than a column, a filter on every read, and a toggle in the UI. If real use shows the list growing stale, it is an additive change later.

### `schema.sql`: one `create table if not exists` plus two `alter`s

```sql
create table if not exists billing_clients (
  id bigserial primary key,
  laboratory_id bigint,
  name varchar(255) not null,
  rtn varchar(255) not null,
  phone varchar(255),
  email varchar(255),
  address varchar(255),
  constraint uk_billing_client_rtn_per_lab unique (laboratory_id, rtn)
);
alter table if exists invoices add column if not exists billing_client_id bigint references billing_clients(id);
alter table if exists invoices add column if not exists patient_name varchar(255);
```

Single statements, no `DO $$` — Spring splits this file on `;` and does not understand dollar-quoting. The unique constraint goes **inside** the `create table` because Postgres has no `add constraint if not exists`, and a second run of the file must be a no-op. No check constraints and no new enum values, so none of the `..._check` drops this file does elsewhere apply here — which is also why nothing in this change has to be run against a database before the image: it is all additive, and `ddl-auto` applies it on startup. The statements are written down as the record of the schema, not as a step someone must remember to perform.

### Everything else follows the house patterns

`BillingClientController` under `/api/v1/billing-clients` with the `CATALOG_*` authorities (plus `INVOICES_VIEW`/`INVOICES_CREATE` on the read endpoints, exactly as `PathologyController` widens its GET for the order screens); `BillingClientService`/`Imp`; `BillingClientRepository`. Every new DTO lives in `payload/` — that package is registered wholesale for Jackson binding in the native image, and a DTO outside it serializes as `{}`.

## Risks / Trade-offs

- **The schema change now rides in with the deploy, unreviewed.** `wrangler deploy` is what creates `billing_clients` and the two columns in production: whatever the entities map at startup is applied, with no human between the image and the schema. That removes the old ordering trap and removes the moment someone looked at the DDL. Mitigation: the same statements live in `schema.sql` and are reviewed in the diff, and the develop deploy in step 1 below is what proves the mapping before production sees it.
- **`ddl-auto` will not add the unique constraint to a table that already exists.** If `billing_clients` is already there from an earlier attempt — a develop database, most likely — `update` creates nothing and `uk_billing_client_rtn_per_lab` never appears, leaving the duplicate-RTN rule resting on the service check alone. Mitigation: confirm the constraint is actually on the table in develop before promoting, and create the table from `schema.sql` when starting from a clean database.
- **The sales report regroups company invoices under the company** → intended, documented in the proposal's Impact, and worth telling the laboratory when the release notes are written.
- **RTN uniqueness rejects a legitimate second record** for a company that really does share an RTN with another (a branch, say) → accepted: the RTN is what the recipient deducts the expense with, and two records under one RTN is how a statement of account quietly splits in half. The message names the existing client so the user goes to it instead.
- **A typed RTN is silently ignored when a client is named** → the frontend does not offer the field in that case, so the only way to hit it is an API client sending both; ignoring beats emitting a fiscal document whose RTN contradicts the name it prints.

## Migration Plan

1. `wrangler deploy --env develop` and exercise it there: issue one invoice to a patient and one to a company, annul one, print both. Confirm in that database that `billing_clients` came up carrying `uk_billing_client_rtn_per_lab`.
2. Merge to `main`, cut the release, push the image, `wrangler deploy` — in that order.
3. Merge the frontend afterwards. The new response fields are additive, so the current frontend against the new API simply does not show them; the reverse would break.

No step runs SQL by hand: everything this change adds is additive and `ddl-auto=update` applies it at startup in both environments.

Rollback: the two new columns and the new table are additive and unread by the previous image, so rolling the Worker back to the previous tag is enough. The empty table stays behind — `ddl-auto` never drops anything — which is harmless, and dropping it would be a hand-run statement not worth the risk.
