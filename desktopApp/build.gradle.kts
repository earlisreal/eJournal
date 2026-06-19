import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // Silences the SLF4J "no binding" startup notice from the SQLite JDBC driver (logs are discarded).
    runtimeOnly(libs.slf4j.nop)
}

compose.desktop {
    application {
        mainClass = "io.earlisreal.ejournal.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // JFXPanel needs access to internal JavaFX initialization APIs on JDK 17+.
        jvmArgs += "--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // jpackage's auto-detected jlink runtime is too minimal — it omits java.sql
            // (SQLDelight JDBC), jdk.unsupported (sun.misc.Unsafe via coroutines/skiko) and
            // jdk.jsobject (JavaFX WebView's JS bridge), causing "Failed to launch JVM" at startup.
            // Bundle every JDK module for correctness; trim to an explicit modules(...) list later if size matters.
            includeAllModules = true
            packageName = "eJournal"
            packageVersion = "1.0.1"
            description = "Trading journal — import broker CSVs, track closed positions and analytics."
            vendor = "earlisreal"

            windows {
                menuGroup = "eJournal"
                shortcut = true
                // Stable identity so future MSIs upgrade in place instead of installing side-by-side.
                // Generated once; do not change between releases.
                upgradeUuid = "a3f1c9e2-7b4d-4e8a-9c1f-2d6b8e0a4f57"
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs("--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED")
}