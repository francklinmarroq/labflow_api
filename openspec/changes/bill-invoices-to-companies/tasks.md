## 1. The billing client catalogue

- [x] 1.1 Create the `BillingClient` entity (table `billing_clients`) with `@TenantId laboratoryId`, `@NotBlank` `name` and `rtn`, and optional `phone`/`email`/`address`, plus the `uk_billing_client_rtn_per_lab` unique constraint on `(laboratory_id, rtn)`. Verify with `mvn -B --settings .mvn/settings.xml test -Dtest=BillingClientTest` once 4.1 exists; until then it is enough that it compiles.
- [x] 1.2 Add `BillingClientRepository` with `existsByRtn`, `findByRtn` (to name the duplicate in the message) and the paginated search for the listing. Hibernate filters the laboratory through `@TenantId`; do not add `laboratoryId` to the signatures.
- [x] 1.3 Add `BillingClientDTO` and `BillingClientResponse` under `payload/` (outside that package they are not registered for Jackson in the native image and serialize as `{}`).
- [x] 1.4 Implement `BillingClientService`/`BillingClientServiceImp` with list, get, create, update and delete. A duplicate RTN is refused with an `APIException` naming the client that already has it — do not let the unique constraint violation bubble up — and deletion is refused with an `APIException` if the client has invoices, annulled ones included. Verify with the tests in 4.1.
- [x] 1.5 Add `BillingClientController` at `/api/v1/billing-clients`: GET listing and GET by id with `hasAnyAuthority('CATALOG_VIEW','INVOICES_VIEW','INVOICES_CREATE')`, POST with `CATALOG_CREATE`, PUT with `CATALOG_EDIT`, DELETE with `CATALOG_DELETE`. Verify by starting the API locally and calling all five endpoints with a test token.
- [x] 1.6 Add to `schema.sql` the `create table if not exists billing_clients (...)` with the unique constraint **inside** the create (Postgres has no `add constraint if not exists`), as standalone statements and without `DO $$`. Verify by running the file twice in a row against a clean Postgres: the second run must not fail.

## 2. Who the invoice is made out to

- [x] 2.1 Add to `Invoice` the `@ManyToOne(fetch = FetchType.LAZY)` `billingClient` association (column `billing_client_id`, nullable) and the `patientName` column, documenting in the Javadoc that `customerName`/`customerRtn` are the recipient — patient or company — and `patientName` is always the order's patient.
- [x] 2.2 Add the two corresponding `alter table if exists invoices add column if not exists ...` statements to `schema.sql`. Verify as in 1.6: two runs in a row, the second without error.
- [x] 2.3 Add `billingClientId` (optional) to `InvoiceRequest`, with Javadoc stating that empty means "made out to the order's patient" and that a hand-typed `customerRtn` only applies in that case.
- [x] 2.4 In `InvoiceServiceImp.createInvoice`, resolve the billing client when `billingClientId` is given — `ResourceNotFoundException` if it does not exist in the laboratory, **before** consuming the CAI number — and set `customerName`/`customerRtn` from the client, ignoring the hand-typed RTN; with no `billingClientId`, leave the current three lines untouched. In both cases set `patientName` from `order.getCustomer().getName()`. Verify with the tests in 4.2.
- [x] 2.5 In `InvoiceServiceImp.toDTO`, report `billingClientId` and `patientName`; add both to `InvoiceDTO`. Read only the id off the lazy association (not `getName()`, which would force the load and turn the listing into an N+1). Verify with 4.5 that a page of invoices still costs a single query.
- [x] 2.6 Confirm there is no other path that builds an `Invoice`: `createInvoice` is the only one, and `annulInvoice`/`registerPayment`/`annulPayment` do not touch the recipient. Note it in the commit if another one turns up.

## 3. Per-company filter and collection

- [x] 3.1 Add the `billingClientId` parameter to `BillingSpecifications.invoices(...)` as an optional `equal` predicate on `billingClient.id` — no join, same as `orderId` — and thread it through `InvoiceService.getAllInvoices` and `InvoiceController` as a `@RequestParam(required = false)`. Verify with 4.3 that it filters across all invoices and that it combines with status, dates, search and tag.
- [x] 3.2 Extract from `getCustomerStatement` the construction of the statement of account (chronological merge of active invoices and payments with a running balance) so it can be reused, and add `getBillingClientStatement(Long)` over `findByBillingClientIdOrderByIssuedAtAsc`. Verify with 4.4 that a client with an annulled invoice and an annulled payment excludes both.
- [x] 3.3 Add the aggregate balance-per-client query (`group by` over the `PENDIENTE`/`PARCIAL` invoices with a non-null `billing_client_id`, returning id, name, count and sum of balances) and its DTO under `payload/`. Verify with 4.4 that invoices billed to a patient are not counted under any client.
- [x] 3.4 Expose `GET /reports/billing-client-statement` and `GET /reports/receivables-by-client` in `AccountingReportController` with `INVOICES_VIEW`, alongside the two that already exist. Verify by calling them against the local API.

## 4. Tests

- [x] 4.1 `BillingClientTest` (H2, `@DataJpaTest` + `TenantContext`, in the style of `OrderTestLockTest`): create with name and RTN; refusal without an RTN; refusal for a duplicate RTN within the same laboratory, naming the existing one; the same RTN accepted in another laboratory; refusal to delete a client with an invoice (an annulled one blocks too); successful deletion of one with no invoices; editing the name does not change an already-issued invoice.
- [x] 4.2 `InvoiceBillingPartyTest`: invoice to a company (customerName/RTN from the client, correct `billingClientId` and `patientName`); invoice without `billingClientId` identical to today's; the hand-typed `customerRtn` ignored when there is a client and honoured when there is not; a non-existent `billingClientId` ⇒ `ResourceNotFoundException`, no invoice created and the CAI sequence **not consumed**; two orders for the same patient billed to different recipients.
- [x] 4.3 Listing filter tests: only the requested client's invoices, and the combination with `status`.
- [x] 4.4 Tests for the per-client statement of account and the per-client balance: chronological order and running balance; exclusion of annulled invoices and payments; patient-billed invoices absent from the per-client balance; a client with no open balance absent.
- [x] 4.5 Verify the invoice listing did not gain queries: with a temporary `spring.jpa.show-sql=true`, count the queries for a page containing company invoices and confirm they are the same as before the change (revert the property afterwards).
- [x] 4.6 Add to `PostgresQueryCompatibilityTest` the listing filtered by `billingClientId` (alone and combined) and the aggregate per-client balance query. Verify by bringing up the documented container and confirming in the output that the tests **ran** (0 skipped), not that they skipped themselves.

## 5. Final verification

- [x] 5.1 `mvn -B --settings .mvn/settings.xml test` green, with `PostgresQueryCompatibilityTest` executed against the running container.
- [x] 5.2 Run the full `schema.sql` twice against a clean Postgres 16 and confirm the second run produces no error.
- [x] 5.3 Review that no new DTO ended up outside `payload/` and that no enum value was added (which would require dropping a `..._check` in `schema.sql`).
