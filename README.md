# Android Agent

A Kotlin Android application scaffold built with Jetpack Compose, the Gradle
Kotlin DSL, and a Gradle version catalog.

## Roadmap

The overall project vision and Phase 1 plan (always-on AI overlay, Italian
voice control, on-device LLM, and an AccessibilityService to drive apps) live
in [`PROJECT_PLAN.md`](PROJECT_PLAN.md).

## Current build (Phase 1 · Step 2)

Implemented so far: a draggable floating **overlay bubble** gated behind
**biometric unlock**, plus **Italian on-device voice recognition** triggered by
tapping the bubble.

To test on the device:

1. Open the app and tap **Concedi permesso overlay**, then enable "Display over
   other apps" for Android Agent and go back.
2. Tap **Concedi microfono** and allow the `RECORD_AUDIO` permission (it is also
   requested automatically the first time you activate the agent).
3. Tap **Attiva agente** and confirm with fingerprint / face / device PIN.
4. A bubble appears on top of every app — drag it around. **Tap it and speak**:
   the recognized text is shown as a toast (it will drive the agent in a later
   step). While listening the bubble turns green.
5. If the microphone permission is missing or an error occurs, the bubble turns
   **red** and shows a message instead of staying silent; tapping it again opens
   the app so you can grant the permission.
6. Stop it from the **Disattiva** notification action or the in-app button.

## Downloading the APK

Development happens phone-first, so every push is built by CI. The `android`
GitHub Actions workflow compiles a debug APK and attaches it to the
`debug-latest` pre-release. Open the repository's **Releases** page and download
`app-debug.apk` directly onto the device (no unzip needed), then install it with
"unknown sources" enabled.

## Requirements

- JDK 17
- Android SDK (compileSdk 34)
- Android Studio (recommended) or the Android command-line tools

## Project structure

```
.
├── app/
│   ├── build.gradle.kts            # App module build script
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/logiop/androidagent/
│       │   │   ├── MainActivity.kt
│       │   │   └── ui/theme/       # Compose theme (Color, Type, Theme)
│       │   └── res/                # Resources (strings, themes, icons)
│       ├── test/                   # Local (JVM) unit tests
│       └── androidTest/            # Instrumented (device) tests
├── build.gradle.kts                # Root build script
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml          # Version catalog
│   └── wrapper/                    # Gradle wrapper
├── gradlew / gradlew.bat           # Gradle wrapper scripts
└── gradle.properties
```

## Building

Point Gradle at your Android SDK by creating a `local.properties` file:

```
sdk.dir=/path/to/Android/sdk
```

Then build:

```bash
./gradlew assembleDebug        # Build the debug APK
./gradlew installDebug         # Install on a connected device/emulator
./gradlew test                 # Run local unit tests
./gradlew connectedAndroidTest # Run instrumented tests (device required)
```

## Configuration

| Setting        | Value                        |
| -------------- | ---------------------------- |
| Application ID | `com.logiop.androidagent`    |
| Min SDK        | 24                           |
| Target SDK     | 34                           |
| Compile SDK    | 34                           |
| Language       | Kotlin 2.0                   |
| UI toolkit     | Jetpack Compose (Material 3) |
