# Windows Update and Automatic Check Plan

Status: agreed

## Goal

Add optional update discovery for official eJournal releases and a secure, user-initiated Windows MSI update path. Automatic behavior stops at checking and notifying: eJournal never downloads or installs an update until the user explicitly asks it to.

## Prerequisite and rollout

- Apply to SignPath Foundation and obtain approval for the public repository, including an explicit eligibility decision for the separately licensed Moomoo SDK.
- Ship update checking and a `View release` action independently if signing is not ready. Do not ship in-app MSI download or execution until the signed release pipeline has passed end to end.
- Once in-app installation is enabled, a release that is unsigned, missing its GitHub SHA-256 digest, or rejected by verification must not be published.
- Record the SignPath choice in [ADR 0005](../adr/0005-use-signpath-for-windows-release-signing.md). It remains proposed until SignPath accepts the project.

## Agreed behavior

- GitHub Releases is the only update source. Only the latest published, non-draft, non-prerelease `vMAJOR.MINOR.PATCH` release is eligible.
- Update checks apply only to official tagged builds. Local and source builds do not check automatically.
- Automatic checking is enabled by default and runs once after startup only when the previous completed check is at least 24 hours old. There is no in-session timer.
- Manual checks are always available and bypass the throttle. Any completed check, manual or automatic, refreshes the last-check timestamp.
- Automatic check failures do not interrupt the user. Settings may show the latest failure; a manual check shows it immediately.
- The update request sends no journal, broker, portfolio, credential, or device data. Settings explains that the enabled toggle contacts GitHub for public release metadata.
- An available update appears in a non-modal app-wide banner and in Settings. Its actions are `Release notes`, `Download update` or `View release`, and `Later`.
- `Later` clears the banner. The same release may be offered again after the next eligible check, no sooner than 24 hours.
- Windows MSI builds may download and install in-app. Portable ZIP builds may check and notify but only open the GitHub release page; they are never converted silently into installed copies.
- Download starts only after `Download update` is clicked, shows progress and Cancel, and continues across navigation.
- A verified download changes the primary action to `Install and close`. Installation remains optional.
- `Install and close` is unavailable while the existing background-task tracker reports active work.
- Before installation, a confirmation defaults to Cancel and says that eJournal will close and unsaved Settings edits must be saved first.
- eJournal launches the visible Windows Installer UI and exits only after the installer process starts successfully. It does not install silently or relaunch itself.
- Updates are never mandatory. There is no remote minimum-version switch.

## Minimal implementation

### 1. Bake trustworthy build identity into each distribution

Extend the existing generated-resource work in `desktopApp/build.gradle.kts` rather than adding a BuildConfig plugin. Generate one classpath properties resource containing:

- the exact numeric application version;
- whether this is an official release build;
- the distribution type: `msi`, `portable`, or `development`.

Keep the existing splash and jpackage version fed from the same `appVersion` value. Validate the new properties at build time so an official artifact cannot silently fall back to `1.0.0` or an unknown distribution type.

Update `.github/workflows/release-windows.yml` to build the portable and MSI artifacts separately with explicit distribution properties. This is the capability boundary: the MSI build enables download/install, while the portable build exposes only check/view-release behavior. Do not add runtime registry or install-path heuristics.

### 2. Add only the update state needed by shared UI

Add one small update contract and state model under `shared/src/commonMain` for the shared Compose UI. It should expose the current build identity, observable update state, and the existing user actions: check, dismiss, download, cancel, and launch the verified installer.

Implement the contract once under `shared/src/jvmMain`. Compose screens must not know about GitHub JSON, temporary files, PowerShell, or `msiexec`. Keep the GitHub client, strict version comparison, streaming download, and Windows verification helpers together in the JVM update package until file size or reuse proves a split is useful.

Construct the manager in `AppDependencies` with the existing Ktor `HttpClient`, application background scope, and `SettingsRepository`; pass it through `App` to `AppShell` and `SettingsScreen`. No updater library, HTTP client, DI framework, generic background-job framework, or second settings store is needed.

Use a compact state progression such as idle, checking, update available, downloading, ready to install, and failed. Preserve enough failure context for Settings and explicit download errors; the app-wide shell renders only actionable update states, never automatic-check failures.

### 3. Persist the toggle and throttle directly

Extend `SettingsRepository` and `PreferencesSettingsRepository` with narrowly named update methods for:

- automatic checks, defaulting to `true`;
- the last completed check time.

Use `kotlin.time.Clock` and `kotlin.time.Instant` in shared code and store epoch milliseconds in `java.util.prefs.Preferences`. Inject a clock function into the JVM manager for deterministic tests; do not create a clock service or generic preference API.

At app startup, request a due check without blocking initialization or first paint. A disabled toggle or non-release build returns immediately. Manual checks ignore the 24-hour gate. One attempt is in flight at a time.

### 4. Read the existing GitHub release contract

Call the unauthenticated GitHub endpoint `GET /repos/earlisreal/eJournal/releases/latest` with the normal GitHub API headers and a fixed eJournal user agent. Read only the fields needed for `tag_name`, `html_url`, and release assets.

Require all of the following before reporting an installable update:

- `tag_name` exactly matches `vMAJOR.MINOR.PATCH` and is greater than the embedded current version;
- exactly one asset is named `eJournal-windows.msi`;
- the asset URL uses HTTPS;
- the asset has a positive declared size and a `sha256:` digest.

Treat equal or older versions as up to date. Treat malformed metadata as a check failure, not as an available update. Do not introduce a second manifest, prerelease channel, GitHub token, HTML scraper, ETag cache, or semantic-version dependency; comparison of the three validated integer components is sufficient.

### 5. Add the global banner and Settings card

Add a small update banner to the existing `AppShell` layout so it survives destination changes. It shows the available version and:

- opens the release's `html_url` through Compose's URI handler for release notes;
- starts download only for MSI builds;
- uses `View release` for portable builds;
- dismisses the current state for `Later`;
- shows streaming progress and Cancel while downloading;
- shows `Install and close` only after verification succeeds.

Use the existing app button/card styles and provide accessible labels and disabled-state explanations. Disable installation while `BackgroundTaskTracker` contains active work; checking and downloading may continue.

Add one `Updates` card to `SettingsScreen` with the current version, automatic-check toggle, `Check now`, last-check time, and current result. Do not add an About screen, tray behavior, modal check result, or a new UI-test framework.

### 6. Download, verify, and launch the MSI fail-closed

Stream the MSI to an update-specific directory under the OS temporary directory while calculating SHA-256 with the JDK `MessageDigest`. Enforce the declared asset size, compare the final digest in constant time, and never hold the installer in memory. Cancellation, transport failure, size mismatch, digest mismatch, or signature failure deletes the partial file.

Before enabling installation, use built-in Windows PowerShell non-interactively to obtain:

- `Get-AuthenticodeSignature` status and signer subject;
- MSI `ProductName`, `ProductVersion`, and `UpgradeCode` through Windows Installer.

Pass the installer path through an environment variable or process argument rather than interpolating it into PowerShell source. Require a valid Windows-trusted signature, the exact publisher subject approved for the SignPath project, `ProductName = eJournal`, a product version matching the GitHub tag, and the existing stable upgrade code `a3f1c9e2-7b4d-4e8a-9c1f-2d6b8e0a4f57`. Do not pin a certificate thumbprint; certificate renewal must remain possible.

After the explicit close confirmation, start `msiexec.exe /i <verified-path>` with `ProcessBuilder`. Exit through the existing Compose application callback only after process creation succeeds. If it fails, keep eJournal open and show the error.

Remove stale or partial updater files on the next launch. Do not add resumable downloads, a persistent installer cache, a bootstrapper, silent flags, automatic restart, or custom rollback. The MSI changes installed binaries; journal data remains under `~/.ejournal`, outside the installation directory, and existing SQLDelight migration tests remain responsible for schema safety.

### 7. Sign and prove artifacts before publication

Configure one SignPath artifact definition from a sample release bundle. In one signing request:

- Authenticode-sign the outer MSI and eJournal-owned launcher inside it;
- sign the eJournal-owned launcher inside the portable ZIP;
- exclude bundled third-party runtime executables from eJournal signing;
- constrain the expected eJournal MSI/launcher metadata where SignPath supports it.

In `.github/workflows/release-windows.yml`:

1. retain the existing Moomoo redistribution gate and strict tag-to-version derivation;
2. build and stage both unsigned artifacts with their correct embedded distribution identity;
3. upload the unsigned bundle as a GitHub Actions artifact;
4. submit it through SignPath's official GitHub action and wait up to 30 minutes for manual approval;
5. download the signed result and verify all expected signatures and metadata on the Windows runner;
6. install the previous stable MSI, apply the candidate MSI, and assert that the candidate version replaced it under the stable upgrade identity;
7. publish the fixed-name signed assets only after every preceding step passes.

A timeout or rejection fails the workflow and publishes nothing. Coordinate approval and rerun the tag workflow when needed; split the signing and publication workflows only if approval timeouts become a recurring problem.

Keep `eJournal-windows.msi` and `eJournal-portable-windows.zip` as the public asset names. GitHub calculates the release-asset digest after upload; verify through an API smoke check that the published MSI exposes the digest required by clients.

### 8. Document the shipped boundary

Update `README.md` when the feature ships:

- MSI builds can check, download, verify, and launch an update;
- portable builds can check and open the release page;
- automatic checks are default-on, daily-at-startup at most, and configurable;
- SignPath is the displayed publisher and manual release approval is intentional.

No `CONTEXT.md` change is needed. Update discovery, signing, and installation are application-lifecycle concepts rather than eJournal trading-domain language.

## Test checkpoints

1. Strict version tests: valid greater/equal/older versions plus malformed, overflow, prefixed, suffixed, and prerelease tags.
2. Ktor `MockEngine` tests: valid latest release, missing/duplicate MSI, missing or malformed digest, non-HTTPS URL, bad JSON, HTTP/rate-limit failure, and cancellation.
3. Manager tests with a fake clock and temporary directory: default-on behavior, 24-hour gating, manual bypass, single in-flight operation, portable/manual-only capability, dismissal, progress, cleanup, and silent automatic failures.
4. Download verification tests: expected size/digest succeeds; truncation, excess bytes, cancellation, and digest mismatch delete the file and cannot reach the install-ready state.
5. PowerShell result parsing tests: valid expected signer/product succeeds; unsigned, invalid, wrong publisher, wrong version, wrong product, and wrong upgrade code all fail closed. Exercise the real verifier against the signed candidate in the Windows release workflow.
6. `PreferencesSettingsRepositoryTest`: defaults, toggle persistence, valid timestamp, and malformed stored timestamp fallback.
7. Use the closest existing shell/state tests for banner visibility and active-task install gating. Keep Compose behavior covered through previews/manual acceptance rather than adding a UI framework.
8. Run targeted update/settings tests, then `./gradlew :shared:jvmTest`, followed by `./gradlew build` before handoff.
9. Run the signed clean-install and previous-version upgrade smoke checks in the release workflow before publication.

## Acceptance scenarios

- An official MSI build that has not checked for 24 hours opens normally, checks in the background once, and shows a banner only when a newer stable release exists.
- Reopening within 24 hours performs no automatic request. `Check now` still works and refreshes the timestamp.
- Going offline produces no global interruption; a manual check reports the network failure in Settings.
- Turning automatic checks off persists across restarts and does not disable `Check now`.
- `Later` hides the update until a later eligible check; no version is permanently skipped.
- A portable build offers release notes and `View release` but never downloads or launches the MSI.
- An MSI build downloads only after consent, reports progress, cancels cleanly, and survives navigation without starting a second operation.
- A tampered, truncated, unsigned, wrongly signed, wrong-version, or wrong-product MSI is deleted and can never enable `Install and close`.
- Active tracked sync work disables installation. Once idle, the close confirmation defaults to Cancel and warns about unsaved Settings edits.
- A verified update launches visible Windows Installer UI and closes eJournal; the user reopens it manually with existing journal data intact.
- A rejected or timed-out SignPath request creates no GitHub Release. A published release contains signed MSI and portable launchers, exposes the MSI digest, and passes an upgrade from the prior stable MSI.

## Out of scope

- macOS or Linux packages and update flows.
- Prerelease channels, percentage rollout, mandatory versions, or remote kill switches.
- Background download before consent, silent installation, scheduled-on-exit installation, or automatic relaunch.
- Portable-to-MSI migration.
- Delta patches, resumable downloads, mirrors, a custom update server, or a separate manifest.
- Certificate-thumbprint pinning or a general signer-rotation protocol. A publisher change requires a transition release or browser installation.
- Application-data backup, custom binary rollback, or changes to SQLDelight migration policy.
- Update analytics, telemetry, or transmission of local journal state.
