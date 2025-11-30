# Project Context: bilitv

## Overview
`bilitv` is an Android application tailored for TV interfaces, built using modern Android development standards. While documentation mentions standard View-based architecture, the project configuration reveals a strong adoption of **Jetpack Compose** and **Material Design 3** for the UI layer.

## Technical Stack
- **Language:** Kotlin (v2.0.21)
- **Build System:** Gradle (Kotlin DSL) with Version Catalog
- **UI Framework:** Jetpack Compose (Material 3), AndroidX AppCompat
- **Image Loading:** Coil
- **Minimum SDK:** 24 (Android 7.0 Nougat)
- **Target SDK:** 36 (Android 16)

## Project Structure
- **`app/`**: Main application module.
  - **`src/main/java`**: Kotlin source code.
  - **`src/main/res`**: Resources (themes, strings, etc.).
  - **`src/main/AndroidManifest.xml`**: App manifest.
- **`gradle/libs.versions.toml`**: Centralized dependency management (Version Catalog).
- **`.qoder/`**: Project documentation (may be slightly outdated regarding UI framework).

## Key Configuration & Conventions
- **Dependency Management:** strictly uses `gradle/libs.versions.toml`. Do not hardcode versions in build files.
- **UI Development:** Prefers Jetpack Compose (Material 3) as enabled in `app/build.gradle.kts`.
- **Build Features:** `compose = true`.

## Development Workflow

### Build
*   **Debug APK:** `./gradlew assembleDebug`
*   **Release APK:** `./gradlew assembleRelease`

### Testing
*   **Unit Tests:** `./gradlew testDebugUnitTest`
*   **Instrumented Tests:** `./gradlew connectedDebugAndroidTest`

## Important Files
- `app/build.gradle.kts`: Module-level build config (enables Compose).
- `gradle/libs.versions.toml`: Dependency versions.
- `app/src/main/AndroidManifest.xml`: Manifest.
