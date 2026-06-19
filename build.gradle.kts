import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin> {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
            // JetBrains Runtime (JBR) is JetBrains' recommended JDK for Compose Multiplatform —
            // it carries desktop/rendering fixes. Resolved by the foojay toolchain resolver.
            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.JETBRAINS)
            }
        }
    }
}