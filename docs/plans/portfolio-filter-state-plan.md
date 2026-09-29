# Portfolio-Scoped Filter State Plan

Status: agreed

## Goal

Remember the complete shared filter state independently for each Portfolio. Switching back to a Portfolio must restore its last date, segment, and Tag selections without exposing another Portfolio's filters, and restarting the application must restore the selected Portfolio and its saved state.

## Agreed behavior

- Each Portfolio owns one filter snapshot containing the date preset, optional custom range, segment, selected Tag IDs, and ANY/ALL Tag matching mode.
- The snapshot remains shared across Dashboard, Trade Logs, Calendar, and Reports. Existing screen-specific consumption remains unchanged: Calendar ignores the saved date range, Reports ignores Tags, and the other screens continue using the fields they use today.
- Switching from Portfolio A to B restores B's snapshot; switching back restores A's snapshot. This supersedes the earlier rule that a Portfolio switch clears selected Tags.
- The last-selected Portfolio ID remains an OS preference because it controls startup selection. The Portfolio's filter snapshot lives in the database so it shares the lifecycle of that Portfolio and its Tag IDs.
- A new or never-visited Portfolio defaults to all time, all segments, no selected Tags, and ANY matching. The same defaults exist only in memory when there is no Portfolio; nothing is persisted without an owner.
- The current global filter snapshot is migrated once into its saved Portfolio when that Portfolio still exists. Other Portfolios start with defaults.
- Invalid persisted fields recover independently. A malformed whole payload uses the default snapshot; an invalid or incomplete custom range, including `from > to`, falls back to all time.
- Restored Tag IDs are intersected with the Tags currently owned by that Portfolio. A cleaned snapshot is written back only when stale IDs were removed.
- Portfolio selection and filter restoration are published together. While a switch is loading, the Portfolio and filter controls are disabled so input cannot be applied to the wrong Portfolio.
- Deleting a Portfolio deletes its snapshot through the existing `PortfolioSetting` cleanup. Deleting a selected Tag removes it from the active snapshot immediately.

## Minimal implementation

### 1. Store one atomic snapshot in the existing Portfolio settings table

Replace the global `FilterPrefs` shape in `data/repository/SettingsRepository.kt` with a Portfolio-owned value such as `PortfolioFilterPrefs` that contains only:

- `preset`
- `customFrom`
- `customTo`
- `segment`
- `selectedTagIds`
- `tagMatch`

Give it one canonical default: `ALL_TIME`, null custom bounds, `ALL`, an empty Tag set, and `ANY`.

Add `data/repository/PortfolioFilterSettings.kt` with small suspend extensions over `PortfolioSettingsRepository` for the single key `filter.state`. Encode the snapshot as one JSON object and persist it with the existing `putString` upsert. One row keeps each update atomic without adding a batch API or transaction abstraction.

Use the already-installed `kotlinx.serialization.json` primitives. Store enum names, ISO `LocalDate` strings, and Tag IDs as JSON numbers. Decode members independently so one bad enum, date, or Tag value does not discard unrelated valid fields; ignore unknown members. Normalize custom dates after decoding and use the canonical defaults when the entire value is not valid JSON. Do not add a schema migration, serialization version, new repository, or dependency.

Keep Tag ownership validation outside the codec: load the Portfolio's current Tags through `TagRepository`, intersect their IDs with the decoded selection, and rewrite only a changed selection. This keeps storage parsing independent from repository access while preventing deleted or foreign Tag IDs from filtering every result away.

### 2. Reduce global preferences to Portfolio selection and one-time migration

Change `SettingsRepository` and `PreferencesSettingsRepository` so the live API reads and writes only the last-selected Portfolio ID. Preserve a narrow legacy reader and clearer for the old preset, custom-date, segment, Tag-ID, and Tag-match keys until the one-time upgrade path has consumed them.

In `PreferencesSettingsRepository`:

- keep `filter_portfolio_id` as the selected-Portfolio key;
- use the presence of the old `filter_preset` key as the legacy-snapshot sentinel;
- parse legacy fields independently with the same defaults and custom-range normalization as the new snapshot;
- clear the old filter keys after migration, but never clear the selected-Portfolio key.

Do not add a settings-version framework. Migration is restart-safe by checking whether the target Portfolio already has `filter.state`: seed only when it is absent, then clear the legacy keys. If the database write fails, leave the legacy keys intact so the next startup can retry. If the saved Portfolio no longer exists, or a newer Portfolio snapshot already exists, discard the obsolete legacy filter fields without overwriting current data.

Update the small `SettingsRepository` fakes in JVM tests to compile against the selected-Portfolio API; do not introduce a new fake hierarchy.

### 3. Load and sanitize the initial snapshot before the first frame

Extend `buildReadyApp` in `startup/AppStartup.kt`, which already runs on `Dispatchers.IO`, to perform initialization in this order:

1. Load Portfolios and capture the saved ID and legacy snapshot before changing machine preferences.
2. Resolve the saved ID to an existing Portfolio, falling back to the first Portfolio or null.
3. Run the one-time legacy migration into the legacy snapshot's valid owner. Clear legacy keys only after the database write succeeds; if it fails, leave the old selected ID intact so the migration owner remains available for retry.
4. Persist the resolved ID as the last-selected Portfolio.
5. Load the resolved Portfolio's `filter.state`, or the canonical defaults when absent.
6. Validate saved Tag IDs against that Portfolio and persist the sanitized snapshot only if it changed.
7. Resolve the existing start destination and return the selected Portfolio ID plus initial snapshot in `ReadyApp`.

Thread those two new values through `desktopApp/main.kt` and `App.kt` into `AppShell`. Do not make `AppShell` repeat initial preference, database, or Tag reads. This keeps the first visible frame consistent and leaves per-screen data loading unchanged.

### 4. Make Portfolio and filter changes one serialized shell operation

In `AppShell.kt`, replace the independent selected-Portfolio, preset, custom-range, segment, selected-Tag, and match-mode states with one remembered holder containing the active `Portfolio?` and its `PortfolioFilterPrefs`. Derive the existing `FilterState` from that holder; screen and ViewModel APIs do not change.

Use one remembered `Mutex` for all filter mutations and Portfolio switches:

- a filter mutation transforms the active snapshot and performs one `filter.state` upsert when a Portfolio exists;
- a Portfolio switch loads and validates the destination snapshot, then assigns the Portfolio and snapshot together;
- an actual Portfolio ID change still clears Analysis state, while reloading an edited Portfolio with the same ID replaces its Portfolio object without resetting its filters;
- `reloadPortfolios` preserves the active ID when possible and otherwise atomically restores the first remaining Portfolio's snapshot;
- Dashboard/Reports Tag selection and selected-Tag deletion use the same serialized mutation path as the Top Bar.

Keep the selected-Portfolio preference update in the same switch operation. Do not launch a second persistence job outside the mutex, add a controller class, or introduce an observation flow.

Set a switching flag before starting a Portfolio switch and clear it in `finally`. Add an `enabled` input to `TopBar` and thread it through the existing `PortfolioSwitcher`, `DateRangeFilter`, `SegmentToggle`, and `TagFilterControl` click targets. Disable only these Portfolio/filter inputs during the local read; navigation and the current screen remain available. No loading screen or progress indicator is needed.

Remove `selectedTagsAfterPortfolioChange`; switching now restores the destination snapshot instead of clearing Tags. Keep the existing actual-ID comparison and selected-Tag deletion behavior.

### 5. Keep documentation aligned

`docs/plans/portfolio-scoped-tags-plan.md` carries a short pointer noting that this plan supersedes its switch-clears-Tags rule. No `CONTEXT.md` change is needed because this is UI preference state, not a new domain concept. No ADR is needed because the decision is local, reversible, and uses an existing persistence boundary.

## Test checkpoints

1. Add a common codec test covering a valid round trip, defaults, unknown members, independently corrupt members, malformed JSON, invalid/incomplete/reversed custom dates, and invalid or duplicate Tag IDs.
2. Update `PreferencesSettingsRepositoryFilterTest` for selected-Portfolio round trips, legacy reads, field-level legacy recovery, clearing legacy fields while preserving the Portfolio ID, and one-time migration behavior.
3. Add a focused startup migration/loading test with fake Portfolio settings and Tags: seed the valid legacy owner once, do not overwrite an existing snapshot, discard a missing owner, sanitize stale Tags, and retry after a failed write.
4. Replace the obsolete `AppShellStateTest` assertion that switching clears Tags with pure checks for the canonical default, atomic active-state replacement, same-Portfolio preservation, fallback selection, and selected-Tag deletion.
5. Extend `SqlDelightPortfolioSettingsRepositoryTest` only with one typed `filter.state` isolation/overwrite case if the codec extensions are not already covered through the startup test. Existing generic string tests already prove the table mechanics.
6. Compile the existing Dashboard, Trade Logs, Calendar, Reports, and Analysis tests against the unchanged `FilterState` behavior. Do not add a Compose UI-test framework solely for the short disabled-switch state.
7. Run the closest changed tests, then `./gradlew :shared:jvmTest`, followed by `./gradlew build` before implementation handoff.

## Acceptance scenarios

- Portfolio A saves This Month, DAY, Tags 1 and 2 with ALL matching. Portfolio B saves All Time, SWING, no Tags, and ANY. Switching A to B to A restores each exact snapshot.
- Restarting selects the last valid Portfolio and displays its saved filters on the first frame without briefly showing defaults or another Portfolio's filters.
- An existing installation migrates its old global snapshot into the saved Portfolio once. A later restart does not overwrite newer Portfolio-scoped state.
- A new Portfolio starts with the canonical defaults. With no Portfolios, defaults remain usable in memory and no ownerless row is written.
- One corrupt enum or date does not erase valid unrelated fields. Malformed JSON uses all defaults, and an invalid custom range becomes all time.
- Deleted, nonexistent, and cross-Portfolio Tag IDs are removed during restore and the cleaned snapshot is saved.
- Clicking filters during a Portfolio switch is prevented; consumers never observe Portfolio B paired with Portfolio A's snapshot.
- Editing the active Portfolio's metadata preserves its filters. Deleting it restores the first remaining Portfolio and its snapshot, or ownerless defaults when none remain.
- Deleting a selected Tag immediately updates and persists the active snapshot.
- Calendar, Reports, Dashboard, Trade Logs, and Analysis retain their current filtering and navigation semantics beyond the new persistence boundary.

## Out of scope

- Per-screen or per-destination filter snapshots.
- Persisting Trade Logs sorting, the Calendar's displayed month, navigation destination/history, Analysis navigation, dialogs, or sidebar state.
- Changing `FilterState`, date-resolution semantics, Tag match semantics, Position derivation, or screen-specific filter consumption.
- A new SQLDelight table or schema migration, multiple `filter.*` rows, database triggers, global foreign-key enforcement, or a generic settings-version framework.
- A loading screen, background observation flow, state-controller abstraction, or new UI-test framework.
- A glossary term or ADR for Portfolio filter preferences.
