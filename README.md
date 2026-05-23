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
