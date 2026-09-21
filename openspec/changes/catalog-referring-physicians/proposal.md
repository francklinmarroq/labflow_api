## Why

The referring physician of an order is free text today: `lab_orders.referring_physician` is a `varchar(150)` that whoever takes the order types from scratch, every time. The same doctor is therefore stored as many different strings — "Dra. Ana Fúnez", "dra ana funez", "Dra. Ana Funez", "Ana Fúnez" — each of them printed on the report exactly as it was mistyped, and none of them groupable. The laboratory cannot answer "which orders came from Dr. X", cannot correct a name once it is wrong without opening every order that carries it, and gets no help at all when capturing the next one.

Order tags already solved this same problem for agreements and campaigns: the tag is born the first time somebody writes it on an order, is reused from then on, and is maintained afterwards from a catalogue screen. The referring physician is the same shape of data — a small, slowly-growing set of names the laboratory reuses constantly — and should behave the same way.

## What Changes

- **A new catalogue of referring physicians**, one entry per physician per laboratory. It carries the name and nothing else: the name as it was written (what gets printed) and a normalized form (no accents, no repeated spaces, lower case) that is unique per laboratory, so "Dra. Ana Fúnez", "dra. ana funez" and " Dra.  Ana Funez " are one physician and not three. Same shape as `OrderTag`, minus the colour.
- **A physician is not created from the catalogue first.** Saving an order with a physician name that the laboratory has never used creates the entry and links the order to it, in the same request — the "resolve or create" that `OrderTagService` already does for tag names. Writing a name that already exists links to the existing entry instead of duplicating it.
- **The order points at the catalogue** (`lab_orders.referring_physician_id`) instead of carrying a copy of the text. Correcting a physician's name in the catalogue corrects it on every order that names them, including on reports already issued — which is the point, and is what renaming a tag already does.
- **The wire format does not change.** `LabOrderDTO.referringPhysician` stays the physician's *name*, written and read as a string, and the public report keeps reporting the same field. The order additionally reports a read-only `referringPhysicianId` so a caller can group by physician. A frontend that has not been deployed yet keeps working unchanged.
- **Full maintenance of the catalogue** under the `CATALOG_*` permissions that already exist: list with a count of how many orders name each physician, rename, and delete. Deleting a physician unlinks them from the orders that named them and leaves those orders otherwise intact — the same contract as deleting a tag.
- **The physician names already typed are migrated, not thrown away.** A start-up backfill creates one catalogue entry per distinct name per laboratory and links the existing orders to it, so the catalogue starts out holding exactly the physicians the laboratory has been using. Once that is verified in production, the old text column is dropped; `schema.sql` carries the statement, marked as the last step.
- **BREAKING (database, not API)**: `lab_orders.referring_physician` (text) stops being the source of truth the moment this deploys, and is dropped after the backfill is verified. No API request or response field is removed or renamed.

## Capabilities

### New Capabilities

- `referring-physicians`: the catalogue of referring physicians — what an entry holds, what makes two names the same physician, who may administer it, what renaming and deleting do to the orders that name them, and how physicians the laboratory typed before the catalogue existed end up in it.
- `order-referring-physician`: how an order names its referring physician — that a name is written rather than an id chosen, that an unknown name creates the physician, what clears it, and what the order and the public report report back.

### Modified Capabilities

<!-- None. `order-composition` is about which exams an order contains and when that
     set is frozen; it says nothing about the referring physician. The behaviour this
     change introduces is described in full in the two new capabilities above. -->

## Impact

- **Model and database**: a new `ReferringPhysician` entity (table `referring_physicians`, `@TenantId`, normalized name unique per laboratory) and a new nullable `referring_physician_id` on `lab_orders`. `LabOrder.referringPhysician` changes from `String` to a `@ManyToOne` — EAGER and fetch-joined in the orders listing, because a lazy to-one cannot build its proxy in the native image (`NativeImageLazyAssociationTest` fails the build on one).
- **Data migration**: a `ReferringPhysicianBackfill` `CommandLineRunner` in `config/`, in the style of `PublicTokenBackfill` — `JdbcTemplate`, deliberately outside the tenant filter so it reaches every laboratory, idempotent, and unable to crash start-up. It is what fills the catalogue on first deploy; the drop of the old column waits on its result.
- **API**: a new `/api/v1/referring-physicians` controller and service (GET readable by the catalogue, the order screens and the reports, like `/order-tags`; POST/PUT/DELETE under `CATALOG_CREATE`/`CATALOG_EDIT`/`CATALOG_DELETE`). `LabOrderDTO` gains a read-only `referringPhysicianId`; `LabOrderServiceImp` resolves the name through the new service on create and update. `PublicReportDTO` is untouched in shape.
- **Listing performance**: the orders listing reads the physician of every row, so it is fetch-joined in `LabOrderSpecifications` alongside `customer`. It is a to-one, so it does not multiply rows and SQL paging stays correct.
- **Not in scope**: a `physicianId` filter parameter on `GET /orders`. The frontend filters the orders listing by physician in the browser over the page it already has, exactly as it does for tags today; adding a server-side parameter nothing calls would be speculative. Invoices are not filtered by physician either — the physician is clinical, not fiscal.
- **Frontend** (`labflow_frontend`, sibling change of the same name): the catalogue screen, the picker that replaces the free-text field when creating and editing an order, and the physician filter in the orders listing.
- No new dependencies.
