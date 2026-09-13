# Calendar Weekly P&L Plan

Status: agreed

## Goal

Add a weekly P&L summary to every row of the monthly Calendar while preserving the existing daily drill-down and Analysis navigation. Weekly values must reconcile with the existing fee-inclusive realized P&L shown in day cells and must remain derived from filtered Positions rather than persisted.

## Agreed behavior

- A Calendar Week is Monday through Sunday.
- The grid shows complete weeks. Dates outside the displayed month remain visible but muted.
- An eighth `Week P&L` column appears to the right of the seven day columns.
- Weekly P&L sums the existing daily Calendar P&L for all seven dates, including adjacent-month dates.
- A week with no Positions shows `—` and is not clickable. A traded week whose gains and losses offset uses the existing signed-money zero format.
- Weekly cells reuse the current profit/loss tint and amount styling. Only the selected weekly cell receives the selection highlight.
- Clicking a weekly cell keeps the user on Calendar and loads that week's Positions into the existing detail area.
- The weekly detail heading is an explicit range such as `Sep 7–13, 2026`.
- Weekly Positions are ordered by exit datetime, earliest first. Each row shows symbol, exit date, Day/Swing classification, and P&L; the whole row is clickable.
- Clicking a Position opens that Position in Analysis and supplies every Position in the selected week for Analysis navigation.
- Returning from Analysis restores the displayed month and selected week.
- Selecting a day clears the selected week; selecting a week clears the selected day.
- Clicking a traded, muted adjacent-month day navigates to that month and selects the day.
- Month navigation clears the current day/week selection.
- `MONTH P&L` continues to include only dates in the displayed month, even though edge weeks include adjacent dates.
- The Calendar continues to honor portfolio, segment, and tag filters and continues to ignore the global date-range filter.
- On narrow layouts, the detail area remains below the grid instead of forcing horizontal overflow.

## Minimal implementation

### 1. Produce complete Monday-first month rows

Update `domain/analytics/MonthGrid.kt` so `monthGrid` returns non-null dates beginning with the Monday on or before the first of the month and ending with the Sunday on or after the last. Remove blank-cell handling instead of adding a second week-grid representation.

Update `MonthGridTest` for Monday-first ordering, leading and trailing adjacent dates, leap/non-leap months, and a week crossing a year boundary.

### 2. Extend Calendar selection state

In `CalendarViewModel.kt`:

- change the grid state to non-null dates;
- add `selectedWeekStart`, represented by that week's Monday;
- keep day and week selections mutually exclusive;
- make an adjacent-day selection apply that date's month before selecting it;
- preserve a selected week when the same filtered dataset reloads after returning from Analysis;
- clear both selections when the dataset or displayed month genuinely changes.

Keep `positionsByDay` as the only Position index. Derive a selected week's Positions with seven map lookups and sort the result by exit datetime. Do not add persisted summaries, a weekly repository API, or a parallel weekly state cache.

### 3. Add the weekly column

In `ui/components/MonthGrid.kt`:

- change weekday headers to Monday-first;
- render adjacent-month days with muted styling while retaining the existing day content and click rules;
- append a `Week P&L` header and one weekly cell per row;
- derive each amount and whether the week has Positions from the seven existing `DaySummary` entries;
- send the row's Monday to the week-selection callback;
- apply the accent selection treatment only to the weekly cell.

Continue using `signedMoney` and the existing profit/loss colors. No Position count is shown in the weekly cell.

### 4. Reuse the Calendar detail panel

Generalize the existing `DayDetailPanel` rather than creating a separate weekly panel. It should accept the selected period's heading and Position list, retain its total and scrollbar behavior, and optionally add the exit-date prefix needed for weekly rows.

`CalendarScreen.kt` chooses day or week details from the mutually exclusive selection, formats the weekly range, and passes the active list to the unchanged shell `onAnalyze(position, positions)` contract. The clicked Position becomes the initial Analysis selection; the ordered weekly list becomes its navigation set.

Keep the existing wide right-side and narrow stacked layouts. Update Compose previews for the non-null grid and weekly selection state; do not introduce a new UI-test framework.

### 5. Document the shipped behavior

When implementation lands, update the README Calendar feature text to mention Monday-through-Sunday weekly totals and weekly drill-down. `CONTEXT.md` already records `Calendar P&L`, `Calendar Week`, and `Weekly P&L`.

No ADR is needed: the change is local to Calendar, easy to reverse, and does not establish an architectural constraint.

## Test checkpoints

1. `MonthGridTest`: every result is a multiple of seven, starts Monday, ends Sunday, and includes the correct adjacent dates across month/year boundaries.
2. `CalendarViewModelTest`: day/week selection is exclusive; month changes clear selection; same-key reload preserves the selected week; dataset changes clear it; adjacent-day selection changes month.
3. Add one focused weekly-list test: all seven dates are included, outside dates are excluded, and Positions are ordered by exit datetime.
4. Verify filter coverage through the existing Calendar ViewModel path so weekly and daily values use the same portfolio, segment, and tag-filtered source.
5. Run the targeted Calendar tests, then `./gradlew :shared:jvmTest`, followed by `./gradlew build` before handoff.

## Acceptance scenarios

- A Monday-to-Sunday week spanning two months shows the same weekly total from either month's grid; each month's `MONTH P&L` excludes the other month's dates.
- A week with `+$100` and `−$100` shows signed zero and remains clickable; a week with no Positions shows `—` and is inert.
- Clicking a weekly cell shows the explicit date range and its filtered Positions earliest-first without leaving Calendar.
- Clicking any weekly Position opens that Position in Analysis; arrow navigation cycles through only that week's Positions; Back returns to the same selected week.
- Clicking a traded adjacent-month day navigates to its month and shows the normal daily detail list.
- Switching portfolio, segment, or tags recomputes both daily and weekly output from the same filtered Positions.

## Out of scope

- A separate weekly Calendar mode or weekly navigation controls.
- Unrealized or mark-to-market P&L.
- Timezone normalization beyond the Calendar's existing `exitDatetime.date` behavior.
- Applying the global date-range filter to Calendar.
- Database, SQLDelight, repository, or dependency changes.
- Direct navigation to Analysis from the weekly P&L cell.
