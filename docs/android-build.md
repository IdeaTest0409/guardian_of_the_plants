# Android build guide

This document explains how to build the Android APK from this repository in a
way that is not tied to one developer machine.

## Project Location

The Android project is in:

```text
android/
```

Run Android build commands from that directory unless a command says otherwise.

## What Is Committed

The repository includes the Gradle Wrapper:

```text
android/gradlew
android/gradlew.bat
android/gradle/wrapper/gradle-wrapper.jar
android/gradle/wrapper/gradle-wrapper.properties
```

These files are intentionally committed. They are small and allow another
environment to use the expected Gradle version without relying on a locally
installed `gradle` command.

The wrapper currently uses:

```text
Gradle 8.13
```

The actual Gradle distribution and dependencies are downloaded into the local
Gradle cache of each build environment. They are not committed to Git.

## What Is Not Committed

Do not commit local SDK settings, Gradle caches, APK outputs, or generated
build directories:

```text
android/local.properties
android/.gradle/
android/build/
android/app/build/
*.apk
```

These are already covered by `.gitignore`.

## Requirements

Any environment that builds the APK needs:

```text
JDK 17 or newer
Android SDK
Android SDK Platform for compileSdk 35
Android SDK Build Tools compatible with the Android Gradle Plugin
Internet access for first-time dependency download, unless caches already exist
```

Android Studio can provide the JDK and Android SDK on a desktop machine. A CI
environment such as GitHub Actions should install or configure them before
running the Gradle Wrapper.

## local.properties

`android/local.properties` is machine-specific and is not committed.

Android Studio usually creates it automatically when opening the `android/`
project.

A template is available at:

```text
android/local.properties.example
```

Copy it to `android/local.properties` and fill only local values:

```bash
cp android/local.properties.example android/local.properties
```

On Windows PowerShell:

```powershell
Copy-Item android\local.properties.example android\local.properties
```

For command-line builds, create it only if Gradle cannot find the Android SDK
through the environment. The file should contain the local SDK path:

```properties
sdk.dir=/path/to/android/sdk
```

Windows example:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

Linux example:

```properties
sdk.dir=/home/<user>/Android/Sdk
```

In CI, prefer using `ANDROID_HOME` or `ANDROID_SDK_ROOT` when available, or
generate `local.properties` during the workflow.

Temporary app-start reporting can also be configured in `local.properties`:

```properties
guardian.api.baseUrl=http://<SERVER_IP_OR_HOSTNAME>/api
guardian.appStartReporting.enabled=true
```

This value is compiled into the debug APK. Keep `local.properties` out of Git.
If the value is blank or missing, the Android app skips app-start reporting.
If the value is malformed or does not end with `/api`, the app also skips
reporting and writes a local warning log.
Set `guardian.appStartReporting.enabled=false` to compile an APK that never
sends startup reports.

For VPS use after HTTPS is configured:

```properties
guardian.api.baseUrl=https://<DOMAIN>/api
```

## Build Debug APK

From the repository root:

```bash
cd android
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
cd android
.\gradlew.bat assembleDebug
```

Expected APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

This output is a build artifact. Do not commit it.

The current Android identity is documented in:

```text
docs/android-identity.md
```

## Build Release APK

An unsigned release APK can be built with:

```bash
cd android
./gradlew assembleRelease
```

On Windows PowerShell:

```powershell
cd android
.\gradlew.bat assembleRelease
```

Expected output:

```text
android/app/build/outputs/apk/release/
```

Release signing is not configured in this repository yet. Add signing only when
there is a clear release workflow, and keep keystores and passwords out of Git.

## Clean Build

Use this when generated files appear stale:

```bash
cd android
./gradlew clean assembleDebug
```

On Windows PowerShell:

```powershell
cd android
.\gradlew.bat clean assembleDebug
```

## Current Windows Machine Note

On the current Windows machine, Android Studio's bundled JDK may be used if Java
is not on `PATH`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'
cd C:\work\guardian_of_the_plants\android
.\gradlew.bat assembleDebug
```

This path is only an example for the current machine. Do not hardcode it into
project files.

## Docker Scope

The Android project is not currently built inside Docker.

Current Docker Compose services are for the server-side environment:

```text
nginx
PostgreSQL
VOICEVOX
```

Recommended split for now:

```text
Android APK build       -> Gradle Wrapper / Android Studio / future CI
Server-side components  -> Docker Compose
```

If reproducible APK creation on Linux is needed, prefer adding a GitHub Actions
Android build workflow before creating a custom Android build Docker image.

## GitHub Actions

The repository includes:

```text
.github/workflows/android-build.yml
```

The workflow builds `:app:assembleDebug` on Ubuntu with JDK 17 and Android SDK.
It creates a temporary CI-only `local.properties`; that file is not committed.

## Troubleshooting

### `JAVA_HOME is not set`

Install a JDK or point `JAVA_HOME` to an existing JDK. Android Studio's bundled
JDK is acceptable for local builds.

### `SDK location not found`

Open the project in Android Studio once, or create `android/local.properties`
with the local Android SDK path.

### Dependency download is slow

This is expected on first build in a new environment. Gradle and Android
dependencies are cached locally after the first successful build.

### Build works locally but not in CI

Check that CI installs:

```text
JDK
Android SDK
compileSdk 35 platform
required build tools
```

Also confirm CI runs commands from the `android/` directory.
