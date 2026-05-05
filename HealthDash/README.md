# HealthDash — Google Fit-style Health Connect Dashboard

A beautiful step & activity tracker inspired by Google Fit's UI, built on Health Connect.

## How to get the APK (no Android Studio needed!)

### Option 1: GitHub Actions (easiest)
1. Create a free account at [github.com](https://github.com)
2. Create a new **public** repository
3. Upload all these files to it (drag & drop works)
4. Go to the **Actions** tab → click the latest workflow run
5. Once it finishes (2–3 min), download **HealthDash-debug.apk** from the Artifacts section
6. Transfer the APK to your phone and install it

> On your phone: Settings → Apps → Install unknown apps → allow your browser/file manager

### Option 2: Build locally (if you have JDK 17)
```bash
gradle assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Requirements
- Android 8.0+ (API 26)
- Health Connect installed (available on Play Store)
- Google Fit or any other app already tracking your steps

## What it shows
- 🟢 Move Minutes ring (daily active time)
- 🟠 Heart Points ring (intensity score)  
- Steps, distance, calories, active minutes
- Weekly step bar chart
- Recent exercise sessions

## Data & Privacy
All data stays on your device via Health Connect. Nothing is uploaded anywhere.
