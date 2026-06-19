import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Bundle the JetBrains Runtime (JBR) in the packaged app. The Compose plugin sources jpackage/jlink
// from the JVM running Gradle — NOT the Kotlin toolchain — so we resolve a JBR 25 toolchain here and
// point Compose's javaHome at it. This makes the shipped runtime JBR regardless of how Gradle launches.
val jbrLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.JETBRAINS)
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
        javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // JFXPanel needs access to internal JavaFX initialization APIs on JDK 17+.
        jvmArgs += "--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // jpackage's default jlink runtime omits modules our deps need at startup — java.sql
            // (SQLDelight JDBC), jdk.unsupported (sun.misc.Unsafe via coroutines/skiko) and
            // jdk.jsobject (JavaFX WebView's JS bridge) — which caused "Failed to launch JVM".
            // This explicit set comes from `./gradlew :desktopApp:suggestRuntimeModules` (jdeps);
            // re-run that and update this list if dependencies change. Far smaller than includeAllModules.
            modules(
                "java.instrument", "java.management", "java.net.http", "java.prefs", "java.sql",
                "jdk.jfr", "jdk.jsobject", "jdk.unsupported", "jdk.unsupported.desktop", "jdk.xml.dom",
            )
            packageName = "eJournal"
            packageVersion = "1.0.2"
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