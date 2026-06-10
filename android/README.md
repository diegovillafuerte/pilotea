# Kompara Android

Native Android app for Kompara — earnings analytics for ride-hailing drivers in Mexico. Kotlin + Jetpack Compose. See `../docs/android-technical-design.md` for architecture.

## Requirements

- JDK 17+ (Android Studio's embedded JDK works: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`)
- Android SDK (`ANDROID_HOME` env var or `local.properties` with `sdk.dir`)

## Build & run

```bash
./gradlew assembleDebug            # build debug APK
./gradlew testDebugUnitTest        # run unit tests
./gradlew installDebug             # install on a connected device/emulator
```

Or open `android/` in Android Studio and run the `app` configuration.

## Versions

Managed in `gradle/libs.versions.toml`: AGP 9.2.0, Kotlin 2.3.21, Compose BOM 2026.05.00. Gradle 9.5.1 via wrapper.

## CI

`.github/workflows/android.yml` (repo root) builds and runs unit tests on every push/PR touching `android/`.
