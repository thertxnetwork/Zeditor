# Duplicate Classes Build Failure Fix

## Problem
The build was failing with duplicate class errors:
```
Duplicate class kotlinx.android.parcel.* found in modules:
- kotlin-android-extensions-runtime-1.3.72.jar (org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.3.72)
- kotlin-parcelize-runtime-2.2.21.jar (org.jetbrains.kotlin:kotlin-parcelize-runtime:2.2.21)
```

## Root Cause
One or more transitive dependencies were pulling in the old `kotlin-android-extensions-runtime:1.3.72` library, which conflicts with the newer `kotlin-parcelize-runtime:2.2.21` that the project uses. The `kotlin-android-extensions` plugin was deprecated in favor of `kotlin-parcelize`.

## Solution
Added a global exclusion rule in `build.gradle.kts` to exclude the old `kotlin-android-extensions-runtime` from all dependency configurations across all subprojects:

```kotlin
subprojects {
    // Exclude old kotlin-android-extensions-runtime to prevent duplicate classes
    configurations.all {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
}
```

## Additional Changes
1. **Repository Mirrors**: Added maven.aliyun.com mirrors in `settings.gradle.kts` to help in environments with restricted access to Google's Maven repository.

2. **Submodule Initialization**: The soraX submodule was initialized using `git submodule update --init --recursive`.

## Testing
To verify the fix works:
```bash
./gradlew :app:checkDebugDuplicateClasses
```

This should complete without duplicate class errors.

## Why This Works
By excluding `kotlin-android-extensions-runtime` at the configuration level, Gradle will not include it in the dependency graph, even if a transitive dependency requests it. The project uses `kotlin-parcelize-runtime` which provides all the necessary Parcelize functionality without conflicts.
