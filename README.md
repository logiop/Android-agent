# Android Agent

A Kotlin Android application scaffold built with Jetpack Compose, the Gradle
Kotlin DSL, and a Gradle version catalog.

## Roadmap

The overall project vision and Phase 1 plan (always-on AI overlay, Italian
voice control, on-device LLM, and an AccessibilityService to drive apps) live
in [`PROJECT_PLAN.md`](PROJECT_PLAN.md). The Phase 3 plan (procedural memory and
skill learning) lives in [`PROJECT_PLAN_FASE3.md`](PROJECT_PLAN_FASE3.md). A
feasibility note on the Jetpack AppFunctions route (Phase 3.3) is in
[`docs/APPFUNCTIONS_EVALUATION.md`](docs/APPFUNCTIONS_EVALUATION.md).

## Developer guide

For the phone-first development workflow — getting the APK from CI, providing
the LLM model, setup, using every feature, per-phase verification checklists,
and troubleshooting — see [`docs/DEVELOPER_GUIDE.md`](docs/DEVELOPER_GUIDE.md).

## Current build (Phase 1 · Step 5)

Implemented so far: a draggable **overlay bubble** gated behind **biometric
unlock**, **Italian on-device voice recognition**, the **"hands"**
(`AccessibilityService`), the **"brain"** (MediaPipe LLM Inference), and the
**agent loop with its safety layer**.

Command flow:

- **"apri &lt;app&gt;"** / **"cerca &lt;query&gt; su google"** run
  deterministically without the model.
- Any other command drives the agent loop: plan with the LLM → act → re-read the
  screen → repeat, up to 15 steps, stopping after 3 steps with no on-screen
  change instead of insisting.
- **Safety layer (non-negotiable):**
  - Navigation (`open_app`, `search`) is always allowed.
  - `tap` / `type` / `scroll` run **only inside apps you added to the
    whitelist** (everything is blocked by default — manage it from **Gestisci
    whitelist app**).
  - Irreversible taps (send / post / delete / pay …) require an explicit
    **confirmation dialog** before running.
  - Screen text is passed to the model as **untrusted data** with an explicit
    instruction to ignore any commands embedded in it (prompt-injection defense).
  - Every agent action is written to an **encrypted local audit log**
    (AES-256/GCM with an Android Keystore key); view it decrypted under **Mostra
    log azioni**.

### Providing the model

The model is not bundled in the APK (too large). Download a MediaPipe-compatible
`.task` LLM bundle onto the phone, then in the app tap **Importa modello LLM**
and pick the file — it is copied into the app's private storage. Until a model
is imported, free-form commands report that the model is missing.

To test on the device:

1. Grant **overlay** and **microphone**, tap **Abilita accessibilità**,
   **Importa modello LLM**, and add the apps you want to control under **Gestisci
   whitelist app**.
2. Tap **Attiva agente** and confirm with fingerprint / face / device PIN.
3. Tap the bubble and say **"apri Chrome"** or **"cerca meteo Genova su
   Google"**; try a free-form command to see the agent loop plan and act (bubble
   turns blue while thinking).
4. Ask for something that ends in an irreversible tap (e.g. sending) to see the
   confirmation dialog; try a control action in a non-whitelisted app to see it
   blocked.
5. Missing permissions/model never fail silently: the bubble turns **red** with
   a message and routes you to the right screen.
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
