# Windows Release, Consent, Update, and Signing Plan

Status: implemented; CI and public-release gates remain

Confirmed: 2026-09-20

## Outcome

Publish a Windows-only eJournal release that is eligible to apply for the SignPath Foundation program, then add signed, user-initiated MSI updates after SignPath accepts the project.

The first compliance release will:

- contain no Moomoo SDK, copied SDK code, Moomoo schema/generated files, or bundled OpenD executable;
- retain read-only automatic Moomoo import through a small independently written JSON-over-TCP OpenD adapter;
- make Yahoo and Alpaca market-data access opt-in and default-off before any automatic request;
- retain default-on GitHub release checks as a separate choice presented before the first request;
- check and notify about updates, but only open the GitHub release page while the build is unsigned;
- publish Windows MSI and portable ZIP assets only; and
- include an exact Windows runtime-component inventory, required notices, privacy disclosure, and code-signing policy.

After SignPath acceptance, signed MSI builds may download, verify, and launch an update only after an explicit user action. Portable builds remain check-and-open only. No update is forced, silently downloaded, or silently installed.

## Non-negotiable boundaries

- GitHub Releases remains the only release and update source.
- OpenD remains a separately installed local application. eJournal connects only to `127.0.0.1` and exposes no remote-host setting.
- The OpenD integration remains read-only. It implements only connection initialization, keepalive, account list, historical orders, historical executions, and exact order fees.
- The adapter uses JDK networking and hashing plus the existing JSON stack. Do not add protobuf, a Moomoo dependency, an updater library, a new HTTP client, a DI framework, or a license/SBOM plugin.
- The **Online Market-Data Setting** controls automatic Yahoo and Alpaca requests only. It does not control local eTape copying, per-Portfolio broker Auto-Sync Settings, explicit broker connection tests, or manual broker synchronization.
- The first-run disclosure gates all automatic startup network work, including opted-in broker startup synchronization and GitHub update checks, until the user continues.
- An unsigned build never downloads or launches an MSI in-app.
- macOS and Linux packaging definitions may remain in Gradle for easy restoration, but no active workflow or ordinary public release documentation advertises those packages.

## Delivery gates

1. **Package gate:** no public compliance release while the Moomoo SDK, its schemas/generated classes, OpenD, or an unreviewed runtime component is present.
2. **Consent gate:** no automatic network work before the current disclosure version is accepted; no automatic Yahoo/Alpaca work while online market data is disabled.
3. **Application gate:** apply to SignPath only after the compliant unsigned Windows release is public and old SDK-bearing binary assets are removed.
4. **Installer gate:** no in-app MSI download or launch until the release workflow produces and verifies a SignPath-signed candidate end to end.
5. **Publication gate:** signing rejection, timeout, identity mismatch, failed upgrade smoke test, or missing digest publishes nothing.

## Phase 1 — Narrow releases to Windows

1. Copy the current Ubuntu and macOS jobs from `.github/workflows/release-windows.yml` into `.github/workflows-disabled/release-linux-macos.yml`. Add a header explaining that GitHub does not execute it and that restoring it requires moving it back, reviewing current runner/action versions, and rerunning platform validation.
2. Reduce `.github/workflows/release-windows.yml` to Windows validation, MSI/portable packaging, release summary, and publication. Remove Linux/macOS matrices, jobs, assets, release text, and cross-platform status handling.
3. Preserve `TargetFormat.Dmg` and `TargetFormat.Deb` plus platform metadata in `desktopApp/build.gradle.kts`; those dormant local definitions are the cheap path back later.
4. Remove Ubuntu/macOS download, support, setup, build, and uninstall claims from `README.md`. Do not describe dormant Gradle targets as supported releases.
5. Mark `.scratch/ubuntu-macos-releases/spec.md` superseded by this plan. Do not implement or delete its historical design.

Checkpoint: manual workflow dispatch produces Windows artifacts only, and a repository search finds no active workflow or README promise for Linux/macOS releases.

## Phase 2 — Replace the Moomoo SDK with the minimum OpenD adapter

Keep the existing common `MoomooClient` contract and sync/import behavior. Replace only the JVM transport implementation behind it.

1. Remove `com.moomoo.openapi:moomoo-api` from `gradle/libs.versions.toml` and `shared/build.gradle.kts`, along with SDK-specific imports, generated types, packaging notice logic, and `MOOMOO_SDK_REDISTRIBUTION_CONFIRMED` gates.
2. Rework `MoomooOpenDClient` under `shared/src/jvmMain` as a small app-specific client using:
   - `java.net.Socket` with bounded connect/read timeouts;
   - the documented 44-byte OpenD frame header in little-endian order;
   - JDK `MessageDigest` SHA-1 body-integrity checks;
   - the existing `kotlinx.serialization.json` dependency;
   - monotonically increasing serial numbers and exact response correlation; and
   - one bounded keepalive loop for the lifetime of a connection.
3. Implement only the documented protocol IDs needed by current behavior: InitConnect, KeepAlive, account list, historical orders, historical executions, and historical order fees. The documented historical-order and historical-fill responses expose no page token or last-page marker, so the existing 90-day `moomooWindows` date partition is the bounded pagination seam; preserve it and do not invent undocumented page fields. Preserve account/order IDs losslessly and reject malformed, mismatched, oversized, truncated, or bad-digest frames.
4. Name private wire fields/types in eJournal's own vocabulary and derive them independently from public protocol documentation. Do not copy Moomoo SDK source, protobuf schemas, generated classes, comments, or constants wholesale.
5. Preserve existing domain mapping, eastern-time interpretation, execution de-duplication, exact-fee reconciliation, error messages, and UI flows. Keep the connection loopback-only and read-only; do not add encryption negotiation, trading commands, push subscriptions, remote hosts, or a general-purpose SDK layer.
6. Add focused JVM tests using a loopback fake server or in-memory frame codec for:
   - frame encode/decode, partial reads, bad magic, length limits, SHA-1 mismatch, and serial mismatch;
   - InitConnect and keepalive;
   - bounded date-window pagination and JSON number/string ID variants;
   - account, order, execution, and fee mapping; and
   - timeout, disconnect, OpenD error, and cleanup behavior.
7. Run a hands-on Windows smoke check against separately installed OpenD: test connection, select an account, import a date range with partial fills and exact fees, repeat to prove idempotency, and confirm no trade/write request is sent.

Checkpoint: `./gradlew :shared:jvmTest` passes with no Moomoo SDK or Moomoo schema/generated artifact in the resolved runtime graph or packaged application, and the OpenD smoke check matches current imported results.

## Phase 3 — Put consent in front of automatic network access

### Persistence and disclosure

Extend the existing `SettingsRepository`/`PreferencesSettingsRepository` with only these values:

- online market data enabled, default `false`;
- automatic update checks enabled, default `true`;
- accepted network-disclosure version, initially absent; and
- last completed update-check time.

Use one integer disclosure version. Increment it only when the providers or material data-transfer behavior changes; wording-only edits do not require a migration framework.

### First-run flow

1. Before starting `StartupSyncCoordinator` or the updater, show a blocking first-run network-choice screen whenever the stored disclosure version is missing or old. This applies to fresh installs and upgraded existing installations.
2. Explain, before Continue:
   - Yahoo/Alpaca market-data requests send symbols and requested date ranges derived from journaled transactions;
   - GitHub update checks send normal network metadata and an eJournal user agent but no journal, broker, portfolio, credential, or device data;
   - local eTape copying does not contact an external service; and
   - broker startup synchronization remains controlled per Portfolio.
3. Link to `PRIVACY.md` and the relevant provider privacy/terms pages. Present two independent toggles: online market data off by default, automatic update checks on by default.
4. Persist both choices and the disclosure version only when Continue is pressed. Closing the screen exits the application, performs no network request, and shows the screen again next launch.
5. After Continue, start eligible broker startup synchronization, automatic market-data work, and the due update check in the existing order without blocking first paint.
6. Expose both choices in Settings afterward, using the same explanation and policy links; changing either setting takes effect without repeating the first-run screen.

### Central market-data enforcement

Deepen the existing `MarketDataService`; do not scatter provider checks through screens, import code, startup coordination, or providers.

1. Split its public entry points into an automatic path and an explicitly confirmed manual path. The automatic path reads the Online Market-Data Setting centrally. The manual path may contact providers once without changing the setting.
2. Both paths may copy local eTape data. When online access is disabled, the automatic path skips Yahoo and Alpaca but still finishes eTape work.
3. Route startup and post-import calls through the automatic path. Change **Sync market data** so, when online access is disabled, it first names the external providers and the symbol/date-range transfer; only confirmation runs one external sync.
4. Enabling online access saves the preference but does not immediately start a request. The next import/startup uses it; the existing manual sync remains available for immediate backfill.
5. Track the active automatic external child job in `MarketDataService`. Turning the setting off cancels that Yahoo/Alpaca work promptly without cancelling independent eTape copying. Keep one external sync in flight rather than adding a scheduler.
6. Continue using provider clients as simple transports. They should not read preferences or decide whether consent exists.

Tests must prove:

- missing/old disclosure prevents all automatic startup network calls;
- fresh and upgraded installs default online market data to off;
- disabled startup and post-import sync make zero Yahoo/Alpaca requests while eTape still copies;
- a declined manual confirmation makes no request;
- a confirmed manual request runs once and leaves the global setting off;
- enabling takes effect on the next automatic trigger;
- disabling during automatic sync cancels external work while local work can finish; and
- broker Auto-Sync and the update toggle remain independent.

Checkpoint: provider fakes record no external market-data request until the user has opted in or confirmed the single manual action.

## Phase 4 — Ship check-and-notify updates in the unsigned compliance release

### Build identity

Extend the generated classpath-properties work in `desktopApp/build.gradle.kts`; do not add BuildConfig tooling. Generate and validate:

- exact numeric application version;
- official-release versus development build;
- distribution type: `msi`, `portable`, or `development`.

The splash, jpackage metadata, release tag, and generated identity continue to use the same `appVersion` input. An official build must fail rather than fall back to `1.0.0` or an unknown capability.

### Update manager

1. Add a small shared state/action contract for build identity, checking, available version, dismissal, release notes, and `View release`. Implement it once under `shared/src/jvmMain` and construct it in `AppDependencies` with the existing Ktor client, application scope, and settings repository.
2. Do not add download/install states or code to the unsigned compliance release; Phase 7 extends this contract only after signing is available.
3. On official builds, run at most one automatic check after the first-run Continue when enabled and the previous completed check is at least 24 hours old. There is no in-session timer. Manual **Check now** bypasses the throttle. A single attempt may be in flight.
4. Automatic errors remain non-modal and visible only in Settings; manual errors appear immediately. Every completed attempt refreshes the last-check timestamp.
5. Request unauthenticated `GET /repos/earlisreal/eJournal/releases/latest` with GitHub API headers and a fixed eJournal user agent. Accept only a published, non-draft, non-prerelease tag exactly matching `vMAJOR.MINOR.PATCH`; compare the three validated integer components without a semver dependency.
6. Treat equal/older versions as current and malformed metadata as failure. Do not add a manifest, token, prerelease channel, ETag cache, HTML scraper, background timer, or analytics.

### UI

1. Add an app-wide non-modal banner to the existing `AppShell`; it survives navigation and offers release notes, `View release`, and Later in the compliance release.
2. Add one Updates card to `SettingsScreen` with current version, independent automatic-check toggle, Check now, last-check time, and current result.
3. Later hides the current banner; the same release may be offered after the next eligible check. No version is permanently skipped.

Tests cover strict version parsing/comparison, GitHub success and malformed/error responses, default-on persistence, 24-hour throttling, manual bypass, dismissal, silent automatic failures, and portable/unsigned capability boundaries. First-run ordering, the UI confirmation/decline path, and in-flight cancellation still require the Windows smoke check in Phase 6.

Checkpoint: the unsigned Windows release can detect a newer stable release and open GitHub, but contains no reachable MSI download or installer-launch path.

## Phase 5 — Make the Windows artifact and public record reviewable

1. Generate a deterministic, sorted `licenses/windows-runtime-components.txt` from the exact resolved Windows runtime coordinates and packaged runtime/JBR inputs. Check it in.
2. Add/update `THIRD_PARTY_NOTICES.md` with eJournal's MIT notice plus every attribution or bundled license required by those components. Review JARs, native libraries, JBR modules, and fonts; an unknown or non-redistributable item blocks release.
3. Add a small Gradle/CI verification step—no plugin—that rebuilds the inventory, fails on drift, compares it with packaged contents, and rejects filenames/classes matching Moomoo SDK, protobuf schemas/generated artifacts, or an OpenD executable.
4. Update `PRIVACY.md` with the default-off Yahoo/Alpaca behavior, transferred symbols/date ranges, manual one-shot consent, cancellation/setting behavior, GitHub update requests, stored preferences, and provider-policy links.
5. Update `CODE_SIGNING_POLICY.md` from the old bundled-SDK condition to the independently implemented adapter boundary. Keep the required SignPath attribution, roles, identity, scope, privacy, and release process accurate.
6. Update `README.md` with Windows-only downloads, OpenD as a separate prerequisite, the SDK-free implementation statement, online market-data opt-in, update-check behavior, uninstall instructions, and links to the privacy and code-signing policies.
7. Ensure every release body includes a visible **Code signing policy** link, even before signing is enabled.

Checkpoint: a clean checkout can reproduce the Windows package and its inventory; the final MSI/ZIP inspection contains no forbidden Moomoo material and every bundled component has a reviewed status.

## Phase 6 — Publish the compliant unsigned release, clean history, and apply

1. Run targeted tests while editing, then `./gradlew :shared:jvmTest`, `./gradlew build`, the Windows packaging/inventory checks, a clean MSI install, portable launch, application restart, and uninstall/data-preservation smoke checks.
2. Tag and publish the compliant unsigned Windows release. Its notes must state that Windows has not yet been Authenticode-signed, that updates open GitHub, and that OpenD is separate.
3. Verify the public tag maps to the source used by Actions, fixed asset names are present, GitHub exposes asset digests, policy/privacy links work, and the MSI uninstalls through normal Windows mechanisms.
4. Remove only MSI/ZIP binary assets from releases `v0.2.0` through `v0.5.1`, which contain the Moomoo runtime dependency. Preserve their tags, source archives, release records, and changelogs. Add a short note to each affected release pointing users to the current compliant release.
5. Update `.scratch/signpath-application/apply-signpath.sh` immediately before applying: refresh repository metrics/facts, replace stale SDK wording, use the compliant release URL, and disclose that the MIT JSON adapter is independently written, limited/read-only, contains no SDK/schema/generated/OpenD binaries, and requires separately installed OpenD.
6. Apply to SignPath immediately after the preceding public checks. Do not wait for another Moomoo answer.

If SignPath says the direct OpenD protocol itself makes the project ineligible, remove direct OpenD sync from the signed application, retain Moomoo CSV import, publish that boundary, and reapply. If rejection is solely discretionary/reputation-based, remain unsigned, keep check-and-View-release behavior, and reapply after greater adoption.

Checkpoint: the application points to a reproducible SDK-free Windows release, and no downloadable historical eJournal binary still contains the Moomoo SDK.

## Phase 7 — Add SignPath signing and enable installer updates

This phase begins only after SignPath accepts the project and the required organization/project/policy are configured.

### Trusted release pipeline

1. Configure SignPath's GitHub trusted build integration, project, artifact configuration, signing policy, API token secret, MFA, role assignments, and manual approval. Use current official actions and pin third-party actions to reviewed immutable revisions where practical.
2. Define one staged bundle that signs the outer MSI and eJournal-owned launcher inside it, plus the eJournal-owned launcher in the portable ZIP. Exclude upstream runtime executables and constrain expected product metadata.
3. In `.github/workflows/release-windows.yml`:
   1. derive the numeric version strictly from the stable tag;
   2. build unsigned MSI and portable artifacts with correct identities and installer capability enabled only for the MSI;
   3. run tests, inventory, and forbidden-artifact checks;
   4. upload the unsigned bundle as an Actions artifact;
   5. submit it through the official SignPath action and wait up to 30 minutes for manual approval;
   6. retrieve the signed result and verify signatures and metadata on Windows;
   7. clean-install it, then install the previous stable MSI and upgrade to the candidate under the same upgrade identity; and
   8. publish fixed-name assets only after all checks pass.
4. A timeout, rejection, or verification failure fails the workflow and creates no partial public release. Split signing and publication only if approval timeouts repeatedly prove that necessary.

### Fail-closed MSI update path

1. Extend the generated build identity with an installer-update capability that is true only for the SignPath-signed MSI build and false for portable/development builds. Extend the existing update state/actions with download progress, cancellation, verification, and install readiness only behind that capability.
2. Require exactly one `eJournal-windows.msi` asset with HTTPS URL, positive size, and GitHub `sha256:` digest before offering download.
3. Download only after the user clicks **Download update**. Stream to an updater-specific OS temp directory while calculating SHA-256 with JDK `MessageDigest`; enforce declared size and compare the final digest in constant time.
4. On cancellation, transport error, size/digest mismatch, or signature/identity failure, delete the partial file and never enter install-ready state. Remove stale updater files on next launch.
5. Verify with non-interactive built-in Windows tooling:
   - Authenticode status is valid and Windows-trusted;
   - signer subject exactly matches the SignPath-approved publisher;
   - MSI `ProductName` is `eJournal`;
   - `ProductVersion` matches the release tag; and
   - `UpgradeCode` is `a3f1c9e2-7b4d-4e8a-9c1f-2d6b8e0a4f57`.
6. Pass paths as process arguments/environment values, never interpolated script source. Pin the publisher subject, not a certificate thumbprint, so normal renewal remains possible.
7. Show progress and Cancel across navigation. After verification, show **Install and close** only when `BackgroundTaskTracker` is idle. Confirmation defaults to Cancel and warns about unsaved Settings edits.
8. Start visible `msiexec.exe /i <verified-path>` with `ProcessBuilder`; exit through the existing application callback only after process creation succeeds. If launch fails, keep eJournal open and show the error. Do not silently install, relaunch, cache permanently, resume partial downloads, or add rollback/bootstrapper machinery.

Tests cover streaming progress/cancellation, size and digest enforcement, temp cleanup, Windows-verifier result parsing, wrong signer/product/version/upgrade code, active-task gating, and launch failure. The workflow exercises the real verifier and previous-version upgrade against the signed candidate.

Checkpoint: the first signed release is installed manually; later signed MSI releases can be downloaded and launched from eJournal only after user consent and all verifications pass.

## Completion criteria

- The active release workflow and public docs are Windows-only; dormant Linux/macOS packaging is easy to restore but makes no support claim.
- No resolved or packaged artifact contains the Moomoo SDK, copied SDK material, protobuf schemas/generated classes, or OpenD.
- Read-only OpenD import still handles account selection, historical orders, executions, pagination, and exact fees on Windows.
- No automatic external request occurs before first-run Continue. Yahoo/Alpaca automatic requests are default-off and centrally enforced; update checks remain a separate default-on choice.
- Local eTape works with online market data disabled, and a confirmed manual external sync neither enables nor persists the global setting.
- The unsigned compliance release can check and notify but cannot download/install; portable builds always remain check-and-open only.
- The packaged Windows component inventory and notices are exact, reproducible, and checked in CI.
- Historical SDK-bearing binary assets are gone while tags, source archives, and release history remain.
- SignPath receives a candid application referencing the compliant public release.
- After acceptance, only signed, digest-bearing, identity-verified MSI assets can reach the in-app installation action or public release.

## Out of scope

- A reusable OpenD/Moomoo SDK, protobuf compatibility layer, remote OpenD access, encryption expansion, push subscriptions, or trading commands.
- Linux/macOS releases or update flows until their archived plan is deliberately restored and revalidated.
- Separate online/offline application editions or removal of automatic update checks.
- Prerelease channels, staged rollout, mandatory updates, minimum-version switches, analytics, telemetry, mirrors, custom update servers, or a second update manifest.
- Background update downloads, silent installs, automatic relaunch, portable-to-MSI migration, delta/resumable downloads, certificate-thumbprint pinning, or custom rollback.
- A dependency-license/SBOM plugin; the checked-in inventory and small drift/forbidden-artifact checks are sufficient for this release.

## Supporting evidence

- [OpenD adapter and SignPath feasibility research](../research/opend-sdk-signpath-feasibility.md)
- [SignPath pre-application audit](../research/signpath-preapplication-audit.md)
- [ADR 0005: Use SignPath for Windows release signing](../adr/0005-use-signpath-for-windows-release-signing.md)
- [Code-signing policy](../../CODE_SIGNING_POLICY.md)
- [Privacy policy](../../PRIVACY.md)
