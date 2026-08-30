# laboratory-settings Specification

## Purpose

Defines how a laboratory's own configuration is read and updated through the API, and which parts of it are exposed to the public patient report. It covers the presentation preferences that shape a laboratory's printed and public documents.

## Requirements

### Requirement: Report range-alert switch

The API SHALL store, per laboratory, a boolean preference `showReportRangeFlags` that controls whether the results report marks values falling outside their reference range. When the preference is off, a report SHALL present an out-of-range value exactly as it presents a normal one: no `(Alto)`, `(Bajo)` or `(¡Crítico!)` marker, and no bold or colored emphasis on the value. Turning the preference off SHALL NOT change the value itself, the reference range shown beside it, or the interpretation text of category-based results.

#### Scenario: Setting is exposed to the laboratory's own client

- **WHEN** an authenticated caller with `LAB_SETTINGS_VIEW` requests its laboratory
- **THEN** the response includes `showReportRangeFlags` as a boolean

#### Scenario: Turning the alerts off

- **WHEN** a caller with `LAB_SETTINGS_EDIT` updates its laboratory with `showReportRangeFlags` set to `false`
- **THEN** the response reports `false`
- **AND** a later read of the same laboratory still reports `false`

#### Scenario: Turning the alerts back on

- **WHEN** a caller with `LAB_SETTINGS_EDIT` updates its laboratory with `showReportRangeFlags` set to `true`
- **THEN** the response reports `true`
- **AND** a later read of the same laboratory still reports `true`

#### Scenario: A caller may not change another laboratory's setting

- **WHEN** a caller attempts to update a laboratory other than its own
- **THEN** the request is rejected with the existing tenant error and no setting is changed

### Requirement: Alerts stay on unless a laboratory turns them off

The preference SHALL default to on. A laboratory that has never set it — including every laboratory that existed before this preference was introduced — SHALL behave exactly as it does today, with the range alerts printed. The API SHALL NOT expose an absent value to clients as null or omit the field; it SHALL report `true`.

#### Scenario: Laboratory that has never set the preference

- **WHEN** a laboratory with no stored value for the preference is read
- **THEN** the response reports `showReportRangeFlags` as `true`

#### Scenario: Newly registered laboratory

- **WHEN** a laboratory is created through account registration
- **THEN** its stored preference is on, and its reports print range alerts

### Requirement: An update that omits the setting preserves it

An update request that does not carry `showReportRangeFlags` SHALL leave the stored preference untouched. A client that predates this setting — or any caller sending a partial body — MUST NOT silently reset a laboratory's choice.

#### Scenario: Older client updates unrelated laboratory fields

- **GIVEN** a laboratory whose preference is stored as `false`
- **WHEN** an update request arrives whose body has no `showReportRangeFlags` key, changing only the laboratory's phone number
- **THEN** the phone number is updated
- **AND** the preference is still `false`

#### Scenario: Explicit value always wins

- **GIVEN** a laboratory whose preference is stored as `false`
- **WHEN** an update request explicitly sets `showReportRangeFlags` to `true`
- **THEN** the preference becomes `true`

### Requirement: The public patient report honors the same setting

The public results endpoint, which serves a patient's report from a token without authentication, SHALL include the issuing laboratory's `showReportRangeFlags` alongside the other laboratory presentation data it already returns, so the public report and the printed report agree. The value SHALL be normalized the same way as on the authenticated read: an absent stored value is reported as `true`. This preference is presentation data, not fiscal or patient-identifying data, so including it does not widen what the public endpoint exposes.

#### Scenario: Public report of a laboratory with alerts off

- **GIVEN** a laboratory whose preference is `false` and an order whose results are ready
- **WHEN** the public report is fetched with the order's token
- **THEN** the laboratory data in the response reports `showReportRangeFlags` as `false`

#### Scenario: Public report of a laboratory that never set the preference

- **WHEN** the public report is fetched for a laboratory with no stored value
- **THEN** the laboratory data in the response reports `showReportRangeFlags` as `true`
