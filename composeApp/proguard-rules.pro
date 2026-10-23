# R8 rules for the release build. Most libraries (kotlinx.serialization, Ktor, Room 3, Koin, Coil, Firebase) ship their
# own consumer rules; only add what a release build or a release smoke test proves is missing.

# Crashlytics: keep file names and line numbers in stack traces (the mapping file restores class and method names).
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
