# Code signing policy

Status: proposed. eJournal is applying for a free SignPath Foundation subscription. No release should be represented as SignPath-signed until the application is accepted and that release completes the signing workflow.

Upon acceptance: **Free code signing provided by [SignPath.io](https://about.signpath.io/), certificate by [SignPath Foundation](https://signpath.org/).**

## Scope

Official Windows releases are built from this repository by the public [GitHub Actions release workflow](https://github.com/earlisreal/eJournal/actions/workflows/release-windows.yml). The intended signing scope is the eJournal MSI and eJournal-owned launchers. Bundled third-party runtime files are not signed as eJournal binaries.

The signed Windows artifact contains no Moomoo OpenAPI SDK, protobuf schema, generated SDK code, or OpenD executable. The Moomoo integration is an independently implemented, read-only JSON client for a separately installed localhost OpenD service. OpenD itself is outside the signed distribution and remains the user's responsibility to install under Moomoo's terms.

## Intended signed-release process

After SignPath accepts eJournal and the signing integration is complete:

- A maintainer creates a version tag in the form `vMAJOR.MINOR.PATCH`.
- GitHub-hosted Actions builds the Windows artifacts from that tagged source and records a matching product version.
- Every signing request requires manual approval by an eJournal approver.
- The release workflow will publish artifacts only after signing and verification succeed. A rejected, failed, or timed-out request will publish nothing.
- Artifact restrictions must enforce the `eJournal` product name and the version derived from the tag.

Current releases must be treated as unsigned unless their release page explicitly states that the SignPath workflow completed successfully.

## Team roles

- Authors and committers: [@earlisreal](https://github.com/earlisreal)
- Reviewers of contributions from non-committers: [@earlisreal](https://github.com/earlisreal)
- Signing-request approver: [@earlisreal](https://github.com/earlisreal)

All team members with source or signing access must use multi-factor authentication for GitHub and SignPath.

## Privacy

See the [eJournal privacy policy](./PRIVACY.md), including its description of default-off Yahoo/Alpaca market-data requests, explicit one-shot confirmation, GitHub update checks, and optional broker integrations.
