This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Desktop tests: `./gradlew :shared:jvmTest`

### Market data setup

Charts and unrealized P&L use OHLCV data fetched per imported trade — daily bars for swing trades, 1-minute bars for day trades. Two sources:

- **Yahoo Finance (default, no setup).** Full daily history, plus 1-minute bars for roughly the last 30 days. Works out of the box.
- **Alpaca (optional, free).** Unlocks 1-minute history older than 30 days. Create a free account at [alpaca.markets](https://alpaca.markets) (the paper/data API keys need no funding), then paste the Key ID and Secret Key into **Settings → Market Data** in the app.

Keys are stored only on your machine in `~/.ejournal/credentials.json` (owner-only permissions) and are sent to no one but Alpaca. Market data syncs automatically after each import and on app startup; use **Settings → Sync market data** to backfill manually after adding keys.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…