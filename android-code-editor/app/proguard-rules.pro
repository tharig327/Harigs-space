# Compose and AndroidX ship their own consumer rules; nothing app-specific is
# needed because the app has no reflection-based entry points.
-dontwarn org.jetbrains.annotations.**
