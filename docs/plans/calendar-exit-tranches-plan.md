# Calendar Exit Tranches Plan

Status: implemented and reviewed

## Goal

Attribute realized Calendar P&L to the dates on which shares were sold or covered, rather than assigning a Position's entire P&L to its latest exit date. Preserve the original Position for Analysis, tags, notes, and Day/Swing classification.

## Agreed behavior

- Combine all exits from the same Position on the same date into one Calendar detail row. Show the symbol, original Day/Swing classification, quantity exited that day, and that day's fee-inclusive realized P&L.
- Count that Position once on each date with realized exits, regardless of the number of fills. Keep the existing `1 trade` / `N trades` labels. The same Position can count on multiple dates.
- Use FIFO entry costs and prorated entry/exit fees for each matched exit. Never apportion the total Position P&L by exit quantity alone.
- Long sales and short covers follow the same attribution rule. Opening and scale-in transactions do not create Calendar entries.
- Continue including the realized portion of partially exited, still-open Positions. Do not wait until the remaining shares are closed, and do not include unrealized P&L.
- A zero-P&L exit day still has a trade count and remains clickable.
- Weekly details retain separate dated rows for each Position/date, including repeated Positions on different dates. Order rows by date and earliest exit time within that day's group, earliest first.
- Clicking any daily or weekly row opens the full original Position in Analysis, preserving its chart, transactions, tags, and notes. Analysis navigation contains each distinct Position in the selected period only once, ordered by its first Calendar row in that period.
- Back from Analysis restores the selected day/week and browsed month under the existing reload rules.
- Apply portfolio, segment, and tag filters to the original Positions before projecting daily exits. All exits inherit their parent Position's Day/Swing classification, including a same-day partial exit from a Position ultimately classified as Swing.
- Continue ignoring the global date-range filter in Calendar.
- Use each closing Transaction's already-normalized local date. US-stock import dates remain New York dates; Calendar performs no extra system-timezone conversion.
- Daily cells, detail-panel totals, weekly totals, and month totals use the same projected exits. Monday-through-Sunday weeks remain complete across month/year boundaries; month totals include only the displayed month's dates.
- Available years, month bounds, and the initial latest month derive from attributed exit dates, so earlier partial-exit months remain accessible.
- Dashboard equity curves, Trade Logs, and date-based Reports retain their existing Position-based attribution. Calendar period totals may therefore differ from those screens; total realized P&L for identical unbounded Position sets must still reconcile.

## Accounting seam

`FifoMatcher.computeClosedPositions` already knows each closing Transaction's matched quantity, entry costs, and allocated fees. Its main matching pass correctly splits reversal transactions between the closing Position and the newly opened Position.

The existing `realizedPnLByTransaction` helper replays full Transactions from a Position's history. That history retains the full transaction on both sides of a reversal, so the replay loses the quantity that actually opened the second Position. For example, `BUY 100`, `SELL 150`, `BUY 80` opens a 50-share short and covers only 50 shares, while the replay can incorrectly match 80. A cap based on total realized shares cannot repair histories with intervening scale-ins.

Capture exit facts during the original FIFO pass and reuse them. Do not add another FIFO matcher or construct synthetic tranche Positions.

## Implementation

### 1. Record derived exit facts

In `domain/FifoMatcher.kt` and `domain/model/ClosedPosition.kt`:

- Add a small derived exit record associated with the closing Transaction's index in the parent Position's transaction list. Store its FIFO-matched quantity and net P&L; get its timestamp from the indexed Transaction.
- Accumulate one record per closing Transaction, combining its matches against multiple entry lots. Record only the shares that close the current Position; any reversal remainder becomes an opening lot on the next Position.
- Attach the immutable exit list when emitting each Position, including partially open tails. Keep existing Position boundaries, total shares, average prices, fees, and total P&L unchanged.
- Make `realizedPnLByTransaction` read these recorded facts while retaining its existing index-aligned `List<Double?>` contract. Opening/scale-in transactions remain `null`; realized zero remains `0.0`. Remove its duplicate FIFO replay.
- Keep the exit facts in memory only. `ClosedPositionService` and `PositionTagService` already preserve added fields through `copy`; no SQLDelight migration, repository API, or composition-root change is needed.

For each derived Position, exit quantities must sum to its realized shares and exit P&L must sum to its existing total P&L, within the tests' floating-point tolerance. The Analysis transaction table receives the corrected per-Transaction attribution through its existing helper call; its full-Transaction display stays unchanged.

### 2. Project Calendar rows once

In `domain/analytics`:

- Introduce a Calendar row holding the original Position, the day's exited quantity and P&L, and the earliest exit timestamp used for date grouping and ordering.
- Group recorded exits by their Transaction date within each Position, then order the resulting rows chronologically. Keep distinct Positions separate even when their symbols match.
- Preserve final-exit-date attribution only for synthetic Positions with no Transactions, as used by existing fixtures/previews. FIFO-produced Positions must use their recorded exits exclusively; do not retain a replay fallback.
- Change `dailySummaries` to consume the projected Calendar rows. Its P&L sums row contributions and its `tradeCount` counts rows, rather than raw fills.
- Update the existing helper callers in `CalendarViewModel`, Compose previews, and summary tests together.

### 3. Integrate Calendar state and Analysis handoff

In `ui/viewmodel/CalendarViewModel.kt`:

- Project the already-filtered, tag-hydrated Positions and use one date index of Calendar rows for both summaries and details. Replace the current final-exit-only `positionsByDay` representation rather than adding a parallel Position index.
- Derive weekly rows from seven date lookups and sort by each row's earliest exit timestamp.
- Build the selected period's Analysis navigation list from those rows, deduplicating original Positions while preserving row order. Preserve the clicked parent Position as the initial Analysis selection. Do not deduplicate by symbol.
- Compare the projected dataset on reload so new exits, changed fees, and changed tags clear stale selections under the existing rules. Unchanged reloads preserve the selected period and browsed month.
- Keep existing month navigation, adjacent-day selection, and day/week exclusivity behavior.

### 4. Reuse the detail panel

In `ui/screen/CalendarScreen.kt` and `ui/components/DayDetailPanel.kt`:

- Pass projected rows to the existing panel. Sum their period P&L for the heading; render each row's day-specific P&L and exited quantity.
- Keep the existing Day/Swing label; prefix the actual exit date in weekly details. Use existing quantity/money formatting and preserve fractional quantities.
- Pass the original clicked Position and deduplicated original-Position list to the unchanged `onAnalyze` contract.
- Adjust empty-state wording to describe realized exits, since a Position need not be fully closed.
- Preserve wide and narrow layouts, scrollbars, click behavior, and the Calendar grid's `trade` label. Update the affected Compose previews.

### 5. Document the shipped behavior

- Update the README Calendar feature description to explain daily exit attribution and full-Position Analysis.
- Refine `CONTEXT.md`'s Calendar P&L definition to refer to matched exit Transactions rather than a Position's final exit date.
- Preserve ADR-0001: tags and Position Notes remain keyed to the opening Transaction. No new persisted Position or tranche identity is introduced.
- This plan supersedes the final-exit-only daily/weekly list behavior in `calendar-weekly-pnl-plan.md`; its week boundaries, layout, and navigation rules still apply.

## Test checkpoints

1. Extend `FifoMatcherTest` / `RealizedPnLByTransactionTest` with separate-day exits, multiple entry lots, partially open long and short tails, fractional quantities, and reversal fees. Include chained reversals with intervening scale-ins, and assert that recorded exit quantities/P&L reconcile with each original Position. Existing total Position results must remain unchanged.
2. Update `DaySummaryTest` and add focused Calendar projection coverage: multiple same-day fills become one row, separate dates remain separate, distinct Positions of one symbol are not merged, zero P&L still counts, and transaction-free fixture behavior remains supported.
3. Extend `CalendarViewModelTest`: an earlier exit month becomes navigable; monthly and complete-week totals follow exit dates; repeated weekly rows map to one Analysis Position; clicked rows retain their parent; original segment/tag filters apply; unchanged reloads preserve selection and newly attributed exits clear it.
4. Run the focused FIFO, attribution, summary, projection, and Calendar tests using the README's `:shared:jvmTest --tests` pattern. Then run `./gradlew :shared:jvmTest` and `./gradlew build` before implementation handoff.
5. Manually inspect daily/weekly detail quantity and P&L, full-position Analysis and Back navigation, and both layout widths. Use the existing UI setup; do not add a UI-test framework.

## Acceptance examples

| Transactions | Expected Calendar behavior |
| --- | --- |
| Buy 100 at $10 with $10 fees; sell 40 at $12 Tuesday with $4 fees; sell 60 at $15 Thursday with $6 fees | Tuesday: 40 shares, +$72, `1 trade`. Thursday: 60 shares, +$288, `1 trade`. Week total: +$360. Analysis opens the original 100-share Position from either row. |
| Split Tuesday's sale into two 20-share fills at the same price, with $2 fees each | Tuesday still has one 40-share row, +$72, and `1 trade`. |
| Import only the opening buy and Tuesday's partial exit | Tuesday already shows +$72; the remaining 60 shares contribute no Calendar P&L. |
| Short 100 at $20 with $20 fees; cover 60 at $18 with $12 fees | Cover date: 60 shares and +$96. Opening short date contributes nothing. |
| One Position has exits before and after a month boundary in the same Calendar Week | Each month includes only its own exit-date amounts. Both grids show the same complete-week total and dated weekly rows. |
| Buy 100; sell 150; buy 80 | The reversal sale closes only 100 long shares; the later buy covers only 50 short shares. The remaining 30 shares open a new long and realize nothing until sold. |
| A Position opens Monday, exits partly Monday, and finishes Tuesday | Both exit dates appear under the original Swing classification. |

## Out of scope

- Changing Dashboard equity curves, Trade Logs, or Report attribution.
- Unrealized or mark-to-market P&L, a new Calendar mode, or new timezone settings.
- Per-fill Calendar rows, isolated-tranche Analysis, or tranche-specific tags/notes.
- Database, SQLDelight, broker/import, dependency, or launcher changes.

## Execution boundary

Implementation was authorized after all behavior decisions were agreed. Follow the repository's relevant verification and automatic commit/push rules, including this plan in the implementation commit while excluding unrelated work.

## Validation results

- Added failing regression checks before implementing FIFO exit attribution, Calendar projection/state, and fractional quantity formatting; all focused checks passed after implementation.
- The complete shared JVM test suite passed earlier during implementation. Final verification encountered coroutine timeouts in different existing Analysis timeframe tests: 619/620 passed in both full attempts, including a fresh run with `--rerun-tasks --no-build-cache`; 5/6 passed in the isolated Analysis retry. All Calendar, FIFO, and formatting checks passed. The chart-loading state paths were not modified; their asynchronous timeout remains unresolved and outside this plan.
- The build completed with `:shared:verifyCommonMainAppDatabaseMigration` excluded, using cached JVM test results. The fresh test failure above remains the authoritative final suite result; the cached build success does not establish a clean test run.
- The ordinary build's migration verification could not load SQLite's native library because its isolated worker attempted to create a native-library lock/DLL in `C:\WINDOWS` and received AccessDenied. Command-local temp overrides did not reach that worker. No database schema changed.
- Manually verified an isolated fixture through the real Calendar and Analysis screens: Tuesday's two AAPL fills combine into 40 shares and +$72; Thursday shows 60 shares and +$288; weekly details retain both dates. A fractional fixture displays `0.3 sh`. Analysis contains distinct parent Positions and all original transactions; Back preserves the selected week. Both wide and narrow layouts passed inspection. The temporary fixture and launcher were removed.
- Parallel Standards and Spec reviews completed with no remaining findings. The Spec review's fractional quantity formatting finding was fixed and regression-tested.
