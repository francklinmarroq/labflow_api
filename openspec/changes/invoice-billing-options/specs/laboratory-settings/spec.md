## ADDED Requirements

### Requirement: Invoice-without-CAI switch

The API SHALL store, per laboratory, a boolean preference `invoiceWithoutCai` that decides whether the laboratory issues non-fiscal documents instead of CAI invoices (see `invoice-without-cai`). It SHALL default to off: a laboratory that has never set it — including every laboratory that existed before this preference — SHALL keep issuing CAI invoices exactly as today. The API SHALL always report the field as a boolean, never null. Only a caller with `LAB_SETTINGS_EDIT` SHALL be able to change it, and only for its own laboratory.

#### Scenario: Default for an existing laboratory

- **WHEN** a laboratory with no stored value for the preference is read by a caller with `LAB_SETTINGS_VIEW`
- **THEN** the response reports `invoiceWithoutCai` as `false`

#### Scenario: Turning the switch on

- **WHEN** a caller with `LAB_SETTINGS_EDIT` updates its laboratory with `invoiceWithoutCai` set to `true`
- **THEN** the response reports `true` and a later read still reports `true`

#### Scenario: Newly registered laboratory

- **WHEN** a laboratory is created through account registration
- **THEN** its `invoiceWithoutCai` is `false`

### Requirement: Invoice paper size

The API SHALL store, per laboratory, a preference `invoicePaperSize` whose value is one of `A5_LANDSCAPE`, `THERMAL_80` or `THERMAL_58`. It SHALL default to `A5_LANDSCAPE`, the only layout that existed before this preference, and SHALL be reported as that value, never null, for a laboratory that has never set it. An update carrying any other value SHALL be rejected with a validation error in Spanish and SHALL change nothing.

#### Scenario: Default paper size

- **WHEN** a laboratory with no stored paper size is read
- **THEN** the response reports `invoicePaperSize` as `A5_LANDSCAPE`

#### Scenario: Choosing a thermal printer

- **WHEN** a caller with `LAB_SETTINGS_EDIT` updates its laboratory with `invoicePaperSize` set to `THERMAL_58`
- **THEN** the response reports `THERMAL_58` and a later read still reports it

#### Scenario: Unknown paper size

- **WHEN** an update carries `invoicePaperSize` set to `LETTER`
- **THEN** the request is rejected with a validation error
- **AND** none of the laboratory's fields change

### Requirement: An update that omits the billing preferences preserves them

An update request that does not carry `invoiceWithoutCai` or `invoicePaperSize` SHALL leave the stored value of the omitted preference untouched. A client that predates these preferences, or any caller sending a partial body, MUST NOT silently reset them. An update SHALL NOT change the laboratory's internal non-fiscal correlative, which is not exposed for editing.

#### Scenario: Older client updates unrelated fields

- **GIVEN** a laboratory with `invoiceWithoutCai` `true` and `invoicePaperSize` `THERMAL_80`
- **WHEN** an update arrives whose body has neither key, changing only the phone number
- **THEN** the phone number is updated
- **AND** `invoiceWithoutCai` is still `true` and `invoicePaperSize` is still `THERMAL_80`
