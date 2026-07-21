# Android Agent

A Kotlin Android application scaffold built with Jetpack Compose, the Gradle
Kotlin DSL, and a Gradle version catalog.

## Roadmap

The overall project vision and Phase 1 plan (always-on AI overlay, Italian
voice control, on-device LLM, and an AccessibilityService to drive apps) live
in [`PROJECT_PLAN.md`](PROJECT_PLAN.md).

## Current build (Phase 1 · Step 3)

Implemented so far: a draggable floating **overlay bubble** gated behind
**biometric unlock**, **Italian on-device voice recognition** triggered by
tapping the bubble, and the **"hands"** — an `AccessibilityService` that reads
the screen and runs deterministic commands.

Voice commands understood right now (deterministic, no LLM yet):

- **"apri &lt;app&gt;"** / "avvia &lt;app&gt;" — launches the app by name.
- **"cerca &lt;query&gt; su google"** / "google &lt;query&gt;" — opens a Google search.
- anything else — the accessibility service reads the current screen and reports
  how many interactive elements it found (the LLM will plan these in a later
  step; the compact UI tree is logged under the `AndroidAgent` tag).

To test on the device:

1. Grant **overlay** and **microphone**, and tap **Abilita accessibilità** to
   turn on the "Android Agent" accessibility service in system settings.
2. Tap **Attiva agente** and confirm with fingerprint / face / device PIN.
3. Tap the bubble and say **"apri Chrome"**, then **"cerca meteo Genova su
   Google"** — the app opens and the search runs.
4. Say a free-form command over any app to see the screen-read element count.
5. Missing permissions never fail silently: the bubble turns **red** with a
   message and routes you to the right settings screen.
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
