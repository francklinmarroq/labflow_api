## MODIFIED Requirements

### Requirement: The invoice recipient is chosen when the invoice is issued

Issuing an invoice SHALL accept an explicit recipient: a patient of the laboratory, a billing client of the catalogue, or a final consumer given by name with an optional RTN. The legacy optional billing client field SHALL keep its meaning: when it is given, the invoice SHALL be made out to that billing client.

When no recipient is given and every order on the invoice belongs to the same patient, the invoice SHALL be made out to that patient, which SHALL remain the behaviour of any caller that does not send the new fields. When no recipient is given and it cannot be deduced — orders of several patients, or no orders at all — the API SHALL refuse the request asking to whom it is made out, and SHALL create no invoice and consume no invoice number.

The choice SHALL be a property of the invoice, not of the orders or of the patients: two orders for the same patient MAY be invoiced to different recipients, and nothing about an order changes when a recipient is chosen.

The API SHALL refuse to issue an invoice naming a patient or a billing client that does not exist in the caller's laboratory, reporting it the way it reports any other unknown resource, and SHALL create no invoice and consume no invoice number in that case.

Every other rule of issuing an invoice SHALL be unaffected: an order that is cancelled, has no exams, or already has a live invoice is refused exactly as before, whoever the recipient would have been, and the whole invoice is refused with it.

#### Scenario: Issuing to a billing client

- **WHEN** a caller with `INVOICES_CREATE` issues an invoice and names a billing client of its laboratory
- **THEN** the invoice is issued to that client

#### Scenario: Issuing to the patient

- **WHEN** an invoice is issued for orders of a single patient without naming a recipient
- **THEN** the invoice is made out to that patient, exactly as it was before this capability existed

#### Scenario: Issuing to a final consumer

- **WHEN** an invoice is issued naming a final consumer with a name and no RTN
- **THEN** the invoice is made out to that name, with no patient and no billing client recorded

#### Scenario: The recipient cannot be deduced

- **WHEN** an invoice is issued for orders of two different patients without naming a recipient
- **THEN** the request is refused asking to whom the invoice is made out
- **AND** no invoice is created and no fiscal number is consumed

#### Scenario: An unknown billing client

- **WHEN** an invoice is issued naming a billing client that does not exist, or that belongs to another laboratory
- **THEN** the request is refused as an unknown resource
- **AND** no invoice is created and no fiscal number is consumed

#### Scenario: The order is unchanged by the choice

- **WHEN** an invoice for one or more orders is issued to a billing client
- **THEN** each order still records the same patient and is otherwise unchanged

### Requirement: The invoice freezes both the recipient and the patient

When an invoice is issued, it SHALL freeze the recipient's name and RTN as the customer name and customer RTN it prints and reports: the patient's, the billing client's, or the final consumer's as typed. When it is issued to a billing client it SHALL also record which client.

The patient SHALL be frozen per order: for every order on the invoice, the invoice SHALL keep that order's patient name, so that an invoice made out to a company still states whose exams were paid for. The invoice's own frozen patient name SHALL be filled when all its orders belong to one patient, and SHALL be empty when they belong to several or when the invoice has no orders. An invoice issued before this capability existed, which has no frozen patient name, SHALL be understood to have been made out to its patient.

The manually typed customer RTN SHALL apply only to invoices made out to a patient or a final consumer. When a billing client is named, the RTN SHALL come from the client's record and the typed value SHALL be ignored, so a fiscal document can never carry an RTN that contradicts the client it names.

An invoice's recipient SHALL NOT be changeable after issue. Correcting it SHALL follow the same path as any other fiscal correction: annul the invoice and issue a new one.

#### Scenario: What a company invoice carries

- **WHEN** an invoice is issued to a billing client for orders of a single patient
- **THEN** its customer name and customer RTN are the client's, its billing client is recorded, and its frozen patient name is that patient

#### Scenario: A company invoice for several patients

- **WHEN** an invoice is issued to a billing client for orders of three different patients
- **THEN** each of its orders carries its own frozen patient name, and the invoice's own frozen patient name is empty

#### Scenario: A typed RTN does not override the client's

- **WHEN** an invoice is issued naming a billing client and also carrying a manually typed customer RTN
- **THEN** the invoice carries the billing client's RTN

#### Scenario: A patient invoice is unchanged

- **WHEN** an invoice is issued to a patient
- **THEN** its customer name and RTN are the patient's, with the typed RTN honoured as before, and its frozen patient name is that same patient

#### Scenario: The recipient cannot be edited afterwards

- **WHEN** an invoice has been issued
- **THEN** no operation changes who it was made out to, and correcting it requires annulling the invoice and issuing a new one
