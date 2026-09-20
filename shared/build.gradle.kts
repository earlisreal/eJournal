plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.wickplot)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
            implementation(libs.sqlite.jdbc)
            implementation(libs.ktor.client.cio)
            implementation(compose.desktop.currentOs)

            // Native file picker. FileKit opens the modern per-OS dialog (NSOpenPanel on macOS, the
            // Win32 COM IFileOpenDialog on Windows) via JNA — with the extension filter honored on
            // both. This replaced JavaFX FileChooser, letting the JavaFX dependency be dropped
            // entirely (no more org.openjfx jars, native classifiers, or FX-toolkit lifecycle).
            implementation(libs.filekit.dialogs)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("io.earlisreal.ejournal.data.database")
        }
    }
}
