plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

/*
 * Compose Resources shows `\'` and `\"` as written (unlike Android resources), so an apostrophe in strings.xml is a
 * plain '. Runs before composeApp and adminApp convert their strings.
 */
val checkStringEscapes by tasks.registering {
    val files = fileTree(rootDir) {
        include("composeApp/src/*/composeResources/values*/strings.xml", "adminApp/src/*/composeResources/values*/strings.xml")
    }
    val root = rootDir
    inputs.files(files)
    doLast {
        val bad = files.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> "\\'" in line || "\\\"" in line }
                .map { (i, _) -> "${file.relativeTo(root)}:${i + 1}" }
        }
        check(bad.isEmpty()) { "Write ' and \" without a backslash in Compose Resources strings:\n" + bad.joinToString("\n") }
    }
}
