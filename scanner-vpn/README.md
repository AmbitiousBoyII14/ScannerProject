# Scanner VPN — Android source preview

Open this directory (the one containing `settings.gradle.kts`) in Android Studio. This is a standalone Kotlin/Jetpack Compose project; it is not a module of ScannerProject.

## Build a debug APK

Use JDK 17 and the Android SDK with platform 36.1. Allow Gradle to download dependencies from Google and Maven Central. From this directory run:

```sh
./gradlew :app:assembleDebug
```

On Windows use `gradlew.bat :app:assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`. The debug build uses Android's standard debug signing; no private keystore or API token is required. Run the `app` configuration on Android 7.0+ to preview the UI.

## What this preview contains

- Dashboard, server list, profile and HTTP payload editors, configuration import/export, history and settings under `app/src/main/java/com/example/`.
- The upstream Android VPN service source under `vpn/`. Its SSH-labelled modes do **not** implement an authenticated SSH transport. Preview the UI without connecting or entering real credentials. The bundled profiles are examples, not Scanner-operated servers.

## Origin and changes

Based on [Dark Tunnel VPN Pro v1.0.0](https://github.com/rodro1926-jpg/dark-tunnel-vpn-pro/tree/eb502b28d683bee2864e7100e78e239d756b3ab0), distributed under the included [Apache license](LICENSE). This fork changes the application ID, launcher and dashboard labels and Gradle setup for local debug builds. It does not include the upstream release workflow or signing configuration.
