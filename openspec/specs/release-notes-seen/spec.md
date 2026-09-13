# release-notes-seen Specification

## Purpose

Defines the per-user record of which release announcement a user has already been shown: how a client reads it, how it is marked, and the guarantees that keep it honest — that it belongs to one user, that it never moves backwards, and that the API never has to understand what a release contains.

## Requirements

### Requirement: The session identity reports the newest release the user has seen

The response that describes the authenticated user's identity and permissions SHALL include the newest release-notes version that user has been marked as having seen. A client SHALL be able to decide whether to announce a release from that response alone, without a second request.

The value SHALL be an opaque string that the API stores and returns unchanged. The API SHALL NOT parse it, order it, or derive meaning from it: it does not know what a release contains and does not decide whether an announcement is due.

A user who has never been marked SHALL report an absent value, which means "has seen nothing" and is the state every user starts in. The API SHALL NOT substitute a current or default version for an absent one.

#### Scenario: Reading identity after having seen a release

- **WHEN** an authenticated user whose marker was set requests its own identity
- **THEN** the response reports exactly the version string that was marked, unchanged

#### Scenario: A user who has never been marked

- **WHEN** a user who has never been marked requests its own identity
- **THEN** the response reports the version as absent, and no default is invented

#### Scenario: The API does not interpret the version

- **WHEN** a version string is marked whose format the API has never seen before
- **THEN** it is stored and returned unchanged, because the API assigns it no meaning

### Requirement: A user can mark a release as seen, for itself only

The API SHALL provide an operation that marks the authenticated caller as having seen a given release-notes version. Any authenticated user SHALL be able to call it for itself; no additional permission SHALL be required.

The operation SHALL affect only the caller's own record. It SHALL NOT accept another user as its target, and there SHALL be no way through it for one user to change what another user has seen, in the same laboratory or in any other.

After a successful mark, the next read of the caller's identity SHALL report the marked version, and it SHALL keep reporting it across sessions, devices and restarts.

#### Scenario: Marking the current release as seen

- **WHEN** an authenticated user marks a release version as seen
- **AND** the user then requests its identity again
- **THEN** the response reports that version

#### Scenario: The mark survives a new session

- **WHEN** a user who marked a version logs out and logs in again, on the same or another device
- **THEN** the identity response still reports that version

#### Scenario: A user cannot mark on another user's behalf

- **WHEN** a user attempts to mark a version seen for anyone other than itself
- **THEN** the attempt does not change any other user's record

#### Scenario: No permission gates the operation

- **WHEN** a user holding no permissions beyond being authenticated marks a version seen
- **THEN** the operation succeeds

### Requirement: The marker never moves backwards

Marking a version SHALL never cause a user to be re-shown an announcement they have already dismissed. The API SHALL treat the newest marked version as final: an attempt to mark a version the user has already passed SHALL leave the stored value as it is, and SHALL NOT be reported as a failure.

Because the API does not interpret version strings, the ordering it enforces SHALL be defined by the client supplying them, and the API's guarantee SHALL be that a mark never *clears* the stored value and never replaces it with an empty or absent one.

#### Scenario: Re-marking a version already seen

- **WHEN** a user marks a version that is already the stored value
- **THEN** the request succeeds and the stored value is unchanged

#### Scenario: A mark cannot clear the marker

- **WHEN** a mark is attempted with an empty or absent version
- **THEN** the stored value is not cleared, and the user is not returned to the "has seen nothing" state

### Requirement: The marker is stored durably and independently of the session

The marker SHALL be persisted with the user's record, not carried in the session token, so that a read reflects the current stored value rather than what was true when the session began. It SHALL survive application restarts and deploys.

The column backing it SHALL be described in the project's migration script in the same change that maps it, so a database built from that script has it. The absent state SHALL require no backfill: every existing user SHALL read as having seen nothing, which is correct for a user who has never been shown an announcement.

#### Scenario: The marker is read from storage, not from the token

- **WHEN** a user marks a version and then reads its identity without obtaining a new session
- **THEN** the response reports the newly marked version

#### Scenario: Existing users after the change is deployed

- **WHEN** a user who existed before this change reads its identity for the first time after deployment
- **THEN** the version is reported as absent, and the user is treated as having seen nothing
