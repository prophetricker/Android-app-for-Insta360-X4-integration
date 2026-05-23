# OmniEye Mobile

Mobile-first MVP for a blind-assistive perception flow using an Insta360 X4, an Android phone, and a laptop/cloud inference service.

## MVP Architecture

```text
Insta360 X4 --WiFi--> Android phone --cellular HTTPS--> laptop/cloud backend
Android phone: speech input, speech output, vibration, state UI
Backend: DAP distance, semantic scene analysis API
```

This project intentionally replaces the earlier ESP32/Windows/serial architecture with a phone-centered design.

## Current Status

See `看板.md`.

## Backend Smoke Test

```powershell
cd OmniEye-Mobile
python -m pip install -r mobile-backend\requirements.txt
python -m pytest mobile-backend
python -m uvicorn mobile-backend.app:app --host 0.0.0.0 --port 8000
```

Expose the local service to the phone with a public tunnel such as ngrok, Cloudflare Tunnel, or Tailscale Funnel.

## Android Build

The project includes a Gradle wrapper copied from the local Insta360 SDK demo:

```powershell
.\gradlew.bat testDebugUnitTest
```

This requires JDK 17 and Android SDK/Build Tools. On this machine, the current blocker is that `JAVA_HOME` is not set and `java.exe` is not in `PATH`.

Create `local.properties` from `local.properties.example` before resolving Android dependencies. Put your Android SDK path and the Insta360 Maven credentials from the official SDK demo/package in that local file, or set `INSTA360_MAVEN_USERNAME` and `INSTA360_MAVEN_PASSWORD` in the environment.

## Local SDK Reference

The local Insta360 Android SDK demo was found under:

```text
../.sdk_extract/mobile/sdk_demo_1.9.11/sdkdemo2
```

Observed SDK demo facts:

- Insta360 Android SDK version: `1.9.11`
- Android Gradle Plugin: `8.9.0`
- Kotlin: `2.0.21`
- `minSdk`: `29`
- `compileSdk`: `35`
- Camera SDK Maven repository: `https://androidsdk.insta360.com/repository/maven-public/`

Do not vendor the full SDK demo, APK, or local credentials into this repo.
