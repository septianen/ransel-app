import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * IRIS design system for Compose: theme (colors, type, shapes, spacing), Material Symbols and the bundled fonts.
 * Shared by the traveller app (composeApp: Android, iOS, web) and the admin app (adminApp: web), so the tokens
 * live in one place. Packages stay `app.ransel.ui.theme`, `app.ransel.ui.symbols`, `app.ransel.ui.preview`.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "app.ransel.iris.generated.resources"
    publicResClass = false
}

android {
    namespace = "app.ransel.iris"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
