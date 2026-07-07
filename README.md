# WHealth Android App

WHealth is an offline-first personal health monitoring app for Android. This first native build implements the core product loop from the supplied spec:

- Animated, step-by-step profile setup with emergency details
- Interactive module selection after profile setup
- Dashboard with enabled modules only
- Manual record entry
- Image-assisted entry flow that always requires user confirmation before saving
- Minimal rounded UI with fade/slide transitions between screens
- Generic local SQLite storage for all health modules
- Blood pressure classification
- Per-module history and combined health timeline
- Emergency contact screen

## Open in Android Studio

1. Open this folder: `C:\.work\WHEALTH`
2. Let Android Studio install or select an Android SDK.
3. If Android Studio asks for an SDK path, choose the installed SDK location. It will create `local.properties`.
4. Run the `app` configuration on an emulator or Android phone.

## Build from Terminal

After an Android SDK is installed and `local.properties` exists:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK will be created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Current OCR Scope

The app includes the safe OCR user experience: attach photo, prefill fields for review, then confirm and save. The actual seven-segment OCR engine is the next native module to add. The database and UI already preserve the important rule from the spec: OCR never saves silently.
