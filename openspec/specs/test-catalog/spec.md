# test-catalog Specification

## Purpose

Defines how an exam and its single profile — the profile's name, its ordered parameters and the settings that shape how its results are captured and printed — are saved and re-read as one aggregate through the API. It covers the durability of those settings: what a client stores is what a later read returns.

## Requirements

### Requirement: The exam editor aggregate is readable

The API SHALL return an exam together with its profile and ordered parameters for any exam in the caller's laboratory. This read SHALL NOT depend on optional or recently introduced profile settings being present in storage: a deployment whose storage is missing a setting SHALL be repaired rather than allowed to fail the read of every exam.

#### Scenario: Reading an exam

- **WHEN** a caller with `CATALOG_VIEW` requests `GET /api/v1/tests/{testId}/full` for an exam in its laboratory
- **THEN** the response describes the exam, its profile settings, and its parameters in report order

#### Scenario: Reading an exam whose profile settings predate the current deployment

- **WHEN** an exam is read whose stored profile was written before a newly introduced setting existed
- **THEN** the read succeeds and reports that setting at its documented default

### Requirement: Result-attachments switch is stored per exam profile

The API SHALL store, per exam profile, a boolean setting `allowResultAttachments` that declares whether a run of that exam may carry photos or scans of the report the instrument prints, in addition to or instead of hand-captured parameter values. The setting SHALL be independent of `resultLayout`: an exam may be an antibiogram and allow attachments, or be a standard exam and allow attachments, in any combination.

The setting SHALL be durable. A value written through the unified exam editor SHALL be returned unchanged by every later read of that exam, across restarts and across deploys.

#### Scenario: Turning attachments on for an existing exam

- **WHEN** a caller with `CATALOG_EDIT` updates an exam through `PUT /api/v1/tests/{testId}/full` with `allowResultAttachments` set to `true`
- **THEN** the response reports `allowResultAttachments` as `true`
- **AND** a later `GET /api/v1/tests/{testId}/full` still reports `true`

#### Scenario: Turning attachments back off

- **WHEN** a caller with `CATALOG_EDIT` updates an exam that currently allows attachments, sending `allowResultAttachments` as `false`
- **THEN** the response reports `allowResultAttachments` as `false`
- **AND** a later `GET /api/v1/tests/{testId}/full` still reports `false`

#### Scenario: Creating an exam with attachments allowed

- **WHEN** a caller with `CATALOG_CREATE` creates an exam through `POST /api/v1/tests/full` with `allowResultAttachments` set to `true`
- **THEN** the created exam reports `true`
- **AND** a later read of that exam reports `true`

#### Scenario: The switch is independent of the antibiogram layout

- **WHEN** an exam is saved with `resultLayout` `ANTIBIOGRAM` and `allowResultAttachments` `true`
- **THEN** a later read reports both values unchanged
- **AND** changing one of the two in a later save SHALL NOT change the other

### Requirement: Attachments stay off unless an exam turns them on

The setting SHALL default to off. An exam profile that has never been given a value — including every profile that existed before the setting was introduced — SHALL report `allowResultAttachments` as `false`, and its runs SHALL NOT offer attachment upload. The API SHALL NOT report an absent value as null or omit the field.

#### Scenario: Exam profile that predates the setting

- **WHEN** an exam whose profile was created before this setting existed is read
- **THEN** the response reports `allowResultAttachments` as `false`

#### Scenario: Exam created without mentioning the setting

- **WHEN** an exam is created through `POST /api/v1/tests/full` with no `allowResultAttachments` in the body
- **THEN** the created exam reports `false`

### Requirement: Saving an exam preserves the rest of its profile settings

Saving an exam through the unified editor SHALL persist every profile setting sent in the same payload — `active`, `chartType`, `chartXAxisLabel`, `resultLayout` and `allowResultAttachments` — as one atomic unit alongside the profile's name and its ordered parameters. A save SHALL NOT persist some settings while silently dropping others, and SHALL NOT report success for a value it did not store.

#### Scenario: All profile settings survive one round trip

- **WHEN** an exam is saved with a non-default value for each of `active`, `chartType`, `chartXAxisLabel`, `resultLayout` and `allowResultAttachments`
- **THEN** a later `GET /api/v1/tests/{testId}/full` reports every one of those values unchanged

#### Scenario: A setting the deployment cannot store is not reported as saved

- **WHEN** the API cannot durably store a profile setting it was sent
- **THEN** the request fails with an error rather than returning a success that reports a value the next read will contradict

### Requirement: Runs of an attachment-enabled exam can hold their attachments

An exam profile that allows result attachments SHALL be able to carry one or more attachments on each of its runs, each attachment retaining the stored object it points at, its content type, and its position in the displayed order. Attachments SHALL be reachable only through the run that owns them, and therefore only by callers of the laboratory that owns that run.

#### Scenario: Attachments persist against a run

- **WHEN** one or more attachments are uploaded against a run of an exam whose profile allows attachments
- **THEN** a later read of that run reports those attachments in the order they were uploaded

#### Scenario: A caller cannot reach another laboratory's attachments

- **WHEN** a caller requests a run belonging to a laboratory other than its own
- **THEN** the request is rejected with the existing tenant error and no attachment is disclosed
