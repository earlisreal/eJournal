---
status: proposed
---

# Use SignPath for Windows release signing

eJournal will use SignPath Foundation to Authenticode-sign its Windows MSI and eJournal-owned launchers before publishing a GitHub Release, accepting manual approval and SignPath's publisher identity in exchange for public-trust signing without operating a private signing key. The updater will require GitHub's asset digest, valid Windows trust, the expected publisher, and matching MSI identity; changing publishers therefore requires a transition release or browser installation. This decision becomes accepted only if SignPath confirms that the project remains eligible with the bundled, separately licensed Moomoo SDK.
