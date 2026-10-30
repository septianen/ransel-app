import java.util.Properties
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
            implementation(projects.iris)
            // receipt photos: image picker everywhere, camera and compression on Android/iOS
            implementation(libs.filekit.dialogs.compose)
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
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.androidx.activity.compose)
            // one coarse fix for "Gunakan lokasi saat ini"
            implementation(libs.play.services.location)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                // named time zones (a trip's "Asia/Tokyo") for kotlinx-datetime in the browser
                implementation(npm("@js-joda/timezone", "2.26.0"))
            }
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
        // CI passes -Pransel.versionCode=<run number> so each distributed build installs over the previous one.
        versionCode = (project.findProperty("ransel.versionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }

    // Release signing: keystore.properties in the repo root (git-ignored, see keystore.properties.example) or the
    // RANSEL_KEYSTORE_* environment variables on CI. Without either, assembleRelease builds an unsigned APK.
    val keystore = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun keystoreValue(key: String, envVar: String): String? =
        keystore.getProperty(key) ?: providers.environmentVariable(envVar).orNull
    val releaseStoreFile = keystoreValue("storeFile", "RANSEL_KEYSTORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = keystoreValue("storePassword", "RANSEL_KEYSTORE_PASSWORD")
                keyAlias = keystoreValue("keyAlias", "RANSEL_KEY_ALIAS")
                keyPassword = keystoreValue("keyPassword", "RANSEL_KEY_PASSWORD")
            }
        }
    }

    // Where the app finds the API (BuildConfig.API_ORIGIN): debug -> staging, release -> production. Override per build
    // type with -Pransel.apiOrigin.debug=... / -Pransel.apiOrigin.release=... (e.g. http://10.0.2.2:8080 for a local
    // server from the emulator; cleartext is allowed in debug only).
    fun apiOrigin(buildType: String, default: String): String =
        (project.findProperty("ransel.apiOrigin.$buildType") as String?) ?: default
    buildTypes {
        getByName("debug") {
            // Installs next to the release app as "RANSEL Dev" (src/debug/res).
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Analytics and Crashlytics collect from debug too; Firebase shows it as its own app (app.ransel.debug).
            buildConfigField("String", "API_ORIGIN", "\"${apiOrigin("debug", "https://ranselapi-dev.mietek.tech")}\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "API_ORIGIN", "\"${apiOrigin("release", "https://ranselapi.mietek.tech")}\"")
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Push (FCM), Analytics and Crashlytics need the Firebase project's google-services.json, which is per environment and
// not in git. Without it the app still builds and runs; push reports that it is not available and analytics is a no-op.
// The file must list both app.ransel and app.ransel.debug (or put one per build type in src/debug/ and src/release/).
// The Crashlytics plugin uploads the R8 mapping file of release builds, so stack traces are readable.
if (listOf("google-services.json", "src/debug/google-services.json", "src/release/google-services.json").any { file(it).exists() }) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// The web service worker (wasmJsMain/resources/sw.js) names its cache after the build, so each deploy starts a
// fresh cache and the changed sw.js makes browsers install the new worker. CI passes its commit as GITHUB_SHA.
tasks.named("wasmJsBrowserDistribution") {
    val buildId = providers.environmentVariable("GITHUB_SHA").map { it.take(12) }
        .orElse(providers.provider { System.currentTimeMillis().toString(36) })
    val outputDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    doLast {
        val sw = outputDir.get().file("sw.js").asFile
        sw.writeText(sw.readText().replace("__BUILD_ID__", buildId.get()))
    }
}
