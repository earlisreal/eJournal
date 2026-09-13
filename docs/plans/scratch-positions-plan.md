# Scratch Position Classification Plan

Status: agreed

## Goal

Classify a closed Position as `Scratch` when its quantity-weighted average entry and exit prices are exactly equal, without erasing Broker Fees or changing fee-inclusive Realized P&L. Scratch Positions must be neutral in outcome statistics, visible as a count on Dashboard and Trade Logs, and remain financially visible in accounting-oriented metrics.

## Agreed behavior

- Scratch is derived from a closed Position; it is never stored on a Transaction or in the database.
- A Position is Scratch when `averagePriceDifference == 0.0`. This already accounts for long versus short direction and uses the Position's quantity-weighted average prices after FIFO matching.
- Equality is exact. There is no tick-size, currency, epsilon, configurable threshold, or manual override.
- Scratch takes precedence over net P&L. A flat-price Position remains Scratch even when Broker Fee debits make `profitLoss` negative or a Broker Fee credit makes it positive.
- A non-Scratch Position remains a Win when `profitLoss > 0.0`, a Loss when `profitLoss < 0.0`, and Break-even when `profitLoss == 0.0`.
- Win rate is `wins / (wins + losses)`. Scratch and Break-even Positions are excluded from both numerator and denominator; if there are no Wins or Losses, win rate is unavailable.
- Scratch is excluded from Win/Loss counts, Average Win/Loss, Largest Win/Loss, Reward : Risk, Top trades, Worst trades, and Win/Loss streak membership.
- Scratch and Break-even both reset an active Win or Loss streak.
- Scratch Broker Fees remain included in Net P&L, the equity curve, Gross Profit/Gross Loss according to the sign of net `profitLoss`, Profit Factor, and Expectancy. Average Price Difference, Average Hold, and total Position count continue to include every Position.
- Dashboard shows a `Scratch` count tile only when the filtered Scratch count is greater than zero. The existing conditional `Break-even` tile remains distinct.
- Trade Logs keeps its total Position count and shows `W / L`, followed by `S` and `BE` segments only when their respective counts are nonzero. Its win-rate context uses the same decisive-outcome denominator as Dashboard.
- A Scratch row keeps its actual fee-inclusive P&L and P&L percentage. Negative values stay red; there is no new outcome badge, table column, filter, or neutral recoloring.
- The behavior applies automatically to historical and future Positions because Position derivation and analytics are recomputed from Transactions.

## Classification contract

Apply these conditions in order so the outcomes form an exhaustive, non-overlapping partition:

| Priority | Condition | Outcome |
| --- | --- | --- |
| 1 | `averagePriceDifference == 0.0` | Scratch |
| 2 | `profitLoss > 0.0` | Win |
| 3 | `profitLoss < 0.0` | Loss |
| 4 | otherwise (`profitLoss == 0.0`) | Break-even |

The outcome counts must always reconcile:

```text
tradeCount == winCount + lossCount + scratchCount + breakEvenCount
```

Do not infer Scratch from `profitLoss == 0.0`: that remains the non-flat Break-even case. Do not inspect individual fills for matching prices; FIFO already supplies the aggregate Position averages required by the rule.

## Metric ownership

Keep outcome classification separate from financial aggregation. This is the intentional boundary agreed during design:

| Metric or surface | Scratch treatment |
| --- | --- |
| `tradeCount` | Included |
| `scratchCount` | Included |
| `winCount`, `lossCount`, `breakEvenCount` | Excluded from all three |
| Win rate | Excluded from numerator and denominator |
| Average/Largest Win or Loss | Excluded |
| Reward : Risk | Excluded indirectly through Average Win/Loss |
| Max Win/Loss streak | Resets the current streak; never increments one |
| Dashboard Top/Worst trades | Excluded |
| Net P&L and equity curve | Fee-inclusive `profitLoss` included unchanged |
| Gross Profit/Gross Loss | Included according to the sign of net `profitLoss` |
| Profit Factor | Includes the same signed financial totals as Gross Profit/Gross Loss |
| Expectancy | Included through existing `netPnl / tradeCount` calculation |
| Average Price Difference and Average Hold | Included |
| Recent trades and navigation lists | Included with existing fee-inclusive P&L color |
| Per-Tag metrics | Receives the same semantics automatically through `computeMetrics` |

A useful edge case follows from this split: one Win plus one fee-debit Scratch can show a 100% win rate and no Average Loss, while Profit Factor remains finite because the Scratch fee is still a real negative cash result.

## Minimal implementation

### 1. Add one derived Scratch predicate to the Position model

Update `shared/src/commonMain/kotlin/io/earlisreal/ejournal/domain/model/ClosedPosition.kt` with a read-only `isScratch` property implemented as exact comparison of the existing `averagePriceDifference` to zero.

Keep the predicate on `ClosedPosition` so Dashboard analytics, Trade Logs, and ranked lists all use the same definition. Do not add a persisted field, outcome enum, service, repository method, configuration value, or database migration. The only novel rule is the flat-price override; existing P&L sign comparisons remain sufficient for every non-Scratch outcome.

Do not change `FifoMatcher`, `realizedPnLByTransaction`, fee proration, `profitLoss`, or equity-curve construction. Those paths already preserve the accounting behavior required here.

Add `Scratch Position` to `CONTEXT.md` using the repository's Position terminology. Define it as a closed Position whose direction-aware quantity-weighted Average Price Difference is exactly zero, neutral for outcome statistics while fee-inclusive Realized P&L remains unchanged. Avoid calling the domain object a Scratch Trade even though the compact UI label is `Scratch`.

### 2. Split outcome buckets from financial-sign buckets in Dashboard metrics

Update `shared/src/commonMain/kotlin/io/earlisreal/ejournal/domain/analytics/DashboardMetrics.kt`:

- add `scratchCount: Int` to `DashboardMetrics` beside the other outcome counts;
- retain the full `pnls` collection for Net P&L, Expectancy, and total Position count;
- retain all positive and negative net P&L values as the financial buckets used by Gross Profit, Gross Loss, and Profit Factor;
- form classified Wins and Losses only from non-Scratch Positions with positive or negative net P&L;
- form Break-even only from non-Scratch Positions with zero net P&L;
- calculate Average/Largest Win and Loss and Reward : Risk from the classified Win/Loss buckets;
- calculate win rate from `winCount + lossCount`, returning `null` when that sum is zero;
- count Scratch independently and preserve the reconciliation invariant above;
- make the streak loop check Scratch before P&L sign, resetting both running streaks exactly as Break-even already does;
- update the streak documentation to name both neutral outcomes.

Keep `grossProfit` and `grossLoss` based on financial sign rather than classified outcome. In particular, do not derive `grossLoss` by summing the classified Loss list, because that would silently remove Scratch fees from Profit Factor. Keep `expectancy = netPnl / tradeCount`; Scratch fees therefore continue to reduce expectancy.

`TagStats.kt` needs no algorithm change because it already delegates to `computeMetrics`. Its `DashboardMetrics` values will inherit the new count, win-rate denominator, and classified Average/Largest Loss behavior automatically. Do not add a Scratch column to Tag reports in this work; the agreed display scope is Dashboard and Trade Logs.

### 3. Remove Scratch from ranked Win/Loss lists

Update `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/viewmodel/DashboardViewModel.kt` so both Top trades and Worst trades exclude `isScratch` before applying the existing P&L sign, ordering, and five-item limit.

This matters primarily for Worst trades because ordinary fee-debit Scratches have negative net P&L. Apply the same predicate to Top trades for completeness when a Broker Fee credit makes a flat Position positive. Leave Recent trades unchanged: it is a recency list, not an outcome list.

Mirror the same preview-only filtering in `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/preview/Previews.kt`. Add one realistic fee-debit Scratch sample with equal entry/exit prices so the Dashboard preview visibly exercises the conditional tile, retained red P&L, and exclusion from Worst trades. Do not build a separate preview state or mock outcome model.

### 4. Surface the Scratch count on Dashboard

Update the Performance `FlowRow` in `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/screen/DashboardScreen.kt`:

- keep the existing Win rate, Winners, Losers, and Trades tiles;
- add `Tile("Scratch", metrics.scratchCount.toString())` after Trades and before Break-even only when `scratchCount > 0`;
- retain the existing conditional Break-even tile and every existing financial tile;
- let the Hero block consume the corrected `metrics.winRate` without adding Scratch copy there.

The existing `FlowRow` handles wrapping, so no layout abstraction or width change is needed. Scratch uses the normal StatCard styling; do not add a new color token or icon.

### 5. Make Trade Logs use the same analytics result

Update `TradeLogsSummary` in `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/screen/TradeLogsScreen.kt` to call the existing `computeMetrics(positions)` rather than maintaining a second definition of Wins, Losses, and win rate. Use the file's existing Compose `remember` support keyed by `positions` so unrelated recompositions do not repeat metric calculation and streak sorting.

Build the context from `DashboardMetrics` in this order:

```text
<N> trade(s)  ·  <W>W / <L>L [ / <S>S ] [ / <BE>BE ]  ·  <rate>% win rate
```

- Always show W and L.
- Append S only when `scratchCount > 0`.
- Append BE only when `breakEvenCount > 0`.
- Append win rate only when `metrics.winRate` is non-null; multiply its stored fraction by 100 for the existing one-decimal presentation.
- Use `metrics.netPnl` for the lead value so the displayed cash result remains fee-inclusive.

Do not modify `TradeLogsTable`, `AnalysisScreen`, `RecentTradesList`, `TradesNavList`, `DayDetailPanel`, or their sign-color rules. A Scratch Position continues to show a red negative P&L when fees made it a negative cash result; the zero Average Price Difference already remains visible in the table.

### 6. Keep documentation and architecture proportional

The `CONTEXT.md` glossary addition is the only product-domain documentation required. README does not need another feature-list bullet for one outcome statistic.

No ADR is needed: Scratch is a local derived classification, does not change Position identity or persistence, and is easy to reverse. No SQLDelight schema, repository contract, dependency, settings key, import parser, or launcher change is involved.

## Expected files

Implementation should normally remain within these files:

1. `CONTEXT.md`
2. `shared/src/commonMain/kotlin/io/earlisreal/ejournal/domain/model/ClosedPosition.kt`
3. `shared/src/commonMain/kotlin/io/earlisreal/ejournal/domain/analytics/DashboardMetrics.kt`
4. `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/viewmodel/DashboardViewModel.kt`
5. `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/screen/DashboardScreen.kt`
6. `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/screen/TradeLogsScreen.kt`
7. `shared/src/commonMain/kotlin/io/earlisreal/ejournal/ui/preview/Previews.kt`
8. `shared/src/commonTest/kotlin/io/earlisreal/ejournal/domain/analytics/DashboardMetricsTest.kt`
9. `shared/src/commonTest/kotlin/io/earlisreal/ejournal/domain/analytics/TagStatsTest.kt` only if its synthetic Position helper would otherwise create unintended Scratches or when adding the propagation assertion described below.

Do not broaden the change solely to make this list exact. Re-read current callers before editing because another session may have moved or consolidated code.

## Test checkpoints

### Focused domain tests

Update `DashboardMetricsTest` first. Its current helpers give every synthetic Position equal entry and exit prices even when injecting arbitrary positive or negative P&L; after `isScratch` exists, those fixtures would all correctly classify as Scratch. Make the helpers construct non-flat prices for ordinary Win/Loss/Break-even scenarios and create explicit Scratch fixtures only where intended.

Cover these cases with the smallest clear set of tests:

1. Empty metrics include `scratchCount == 0`, no win rate, and unchanged null/zero financial behavior.
2. A fee-debit Scratch with equal averages and negative net P&L increments Scratch, not Loss or Break-even.
3. A zero-fee flat Position is Scratch, not Break-even.
4. A non-flat Position whose favorable price move exactly offsets fees is Break-even, not Scratch.
5. Exact equality is enforced: a nonzero price difference, however small, does not become Scratch.
6. Long and short Positions with equal weighted averages both classify as Scratch.
7. Outcome counts reconcile to total Positions and win rate uses only Wins plus Losses.
8. A Scratch fee remains in Net P&L, Gross Loss, Profit Factor, and Expectancy, while Average/Largest Loss and Reward : Risk use only classified Losses.
9. With Wins and fee-only Scratches but no classified Losses, win rate is 100%, Average/Largest Loss and Reward : Risk are unavailable, and Profit Factor remains finite when Scratch fees make Gross Loss nonzero.
10. An all-Scratch set has no win rate and no Win/Loss streak.
11. `Win -> Win -> Scratch -> Win` has a maximum Win streak of two; add the analogous Loss assertion in the same test if it stays readable. Preserve the existing Break-even reset test.

Keep existing mixed-trade, all-Win, payoff, holding-time, and chronological-streak coverage. Update their fixtures rather than weakening assertions.

### Propagation and UI checks

- In `TagStatsTest`, make default synthetic positive/negative Positions non-flat so existing tests do not silently become Scratch. Add one assertion showing a tagged Scratch contributes to that tag's `scratchCount` and corrected win rate while its fee remains in the tag's Net P&L; no separate TagStats algorithm test suite is needed.
- Verify the Dashboard preview shows the Scratch tile only with the Scratch sample present, keeps Break-even distinct, wraps the Performance tiles cleanly, and omits the Scratch from Worst trades.
- In a local app run or the closest existing preview path, verify Trade Logs produces conditional `S` and `BE` segments, omits win rate for an all-neutral set, and keeps a fee-only Scratch P&L red.
- Do not introduce a Compose UI-test framework solely for two conditional text segments and one tile. The domain tests protect the non-trivial classification and metric math; use existing previews/manual inspection for layout and color.

### Commands

Run the narrow tests while iterating:

```bash
./gradlew :shared:jvmTest --tests "io.earlisreal.ejournal.domain.analytics.DashboardMetricsTest"
./gradlew :shared:jvmTest --tests "io.earlisreal.ejournal.domain.analytics.TagStatsTest"
```

Then run the repository checkpoints before handoff:

```bash
./gradlew :shared:jvmTest
./gradlew build
```

If implementation-time changes make another existing test class directly relevant, run it as a targeted checkpoint rather than adding speculative coverage.

## Acceptance scenarios

- A long Position bought and sold at the same weighted-average price with `$2.00` of fees shows net P&L `-$2.00`, counts as one Scratch and zero Losses, lowers the equity curve by `$2.00`, contributes `-$2.00` to Gross Loss, and does not populate Average/Largest Loss or Worst trades.
- A short Position entered and covered at the same weighted-average price behaves identically.
- A scaled Position whose FIFO-derived aggregate weighted-average entry and exit prices are exactly equal counts as one Scratch even when its individual fill prices differ.
- A Position with a tiny but nonzero Average Price Difference is classified from net P&L as Win, Loss, or Break-even; no tolerance turns it into Scratch.
- A favorable non-flat move whose gains exactly equal fees counts as Break-even. It does not overlap with Scratch.
- A filtered set containing one Win, one Loss, one Scratch, and one Break-even shows four Positions, `1W / 1L / 1S / 1BE`, and a 50.0% win rate on Trade Logs; Dashboard shows the same counts and rate.
- A filtered set containing one Win and nine fee-debit Scratches shows a 100.0% win rate, ten total Positions, nine Scratches, fee-reduced Net P&L/Expectancy, and a Profit Factor whose denominator includes the nine Scratch fees.
- A filtered set containing only Scratches shows `—` for Dashboard win rate and omits win-rate text from Trade Logs. Its Net P&L and equity curve still show the accumulated fees.
- Scratch and Break-even each interrupt Win and Loss streaks.
- Dashboard, Trade Logs, and per-Tag metrics update historical Positions without migration or re-import.
- Scratch P&L stays red wherever a negative fee-inclusive amount is currently sign-colored; no badge, new column, filter, or neutral recoloring appears.

## Out of scope

- Changing FIFO matching, fee proration, Realized P&L, or Transaction-level realized values.
- Persisting an outcome or introducing a Position table, schema migration, repository API, or cache.
- An outcome enum or generalized classification framework.
- Fuzzy, tick-aware, currency-aware, configurable, or manually assigned Scratch thresholds.
- Treating near-zero P&L or small gains/losses as Scratch.
- Removing Scratch fees from Net P&L, Gross Loss, Profit Factor, Expectancy, or the equity curve.
- A Scratch rate, Scratch-specific financial total, dedicated report, chart series, filter, badge, table column, icon, or color.
- Adding Scratch counts to Tag report tables or other navigation/detail surfaces.
- Recoloring fee-only negative P&L as neutral.
- README feature marketing or an ADR.

## Execution handoff

At the start of the implementation session, read this plan, `AGENTS.md`, `CONTEXT.md`, and the current versions of every expected file; inspect `git status` and preserve unrelated user changes. Implement domain classification and focused tests first, then analytics, ranked lists, and the two UI surfaces. Do not modify FIFO or persistence code to make tests pass.

After targeted tests and both repository checkpoints pass, review the diff for the classification/financial-boundary split and for accidental fixture Scratches. Follow the automatic commit and push rule in `AGENTS.md` based on the execution session's Manila date and time, staging only this feature's files.
