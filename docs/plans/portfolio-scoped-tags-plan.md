# Portfolio-Scoped Tags Plan

Status: agreed

## Goal

Move tag definitions from one global vocabulary to portfolio ownership without changing what a Tag means: Tags remain user-defined labels on derived Positions, and assignments remain anchored to opening transaction IDs. Each portfolio must expose and mutate only its own Tags, while existing valid assignments survive the migration.

## Agreed behavior

- A Tag belongs to exactly one Portfolio and can be assigned only to Positions in that Portfolio.
- Tag names are case-insensitively unique within a Portfolio. Different portfolios may have independent Tags with the same name.
- A Position may keep multiple Tags. Existing ANY/ALL filtering and tag statistics remain unchanged.
- Migration clones a legacy global Tag once into each Portfolio where it has at least one valid assignment, preserving its name, color, and assignments.
- Legacy Tags with no assignments are discarded. Assignments whose opening transaction no longer exists are also discarded because no Portfolio can be inferred.
- A new Portfolio starts with no Tags. There is no global catalog, template set, or copy workflow.
- A portfolio-owned Tag remains after its final assignment is removed and is deleted only through explicit Tag or Portfolio deletion.
- Existing Tag management and quick-create surfaces operate on the active Portfolio only. There is no central cross-portfolio manager.
- Switching portfolios clears the selected Tag filter. Restarting with the same saved Portfolio preserves its valid scoped filter.
- The migration clears the legacy global Tag filter once. Deleting a selected Tag removes it from the active filter immediately.
- Deleting a Portfolio also deletes its Tag definitions and assignments.
- A cross-portfolio assignment is rejected as an internal invariant violation rather than silently ignored.

## Minimal implementation

### 1. Replace global Tag rows with portfolio-owned rows

Update `Tag.sq` so `Tag` contains a non-null `portfolioId` and replace the global name index with a unique `(portfolioId, name COLLATE NOCASE)` index. Scope select, insert, update, and delete queries by `portfolioId`; add the small delete-by-portfolio queries needed by Portfolio cleanup.

Add `migrations/3.sqm` for the v3-to-v4 upgrade. In one migration:

1. Preserve the legacy `Tag` and `PositionTag` rows while creating their replacement tables and indexes.
2. Join each legacy assignment through `TradeTransaction.id` to obtain its Portfolio.
3. Insert one new Tag for each distinct `(legacyTagId, portfolioId)` pair, preserving name and color.
4. Rebuild assignments by mapping each valid legacy pair to the new portfolio-owned Tag ID.
5. Drop the legacy tables and temporary mapping state.

This naturally omits unassigned Tags and orphaned assignments. Do not preserve a legacy namespace, prompt during startup, or assign ownerless Tags arbitrarily. Keep `PositionTag` as `(openingTxId, tagId)`; its Portfolio remains derivable from the opening Transaction.

Do not enable SQLite foreign keys globally or add triggers/composite ownership columns as part of this change. Existing cleanup is repository-managed, and changing global enforcement could affect unrelated tables.

Extend `JvmDatabaseFactoryMigrationTest` with a v3 fixture containing two portfolios, a Tag used in both, a Tag used in only one, an unused Tag, and an orphaned assignment. Assert the exact clones, preserved colors and valid assignments, discarded rows, and v4 schema/index shape.

### 2. Make ownership explicit at the domain and repository boundary

Add `portfolioId` to `domain/model/Tag.kt` and replace its global-vocabulary documentation.

Make `TagRepository` portfolio-scoped:

- `getAll`, `create`, `update`, and `delete` receive `portfolioId`;
- assignment lookup receives the Portfolio alongside opening transaction IDs;
- add/remove assignment operations receive the Portfolio as well as the opening transaction and Tag IDs.

In `SqlDelightTagRepository`, include `portfolioId` in every definition query so an ID cannot read, rename, recolor, or delete another Portfolio's Tag. Before assignment mutation, resolve the Tag owner and opening Transaction owner in the same repository operation and require both to equal the supplied Portfolio. Preserve idempotent same-Portfolio assignment.

Pass the known Portfolio through `PositionTagService`. Keep the service layered over `ClosedPositionService`, and keep `AppDependencies.kt` structurally unchanged; no new service, cache, or dependency is needed.

Update repository and service tests for scoped reads, same-name Tags in different portfolios, same-Portfolio duplicate rejection, cross-Portfolio mutation rejection, idempotent assignment, and scoped hydration.

### 3. Scope the existing UI instead of adding a new management surface

Pass the active `portfolioId` through `TagFilterControl`, `TagManagerDialog`, Trade Logs, and Analysis. Each existing `getAll` and quick-create call must use that ID. Make the manager title identify the active Portfolio so its scope is visible; when no Portfolio is selected, no Tag action is available.

`TradeLogsViewModel` should retain the Portfolio ID supplied by its existing load operation and use it for toggle and quick-create operations. When `AppShell` opens Analysis, capture the source Portfolio ID alongside the existing Position snapshot and pass it to Analysis; Tag edits must keep using that owner even if the top-bar selection later changes. Reports and Dashboard drill-down continue passing Tag IDs from the current Portfolio and need no new cross-portfolio model.

Do not add a Portfolio chooser to Tag dialogs. Their scope is always the active Portfolio.

### 4. Keep filter state valid with the smallest state change

In `AppShell.kt`, clear `selectedTagIds` whenever the selected Portfolio ID actually changes, including fallback selection after Portfolio deletion. Restore saved Tag IDs only when the saved Portfolio still resolves; preserve the other date, segment, and match-mode filters.

The filter preference lives outside SQLite, so reset the legacy selection by moving only the Tag-ID preference to a new scoped key in `PreferencesSettingsRepository`. No preference-version framework is needed. Extend the existing filter preference test to prove the old key is ignored and new same-Portfolio selections survive restart.

Have `TagManagerDialog` report a deleted Tag ID and thread that callback through its Trade Logs and Analysis call sites to `AppShell`. The shell removes that ID from `selectedTagIds` and persists the result; creation and editing do not disturb the filter. Avoid repository observation flows or polling for this one event.

### 5. Delete Tags with their Portfolio

Extend the existing `SqlDelightPortfolioRepository.delete` transaction to remove assignments referencing the Portfolio's Tags, then the Tag definitions, before deleting the Portfolio. Keep the current `PortfolioManagerViewModel` orchestration and transaction cleanup; do not add `TagRepository` as another ViewModel dependency.

Portfolio creation remains unchanged and therefore creates no Tags.

### 6. Keep documentation aligned

`CONTEXT.md` defines Portfolio and Tag using the agreed language. ADR 0003 records the ownership boundary and migration trade-off. README needs no change because it does not currently describe Tag scope.

## Test checkpoints

1. `JvmDatabaseFactoryMigrationTest`: v3-to-v4 cloning/remapping, two-Portfolio reuse, unused Tag removal, orphan removal, and new uniqueness shape.
2. `SqlDelightTagRepositoryTest`: portfolio-scoped CRUD, within-Portfolio case-insensitive uniqueness, same names across portfolios, guarded assignment/removal, and Portfolio deletion cleanup.
3. `PositionTagServiceTest`: Portfolio context reaches hydration and assignment paths; cross-Portfolio assignment fails.
4. Existing Trade Logs, Analysis, Calendar, Dashboard, and Reports tests/fakes compile against the scoped repository and continue exercising unchanged filtering/statistics behavior.
5. `PreferencesSettingsRepositoryFilterTest`: the legacy Tag key is ignored, scoped IDs persist, and unrelated filter preferences remain intact.
6. Exercise the active-Portfolio switch and selected-Tag deletion paths with the closest existing shell/UI test. Do not introduce a new UI-test framework solely for these state changes.
7. Run targeted changed tests, then `./gradlew :shared:jvmTest`, followed by `./gradlew build` before implementation handoff.

## Acceptance scenarios

- A legacy Tag assigned in two portfolios becomes two independent Tags with the same name and color; each original Position keeps the correct assignment.
- A legacy Tag assigned in one Portfolio appears only there. An unassigned Tag and an assignment to a missing opening Transaction do not survive migration.
- Two portfolios can each create `Breakout`; `Breakout` and `breakout` cannot coexist within one Portfolio.
- Renaming, recoloring, or deleting a Tag affects only its owning Portfolio.
- Attempting to attach a Tag from Portfolio A to a Position in Portfolio B fails without writing an assignment.
- An Analysis snapshot opened from Portfolio A continues to read and mutate Portfolio A's Tags even if the top-bar selection later changes.
- A new Portfolio has an empty Tag list. Removing a Tag's final assignment does not delete the Tag.
- Switching from Portfolio A to Portfolio B clears the Tag filter but preserves the other filters. Restarting on Portfolio B restores its valid saved Tag selection.
- Deleting a selected Tag immediately removes it from the filter. Deleting a Portfolio leaves no Tag definitions or assignments owned by it.
- Trade Logs, Analysis, Dashboard, Calendar, and Reports retain their current multi-Tag, ANY/ALL, and statistics behavior inside the active Portfolio.

## Out of scope

- Global Tags, shared presets, Tag templates, or copying Tags between portfolios.
- A central cross-portfolio Tag manager or all-portfolio Tag analytics.
- Importing or exporting Tags through broker, CSV, XLSX, or backup workflows.
- Automatically deleting portfolio-owned Tags when their assignment count reaches zero.
- Changing Position derivation, assignment identity, filter match semantics, or tag-statistics calculations.
- Adding `portfolioId` to `PositionTag`, enabling SQLite foreign keys globally, or introducing database triggers.
- A generic settings migration/versioning framework.
