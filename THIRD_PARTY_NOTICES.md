# Third-party notices

The Windows MSI and portable archive are assembled from the coordinates listed in
[`licenses/windows-runtime-components.txt`](./licenses/windows-runtime-components.txt). No Moomoo SDK,
protobuf runtime/schema, OpenD executable, or other Moomoo binary is included.

eJournal itself is MIT-licensed; see [`LICENSE`](./LICENSE). The bundled runtime and libraries remain
under their own terms. The following notices cover the components intentionally shipped in the
Windows package; the inventory file is the review boundary for release checks.

| Component | License / notice |
| --- | --- |
| Kotlin, Kotlinx Coroutines, Kotlinx Serialization, Kotlinx Datetime, Kotlinx I/O | Apache License 2.0; https://www.apache.org/licenses/LICENSE-2.0 |
| Compose Multiplatform, AndroidX, JetBrains Skiko, JetBrains Runtime | Apache 2.0 for libraries; JBR redistribution terms are supplied with the JBR distribution |
| dbus-java | Apache License 2.0; https://github.com/hypfvieh/dbus-java |
| Ktor client | Apache License 2.0; https://www.apache.org/licenses/LICENSE-2.0 |
| SQLDelight and SQLite JDBC | Apache License 2.0 for SQLDelight; SQLite is public domain; https://www.sqlite.org/copyright.html |
| FileKit, JNA, and JNA Platform | FileKit Apache 2.0 (https://github.com/vinceglb/file-kit); JNA Apache 2.0 / LGPL 2.1 with classpath exception (https://github.com/java-native-access/jna) |
| JSpecify | Apache License 2.0; https://github.com/jspecify/jspecify |
| SLF4J | MIT; https://www.slf4j.org/license.html |
| Wickplot | MIT; see the upstream project at https://github.com/earlisreal/wickplot |
| JetBrains Mono | SIL Open Font License 1.1; see [`licenses/JetBrainsMono-OFL.txt`](./licenses/JetBrainsMono-OFL.txt) |

The inventory is the authoritative per-coordinate review list. Prefixes map to the grouped notices
above: `org.jetbrains.*`, `org.jetbrains.kotlin*`, and `org.jetbrains.compose*` are JetBrains/Kotlin;
`androidx.*` is AndroidX; `io.ktor:*` is Ktor; `app.cash.sqldelight:*` is SQLDelight; `org.xerial:*`
is SQLite JDBC; `net.java.dev.jna:*` is JNA; `io.github.vinceglb:*` is FileKit; and
`com.github.hypfvieh:*` is dbus-java; `org.jspecify:*` is JSpecify; and `io.github.earlisreal:wickplot-*`
is Wickplot. The packaged JBR `runtime/legal/` directory remains in
the Windows app image and carries the JBR and bundled JDK-component notices. Any new runtime
coordinate or packaged native/font input must be added to the inventory and mapped here before it is
eligible for a public or signed release.

The release workflow fails if the packaged Windows app or extracted MSI contains filenames/classes
associated with the removed Moomoo SDK, protobuf artifacts, or an OpenD executable.
