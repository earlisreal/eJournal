# SignPath pre-application audit

Date: 2026-09-20

Scope: current eJournal repository and public releases, the current SignPath Foundation conditions and GitHub trusted-build documentation, and Phillip's reply supplied by the maintainer. This is a readiness audit, not legal advice.

## Bottom line

Do **not** submit the application yet. Beyond the already agreed Moomoo SDK replacement, Windows-only release focus, and default-off external market-data consent, eJournal should first publish a new compliant, unsigned Windows release and close the documentation/license-evidence gaps below. SignPath requires a project to be "already released in the form that should be signed," and Phillip explicitly asked that the SDK and privacy changes be made before applying ([SignPath conditions](https://signpath.org/terms.html#conditions-for-free-oss-signpathio-subscriptions)).

The current latest release, [`v0.5.1`](https://github.com/earlisreal/eJournal/releases/tag/v0.5.1), is not adequate as that evidence:

- Its tagged source includes `implementation(libs.moomoo.api)` ([tagged build file](https://github.com/earlisreal/eJournal/blob/v0.5.1/shared/build.gradle.kts#L31-L35)), so both Windows artifacts likely contain the restricted JAR. The current resolved package also contains `moomoo-api-10.8.6808-*.jar`.
- Its release notes contain no **Code signing policy** heading or link, although SignPath requires that term on the home page and download/release pages ([SignPath conditions](https://signpath.org/terms.html#conditions-for-the-website--repository)).
- The current privacy policy says Yahoo requests occur automatically with no separate switch ([`PRIVACY.md:15`](../../PRIVACY.md#L15), [`PRIVACY.md:27`](../../PRIVACY.md#L27)).

## Blockers before applying

### 1. Release the corrected Windows package first

After replacing the Moomoo SDK and implementing consent, publish a new **unsigned, Windows-only** release from the public GitHub Actions workflow. Test the exact MSI and portable ZIP that users can download. This satisfies the "released in the form that should be signed" condition with the artifact SignPath is actually being asked to approve, rather than with the non-compliant `v0.5.1` artifacts.

The new release page should:

- identify both artifacts as unsigned pending SignPath acceptance;
- contain a **Code signing policy** link;
- link the privacy policy and Windows install/uninstall instructions;
- describe the OpenD requirement and external-network consent accurately.

The repository already documents Windows uninstallation ([`README.md:92-101`](../../README.md#L92-L101)) and has a policy with the required attribution and solo-project roles ([`CODE_SIGNING_POLICY.md:1-35`](../../CODE_SIGNING_POLICY.md#L1-L35)). Those parts do not need redesign.

### 2. Resolve old downloadable SDK-containing artifacts

SignPath's published conditions do not expressly say that historical unsigned releases must be deleted. However, merely superseding `v0.5.1` leaves a non-OSI component downloadable as part of the project, while SignPath says the project may not contain proprietary/non-open-source components ([SignPath conditions](https://signpath.org/terms.html#conditions-for-free-oss-signpathio-subscriptions)). Redistribution permission for the old binaries is also not established in the repository.

Safest pre-application action: remove the **binary assets** from every historical release that bundled `moomoo-api`, while preserving tags, source, release records, and changelogs. If the maintainer wants to keep those assets, obtain written confirmation from both Moomoo (redistribution) and SignPath (Foundation eligibility). A "superseded" label alone does not resolve the missing evidence.

### 3. Make every automatic network request consent-safe

The market-data switch must gate Yahoo and Alpaca market-data traffic before any startup/import-triggered request. The first-run flow must display the privacy policy, explain what data is sent, and leave external market data off until the user explicitly opts in. Add a regression check proving that startup and import cause no Yahoo/Alpaca market-data request while disabled. This implements both Phillip's reply and SignPath's installation disclosure/disablement condition ([SignPath conditions](https://signpath.org/terms.html#conditions-for-end-user-interactions)).

The planned default-on GitHub update check is a separate automatic transfer. Keep its separate setting, but show it in the same first-run disclosure and do not perform the first check until the user has had an opportunity to disable it. Update `PRIVACY.md` to describe the GitHub request and its data (including ordinary network metadata and application version/user agent). Otherwise the updater recreates the privacy timing problem just fixed for market data.

### 4. Align all public documentation with the shipped Windows-only state

Disabling the Linux/macOS jobs is easy to reverse later, but the public documentation must stop promising downloads that are not published. The latest release currently contains only Windows assets, while [`README.md:64-90`](../../README.md#L64-L90) advertises Ubuntu and macOS packages, and the `v0.5.1` notes contain installation instructions for assets that were not published.

Before applying:

- update the Download section to Windows-only;
- remove/mark deferred the Linux/macOS release claims and commands;
- remove the obsolete Moomoo redistribution gate text and SDK-version statement ([`README.md:60-62`](../../README.md#L60-L62), [`README.md:115-124`](../../README.md#L115-L124));
- update `CODE_SIGNING_POLICY.md` so it describes the SDK-free OpenD adapter instead of seeking approval for a bundled SDK ([`CODE_SIGNING_POLICY.md:7-12`](../../CODE_SIGNING_POLICY.md#L7-L12));
- ensure the release workflow always adds the Code signing policy link to release notes.

### 5. Complete and preserve a packaged-component license inventory

The project itself is MIT-licensed ([`LICENSE:1-20`](../../LICENSE#L1-L20)), which is OSI-approved. The resolved Windows runtime shows the expected open-source families (Kotlin/Compose/AndroidX, Ktor, kotlinx, SQLDelight/sqlite-jdbc, FileKit/JNA/dbus-java, SLF4J, wickplot, Skiko, and the bundled JetBrains Runtime). No second clearly proprietary runtime dependency was found during this pass, and Moomoo's protobuf/Bouncy Castle transitives should disappear with `moomoo-api`.

That is **not** a complete clearance. Before the compliant release:

- regenerate the resolved Windows runtime inventory after removing Moomoo;
- record the exact license for every packaged JAR, native library, bundled JBR component, and font;
- verify every license against the current [OSI-approved list](https://opensource.org/licenses);
- preserve and bundle all required license/notice texts, including eJournal's MIT license and `licenses/JetBrainsMono-OFL.txt`;
- treat SQLite's bundled native/public-domain material and any component with incomplete metadata as unresolved until documented or confirmed with SignPath.

The need is visible in the build: direct dependencies are declared at [`gradle/libs.versions.toml:27-51`](../../gradle/libs.versions.toml#L27-L51), while packaging currently copies generated app resources and only an optional Moomoo notice ([`desktopApp/build.gradle.kts:34-50`](../../desktopApp/build.gradle.kts#L34-L50)). The repository has no complete checked-in third-party notice/inventory. Do not claim that all components are cleared until this follow-up is complete.

### 6. Disclose the independent OpenD-client boundary

The replacement should be independently written under eJournal's MIT license, implement only the documented read-only calls, and package no Moomoo SDK, generated Moomoo classes, copied SDK source, official schema files with unclear terms, or OpenD binaries. OpenD remains a separate user-installed application. Describe that exact boundary in the application so SignPath can decide it; Phillip's prior reply clears only the removal/replacement requirement, not every possible implementation of a replacement.

## Already satisfied or evidenced

- Public repository, active maintenance, and existing Windows releases are visible on [GitHub](https://github.com/earlisreal/eJournal).
- The README describes product functionality in detail ([`README.md:1-58`](../../README.md#L1-L58)).
- The home/download page uses the term **Code signing policy**, contains the required SignPath attribution, and links both policy and privacy documents ([`README.md:92-95`](../../README.md#L92-L95)).
- The policy names the solo author/reviewer/approver and requires MFA ([`CODE_SIGNING_POLICY.md:25-31`](../../CODE_SIGNING_POLICY.md#L25-L31)). SignPath's published rules require roles, but do not state that a solo project must assign different people.
- The Windows build runs on a GitHub-hosted runner and derives its version from the public tag ([`.github/workflows/release-windows.yml:22-74`](../../.github/workflows/release-windows.yml#L22-L74)).
- Current package configuration supplies `eJournal` product name and one `appVersion` to the package/launcher ([`desktopApp/build.gradle.kts:24-27`](../../desktopApp/build.gradle.kts#L24-L27), [`desktopApp/build.gradle.kts:122-147`](../../desktopApp/build.gradle.kts#L122-L147)); SignPath must still enforce those values in its artifact configuration.

## Post-acceptance setup (not a reason to delay the application once blockers close)

1. Enable MFA for every GitHub account with source access now; enable SignPath MFA when the account is created ([SignPath conditions](https://signpath.org/terms.html#conditions-for-oss-contributors)).
2. In SignPath, create/link the project and repository URL, add the predefined GitHub.com trusted build system, and install the SignPath GitHub App if audit-log/policy evaluation requires it ([GitHub trusted build system](https://docs.signpath.io/trusted-build-systems/github#prerequisites)).
3. Define submitter and manual approver permissions; every release must receive manual signing approval.
4. Create an artifact configuration that signs only the outer MSI and eJournal-owned launcher(s), excludes upstream runtime binaries from eJournal signing, and enforces `eJournal` product name plus one tag-derived version.
5. Store the SignPath API token as a GitHub Actions secret. Upload the unsigned bundle with `actions/upload-artifact` and pass its artifact ID to `signpath/github-action-submit-signing-request@v3`; SignPath requires the artifact to be stored by GitHub before submission and all preceding OSS jobs to use GitHub-hosted runners ([GitHub integration checks and usage](https://docs.signpath.io/trusted-build-systems/github#checks-performed-by-signpath)).
6. Replace direct unsigned publication at [`.github/workflows/release-windows.yml:96-120`](../../.github/workflows/release-windows.yml#L96-L120) with sign, wait for approval, download, verify, smoke-test, then publish. A rejected, failed, or timed-out request must publish nothing.
7. Configure origin verification, repository URL, and allowed release refs/branches in the SignPath project/signing policy ([SignPath projects](https://docs.signpath.io/projects#origin-verification-restriction)).

## Application posture

Apply after the new compliant unsigned release is public and the old binary-assets decision is resolved. State the project's modest reputation factually; acceptance remains discretionary because SignPath says executable projects require verifiable reputation and it alone decides eligibility ([SignPath conditions](https://signpath.org/terms.html#common-misunderstandings)). Include the compliant release URL, public workflow URL, code-signing policy, privacy policy, Windows uninstall instructions, resolved dependency-license inventory, and Phillip's email thread.
