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
            packageName = "io.earlisreal.ejournal"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgs("--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED")
}