## Purpose

The catalogue of physicians who refer work to the laboratory: the reusable set of names an order can be attributed to. It defines what an entry holds, when two spellings are the same physician, who may administer the catalogue, and what renaming or deleting an entry does to the orders that name it.

## ADDED Requirements

### Requirement: A referring physician is a name, unique per laboratory

A referring physician SHALL consist of a name and nothing else. The name SHALL be stored as it was written — that is what gets printed — and SHALL be at most 150 characters; anything longer SHALL be cut to that length rather than refused. Leading and trailing spaces SHALL be dropped and runs of spaces collapsed to one.

Two names SHALL be the same physician when they match after removing accents, collapsing spaces and lower-casing. The catalogue SHALL NOT hold two physicians that are the same physician under that comparison **within one laboratory**. Two laboratories SHALL be able to hold the same physician independently; neither SHALL see the other's catalogue.

A blank name SHALL be refused.

#### Scenario: The same physician written three ways

- **WHEN** a laboratory's catalogue already holds "Dra. Ana Fúnez" and something in the laboratory names "dra. ana funez" or " Dra.  Ana  Funez "
- **THEN** it resolves to the physician already in the catalogue, and the catalogue still holds exactly one entry for them

#### Scenario: The same physician in two laboratories

- **WHEN** two laboratories each register "Dr. Carlos Mejía"
- **THEN** each holds its own entry, and neither laboratory sees the other's

#### Scenario: A blank name

- **WHEN** a request would register a physician whose name is empty or only spaces
- **THEN** it is refused with a business-rule error and nothing is created

### Requirement: The catalogue reports how used each physician is

Listing the catalogue SHALL return the laboratory's physicians ordered by name, each with the number of orders that name them. A physician no order names SHALL be reported with a count of zero rather than omitted.

The listing SHALL be readable by a caller holding any of the permissions that already read catalogues or work with orders and reports — the same set that reads order tags — because it feeds the picker on the order screens as well as the catalogue screen.

#### Scenario: Listing with usage

- **WHEN** a caller requests `GET /api/v1/referring-physicians` for a laboratory with three physicians, one of them named by four orders and one named by none
- **THEN** all three come back ordered by name, with counts of four and zero on the respective entries

#### Scenario: A caller who only works with orders

- **WHEN** a caller holding only the permission to create orders requests the listing
- **THEN** the listing is returned, so the order screen can offer the existing physicians

### Requirement: The catalogue is administered under the catalogue permissions

Registering a physician directly SHALL require the permission to create catalogue entries, renaming one SHALL require the permission to edit them, and deleting one SHALL require the permission to delete them.

Registering a physician whose name is already in the laboratory's catalogue SHALL be refused with a business-rule error that names the existing entry, and SHALL create nothing.

Renaming a physician SHALL apply to every order that names them, including orders already reported. Renaming to a name another physician in the laboratory already has SHALL be refused, naming that physician, and SHALL change nothing — merging two physicians is not what a rename means. Renaming to the same physician written differently — changing only accents, spacing or capitalisation — SHALL be allowed, since that is the same entry correcting its own spelling.

#### Scenario: Correcting how a physician's name is written

- **WHEN** a caller with the edit permission renames "dra ana funez" to "Dra. Ana Fúnez"
- **THEN** the rename is applied, and every order that named that physician now reports the corrected name

#### Scenario: Renaming onto another physician

- **WHEN** a caller with the edit permission renames a physician to a name another physician in the same laboratory already has
- **THEN** the rename is refused with a business-rule error naming that other physician, and neither entry changes

#### Scenario: Registering a duplicate

- **WHEN** a caller with the create permission registers a physician the laboratory already has, however it is spelled
- **THEN** it is refused with a business-rule error naming the existing entry, and no second entry is created

#### Scenario: Without the catalogue permissions

- **WHEN** a caller lacking the catalogue permissions attempts to register, rename or delete a physician
- **THEN** the request is refused on authorization and the catalogue is unchanged

### Requirement: Deleting a physician unlinks the orders, it does not touch them

Deleting a physician SHALL succeed whether or not orders name them. Every order that named the deleted physician SHALL remain, with its exams, results, status and invoices untouched, and SHALL simply no longer name a referring physician — reported exactly as an order on which none was ever recorded.

A deletion SHALL never fail because the physician is in use, and SHALL never delete, cancel or otherwise alter an order.

#### Scenario: Deleting a physician named by orders

- **WHEN** a caller with the delete permission deletes a physician named by six orders
- **THEN** the physician is removed from the catalogue, the six orders still exist unchanged, and each of them now reports no referring physician

#### Scenario: Deleting an unused physician

- **WHEN** a caller with the delete permission deletes a physician no order names
- **THEN** the physician is removed and nothing else changes

### Requirement: Physicians recorded before the catalogue existed end up in it

Orders that recorded a referring physician as free text before this catalogue existed SHALL end up naming a catalogue entry, without anyone re-entering them. For each laboratory, every distinct name already recorded SHALL become one catalogue entry — distinct under the same comparison that makes two spellings one physician — and every order that recorded that name SHALL be linked to it. The name kept for the entry SHALL be one of the spellings actually recorded, so nothing is invented.

Orders that recorded no physician SHALL stay without one. The migration SHALL be repeatable without creating duplicates or relinking what is already linked, SHALL cover every laboratory rather than only the one whose session triggered it, and SHALL never prevent the API from starting: if it cannot run, the API SHALL come up and the migration SHALL be retried on a later start.

#### Scenario: Names typed before the catalogue existed

- **WHEN** the API starts with existing orders recording "Dra. Ana Fúnez", "dra ana funez" and "Dr. Carlos Mejía" as free text
- **THEN** the laboratory's catalogue holds one entry for Ana Fúnez and one for Carlos Mejía, and each order names the matching entry

#### Scenario: Starting again after the migration has run

- **WHEN** the API starts again
- **THEN** no further entries are created, no order is relinked, and the catalogue is exactly as the first run left it

#### Scenario: Orders with no physician

- **WHEN** the migration runs over orders that recorded no referring physician
- **THEN** those orders continue to name none, and no entry is created for them
