plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Standalone, distributable candlestick chart drawn natively on a Compose Canvas. Depends only on
// Compose (runtime/foundation/ui) and kotlinx-datetime — no app domain, theme, DB, or network. Kept
// as its own Gradle module so it can later be published as a Compose Multiplatform library.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // Skia (skiko) + desktop Compose graphics for the offscreen PNG screenshot harness.
            implementation(compose.desktop.currentOs)
        }
    }
}
