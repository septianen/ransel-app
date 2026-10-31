import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/*
 * Karung, RANSEL's admin web app (docs/specs/admin-spec.md). Web only (Wasm), online only, IRIS theme from `iris`.
 * Code lives in wasmJsMain (the only target); strings in commonMain/composeResources (values = English, values-in = Indonesian).
 * Dev server on 8082 (8080 is the API, 8081 the traveller web app).
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("adminApp")
        browser {
            commonWebpackConfig {
                outputFileName = "adminApp.js"
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer())
                    .copy(port = 8082)
            }
        }
        binaries.executable()
    }

    sourceSets {
        // Declared on commonMain so the Compose resources (Res) are generated; wasmJs is the only target.
        commonMain {
            dependencies {
                implementation(projects.shared)
                implementation(projects.iris)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "app.ransel.admin.generated.resources"
    publicResClass = false
}

tasks.matching { it.name.startsWith("convertXmlValueResources") }.configureEach { dependsOn(":checkStringEscapes") }
