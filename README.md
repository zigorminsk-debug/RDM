# RDM for Android

RDM is a native Android manager for saved Remote Desktop Protocol (RDP) connections. It uses the **FreeRDP Android core** for the actual RDP engine and session UI, and adds a focused connection library on top: saved desktops, quick connect, favourites, search, and encrypted password storage.

## What it does

- Save and edit RDP profiles (name, host, port, username, domain).
- Launch a FreeRDP session directly from a profile or connect to a host without saving it.
- Search and favourite saved desktops; see the most recently used profiles.
- Optionally remember a password. Passwords are encrypted with AES-GCM using a key held in Android Keystore. They are not included in Android backup.
- Keep the connection manager separate from the remote session screen; the latter is FreeRDP's native Android `SessionActivity`.

This initial version targets direct RDP hosts. RD Gateway profiles, `.rdp` import/export, and other remote-desktop protocols are not included yet.

## Build

Requirements: Android Studio with Android SDK 36 and Build Tools `36.0.0`, JDK 17 or newer, Android NDK `29.0.13113456`, and CMake `4.1.2`.

The upstream FreeRDP repository is pinned as a Git submodule. After cloning this repository, initialize it once:

```sh
git submodule update --init --recursive
./scripts/prepare-freerdp-android.sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The preparation script adjusts the pinned FreeRDP Gradle configuration, caps native OpenSSL/FFmpeg build parallelism, and uses AndroidX Core 1.17.0, which supports compile SDK 36. Android Studio can open this repository directly after the submodule has been initialized and the compatibility script has run.

## GitHub Actions builds

`.github/workflows/android-apk.yml` builds a debug APK on every branch push, on pull requests to `main`, and on manual dispatch. Each run is numbered with GitHub's workflow run number and attempt (for example, `12.1`). The APK's Android `versionCode` is `run_number × 1000 + attempt`, and the version name and downloadable artifact include both numbers. Find the APK in the run's **Artifacts** section; artifacts are retained for 30 days.

## FreeRDP attribution

The native protocol engine and session implementation are provided by [FreeRDP](https://github.com/FreeRDP/FreeRDP), pinned in `vendor/FreeRDP`. RDM launches the FreeRDP Android session activity with a `freerdp://` connection URI; it does not reimplement the RDP protocol. FreeRDP contains components under the licenses and notices included in that upstream repository (including Apache-2.0 and MPL-2.0 files). See its `LICENSE` and source-file headers before redistributing builds.

RDM's profile store contains connection metadata locally. Only saved passwords are encrypted; avoid using the password-save option on shared devices.
