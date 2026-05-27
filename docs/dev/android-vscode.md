# VSCode Android Development

This project can be developed from VSCode for the common loop:

```text
edit -> run Gradle task -> install APK -> watch logcat -> iterate
```

Android Studio is still useful for visual layout previews and full Android debugger setup, but it is not required for day-to-day build/install/log work.

## Workspace

Open this folder in VSCode:

```powershell
D:\MyProject\Bohack2\OmniEye-Mobile-roadshow
```

Do not open `D:\MyProject\Bohack2` as the Android workspace. The root folder contains SDK archives, extracted SDKs, DAP models, tooling downloads, and test artifacts that must not be treated as project files.

## Required Local Paths

The checked-in VSCode tasks currently assume these local paths:

```text
JAVA_HOME=D:\MyProject\Bohack2\.tooling\jdk17\jdk-17.0.19+10
ANDROID_HOME=D:\MyProject\Bohack2\.tooling\android-sdk
ANDROID_SDK_ROOT=D:\MyProject\Bohack2\.tooling\android-sdk
DAP_REPO_DIR=D:\Models\DAP
DAP_WEIGHTS_PATH=D:\Models\DAP-weights-repo\model.pth
DAP_PYTHON=D:\MyProject\Bohack2\.tooling\dap-venv\Scripts\python.exe
```

If any path changes, update `.vscode/tasks.json`.

Current Gradle note:

```text
This repo's checked-in gradlew.bat currently cannot run by itself because gradle/wrapper/gradle-wrapper.jar is not tracked.
VSCode tasks therefore call the already-installed Gradle 8.11.1 at:
C:\Users\EZ\.gradle\wrapper\dists\gradle-8.11.1-bin\7800bkpvjdl6wgx6vnys98319\gradle-8.11.1\bin\gradle.bat
```

Later cleanup should restore a standard Gradle wrapper and remove the tracked `gradle/wrapper/gradle-8.2/lib/` distribution files.

## Recommended VSCode Extensions

Install these from VSCode Extensions:

```text
Extension Pack for Java
Kotlin Language
Gradle for Java
Android iOS Emulator
ADB Interface for VSCode
Python
```

The exact extension names can vary. The important capabilities are Java/Kotlin language support, Gradle task discovery, Python editing, and ADB/logcat integration.

## Main Tasks

Open Command Palette:

```text
Ctrl+Shift+P -> Tasks: Run Task
```

Useful tasks:

```text
repo: full verification
repo: check forbidden files
android: test debug unit
android: assemble debug
android: install debug apk
android: devices
android: logcat omnieye
backend: test
backend: run local
```

Expected daily flow:

1. Run `android: devices`.
2. If the phone appears, run `android: install debug apk`.
3. Run `android: logcat omnieye`.
4. Start the app on the phone.
5. Reproduce the issue or feature path.
6. Read logs for `CameraManager`, `CloudRepository`, `CellularNetworkProvider`, and `MainViewModel`.

## Backend Flow

Start backend:

```text
Tasks: Run Task -> backend: run local
```

Check health from another terminal:

```powershell
curl.exe http://127.0.0.1:8000/health
```

If using a phone, expose the backend through ngrok or another HTTPS tunnel, then set the Android backend URL accordingly.

## Device Checklist

Before installing or debugging:

1. Enable Developer Options on the phone.
2. Enable USB Debugging.
3. Plug in USB cable.
4. Accept the RSA debugging prompt on the phone.
5. Run `android: devices`.

Expected output includes a device row:

```text
List of devices attached
<serial> device ...
```

If the list is empty:

1. Replug the cable.
2. Unlock the phone.
3. Switch USB mode to file transfer if needed.
4. Revoke USB debugging authorizations and accept again.
5. Run:

```powershell
D:\MyProject\Bohack2\.tooling\android-sdk\platform-tools\adb.exe kill-server
D:\MyProject\Bohack2\.tooling\android-sdk\platform-tools\adb.exe start-server
D:\MyProject\Bohack2\.tooling\android-sdk\platform-tools\adb.exe devices -l
```

## Attach Debugger Note

`.vscode/launch.json` contains an initial Java attach configuration on port `8700`. This is a placeholder for a full JDWP workflow.

For now, the reliable debugging loop is:

```text
unit tests + APK install + filtered logcat
```

Once the device is consistently visible through ADB, the next step is to add a task that forwards or discovers the app JDWP port and then attach VSCode to it.

## Forbidden Files

Run before every commit:

```text
Tasks: Run Task -> repo: check forbidden files
```

It fails if Git tracks:

```text
Insta360 SDK extracts
CameraSDK / MediaSDK folders
model weights
archives such as .rar/.7z
native SDK libraries such as .aar/.so
very large tracked files over the configured threshold
```

The local SDK and DAP directories must stay outside Git:

```text
D:\MyProject\Bohack2\.sdk_extract
D:\MyProject\Bohack2\赛事SDK包（Android+iOS）(2).rar
D:\MyProject\Bohack2\赛事SDK包（Windows+Linux）(2).rar
D:\Models\DAP
D:\Models\DAP-weights-repo
```

## Full Verification

Before pushing:

```text
Tasks: Run Task -> repo: full verification
```

This runs, in order:

```text
repo: check forbidden files
backend: test
android: test debug unit
android: assemble debug
```

If any step fails, fix that first and rerun the full verification.
