# eJournal

> Desktop trading journal that turns broker CSVs into closed positions, charts, and analytics — built with Kotlin & Compose Multiplatform.

eJournal is a **free, open-source, local-first desktop trading journal**. Import your broker's transaction CSV and it matches every fill into round-trip trades (FIFO), computes realized P&L, and gives you a performance dashboard, a P&L calendar, a sortable trade log, and a per-trade candlestick chart with your entries and exits plotted on it. Your trades and API keys never leave your machine.

## Screenshots

<!--
  Screenshots are hosted on GitHub's attachment CDN.
  To populate: open a new issue (or any comment box) on GitHub, drag-drop each
  screenshot into it, and GitHub mints a https://github.com/user-attachments/assets/<id>
  URL. Paste each URL over the REPLACE_WITH_* placeholders below.
  (You don't have to submit the issue — uploading the file is enough to mint the URL.)
-->

| Dashboard | P&L Calendar |
| --- | --- |
| ![Dashboard](https://github.com/user-attachments/assets/0841a809-5036-4271-9ec5-1f8a1ae20af8) | ![Calendar](https://github.com/user-attachments/assets/a5013294-1ec0-4959-bc26-01416ae30167) |

| Trade Log | Trade Analysis |
| --- | --- |
| ![Trade Log](https://github.com/user-attachments/assets/cad889b3-e82f-482a-9b17-f43e7a85bf11) | ![Analysis](https://github.com/user-attachments/assets/08e5f787-454d-4ee1-93c5-69ee7d1f9309) |

## Features

- **Automatic FIFO trade matching** — groups individual fills into round-trip trades; handles longs, shorts, scale-ins/outs, and position flips. Closed positions are recomputed from your transactions (never stored), so editing or deleting a transaction just works — no sync to manage.
- **Performance dashboard** — net & gross P&L, win rate, profit factor, expectancy, reward:risk, average win/loss, streaks, average hold time, top/worst trades, and an equity curve — all filterable by date range.
- **P&L calendar** — a month grid color-coded by daily profit/loss with Monday-through-Sunday weekly totals; click a day or week to drill into its trades.
- **Per-trade analysis** — candlestick chart (10-second eTape bars by default for sub-minute Positions when available, 1/5/15-minute intraday, or daily/weekly for swing trades) with your entries and exits plotted, a VWAP toggle, a transaction breakdown, and arrow-key navigation between trades.
- **Sortable, filterable trade log** — every closed position with entry/exit times & prices, shares, P&L, fees, and hold duration. Click through to the chart.
- **Drag-and-drop import** — drop a CSV, let eJournal auto-detect the broker, and preview parsed transactions before committing.
- **Free market data** — Yahoo Finance daily bars work out of the box; add free Alpaca keys for 1-minute intraday bars on day trades. Optional local eTape data supplies exact 10-second bars for US stock day Positions.
- **Direct Alpaca synchronization** — read-only import of executed US stock fills from a Paper or Live trading account, including partial fills.
- **Direct Moomoo OpenD synchronization** — read-only, localhost-only import of live US stock orders, executions, and exact order fees.
- **Local-first & private** — everything lives in a single SQLite file under `~/.ejournal`; API keys are stored with owner-only permissions on your machine.
- **Light / dark / system themes.**

## Supported brokers

| Broker | Import format | Status |
| --- | --- | --- |
| **Alpaca** | Trading API direct sync | ✅ Supported |
| **TradeZero** | TradeHistory CSV export (plus optional API sync) | ✅ Supported |
| **Moomoo** | OpenD direct sync or order history CSV export | ✅ Supported |
| **Charles Schwab** | Transaction history CSV (web "History" export) | ✅ Supported |
| **E\*TRADE** | Transaction history CSV (classic `DownloadTxnHistory.csv`) | ✅ Supported |
| **Robinhood** | Account activity report CSV | ✅ Supported |
| **Webull** | Order history CSV (`Webull_Orders_Records.csv`) | ✅ Supported |
| **Fidelity** | Accounts "History" transaction CSV | ✅ Supported |
| **Interactive Brokers** | Activity Statement CSV (Trades section) | ✅ Supported |
| **Tastytrade** | Transactions CSV export | ✅ Supported |
| **eToro** | Account statement XLSX (Account Activity sheet) | ✅ Supported |
| **Generic CSV** | `datetime, symbol, action, price, shares, fees` | ✅ Manual fallback |

Every file is auto-detected on drop (eToro by its workbook sheets, the rest by their CSV header). Only **buy/sell** rows are imported; option legs and non-trade rows (dividends, interest, transfers, cancelled orders, eToro deposits/withdrawals/overnight fees) are detected and skipped, with the skipped count shown in the import summary. Most US web exports are date-only — same-day trades land at midnight — except Webull, Interactive Brokers, Tastytrade, and eToro, which carry intraday execution times. eToro charges no per-trade commission (its cost is the spread), so imported eToro trades carry no fees.

Don't see your broker? Use the **Generic CSV** importer with any file that has the columns above, or open an issue/PR to add a parser.

## Download

> **Moomoo SDK public-binary release gate:** the `com.moomoo.openapi:moomoo-api:10.8.6808` POM names a non-commercial license, but its linked license text was unavailable when this integration was added. The Windows release workflow fails before packaging unless repository Actions variable `MOOMOO_SDK_REDISTRIBUTION_CONFIRMED` is exactly `true`. Set that variable only after Moomoo's redistribution terms and required notices have been confirmed, then include every required notice in the distribution. Local packaging remains available for verification and does not imply redistribution approval. Do **not** publish an installer, portable archive, or other public binary containing the SDK before that confirmation. No license terms are inferred here.

Grab the latest build from the [**Releases page**](https://github.com/earlisreal/eJournal/releases/latest):

- **Windows** — `.msi` installer, or the portable `.zip` (no install needed; bundles its own Java runtime).

On **macOS / Linux**, build and run from source — see [Building from source](#building-from-source) below.

## Alpaca and market data setup

Charts and unrealized P&L use OHLCV data fetched per imported trade — daily bars for swing trades, 1-minute bars for day trades, and optional native 10-second bars for sub-minute US stock day trades. Analysis defaults to 10-second bars for those Positions when complete data is available, then falls back to 1-minute bars. Three sources:

- **Yahoo Finance (default, no setup).** Full daily history for daily bars. Works out of the box.
- **Alpaca (optional, free).** The same Key ID and Secret Key unlock 1-minute market-data history and read-only trading-account synchronization. In **Settings → Alpaca**, select the matching **Paper** or **Live** trading environment; Paper and Live credentials are different. Create a free account at [alpaca.markets](https://alpaca.markets) (Paper/data keys need no funding), then follow steps 1 and 2 of [Alpaca's guide](https://alpaca.markets/learn/connect-to-alpaca-api).
- **eTape (optional, local).** When `~/.eTape/etape.db` exists, eJournal copies the complete available Position date of native `bars_10s` rows into its own database during market-data sync. Use **Settings → Sync → Choose eTape database…** for another file; the selected path is remembered. Imports are read-only, idempotent, and never synthesize bars from 1-minute data.

Alpaca synchronization only reads `/v2/account`, `/v2/assets`, and legacy `/v2/account/activities/FILL` plus `FEE` data. It never places, modifies, or cancels orders. Phase 1 imports US stock fills only; options and crypto fills are skipped. Each execution, including partial fills, remains a separate eJournal transaction. Alpaca's aggregate CAT, REG, and TAF Broker Fees are preserved and allocated as eJournal-derived Fee Allocation across matching Alpaca Transactions: REG by sell notional, TAF by sell shares, and CAT by all executed shares. Fee Dates use Alpaca's UTC activity boundary, debits become positive journal fees, fractional cents are retained, and unsupported, contaminated, malformed, or unmatched buckets remain unapplied with warnings. The first sync reconciles full history; later syncs replay an inclusive 95-day window so recent corrections replace prior allocations. Startup synchronization is opt-in per portfolio and runs before market-data synchronization.

Keys are stored only on your machine in `~/.ejournal/credentials.json` (owner-only permissions) and are sent to no one but Alpaca. Market data syncs automatically after each import and on app startup; use **Settings → Sync market data** to backfill manually after adding keys.

## Moomoo OpenD setup

Direct Moomoo import requires the separately installed **Moomoo OpenD** application. eJournal uses Moomoo OpenAPI SDK `10.8.6808`; it does not bundle OpenD, OpenD native libraries, or SDK source.

1. Install and start OpenD using Moomoo's official instructions, sign in, and authorize the live trading account you want to journal.
2. Keep OpenD on the same computer as eJournal. eJournal always connects to `127.0.0.1`; remote hosts are not accepted.
3. In eJournal, add or edit a US Stocks portfolio, choose **Moomoo**, enter OpenD's listening port (`11111` by default), and select **Discover accounts**.
4. Select an eligible account, save the portfolio, then use **Import Transactions → Sync Moomoo OpenD**. Startup synchronization is available but is opt-in for each portfolio.

Only active **REAL**, normal/non-master accounts authorized for the US market are selectable. The integration is read-only: it requests the account list, historical orders, historical deals, and exact order fees; it contains no order placement, modification, cancellation, or trade-unlock flow. OpenD credentials and passwords are never requested or stored. The selected account label, account ID, security firm, port, binding, and resumable sync cursor live in the portfolio settings database.

The first sync reads the last 365 days of live US stock history in oldest-first 90-day windows. Later syncs resume from the completed checkpoint with a three-day overlap, clamped to that same one-year boundary. Filled and partially-filled-cancelled orders are aggregated to one transaction per order using the earliest execution time and exact total fee. Options, combos, prediction contracts, other markets, unsupported sides, zero fills, and malformed rows are skipped and reported.

## Building from source

eJournal is a Kotlin Multiplatform project targeting Desktop (JVM only). Building requires **JDK 25** (the Gradle toolchain resolves it automatically via foojay).

```bash
./gradlew :desktopApp:run            # run the app
./gradlew :desktopApp:hotRun --auto  # run with Compose hot reload
./gradlew :shared:jvmTest            # run all tests
./gradlew build                      # full build
```

Run a single JUnit test class or method with `--tests`:

```bash
./gradlew :shared:jvmTest --tests "io.earlisreal.ejournal.domain.FifoMatcherTest"
./gradlew :shared:jvmTest --tests "io.earlisreal.ejournal.domain.FifoMatcherTest.partial fill across multiple lots"
```

Almost all code lives in [`shared/`](./shared/src) (UI + business logic); [`desktopApp/`](./desktopApp/src) is a thin launcher. Contributor architecture and repository conventions are in [`AGENTS.md`](./AGENTS.md).

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) and [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).

## License

Released under the [MIT License](./LICENSE).
