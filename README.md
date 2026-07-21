# Android Agent

A Kotlin Android application scaffold built with Jetpack Compose, the Gradle
Kotlin DSL, and a Gradle version catalog.

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
