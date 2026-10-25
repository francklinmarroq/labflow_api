## Purpose

Lets a laboratory issue non-fiscal sales documents (a Honduran *factura sin membrete*) instead of CAI invoices, decided by a laboratory-level switch, keeping them out of the fiscal numbering and the accounting while still tracking what is owed and paid.

## ADDED Requirements

### Requirement: The laboratory switch decides the document type

When a caller issues an invoice, the API SHALL decide whether it is fiscal or non-fiscal solely from the laboratory's `invoiceWithoutCai` preference at the moment of issuing. The issuing request SHALL NOT carry the document type, and any document-type value present in the request SHALL be ignored. With the switch off the API SHALL issue a fiscal invoice exactly as before this change. With the switch on the API SHALL issue a non-fiscal document regardless of whether the laboratory has a current CAI with numbers available.

#### Scenario: Switch off issues a fiscal invoice

- **GIVEN** a laboratory with `invoiceWithoutCai` off and a current CAI with numbers available
- **WHEN** a caller with `INVOICES_CREATE` issues an invoice
- **THEN** the invoice reports `fiscal` as `true`
- **AND** its number is the next number of the CAI and the CAI correlative advances by one

#### Scenario: Switch off and no usable CAI blocks issuing

- **GIVEN** a laboratory with `invoiceWithoutCai` off and no CAI that is current, configured and with numbers left
- **WHEN** a caller issues an invoice
- **THEN** the request is rejected with the existing error that explains each CAI
- **AND** no invoice, payment or journal entry is created

#### Scenario: Switch on ignores a valid CAI

- **GIVEN** a laboratory with `invoiceWithoutCai` on and a current CAI with numbers available
- **WHEN** a caller issues an invoice
- **THEN** the invoice reports `fiscal` as `false`
- **AND** neither CAI correlative changes

#### Scenario: Switch on without any CAI configured

- **GIVEN** a laboratory with `invoiceWithoutCai` on and no CAI configured at all
- **WHEN** a caller issues an invoice
- **THEN** the invoice is issued and reports `fiscal` as `false`

#### Scenario: Every other validation still applies

- **GIVEN** a laboratory with `invoiceWithoutCai` on
- **WHEN** a caller issues an invoice with an order that is already invoiced, a future issue date, or a cash sale without full payment
- **THEN** the request is rejected with the same error a fiscal invoice gets
- **AND** the internal correlative does not advance

### Requirement: Non-fiscal documents carry their own correlative

A non-fiscal document SHALL be numbered from a correlative of the laboratory that is independent of both CAIs, starting at 1 for a laboratory that never issued one and increasing by one per document. Its number SHALL be the correlative written with eight digits and no separators (for example `00000001`), so that it can never be mistaken for or collide with a CAI number. Two documents issued concurrently by the same laboratory SHALL NOT receive the same number. The correlative SHALL NOT advance when issuing fails.

#### Scenario: First non-fiscal document

- **GIVEN** a laboratory that has never issued a non-fiscal document
- **WHEN** it issues one
- **THEN** its number is `00000001`

#### Scenario: Correlative survives toggling the switch

- **GIVEN** a laboratory whose last non-fiscal document is `00000007`
- **WHEN** it turns the switch off, issues fiscal invoices, turns it back on and issues a non-fiscal document
- **THEN** the new document is `00000008`
- **AND** the CAI correlative only advanced for the fiscal invoices

### Requirement: The document type and its data are frozen at issue

Each invoice SHALL report a read-only boolean `fiscal`. Every invoice issued before this change SHALL report `true`. A non-fiscal document SHALL store no CAI, CAI range or CAI expiration date and SHALL report them as null; it SHALL still freeze the laboratory header (name, RTN, address, phone, email, headline and footer note), the recipient, the lines and the totals, as a fiscal invoice does. Changing the laboratory's switch afterwards SHALL NOT change the type or any frozen data of an invoice already issued.

#### Scenario: Non-fiscal document has no CAI data

- **WHEN** a non-fiscal document is read
- **THEN** `fiscal` is `false` and `cai`, `caiRangeFrom`, `caiRangeTo` and `caiExpirationDate` are null
- **AND** `labName`, `labRtn` and `labAddress` hold the laboratory's values at issue

#### Scenario: Toggling the switch does not rewrite history

- **GIVEN** a non-fiscal document issued while the switch was on
- **WHEN** the laboratory turns the switch off and the document is read again
- **THEN** it still reports `fiscal` as `false` and no CAI data

#### Scenario: Existing invoices remain fiscal

- **WHEN** an invoice issued before this change is read
- **THEN** it reports `fiscal` as `true` with its original CAI data

### Requirement: Non-fiscal documents stay out of the accounting

Issuing a non-fiscal document, registering a payment on it, annulling it, and annulling one of its payments SHALL NOT create or reverse any journal entry. Because they post nothing, these operations SHALL NOT be rejected for falling inside a closed accounting period. Fiscal invoices SHALL keep posting and reversing exactly as before.

#### Scenario: Issuing posts nothing

- **WHEN** a non-fiscal document with a positive total is issued on credit
- **THEN** no journal entry references it

#### Scenario: Cash sale posts nothing

- **WHEN** a non-fiscal document is issued as a cash sale with its full payment
- **THEN** the payment is recorded with its receipt number and the document is `PAGADA`
- **AND** no journal entry references the document or the payment

#### Scenario: Annulling a document and a payment

- **GIVEN** a non-fiscal document with one active payment
- **WHEN** the payment is annulled and then the document is annulled
- **THEN** both operations succeed, the payment and the document are annulled
- **AND** no journal entry is created

#### Scenario: Closed period does not apply

- **GIVEN** an accounting period that is closed
- **WHEN** a non-fiscal document is issued with an issue date inside that period
- **THEN** the document is issued

#### Scenario: Fiscal invoice keeps its accounting

- **WHEN** a fiscal invoice is issued, paid and annulled
- **THEN** the same issue, payment and reversal entries as before this change are posted

### Requirement: Receivables and statements include non-fiscal documents

The receivables listing, the receivables total, the receivables by billing client, and the customer and billing-client statements SHALL include non-fiscal documents with their balances exactly as they include fiscal invoices. Each listed invoice SHALL report its `fiscal` value so a client can tell them apart.

#### Scenario: Pending non-fiscal document is owed

- **GIVEN** a non-fiscal document of L 500 issued on credit with no payments
- **WHEN** the receivables listing and the receivables total are read
- **THEN** the document appears with a balance of L 500 and `fiscal` `false`
- **AND** the total includes those L 500

### Requirement: Fiscal sales register excludes non-fiscal documents

The detailed sales register SHALL list only fiscal invoices. The aggregated sales report and the collections report SHALL keep including every document and payment, and their summaries SHALL additionally report the part of the total that came from non-fiscal documents (count and amount for sales, amount for collections), which is zero when there are none.

#### Scenario: Sales register is fiscal only

- **GIVEN** a range with two fiscal invoices and one non-fiscal document
- **WHEN** the detailed sales register for that range is read
- **THEN** it lists the two fiscal invoices only and its totals add up only those

#### Scenario: Aggregated reports break out non-fiscal sales

- **GIVEN** a range with fiscal invoices totalling L 1,000 and one non-fiscal document of L 300, all fully paid
- **WHEN** the sales report and the collections report for that range are read
- **THEN** the sales summary total is L 1,300 with a non-fiscal count of 1 and amount of L 300
- **AND** the collections summary total is L 1,300 with a non-fiscal amount of L 300
