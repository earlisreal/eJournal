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
            implementation(libs.jcefmaven)

            // JavaFX WebView for chart rendering (replaces JCEF — far more reliable on macOS).
            // Full module list required: Maven won't resolve platform-classified transitive jars.
            val javafxOs = when {
                System.getProperty("os.name").lowercase().startsWith("mac") ->
                    if ("aarch64" in System.getProperty("os.arch")) "mac-aarch64" else "mac"
                System.getProperty("os.name").lowercase().startsWith("win") -> "win"
                else -> "linux"
            }
            val javafxVer = "21"
            implementation("org.openjfx:javafx-base:$javafxVer:$javafxOs")
            implementation("org.openjfx:javafx-graphics:$javafxVer:$javafxOs")
            implementation("org.openjfx:javafx-controls:$javafxVer:$javafxOs")
            implementation("org.openjfx:javafx-media:$javafxVer:$javafxOs")
            implementation("org.openjfx:javafx-web:$javafxVer:$javafxOs")
            implementation("org.openjfx:javafx-swing:$javafxVer:$javafxOs")
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
