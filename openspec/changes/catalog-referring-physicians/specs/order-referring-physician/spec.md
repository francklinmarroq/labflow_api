## Purpose

How an order says who referred it: a physician's name is written on the order rather than an identifier chosen from a list, an unknown name enrols the physician in the laboratory's catalogue as it is saved, and the order — and the patient's public report — reports that physician back by name.

## ADDED Requirements

### Requirement: An order names its referring physician by name

Creating or updating an order SHALL accept the referring physician as a name written in full, not as an identifier the caller had to look up first. The name SHALL be optional: an order without one is valid and is the common case.

The name given SHALL be matched against the laboratory's catalogue of referring physicians under the comparison that catalogue defines — accents, spacing and capitalisation ignored. A name already in the catalogue SHALL link the order to that entry, whatever spelling was used on this order; a name that is not SHALL be enrolled in the catalogue as written and the order linked to the new entry. The catalogue SHALL NOT have to be visited first, and no separate request SHALL be required to register the physician.

A name given as empty or only spaces SHALL be treated as no physician at all, not as a physician whose name is blank, and SHALL NOT create a catalogue entry.

#### Scenario: Writing a physician the laboratory has never used

- **WHEN** an order is created through `POST /api/v1/orders` naming "Dra. Ana Fúnez", whom the laboratory has never recorded
- **THEN** the order is created naming that physician, and the physician is now in the laboratory's catalogue available to the next order

#### Scenario: Writing a physician the laboratory already uses

- **WHEN** an order is created naming "dra. ana funez" and the catalogue already holds "Dra. Ana Fúnez"
- **THEN** the order is linked to the existing physician and no second entry is created

#### Scenario: An order with no physician

- **WHEN** an order is created with the referring physician left out, empty or blank
- **THEN** the order is created naming no physician and the catalogue is unchanged

#### Scenario: Creating the physician requires no catalogue permission

- **WHEN** a caller holding the permission to create orders, but not the permission to create catalogue entries, creates an order naming a physician the laboratory has never used
- **THEN** the order is created and the physician is enrolled, because enrolling is part of saving the order and not an administration of the catalogue

### Requirement: The physician on an existing order can be set, changed and cleared

Updating an order SHALL be able to add a referring physician to an order that had none, replace the one it had, and remove it. Sending a name SHALL set or replace; sending an empty or blank name SHALL leave the order naming no physician. Changing the physician of an order SHALL NOT affect any other order, and SHALL NOT remove from the catalogue the physician the order stopped naming.

Nothing about an order other than its referring physician SHALL change as a result — not its exams, its results, its status or its invoices.

#### Scenario: Adding the physician after the fact

- **WHEN** an order created without a physician is updated with a name
- **THEN** the order names that physician, enrolled in the catalogue if it was not already there

#### Scenario: Clearing the physician

- **WHEN** an order that names a physician is updated with an empty name
- **THEN** the order names no physician, the physician stays in the catalogue, and the other orders naming them are unaffected

#### Scenario: Replacing the physician

- **WHEN** an order naming one physician is updated to name another
- **THEN** only that order changes, and both physicians remain in the catalogue

### Requirement: An order reports its physician by name, and identifies them

An order read back from the API SHALL report its referring physician as a name, in the same field and the same shape as before this catalogue existed, so that a caller written against the previous behaviour keeps working without change. The name reported SHALL be the catalogue's current name for that physician, which is what makes a correction in the catalogue reach the orders already recorded.

An order SHALL additionally report a read-only identifier for its referring physician, so a caller can tell two physicians with similar names apart and group orders by physician without matching on text. That identifier SHALL be ignored if it is sent on a create or update: the name is what is written.

An order with no referring physician SHALL report neither.

#### Scenario: Reading an order with a physician

- **WHEN** an order naming a physician is read
- **THEN** it reports the physician's current catalogue name, and their identifier alongside it

#### Scenario: A caller written before the catalogue existed

- **WHEN** a caller that only knows the physician-name field creates, reads and updates orders
- **THEN** everything behaves as it did before this change, including creating the physician when the name is new

#### Scenario: An identifier sent on a write

- **WHEN** an order is saved with a physician identifier as well as a name
- **THEN** the identifier is ignored and the physician is resolved from the name

### Requirement: The public report keeps naming the physician as before

The patient's public report SHALL keep reporting the referring physician's name in the field it already reports it in, with no change in shape, and SHALL keep omitting it when the order names none. The name reported SHALL be the catalogue's current name, so a correction made in the catalogue shows up on the report from then on.

The public report SHALL NOT expose the catalogue: no identifier, no list of physicians, nothing beyond the name printed on that one order.

#### Scenario: Report of an order with a physician

- **WHEN** the public report of an order naming a physician is requested with its token
- **THEN** the physician's current name is reported in the field it has always been reported in

#### Scenario: Report of an order without one

- **WHEN** the public report of an order naming no physician is requested
- **THEN** no referring physician is reported and nothing about the report changes

#### Scenario: The catalogue stays private

- **WHEN** any public report is requested
- **THEN** it reveals nothing about the laboratory's catalogue of physicians beyond the name on that order

### Requirement: Listing orders does not get more expensive because of the physician

Reading a page of orders SHALL report each order's referring physician without the cost growing with the number of rows in the page: the physicians of a page SHALL NOT be fetched one order at a time. Paging, sorting and the total count SHALL be exactly what they were before orders pointed at a catalogue.

#### Scenario: A page of orders naming physicians

- **WHEN** a page of orders is requested and the orders on it name physicians
- **THEN** every order reports its physician's name, and the listing costs the same number of queries as it did when the name was stored on the order itself

#### Scenario: Paging is unchanged

- **WHEN** pages of orders are requested with the same size, sort and filters as before this change
- **THEN** the same orders come back on the same pages, in the same order, with the same total count
