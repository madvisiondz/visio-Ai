# MadVision

Private Android project for **OASIS MALL** retail workflows: GestiumERP article import, barcode scanning, product images, shelf labels, and print queues.

> Branding, logo, and product details will be added later.

## Stack

- **Android** — Kotlin, Jetpack Compose, Room (SQLite)
- **Target:** API 34, min SDK 26

## Build

From repo root (Windows):

```powershell
.\BUILD-APK.ps1
```

Install output: `OasisAI-debug.apk` at repo root (~129 MB). Requires Android Studio JBR and SDK (not in this repo).

## Docs

| File | Purpose |
|------|---------|
| [`PROJECT.md`](PROJECT.md) | Living spec, scope, changelog |
| [`docs/WHAT_WE_BUILT.md`](docs/WHAT_WE_BUILT.md) | Full technical reference |

## First-time setup

1. Install [Android Studio](https://developer.android.com/studio)
2. Open the `android/` folder
3. Optional: run `scripts/download-u2netp-tflite.ps1` for the background-removal model
4. Build with `BUILD-APK.ps1` or Android Studio → Build APK

## Data (not in repo)

Gestium CSV exports and product PNG libraries stay local — see `.gitignore`.
