## ADDED Requirements

### Requirement: New laboratories receive an initial exam catalog

A laboratory that accepts the initial catalog during onboarding SHALL receive the exams, profiles, parameters, units and reference ranges of the bundled default catalog. The seed SHALL NOT run, and SHALL NOT change anything, for a laboratory that already has a catalog.

#### Scenario: Seeding an empty laboratory

- **WHEN** the default catalog is seeded into a laboratory with no units and no exams
- **THEN** every exam of the default catalog exists in that laboratory with exactly one profile

#### Scenario: Seeding twice

- **WHEN** the default catalog is seeded into a laboratory that already has a catalog
- **THEN** nothing is added or changed

### Requirement: The initial catalog is usable without manual fixes

Every exam in the default catalog SHALL have an area. No two exams SHALL share a name, compared without regard to case. A quantitative parameter SHALL NOT have a reference range with neither a lower nor an upper limit. A parameter of the microscopic section SHALL be qualitative.

#### Scenario: Exam areas

- **WHEN** the default catalog is seeded
- **THEN** no exam lacks an area

#### Scenario: Numeric ranges flag results

- **WHEN** the default catalog is seeded
- **THEN** every reference range of a quantitative parameter has at least one numeric limit

#### Scenario: Microscopic results are text

- **WHEN** the default catalog is seeded
- **THEN** no parameter of the microscopic section is quantitative
