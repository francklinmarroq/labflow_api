## Purpose

Defines which exams an order contains and when that set may change. It covers the point at which the set stops being editable — the issuing of an invoice, which charges for exactly those exams — and what remains permitted on an order whose set is frozen.

## ADDED Requirements

### Requirement: An order's exam set is frozen while it has a live invoice

An order SHALL be considered to have a live invoice when an invoice exists for it whose status is not `ANULADA`. While an order has a live invoice, the API SHALL refuse to add an exam to it and SHALL refuse to remove an exam from it.

A refusal SHALL change nothing: no exam is created or deleted, the invoice is untouched, and the order's status is unaffected. The refusal SHALL be reported as a business-rule error — the same HTTP 400 and message shape the API already uses to refuse invoicing a cancelled order — and the message SHALL name the invoice that holds the lock, so the caller can find it.

The lock SHALL NOT depend on who is calling. It is a property of the order, not a permission: a caller holding `ORDERS_CREATE` or `ORDERS_DELETE` is refused exactly as any other caller is.

#### Scenario: Adding an exam to an invoiced order

- **WHEN** a caller with `ORDERS_CREATE` posts an exam to `POST /api/v1/orders/{orderId}/tests` for an order that has a live invoice
- **THEN** the request is refused with a business-rule error naming that invoice
- **AND** the order's exams are exactly what they were before the request

#### Scenario: Removing an exam from an invoiced order

- **WHEN** a caller with `ORDERS_DELETE` requests `DELETE /api/v1/orders/{orderId}/tests/{testId}` for an order that has a live invoice
- **THEN** the request is refused with a business-rule error naming that invoice
- **AND** the exam is still on the order, with its results intact

#### Scenario: An order with no invoice is unaffected

- **WHEN** an exam is added to or removed from an order that has never been invoiced
- **THEN** the operation succeeds exactly as it did before this requirement existed

#### Scenario: Creating an order with its exams

- **WHEN** an order is created through `POST /api/v1/orders` with a list of exams
- **THEN** the exams are created with it, because an order cannot have an invoice before it exists

### Requirement: Annulling the invoice releases the lock

An invoice whose status is `ANULADA` SHALL NOT lock the order it was issued from. An order whose only invoices are annulled SHALL accept exams being added and removed exactly as an order that was never invoiced does.

This makes annul → correct the exams → issue a new invoice the way an order invoiced with the wrong exams is corrected. The API SHALL NOT offer any other way to change a locked order's exams: there SHALL be no permission, role or parameter that adds or removes an exam while a live invoice exists.

#### Scenario: Correcting an order invoiced with the wrong exams

- **WHEN** the live invoice of a locked order is annulled, and an exam is then added to that order
- **THEN** the exam is added, because the order no longer has a live invoice

#### Scenario: A second invoice locks the order again

- **WHEN** a new invoice is issued for an order whose previous invoice was annulled
- **THEN** the order's exams are frozen again by the new invoice

#### Scenario: No override exists

- **WHEN** any caller, holding any combination of permissions, attempts to add or remove an exam on an order with a live invoice
- **THEN** the request is refused

### Requirement: Everything else about an invoiced order stays available

The lock SHALL apply only to which exams the order contains. Every other operation on an order with a live invoice SHALL remain available to the callers who could already perform it, unchanged: recording and editing results for its exams, assigning a profile to an exam, editing an exam's notes, sample type and method, changing the order's status through the results workflow, printing the report, the public patient report, referrals, tags, and cancelling the order.

#### Scenario: Entering results on an invoiced order

- **WHEN** a caller with `ORDERS_ENTER_RESULTS` records results for an exam of an order that has a live invoice
- **THEN** the results are saved, and the order's status advances through the results workflow as it normally does

#### Scenario: Editing an exam's capture details on an invoiced order

- **WHEN** a profile, note, sample type or method is set on an exam of an invoiced order
- **THEN** the change is saved, because none of them changes which exams the order contains

#### Scenario: Printing and cancelling an invoiced order

- **WHEN** the report of an invoiced order is printed, or the order is cancelled by a caller permitted to do so
- **THEN** the operation behaves exactly as it did before this change

### Requirement: The lock is readable on the order

The order the API returns SHALL report whether its exam set is currently locked, as a read-only boolean derived from the live-invoice rule. It SHALL be present on every read of an order, and SHALL be ignored when an order is created or updated, in the same way the order's patient name and public token already are.

A client SHALL be able to rely on it to present the add and remove controls as unavailable, rather than having to issue a separate query about the order's invoices, and SHALL NOT be able to change the lock by sending the field.

#### Scenario: Reading a locked order

- **WHEN** a caller reads an order that has a live invoice
- **THEN** the order reports its exam set as locked

#### Scenario: Reading an order that is not locked

- **WHEN** a caller reads an order that has no invoice, or whose only invoices are annulled
- **THEN** the order reports its exam set as not locked

#### Scenario: The flag cannot be written

- **WHEN** a client sends the lock field while creating or updating an order
- **THEN** the value is ignored, and a later read reports the lock as the live-invoice rule determines it
