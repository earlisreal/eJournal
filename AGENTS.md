# eJournal Agent Guide

eJournal is a local-first desktop trading journal built with Kotlin Multiplatform and Compose Multiplatform. Keep product, setup, and build documentation in `README.md`; keep this file focused on agent workflow and implementation invariants.

## Workflow

- Design substantial changes before implementation. Once the design is agreed, implement directly with tests as checkpoints; write a separate plan only for large work, a session handoff, or an explicit user request.
- Use the closest targeted tests while iterating, then run the relevant module or full-build checks from `README.md` before handing off.

## Architecture and invariants

- `shared` owns UI and business logic; `desktopApp` is a thin launcher. Keep `commonMain` platform-agnostic and put JVM/Swing integrations in `jvmMain` or `desktopApp`.
- `Transaction` is the source of truth. Closed positions are derived with FIFO matching and must not be persisted.
- There is no DI framework. `shared/src/jvmMain/kotlin/io/earlisreal/ejournal/AppDependencies.kt` is the composition root for repositories, parsers, clients, services, and app-wide scopes.
- SQLDelight schemas live under `shared/src/commonMain/sqldelight/io/earlisreal/ejournal`. Use the adapters in `data/database/Adapters.kt`; keep the generated `TradeTransaction` database type distinct from the domain `Transaction`.
- The trade chart uses wickplot through `ui/chart/canvas/CanvasChartAdapter.kt`; the domain `Bar` implements wickplot's `Candle` interface.
- Add shared dependencies through `gradle/libs.versions.toml` and use `libs.*` aliases instead of hardcoded versions.
- Money and share quantities are `Double`. FIFO fees are prorated across matched lots.
- Use `kotlin.time.Clock` and `kotlin.time.Instant`; the deprecated `kotlinx.datetime` versions can compile but fail at runtime with kotlinx-datetime 0.7.x. `LocalDate`, `LocalDateTime`, and `TimeZone` remain in `kotlinx.datetime`.

## Automatic commit and push

- After completing an executed plan or addressing review comments, automatically commit the resulting changes once the work and relevant verification are complete.
- Skip automatic commits for small, specific tasks unless the user explicitly asks for a commit.
- This automatic commit rule applies only on weekends or on weekdays after 15:00 PH time (`Asia/Manila`). Outside that window, leave changes uncommitted unless explicitly instructed otherwise.
- Automatically push the current branch to its configured upstream remote immediately after an automatic commit. If no upstream is configured, report that instead of forcing a remote setup.
- "Implement directly to main" means edit `main` without creating a feature branch.
- Never add AI attribution, `Co-Authored-By`, or "Generated with" text to commit messages.

## Agent skills

### Issue tracker

Use local markdown issues under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the canonical triage-label vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository. See `docs/agents/domain.md`.
