# billing-clients Specification

## Purpose

Defines the catalogue of billing clients — the companies, insurers and agreement holders an invoice can be made out to — which is kept separate from the patient roster. It covers what a billing client is made of, what makes one unique, who may administer the catalogue, and when an entry may be removed.

## Requirements

### Requirement: A billing client is a distinct catalogue from patients

The API SHALL keep billing clients in a catalogue of their own, separate from patients. A billing client SHALL NOT appear in any patient listing, SHALL NOT be selectable as the subject of an order, and SHALL NOT carry clinical attributes (age, sex, pathologies, results history).

A billing client SHALL belong to exactly one laboratory and SHALL only ever be visible to that laboratory, on the same terms as every other catalogue in the system.

#### Scenario: A billing client is not a patient

- **WHEN** a billing client has been created
- **THEN** it is absent from the patient listing at `GET /api/v1/customers`
- **AND** it cannot be used as the patient of an order

#### Scenario: Another laboratory cannot see it

- **WHEN** a caller authenticated for a different laboratory lists or reads billing clients
- **THEN** the clients of the first laboratory are neither listed nor readable

### Requirement: A billing client carries a business name and an RTN

A billing client SHALL have a non-blank name (razón social) and a non-blank RTN. The API SHALL refuse to create or update one without either, reporting it as a validation error.

A billing client MAY additionally carry a phone, an email and an address; all three are optional and MAY be blank.

The RTN SHALL be unique within the laboratory. The API SHALL refuse to create or update a billing client whose RTN already belongs to another billing client of the same laboratory, and the refusal SHALL be a business-rule error naming the client that already holds it — not a database error surfaced to the caller. Two different laboratories MAY each hold a client with the same RTN.

#### Scenario: Creating a billing client

- **WHEN** a caller with `CATALOG_CREATE` posts a name and an RTN to `POST /api/v1/billing-clients`
- **THEN** the client is created and returned with its identifier

#### Scenario: Missing RTN

- **WHEN** a billing client is posted without an RTN, or with a blank one
- **THEN** the request is refused as a validation error and nothing is created

#### Scenario: Duplicate RTN in the same laboratory

- **WHEN** a billing client is posted with an RTN another client of the same laboratory already holds
- **THEN** the request is refused with a business-rule error naming that client
- **AND** no second client is created

#### Scenario: The same RTN in another laboratory

- **WHEN** a laboratory creates a billing client with an RTN that a client of a different laboratory holds
- **THEN** the client is created

### Requirement: The catalogue is administered under the catalogue permissions

Listing and reading billing clients SHALL be available to callers holding `CATALOG_VIEW`, and also to callers holding `INVOICES_VIEW` or `INVOICES_CREATE`, who need the catalogue to issue and read invoices without being given catalogue administration.

Creating SHALL require `CATALOG_CREATE`, updating SHALL require `CATALOG_EDIT`, and deleting SHALL require `CATALOG_DELETE`. A caller without the required permission SHALL be refused and nothing SHALL change.

#### Scenario: Issuing staff can read the catalogue

- **WHEN** a caller holding only `INVOICES_CREATE` lists `GET /api/v1/billing-clients`
- **THEN** the laboratory's billing clients are returned

#### Scenario: Reading does not grant editing

- **WHEN** a caller holding only `INVOICES_CREATE` posts, updates or deletes a billing client
- **THEN** the request is refused on authorization and the catalogue is unchanged

### Requirement: A billing client that has been invoiced cannot be deleted

The API SHALL refuse to delete a billing client that any invoice was issued to, including invoices that are annulled: the client's identity is part of a fiscal document and deleting it would leave that document pointing at nothing.

The refusal SHALL be a business-rule error stating that the client has invoices, so the caller understands it is not a permission problem. A billing client that has never been invoiced SHALL be deletable.

Editing a billing client SHALL remain possible at any time, and SHALL NOT alter any invoice already issued to it — invoices keep what was frozen into them when they were issued.

#### Scenario: Deleting an invoiced client

- **WHEN** a caller with `CATALOG_DELETE` requests the deletion of a billing client that has at least one invoice
- **THEN** the request is refused with a business-rule error stating it has invoices
- **AND** the client and its invoices are untouched

#### Scenario: Deleting a client that was never invoiced

- **WHEN** a caller with `CATALOG_DELETE` requests the deletion of a billing client with no invoices
- **THEN** the client is removed from the catalogue

#### Scenario: Renaming a client does not rewrite history

- **WHEN** a billing client's name or RTN is corrected
- **THEN** invoices already issued to it still print the name and RTN they were issued with
