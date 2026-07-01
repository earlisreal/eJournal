import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import javax.imageio.ImageIO
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.language.jvm.tasks.ProcessResources
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

// Installer version. CI derives it from the release tag (vX.Y.Z -> X.Y.Z) and passes -PappVersion;
// local/dev builds fall back to a valid numeric default. Must stay numeric major.minor.patch — MSI
// rejects suffixes like -beta.
val appVersion: String = (project.findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0"

// Bake the version into the splash at build time so it shows together with the image — no runtime
// pop-in (drawing on the live splash from main() pays a one-time AWT/font-init cost on launch).
// Copies resources/ into a generated appResources root, then renders "v<appVersion>" onto
// common/splash.png. Up-to-date unless appVersion or the source resources change, so day-to-day dev
// builds skip it. Inputs/outputs are captured at configuration time to stay config-cache-safe.
val versionedAppResources = layout.buildDirectory.dir("generated/appResources")
val generateVersionedSplash by tasks.registering {
    val sourceResources = layout.projectDirectory.dir("resources").asFile
    val outputDir = versionedAppResources
    val version = appVersion
    inputs.dir(sourceResources)
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        sourceResources.copyRecursively(out, overwrite = true)
        out.walkTopDown().filter { it.name == ".DS_Store" }.forEach { it.delete() }

        val splash = out.resolve("common/splash.png")
        val image = ImageIO.read(splash)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = Font("SansSerif", Font.PLAIN, 13)
        g.color = Color(60, 64, 84, 200) // dark navy matching the logo, slightly translucent
        val label = "v$version"
        val metrics = g.fontMetrics
        // Bottom-right, clear of the centered logo.
        g.drawString(label, image.width - metrics.stringWidth(label) - 16, image.height - 16)
        g.dispose()
        ImageIO.write(image, "png", splash)
    }
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // Provides a no-op SLF4J binding so the SQLite JDBC driver's logging is discarded silently.
    // Version must match the slf4j-api forced onto the classpath (2.x) — see libs.versions.toml.
    runtimeOnly(libs.slf4j.nop)
}

compose.desktop {
    application {
        mainClass = "io.earlisreal.ejournal.MainKt"
        javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
        // Silences the JDK 24+ restricted-native-access warning from native-lib loaders calling
        // System::load from the unnamed module — Skiko (Compose rendering) and JNA (FileKit's native
        // file picker).
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // Dropped --sun-misc-unsafe-memory-access=allow with JavaFX: its Marlin renderer was the only
        // caller of the deprecated sun.misc.Unsafe *memory-access* methods this flag gates (Skiko and
        // JNA are clean; coroutines' Unsafe use is objectFieldOffset, a different deprecation). On JDK
        // 25 those methods still work in the default "warn" mode, so re-add this only if a
        // "sun.misc.Unsafe::<memory method>" warning reappears in the logs.
        // Favor fast startup over peak JIT throughput — good for a desktop app. Validated on JBR 25.
        jvmArgs += "-XX:TieredStopAtLevel=2"
        // Paint a splash from JVM boot. $APPDIR is substituted by the jpackage launcher; splash.png
        // is placed in $APPDIR/resources/ via appResourcesRootDir below. Auto-closes when the first window shows.
        jvmArgs += "-splash:${'$'}APPDIR/resources/splash.png"
        // Serial GC avoids parallel-GC thread pool spin-up on this small-heap desktop app.
        // Pre-sized heap prevents early resize pauses. Validated on JBR 25.
        jvmArgs += "-XX:+UseSerialGC"
        jvmArgs += "-Xms128m"
        jvmArgs += "-Xmx512m"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Version-stamped resources (see generateVersionedSplash). Mapping the property off the
            // task provider carries the task dependency into Compose's prepareAppResources, so the
            // versioned splash is generated before it's packaged. $APPDIR/resources/splash.png (the
            // -splash: target above) resolves to this common/splash.png.
            appResourcesRootDir.set(layout.dir(generateVersionedSplash.map { versionedAppResources.get().asFile }))
            // jpackage's default jlink runtime omits modules our deps need at startup — java.sql
            // (SQLDelight JDBC) and jdk.unsupported (sun.misc.Unsafe via coroutines/skiko) — without
            // which the packaged app fails with "Failed to launch JVM". Derived from
            // `./gradlew :desktopApp:suggestRuntimeModules` (jdeps). jdk.jsobject is a leftover from the
            // now-removed JavaFX-WebView/JCEF charts and is likely droppable — re-run
            // suggestRuntimeModules to confirm before removing it.
            modules(
                "java.instrument", "java.management", "java.net.http", "java.prefs", "java.sql",
                "jdk.jfr", "jdk.jsobject", "jdk.unsupported", "jdk.unsupported.desktop", "jdk.xml.dom",
            )
            packageName = "eJournal"
            packageVersion = appVersion
            description = "Trading journal — import broker CSVs, track closed positions and analytics."
            vendor = "earlisreal"

            // App icons live in desktopApp/icons/ (generated from icons/icon-master.png — the 1024²
            // eJ monogram). Each platform takes its own container format.
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "eJournal"
                shortcut = true
                // Stable identity so future MSIs upgrade in place instead of installing side-by-side.
                // Generated once; do not change between releases.
                upgradeUuid = "a3f1c9e2-7b4d-4e8a-9c1f-2d6b8e0a4f57"
            }
        }
    }
}

// The runtime window icon (loaded via painterResource("icon.png") in main.kt) reuses the single
// source of truth in icons/ — the same PNG jpackage ships for Linux — instead of a checked-in copy
// under src/main/resources. Copying just icon.png onto the classpath root keeps the 1024² master and
// the .icns/.ico container formats off the runtime classpath.
tasks.named<ProcessResources>("processResources") {
    from("icons/icon.png")
}

// Applies to `run`, `hotRun` (Compose Hot Reload's run task extends JavaExec) and other JavaExec tasks,
// so dev runs match the packaged app's JVM args above.
tasks.withType<JavaExec>().configureEach {
    dependsOn(generateVersionedSplash)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs("-XX:TieredStopAtLevel=2")
    jvmArgs("-splash:${versionedAppResources.get().asFile}/common/splash.png")
    // Match packaged app heap/GC flags — serial GC avoids thread pool spin-up; pre-sized heap
    // prevents early resize pauses. Validated on JBR 25.
    jvmArgs("-XX:+UseSerialGC")
    jvmArgs("-Xms128m")
    jvmArgs("-Xmx512m")
}
