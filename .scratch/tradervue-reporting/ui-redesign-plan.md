# Reports design revision

Baseline: e3bda60. User asked for a stronger UI and design skill guidance.
Use Anthropic's frontend-design guidance, adapted to native Compose and the existing eJournal theme.
Source: https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md

## Design direction

A trading review workspace: make the distribution of results the visual focus, with precise, quiet supporting controls.

- Palette: existing graphite #0F1115, panel #171A21, text #E6E9EF, amber #E0A340, gain #3FB950, loss #F85149; existing light equivalents.
- Typography: existing sans-serif for navigation/labels, existing JetBrains Mono for financial figures. Clear heading/body/data hierarchy; sentence case.
- Alignment: left-aligned controls and labels, right-aligned numerical columns, aligned chart domains.
- Scope: Reports presentation, chart interactions, responsive layout, clear states, and cohort navigation. Preserve data, calculations, filter semantics, and Tag editing.

```
Reports                            Portfolio / date context
Entry time | Entry weekday | Duration | Symbol | Direction | Tag
Net P&L         Positions          Win rate        Profit factor
Performance distribution                      Timing coverage
             net P&L plot across the complete domain
             Position-count plot on the same domain
Breakdown                                    Select a group
Group | Count | Net P&L | Avg | Win rate | Avg win | Avg loss | PF
```

Review before implementation: a row of generic stat cards and oversized decorative headings would distract from distribution analysis. Use a single summary strip and one aligned chart panel, retain a practical table, and put the clock/coverage explanation behind a clear disclosure with concise visible context. All 48 slots must fit without horizontal chart scrolling; detailed labels come from pointer/keyboard inspection.

## Execution and verification

1. Replace dropdown-only navigation and dense metadata with grouping tabs, filter context, summary strip, and a clear cohort header.
2. Replace floating miniature bars with aligned interactive charts; refine table hierarchy, numeric alignment, sign colors, hover/focus, and narrow-window handling.
3. Render the complete Reports content with synthetic data in light/dark themes, narrow/wide sizes, cohort, empty, and unverified states. Inspect and revise.
4. Run targeted Reports/analytics checks and relevant module verification; review changes, commit and push only this revision.

## Results

- Applied frontend-design guidance directly to Compose, retaining existing eJournal colors, fonts, house components, and financial behavior. No design dependency or global skill installation added.
- Full-domain aligned charts replace horizontally scrolling miniature bars. Added visible grouping tabs, a summary strip, accessible inspection/selection, clearer numeric hierarchy, hover/focus feedback, and native table/page scrolling at smaller sizes.
- First populated group is initially visible; all fixed buckets remain available. Metric sorting returns to the first row. Page/table/list scroll state survives Analysis navigation.
- Complete screens rendered and inspected with synthetic Positions in light/dark themes at 1200×850 and 860×640, including cohort, weekday, empty, and all-unverified states.
- Integration checks exercise chart accessibility inspection, exact cohort selection, return, metric sorting, table selection, grouping tabs, and empty fixed buckets. Hit testing covers boundaries and empty domains.
- 646 shared JVM tests pass with no failures or skips. Standards review clear; two spec-review presentation omissions corrected and rechecked.
- Build verification retains the prior exclusion of `:shared:verifyCommonMainAppDatabaseMigration` due to this host's SQLite native-library extraction permissions. No schema changes in this revision. Native mouse/keyboard interactions were not manually exercised.
