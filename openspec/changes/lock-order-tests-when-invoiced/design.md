## Context

See proposal.md — Why, and specs/order-composition/spec.md for the requirements. The current state that shapes the approach:

- **Two methods change an existing order's exam set**, both in `LabTestServiceImp`: `addTestToOrder` and `removeTestFromOrder`. `LabOrderServiceImp.createOrder` is the only other place a `LabTest` is constructed, and it runs while the order is being created, before any invoice can reference it.
- **Neither of those two methods is `@Transactional`.** They rely on the repository's own per-call transaction, so a guard written naively would read the invoice state in one transaction and write in another.
- **The "live invoice" concept already exists** and is used in two places: `InvoiceServiceImp.createInvoice` refuses a second invoice with `findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(orderId, ANULADA)`, and `LabOrderServiceImp.cancelOrder` uses the same call to decide whether cancelling must annul in cascade. `existsByOrderIdAndStatusNot` is declared on the repository and currently has no callers.
- **`LabOrderServiceImp.toDTO` is private and serves both reads** — `getOrderById` and the paged `getAllOrders`. Anything computed inside it runs once per row of the listing.
- **The repository is deliberate about N+1**: `default_batch_fetch_size=50` globally, `@BatchSize(50)` on `LabOrder.tags` and `Invoice.items`, an `@EntityGraph` on `findReceivables`. Each was added because a listing screen went serial. On Cloudflare each request also pays a ~0.7 s floor, which is why `LabOrderDTO.testIds` exists at all.
- **`APIException` maps to HTTP 400** with its message in the body (`MyGlobalExceptionHandler`). This is the established shape for a refused business rule, and the messages are user-facing Spanish shown directly by the frontend.
- **No migration mechanism.** `schema.sql` is hand-run against production and this repository has just spent a change recovering from a column that was mapped but never created. A design that needs no DDL is worth a lot here.

## Goals / Non-Goals

**Goals:**

- The rule holds regardless of the caller: no client, script or future screen can add or remove an exam on an order with a live invoice.
- The check and the mutation are atomic, so the rule cannot be lost to a race between them.
- A client can render the controls correctly from the order it already fetched, without a second query.
- The order listing does not become one query per row.

**Non-Goals:**

- Making invoice issuance itself race-proof against a simultaneous exam addition. See Risks — the guard closes the direction that matters and the remaining window is narrower than the one that exists today.
- Reconciling orders that already diverged from their invoices before this change. There is no detection or repair pass here.
- Freezing anything else about an invoiced order. The specs are explicit about what stays available.
- Any stored representation of "locked". Nothing is persisted.

## Decisions

### Enforce in the service, at the two methods that mutate the set

The guard goes in `LabTestServiceImp.addTestToOrder` and `removeTestFromOrder`, next to the existing "el examen no pertenece a la orden indicada" check. Both are the single funnel for their operation: the controller endpoints are thin delegations, and no other service calls them.

*Alternative considered:* enforce in `LabOrderController` with a method-security expression, next to the `@PreAuthorize` lines. Rejected — the lock is not a permission. It depends on the state of the order being addressed, `@PreAuthorize` would have to load the invoice to decide, and the rule would then live somewhere the service could be called around. The specs state it as a property of the order for this reason.

*Alternative considered:* a JPA lifecycle hook (`@PrePersist` / `@PreRemove`) on `LabTest`. Rejected — it cannot produce the user-facing message the specs require (naming the invoice), it fires inside the flush where the transaction is already committing, and it would also fire for `createOrder`, which must keep working.

### Make both methods transactional, and read the invoice inside that transaction

Neither method is `@Transactional` today, so the guard's read and the subsequent write would be two separate transactions with a window between them. Both get `@Transactional`, matching `LabOrderServiceImp`, where every mutation already carries it.

This is a behavior-preserving addition for the existing logic — each method performs a single write — and it is what makes "a refusal changes nothing" true rather than merely likely.

### Use the invoice-returning query for the guard, and the `exists` query for the flag

The guard needs the invoice number for its message, so it uses `findFirstByOrderIdAndStatusNotOrderByIssuedAtDesc(orderId, ANULADA)` — the same call `cancelOrder` makes, which keeps the two places that reason about a live invoice reading identically. The message follows the house style of `createInvoice`'s refusals, naming the document: the caller is told which invoice holds the order and can navigate to it to annul.

The read-side flag does not need the number, only whether one exists, so it uses `existsByOrderIdAndStatusNot` — already declared, and cheaper than materialising an invoice per order.

*Alternative considered:* deriving the lock from `OrderStatus` by adding an `INVOICED` value. Rejected outright — status is the results workflow (`PENDING → IN_PROGRESS → COMPLETED → VERIFIED → DELIVERED`) and invoicing is orthogonal to it; an order can be invoiced at any point in that progression. Overloading it would make "invoiced" and "results ready" mutually exclusive, which is wrong, and it would need a migration and a check-constraint drop.

### Compute the flag for a page in one query, not per row

`toDTO` is shared by `getOrderById` and the paged `getAllOrders`, so calling `existsByOrderIdAndStatusNot` from inside it would add one query per order on the listing — exactly the pattern `@BatchSize` and the `@EntityGraph` on `findReceivables` were added to remove, and expensive at the platform's per-request floor.

Instead the flag is resolved for a whole page at once: a repository query returning the ids of the orders in the page that have a live invoice

```
select distinct i.order.id from Invoice i
 where i.order.id in :orderIds and i.status <> ANULADA
```

and `toDTO` takes that set as a parameter, in the same spirit as `salesInvoiceRows` returning rows for the service to fold. `getOrderById` passes a one-element set, so both paths share one code path and the single read stays a single extra query.

*Alternative considered:* a `@Formula` or a mapped `@OneToMany<Invoice>` on `LabOrder` with the filter applied in Java. Rejected — `@Formula` puts a correlated subquery on every load of the entity, including the many paths that never map it to a DTO, and mapping the collection invites loading every invoice of the order to answer a boolean.

### Name the field for what the client must do: `testsLocked`

`LabOrderDTO` gains `private boolean testsLocked`, read-only in the same way `customerName`, `customerSex` and `publicToken` already are — populated in `toDTO`, ignored on the way in because `createOrder`/`updateOrder` never read it.

*Alternative considered:* `hasLiveInvoice`. It is more literal, and the frontend already fetches the invoice so it could compute that itself. Rejected as the name because it leaks the reason rather than stating the constraint: a client should not have to know that invoicing is what freezes exams in order to disable a button, and if another reason to freeze the set is ever added, `testsLocked` remains true and `hasLiveInvoice` becomes a lie. A primitive `boolean` (not `Boolean`) keeps it absent-means-false for older clients, consistent with `pregnant` and `menopausal`.

## Risks / Trade-offs

- **A race in the other direction still exists**: an exam added at the same instant an invoice is being issued could land after `createInvoice` has read the exams to bill, producing exactly the discrepancy this change targets. → Not closed here. The window is narrow (both sides are short transactions), it is strictly smaller than today's — where the same discrepancy needs no race at all, just a click — and closing it properly means a pessimistic read of the order's exams in `createInvoice`, alongside the `findWithLockById` it already uses for payments. Called out so it is a known gap rather than an assumed guarantee.

- **The frontend can still present an enabled control.** The flag makes the correct state available, but the existing screen derives the invoice from a best-effort fetch that swallows its own failure. → The frontend change is proposed separately and switches to the flag; regardless, the API refuses, so the worst case is a 400 with a message that explains itself instead of silent corruption.

- **The guard's message is only as good as its wording.** A refusal a receptionist cannot act on becomes a support call. → The message names the invoice and points at annulment, which is the only correction path; the specs require it to name the invoice for this reason.

- **Two tests exercising the H2 suite cannot prove the production behavior of the invoice status filter.** The rule is plain JPQL over an enum, so the exposure is low, but `PostgresQueryCompatibilityTest` exists precisely because H2 accepts queries Postgres rejects. → The new query is a simple `in` plus an enum comparison, the same shape as the already-covered `salesInvoiceRows`; if the listing query is written as a `@Query`, adding it to that test is cheap and worth doing.

## Migration Plan

1. Land the guard, the transactional annotations, the repository query and the DTO field together — the guard is the part that makes the rule true, and the flag is useless without it.
2. No DDL, no `schema.sql` entry, no data backfill. The rule is derived at read time from invoice status, so it applies to every existing order the moment the code deploys, including orders already invoiced.
3. Deploy before or independently of the frontend change. The API refusing is correct on its own; the frontend only improves how the refusal is avoided.

**Rollback:** revert the code. Nothing is persisted and no schema changed, so there is no state to undo — an older image simply stops enforcing the rule.

## Open Questions

- Whether the order **listing** should carry the flag at all, or only the detail read. The listing has no add/remove controls today, so nothing consumes it there. It is included because `toDTO` is shared and splitting it would cost more than the one batched query, but if the listing query ever becomes a bottleneck this is the first thing to drop. Does not affect the specs, the approach or the tasks.
