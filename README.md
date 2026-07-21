# Android Agent

A Kotlin Android application scaffold built with Jetpack Compose, the Gradle
Kotlin DSL, and a Gradle version catalog.

## Roadmap

The overall project vision and Phase 1 plan (always-on AI overlay, Italian
voice control, on-device LLM, and an AccessibilityService to drive apps) live
in [`PROJECT_PLAN.md`](PROJECT_PLAN.md).

## Current build (Phase 1 · Step 4)

Implemented so far: a draggable **overlay bubble** gated behind **biometric
unlock**, **Italian on-device voice recognition**, the **"hands"**
(`AccessibilityService` that reads the screen and acts), and the **"brain"** — a
local LLM (MediaPipe LLM Inference) that turns a command plus the current screen
into a `{action, target, text}` decision.

Command flow:

- **"apri &lt;app&gt;"** / **"cerca &lt;query&gt; su google"** run
  deterministically without the model.
- Any other command is planned by the LLM. Safe actions (`open_app`, `search`)
  are executed; `tap` / `type` / `scroll` are only reported for now — executing
  them needs the safety layer (whitelist + confirmation) coming in the next step.
- The screen text is passed to the model as **untrusted data** with an explicit
  instruction to ignore any commands embedded in it (prompt-injection defense).

### Providing the model

The model is not bundled in the APK (too large). Download a MediaPipe-compatible
`.task` LLM bundle onto the phone, then in the app tap **Importa modello LLM**
and pick the file — it is copied into the app's private storage. Until a model
is imported, free-form commands report that the model is missing.

To test on the device:

1. Grant **overlay** and **microphone**, tap **Abilita accessibilità**, and
   **Importa modello LLM**.
2. Tap **Attiva agente** and confirm with fingerprint / face / device PIN.
3. Tap the bubble and say **"apri Chrome"** or **"cerca meteo Genova su
   Google"**; try a free-form command to see the LLM plan an action (bubble
   turns blue while thinking).
4. Missing permissions/model never fail silently: the bubble turns **red** with
   a message and routes you to the right screen.
5. Stop it from the **Disattiva** notification action or the in-app button.

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
