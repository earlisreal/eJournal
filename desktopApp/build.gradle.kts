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

    // JCEF via jcefmaven — SPIKE ONLY (LWC-v5-on-Chromium experiment). Brings org.cef + a matching
    // CEF native bundle it downloads on first run. Used by the `jcef-test` launch mode only; the
    // production chart still renders on JavaFX WebView. Remove if the experiment is abandoned.
    implementation(libs.jcefmaven)
}

compose.desktop {
    application {
        mainClass = "io.earlisreal.ejournal.MainKt"
        javaHome = jbrLauncher.get().metadata.installationPath.asFile.absolutePath
        // Silences the JDK 24+ restricted-native-access warning from JavaFX's NativeLibLoader
        // (com.sun.glass.utils.NativeLibLoader calling System::load from the unnamed module).
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // JavaFX 21's Marlin renderer calls the terminally-deprecated sun.misc.Unsafe memory methods;
        // on JDK 25 that prints a 4-line warning on first paint. "allow" permits it without warning.
        jvmArgs += "--sun-misc-unsafe-memory-access=allow"
        // NOTE: no --add-exports for javafx.graphics — our JavaFX comes from org.openjfx jars on the
        // classpath (the unnamed module), where no export is needed. With JavaFX off the module path
        // that flag only printed "WARNING: Unknown module: javafx.graphics specified to --add-exports".
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
            // (SQLDelight JDBC), jdk.unsupported (sun.misc.Unsafe via coroutines/skiko) and
            // jdk.jsobject (JavaFX WebView's JS bridge) — which caused "Failed to launch JVM".
            // This explicit set comes from `./gradlew :desktopApp:suggestRuntimeModules` (jdeps);
            // re-run that and update this list if dependencies change. Far smaller than includeAllModules.
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
    jvmArgs("--sun-misc-unsafe-memory-access=allow")
    jvmArgs("-XX:TieredStopAtLevel=2")
    jvmArgs("-splash:${versionedAppResources.get().asFile}/common/splash.png")
    // Match packaged app heap/GC flags — serial GC avoids thread pool spin-up; pre-sized heap
    // prevents early resize pauses. Validated on JBR 25.
    jvmArgs("-XX:+UseSerialGC")
    jvmArgs("-Xms128m")
    jvmArgs("-Xmx512m")
}

// Dedicated launcher for the JCEF + Lightweight Charts v5 spike — kept separate from `run` so its
// JCEF-specific JVM flags never touch the production JavaFX path.
//   ./gradlew :desktopApp:runJcefTest
tasks.register<JavaExec>("runJcefTest") {
    group = "application"
    description = "Run the JCEF + Lightweight Charts v5 spike (jcef-test mode)"
    dependsOn("jar", generateVersionedSplash)
    mainClass.set("io.earlisreal.ejournal.MainKt")
    args("jcef-test")
    javaLauncher.set(jbrLauncher)
    classpath = sourceSets["main"].runtimeClasspath
    // jcefmaven needs deep reflection into AWT internals on JDK 16+.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
    // CRITICAL: suppress JBR's bundled `jcef` and `jogl.all` SYSTEM modules. They export org.cef /
    // org.jogamp unqualified and auto-resolve as roots, shadowing jcefmaven's classpath jars — so
    // org.cef.CefApp loads from the JBR module (no build_meta.json, mismatched natives → crash).
    // Limiting the observable module set (classpath jars are unaffected) forces the classpath stack.
    jvmArgs(
        "--limit-modules",
        listOf(
            "java.base", "java.desktop", "java.logging", "java.management", "java.naming",
            "java.net.http", "java.prefs", "java.sql", "java.xml", "java.datatransfer",
            "java.scripting", "java.instrument", "jdk.unsupported", "jdk.unsupported.desktop",
            "jdk.jfr", "jdk.jsobject", "jdk.xml.dom",
        ).joinToString(","),
    )
}
