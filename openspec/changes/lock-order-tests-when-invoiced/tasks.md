## 1. Read model: expose the lock on the order

- [ ] 1.1 Add `private boolean testsLocked;` to `payload/LabOrderDTO.java` with a comment marking it read-only and derived from the live invoice, in the same style as the `customerName` / `publicToken` comments — verified by the field being present and no setter call existing in `createOrder` or `updateOrder`
- [ ] 1.2 Add to `repositories/InvoiceRepository.java` a query returning the ids of orders with a live invoice for a page: `select distinct i.order.id from Invoice i where i.order.id in :orderIds and i.status <> ANULADA` — verified by the method compiling and its JPQL naming `InvoiceStatus.ANULADA` by its fully qualified enum path, as the existing `@Query` methods in that repository do
- [ ] 1.3 Change the private `LabOrderServiceImp.toDTO` to take the set of locked order ids and set `testsLocked` from it — verified by both call sites compiling and no invoice query remaining inside `toDTO` itself
- [ ] 1.4 Have `getAllOrders` resolve the set once per page from the query in 1.2 and pass it to `toDTO` — verified by the listing issuing exactly one invoice query per page regardless of page size, visible in the `show-sql` output
- [ ] 1.5 Have `getOrderById` pass a one-element set for the order it read — verified by a single order read issuing one invoice query

## 2. Enforce the lock

- [ ] 2.1 Annotate `LabTestServiceImp.addTestToOrder` and `removeTestFromOrder` with `@Transactional` so each method's guard and write commit together — verified by both methods carrying the annotation and the existing add/remove behavior still passing its tests
- [ ] 2.2 Add a private helper in `LabTestServiceImp` that loads the order's live invoice with `findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(orderId, InvoiceStatus.ANULADA)` and throws `APIException` naming the invoice number when one is present, wording it in Spanish and pointing at annulment as the way to correct the order — verified by the helper being the only place the message is written
- [ ] 2.3 Call that helper at the top of `addTestToOrder`, before the exam is constructed — verified by a test that posting an exam to an invoiced order is refused and the order's exam count is unchanged
- [ ] 2.4 Call it at the top of `removeTestFromOrder`, before the delete — verified by a test that deleting an exam from an invoiced order is refused and the exam and its runs are still there
- [ ] 2.5 Confirm no guard is added to `LabOrderServiceImp.createOrder` — verified by creating an order with `testIds` still producing all its exams, since an order cannot be invoiced before it exists

## 3. Regression tests

- [ ] 3.1 Add a test class for the lock alongside the existing tests in `src/test/java/marroquinsoftware/labflowapi/`, following the `@DataJpaTest` + H2 + `TenantContext` setup the rest of the suite uses (`OrderTagTest` and `InvoiceAccountingTest` are the closest precedents for building an order with an invoice) — verified by the class compiling and running
- [ ] 3.2 Cover "adding an exam to an invoiced order" and "removing an exam from an invoiced order": both refused, and the order's exam set unchanged after each — verified by both tests passing
- [ ] 3.3 Cover "an order with no invoice is unaffected": add and remove both succeed — verified by the test passing
- [ ] 3.4 Cover "annulling the invoice releases the lock": annul the live invoice, then add an exam successfully — verified by the test passing
- [ ] 3.5 Cover "a second invoice locks the order again": issue a new invoice after the annulment and confirm the refusal returns — verified by the test passing
- [ ] 3.6 Cover "everything else stays available": with a live invoice in place, assigning a profile, setting notes, sample type and method, and recording a result all still succeed — verified by the test passing, since this is the half of the requirement a guard in the wrong place would break
- [ ] 3.7 Cover the read model: an invoiced order reports `testsLocked` true, an order with no invoice and one whose only invoice is annulled report false, and a client sending the field cannot change it — verified by the tests passing
- [ ] 3.8 Run `mvn -B --settings .mvn/settings.xml test -Dtest=<NewTestClass>` — verified by the new suite passing on H2

## 4. Verification

- [ ] 4.1 Add the page query from 1.2 to `PostgresQueryCompatibilityTest` — verified by it passing against a real PostgreSQL (`docker run -d --rm --name labflow-pg-test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=labflow -p 55432:5432 postgres:16-alpine`), since the `in` + enum comparison is the shape that test exists to cover and H2 will not catch a mismatch
- [ ] 4.2 Run `mvn -B --settings .mvn/settings.xml test` — verified by the run being green, allowing for the two known pre-existing conditions (`PostgresQueryCompatibilityTest` self-skips without the local PostgreSQL; `LabflowapiApplicationTests` fails on `${DB_URL}` without a `.env`)
- [ ] 4.3 In the deployed app, open an invoiced order and confirm the API refuses both operations with a readable message — verified by the 400 and its Spanish text appearing, and by the order being unchanged afterwards
- [ ] 4.4 Confirm the results workflow on that same invoiced order still works end to end: enter a result, advance the status, print the report — verified by each step succeeding, which is what distinguishes this change from freezing the whole order
