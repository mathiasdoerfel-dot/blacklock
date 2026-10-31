# ProGuard / R8 Rules for BlackBox

# Keep all class/method names in plaintext (no obfuscation) for Play Protect safety
-dontobfuscate

# AndroidX & Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class androidx.work.impl.WorkManagerImpl { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
-dontwarn okio.**
-dontwarn okhttp3.**

# Keep our data classes and manifest models
-keepclassmembers class de.matze.blackbox.tools.mediacleaner.TrashItem { *; }
-keepclassmembers class de.matze.blackbox.tools.mediacleaner.PendingCleanFile { *; }
-keepclassmembers class de.matze.blackbox.tools.mediacleaner.WatchedFolderDef { *; }
-keepclassmembers class de.matze.blackbox.core.ChangelogRelease { *; }
