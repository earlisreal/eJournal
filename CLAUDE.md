# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

eJournal is a desktop trading journal app (Kotlin Multiplatform + Compose Multiplatform, JVM/Desktop only). It imports broker transaction CSVs, computes closed positions via FIFO matching, and shows trade logs/analytics. It's a from-scratch rebuild of an original JavaFX app, built one phase at a time — see `docs/superpowers/specs/2026-06-08-ejournal-design.md` for the full design spec and phase roadmap, and `docs/superpowers/plans/` for per-phase implementation plans.

## Commands

```bash
./gradlew :desktopApp:run            # run the app
./gradlew :desktopApp:hotRun --auto  # run with Compose hot reload
./gradlew :shared:jvmTest            # run all tests
./gradlew build                      # full build
```

Run a single test class or method (tests use the JUnit platform):
```bash
./gradlew :shared:jvmTest --tests "io.earlisreal.ejournal.domain.FifoMatcherTest"
./gradlew :shared:jvmTest --tests "io.earlisreal.ejournal.domain.FifoMatcherTest.partial fill across multiple lots"
```

Requires JDK 25 (toolchain is resolved via foojay). Gradle config cache and build cache are enabled.

## Development workflow

- **Design, then usually implement directly — skip the separate written plan.** The user prefers: brainstorm/design spec → implement, *not* brainstorm → `writing-plans` → execute. Once a design spec in `docs/superpowers/specs/` is approved, go straight to implementation using TDD, working in the spec's phase order (tests are the checkpoints). Only write a formal plan in `docs/superpowers/plans/` when the work is large, handed off to a separate session, or the user asks. Keep the design step — that's the one that catches wrong assumptions cheaply; skipping *it* is where things go wrong, not skipping the plan.
- **Committing locally without asking is fine** (policy updated 2026-07-02) — commit at natural checkpoints (task done, tests green). **Never push** (or tag-push, or rewrite pushed history) without an explicit request. "Implement directly to main" means editing files on `main` (no feature branch).

## Architecture

Two Gradle modules: **`shared`** holds all UI and business logic; **`desktopApp`** is a thin launcher only (`main.kt` constructs `AppDependencies` and calls `App()`). This split deliberately keeps an Android target viable later without restructuring — keep new logic in `shared/commonMain`, not `desktopApp`.

Source layering inside `shared` (package `io.earlisreal.ejournal`):
- `domain/model/` — pure data classes and enums (`Transaction`, `Portfolio`, `ClosedPosition`, `Action`). `Transaction` is the single source of truth.
- `domain/` — business logic. `FifoMatcher` computes `ClosedPosition`s on the fly (FIFO per symbol). Closed positions are **never persisted** — they're recomputed from transactions, so editing/deleting a transaction needs no sync logic.
- `domain/parser/` — `TransactionParser` interface (one impl per broker). `GenericCsvParser` is currently a stub the user fills in with broker-specific parsing.
- `data/repository/` — repository interfaces (`TransactionRepository`, `PortfolioRepository`).
- `data/` — SQLDelight-backed repository implementations that map generated rows to domain models via `toDomain()`.
- `ui/` — Compose screens (`ui/screen/`), `ViewModel`s (`ui/viewmodel/`), navigation (`ui/navigation/Screen.kt`). Screens get repos/parsers passed in and create their own `ViewModel` via `viewModel { ... }`; ViewModels expose a single `StateFlow<XState>` of immutable state.
- `ui/chart/canvas/` — the Trade Analysis candlestick chart, drawn natively on a Compose `Canvas` (no webview/JCEF). The pure pan/zoom/scale math (`BarWindow`, `ChartViewport`, `ChartInitialView`) is unit-tested; `CandlestickChartRenderer` is the single drawing source of truth (candles, volume, VWAP, trade diamonds, crosshair, axes); `CandlestickCanvasChart` is the composable and `CanvasChartAdapter` maps `AnalysisState` onto it. Replaced an earlier Lightweight-Charts-in-JCEF bridge (removed to drop ~130 MB/platform of native CEF payload).

### Dependency wiring

There is no DI framework. `AppDependencies` (in `jvmMain`) is the composition root: it builds the database, repositories, and parser list, then `main.kt` threads them into `App()` and down into screens as constructor params. To add a new repository/parser/service, instantiate it in `AppDependencies` and pass it through.

### Persistence (SQLDelight + SQLite)

- Schema and queries live in `shared/src/commonMain/sqldelight/io/earlisreal/ejournal/*.sq`. The SQLDelight plugin generates `AppDatabase` + typed query classes into package `io.earlisreal.ejournal.data.database` at build time.
- A single SQLite file at `~/.ejournal/ejournal.db` holds everything (trade data and, later, OHLCV market data). `JvmDatabaseFactory` creates the schema on first run only.
- SQLite has no enum/datetime types: columns use SQLDelight column adapters (`Adapters.kt` — `DateTimeAdapter`, `ActionAdapter`) to map `TEXT` ↔ `kotlinx.datetime.LocalDateTime` and the `Action` enum. The DB table is `TradeTransaction`; the domain class is `Transaction` — keep these distinct.
- `LocalDateTime` (not date) is used throughout to support intraday/day-trading entries.

## Conventions

- Money/shares are `Double`; fees are pro-rated per matched lot inside `FifoMatcher` (entry fee scaled by matched fraction + exit fee per share).
- New shared dependencies go in `gradle/libs.versions.toml` and are referenced as `libs.*` aliases — don't hardcode versions in `build.gradle.kts`.
- `desktopApp` may use JVM/Swing APIs (e.g. `java.awt.FileDialog` for the file picker); `commonMain` must stay platform-agnostic.
- **kotlinx-datetime is on 0.7.x** (`material3` forces it transitively). `Clock` and `Instant` were removed from the `kotlinx.datetime` package and now live in the stdlib: use `kotlin.time.Clock` / `kotlin.time.Instant`. `kotlinx.datetime.Clock` compiles (deprecated) but throws `NoClassDefFoundError` at runtime — don't use it. `LocalDate`/`LocalDateTime`/`TimeZone` and the conversion extensions (`todayIn`, `toInstant`, `atStartOfDayIn`) remain in `kotlinx.datetime`.
- **Commit messages: no AI attribution.** Never append a `Co-Authored-By:` trailer (or any "Generated with" / Claude attribution line) to commits. Write the message body only.