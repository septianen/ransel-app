// Minimal module so the Gradle project is complete. The backend agent owns everything in server/.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
}

application {
    mainClass.set("app.ransel.server.ApplicationKt")
}
