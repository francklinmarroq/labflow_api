## Purpose

Defines who an invoice is made out to: the patient of the order, as it has always been, or a billing client from the catalogue when a company or insurer is paying. It covers how that choice is made when the invoice is issued, what the document freezes about both parties, and how invoices billed to a company are found and collected.

## ADDED Requirements

### Requirement: The invoice recipient is chosen when the invoice is issued

Issuing an invoice SHALL accept an optional billing client. When one is given, the invoice SHALL be made out to that billing client; when none is given, the invoice SHALL be made out to the patient of the order, which SHALL remain the behaviour of any caller that does not send the new field.

The choice SHALL be a property of the invoice, not of the order or of the patient: two orders for the same patient MAY be invoiced to different recipients, and nothing about the order changes when a recipient is chosen.

The API SHALL refuse to issue an invoice naming a billing client that does not exist in the caller's laboratory, reporting it the way it reports any other unknown resource, and SHALL create no invoice and consume no invoice number in that case.

Every other rule of issuing an invoice SHALL be unaffected: an order that is cancelled, has no exams, or already has a live invoice is refused exactly as before, whoever the recipient would have been.

#### Scenario: Issuing to a billing client

- **WHEN** a caller with `INVOICES_CREATE` issues an invoice for an order and names a billing client of its laboratory
- **THEN** the invoice is issued to that client

#### Scenario: Issuing to the patient

- **WHEN** an invoice is issued for an order without naming a billing client
- **THEN** the invoice is made out to the patient of the order, exactly as it was before this capability existed

#### Scenario: An unknown billing client

- **WHEN** an invoice is issued naming a billing client that does not exist, or that belongs to another laboratory
- **THEN** the request is refused as an unknown resource
- **AND** no invoice is created and no fiscal number is consumed

#### Scenario: The order is unchanged by the choice

- **WHEN** an invoice for an order is issued to a billing client
- **THEN** the order still records the same patient and is otherwise unchanged

### Requirement: The invoice freezes both the recipient and the patient

When an invoice is issued to a billing client, the invoice SHALL freeze that client's name and RTN as the customer name and customer RTN it prints and reports — the same fields an invoice made out to a patient already freezes — and SHALL record which billing client it was issued to.

Every invoice SHALL additionally freeze the name of the patient of its order, so that an invoice made out to a company still states whose exams were paid for. An invoice issued before this capability existed, which has no frozen patient name, SHALL be understood to have been made out to its patient.

The manually typed customer RTN that the API already accepts SHALL apply only to invoices made out to the patient. When a billing client is named, the RTN SHALL come from the client's record and the typed value SHALL be ignored, so a fiscal document can never carry an RTN that contradicts the client it names.

An invoice's recipient SHALL NOT be changeable after issue. Correcting it SHALL follow the same path as any other fiscal correction: annul the invoice and issue a new one.

#### Scenario: What a company invoice carries

- **WHEN** an invoice is issued to a billing client
- **THEN** its customer name and customer RTN are the client's, its billing client is recorded, and its frozen patient name is the patient of the order

#### Scenario: A typed RTN does not override the client's

- **WHEN** an invoice is issued naming a billing client and also carrying a manually typed customer RTN
- **THEN** the invoice carries the billing client's RTN

#### Scenario: A patient invoice is unchanged

- **WHEN** an invoice is issued without a billing client
- **THEN** its customer name and RTN are the patient's, with the typed RTN honoured as before, and its frozen patient name is that same patient

#### Scenario: The recipient cannot be edited afterwards

- **WHEN** an invoice has been issued
- **THEN** no operation changes who it was made out to, and correcting it requires annulling the invoice and issuing a new one

### Requirement: Invoices report who they were billed to

Every read of an invoice — the listing, the detail, and the receivables listing — SHALL identify which billing client it was issued to, and SHALL report the frozen patient name. An invoice made out to a patient SHALL report no billing client.

The name to show for the recipient SHALL be the customer name the invoice froze at issue, for a company exactly as for a patient: a reader never has to decide between two names, and a client renamed afterwards does not rewrite what the document says.

#### Scenario: Reading a company invoice

- **WHEN** an invoice issued to a billing client is read, listed, or appears among receivables
- **THEN** it identifies that client, and its customer name is the client's name frozen at issue, alongside the patient the exams were for

#### Scenario: Reading a patient invoice

- **WHEN** an invoice issued to a patient is read or listed
- **THEN** it reports no billing client

### Requirement: Invoices can be filtered and collected by billing client

The invoice listing SHALL accept an optional billing client filter and SHALL return only the invoices issued to that client. The filter SHALL be applied by the API over all matching invoices, not over one page, and SHALL combine with the status, order, date-range, search and tag filters already supported.

The API SHALL provide a statement of account for a billing client — its invoices and their active payments in chronological order, with a running balance and the totals invoiced, paid and outstanding — on the same terms as the statement that already exists for a patient, excluding annulled invoices and annulled payments.

The API SHALL also report what each billing client currently owes: for every billing client with at least one invoice that still has an open balance, its name, how many such invoices it has, and the sum of those balances.

Both SHALL require `INVOICES_VIEW`, as the receivables and patient statement already do.

#### Scenario: Filtering the listing

- **WHEN** the invoice listing is requested for a given billing client
- **THEN** only invoices issued to that client are returned, across every page of results

#### Scenario: Filtering combined with status

- **WHEN** the invoice listing is requested for a billing client and a status
- **THEN** only that client's invoices in that status are returned

#### Scenario: Statement of a billing client

- **WHEN** the statement of a billing client is requested
- **THEN** its invoices and active payments are returned in chronological order with a running balance and the totals invoiced, paid and outstanding
- **AND** annulled invoices and annulled payments are excluded

#### Scenario: What each client owes

- **WHEN** the outstanding balance by billing client is requested
- **THEN** every billing client with at least one invoice carrying an open balance is listed with its name, its number of such invoices, and the sum of their balances
- **AND** clients with nothing outstanding are absent

#### Scenario: Invoices billed to patients are not attributed to a client

- **WHEN** the outstanding balance by billing client is requested and open invoices exist that were made out to patients
- **THEN** those invoices are not counted under any billing client
