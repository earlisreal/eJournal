---
status: proposed
---

# Use SignPath for Windows release signing

eJournal will use SignPath Foundation to Authenticode-sign its Windows MSI and eJournal-owned launchers before publishing a GitHub Release, accepting manual approval and SignPath's publisher identity in exchange for public-trust signing without operating a private signing key. The updater will require GitHub's asset digest, valid Windows trust, the expected publisher, and matching MSI identity; changing publishers therefore requires a transition release or browser installation. This decision becomes accepted only if SignPath accepts the project with eJournal's independently implemented, SDK-free OpenD adapter and separately installed OpenD runtime; if that direct integration is ineligible, eJournal will remove it from the signed application and retain Moomoo CSV import.
