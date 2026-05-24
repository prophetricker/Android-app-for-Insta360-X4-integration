# OmniEye

Android App for Insta360 X4 Camera Integration with AI Voice Assistant

## Features

- **Insta360 X4 WiFi Connection** - Connect to your Insta360 X4 camera via WiFi
- **Voice Control** - Long press volume key to input voice commands
- **Auto Capture** - Automatically captures photos every 0.5 seconds when connected
- **AI Processing** - Cloud-based image analysis and scene recognition
- **Text-to-Speech** - Automatic voice output of analysis results

## Requirements

- Android 10+ (API 29+)
- Insta360 X4 Camera
- Internet connection (for cloud processing)

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and run on your device

## Roadshow Demo

This branch wires the Android app directly to the DAP-first FastAPI backend.

Start the backend:

```powershell
python -m uvicorn omnieye_cloud.main:app --app-dir cloud-backend --host 0.0.0.0 --port 8000
```

Build the Android app with the backend URL:

```powershell
gradle.bat assembleDebug -PCLOUD_BASE_URL="https://your-tunnel.example/"
```

The default URL is `http://10.0.2.2:8000/`, which is only useful for an Android emulator. For a real phone, use an HTTPS tunnel such as Cloudflare Tunnel, ngrok, or Tailscale Funnel.

When the phone is not connected to an Insta360 X4, the capture button sends a generated roadshow frame so the upload, cloud analysis, Chinese TTS, and vibration loop can still be demonstrated.

## Permissions

- WiFi permissions (camera connection)
- Microphone permission (voice input)
- Internet permission (cloud processing)

## Architecture

- **MVVM** architecture with Jetpack Compose
- **Kotlin Coroutines** for async operations
- **Retrofit** for network requests
- **Material Design 3** UI components

## Project Structure

```
app/src/main/java/com/omniveye/app/
├── MainActivity.kt          # Main activity with volume key handling
├── camera/                  # Camera connection management
├── cloud/                   # Cloud API integration
├── speech/                  # Voice input/output
├── ui/
│   ├── components/          # Reusable UI components
│   ├── screens/             # Main screens
│   └── theme/               # App theming
└── viewmodel/               # ViewModels
```

## License

MIT License - See LICENSE file for details.
