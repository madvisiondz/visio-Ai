# Offline background removal (v1.3.0)

## Module

`com.oasismall.oasisai.domain.backgroundremoval`

| Class | Role |
|-------|------|
| `BackgroundRemovalService` | `removeBackground()` — backup original, run model, save transparent PNG |
| `BackgroundRemovalResult` | `originalPath`, `outputPngPath`, `success`, `errorMessage` |
| `BackgroundRemovalOptions` | mask threshold, edge smooth, crop rect, max dimension |
| `OnnxU2NetSegmenter` / `TfliteU2NetSegmenter` | U2NetP inference (320×320, rembg-compatible) |
| `MaskPostProcessor` | resize mask, threshold, blur edges, apply alpha |

No cloud APIs. No API keys. Runs on `Dispatchers.Default`.

## Model (one-time on PC)

Place **u2netp_320x320_float32.tflite** (~4.5 MB) in:

`android/app/src/main/assets/`

Run from repo root:

```powershell
.\scripts\download-u2netp-tflite.ps1
```

If download fails, get **u2netp 320×320 float32** from [PINTO model zoo 061_U-2-Net](https://github.com/PINTO0309/PINTO_model_zoo/tree/master/061_U-2-Net), rename to `u2netp_320x320_float32.tflite`, rebuild APK.

## Storage

| Path | Content |
|------|---------|
| `files/image_originals/` | Backup before processing (never overwritten) |
| `files/bg_removal_work/` | Working cutouts until accepted |
| `files/product_images/` | Accepted transparent PNG (via `ImageMatcher`) |
| `product_images.originalImagePath` | DB column — backup path |

## UI

### Scan & shoot (primary flow, v1.3.1)

1. Scan barcode → **Lock this barcode**
2. If no PNG: **Create asset** (replaces old “Shoot to DCIM”)
3. System camera opens → user confirms photo
4. **Background removal runs automatically** (progress overlay only)
5. Brief **cutout-only** preview, then **auto-save** transparent PNG to `product_images` + **To share** (no sliders or manual accept)
6. Original capture kept in `image_originals/`

### Also available

- **Settings → Remove background (offline)** — manual import
- **Article detail → Remove background (offline)**

## Barcode workflow

Accept from **article detail** calls `ImageMatcher.registerBackgroundRemovedImage()` — same PNG naming/metadata as Stamper; does not break carts or share.
