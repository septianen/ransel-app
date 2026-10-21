import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

/*
 * Source sets (composeApp/CLAUDE.md):
 *   commonMain  -> UI, ViewModels, repository interfaces, API client
 *   mobileMain  -> androidMain + iosMain: Room 3, DAOs, local repositories, sync worker
 *   wasmJsMain  -> remote-only repositories, no Room
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
private val ranselHierarchy = KotlinHierarchyTemplate {
    withSourceSetTree(KotlinSourceSetTree.main, KotlinSourceSetTree.test)
    common {
        withCompilations { true }
        group("mobile") {
            withAndroidTarget()
            group("ios") { withIos() }
        }
        group("web") { withWasmJs() }
    }
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(ranselHierarchy)

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // No iosX64: Compose 1.12 and Room 3 do not publish the Intel simulator. Apple Silicon sim + devices only.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                // 8080 is the Ktor server; CORS_ORIGINS allows localhost:8081 (server/.env.example).
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer())
                    .copy(port = 8081)
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Room 3 lives only here (Android + iOS). Never reference it from commonMain or wasmJsMain.
        val mobileMain by getting {
            dependencies {
                implementation(libs.room3.runtime)
                implementation(libs.sqlite.bundled)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.espresso.core)
                implementation(libs.androidx.test.ext.junit)
            }
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val wasmJsMain by getting {
            dependencies { implementation(libs.ktor.client.js) }
        }
    }
}

dependencies {
    // Room 3 compiler, KSP only, per mobile target.
    add("kspAndroid", libs.room3.compiler)
    add("kspIosArm64", libs.room3.compiler)
    add("kspIosSimulatorArm64", libs.room3.compiler)
    // HTTP inspector (Android, debug builds only); the release build gets the no-op artifact with the same API.
    debugImplementation(libs.chucker)
    releaseImplementation(libs.chucker.no.op)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

compose.resources {
    packageOfResClass = "ransel.composeapp.generated.resources"
    publicResClass = false
}

android {
    namespace = "app.ransel"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.ransel"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    buildTypes.configureEach {
        // Where the app finds the API. 10.0.2.2 is the host machine seen from the Android emulator (gradle.properties sets the deployed API);
        // override with `-Pransel.apiOrigin=https://api.ransel.app` (or gradle.properties) for a device/release.
        val origin = (project.findProperty("ransel.apiOrigin") as String?) ?: "http://10.0.2.2:8080"
        buildConfigField("String", "API_ORIGIN", "\"$origin\"")
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Push (FCM) needs the Firebase project's google-services.json, which is per environment and not in git.
// Without it the app still builds and runs; PushRegistrar just reports that push is not available.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
