# Grouped reporting implementation

Baseline: current HEAD on main. Preserve pre-existing documentation changes.

Implement the agreed first release in spec.md, using its acceptance checks as the test seams.

1. Persist nullable timing evidence, migrate genuine legacy schemas, and atomically insert or enrich exact identities. Invalidate market-dependent evidence and caches.
2. Preserve supported source evidence and propagate enrichment/conflict counts and refreshes through all ingestion paths.
3. Add verified timing and exclusive grouped analytics; update existing duration consumers.
4. Extend Reports with grouping, sorting, charts, coverage, exact cohorts and Analysis return state, including functional Tags and refresh handling.
5. Run targeted checks throughout, full shared tests and build, review standards and spec in parallel, fix findings, document and commit on the current branch; push its configured upstream.

Deferred work in the spec remains deferred.

## Completed verification

- Timing schema/migration, atomic enrichment, source mappings, cache/market changes, shared duration consumers, grouped Reports and exact cohorts implemented.
- 643 shared JVM tests pass, including boundary/DST, legacy migration, conflicts, timing-only ingestion refresh, Tag edits, and stale Reports loads.
- Light/dark report components rendered at 1200×720 with synthetic Positions and visually inspected.
- Standards review: one ingestion-count finding fixed; final review found no blockers.
- Spec review: open-Position exclusion, conflict insertion counts, and malformed execution-clock promotion findings fixed; final review found no blockers.
- `:shared:jvmTest` passes. `build -x :shared:verifyCommonMainAppDatabaseMigration` passes.
- Full `build` attempted: SQLDelight's isolated migration verifier cannot extract SQLite's native library because its worker uses protected C:\Windows as the temporary directory. Fresh-worker and temporary-directory overrides did not resolve the host failure. The JVM migration tests independently pass against genuine old schemas.
- No live broker/account requests or real journal data used. Native Analysis/Back interaction was not manually exercised; cohort/state behavior is covered by tests.
