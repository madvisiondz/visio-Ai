# PARAY Learn V1

> Teach PARAY what trusted catalog products look like in the real world.  
> **Recognition stays in AGENT** — learning stays in **PARAY → Learn**.

## Vision

PARAY does not start from unknown products. It learns products that already exist in VisioAI:

- CSV article exists
- Barcode exists
- PNG asset exists

Identity knowledge lives in Room (`articles`, `product_images`). Visual knowledge is created by learning and stored separately under `paray_home/memory/`.

## Navigation

| Screen | Route | Notes |
|--------|-------|-------|
| **PARAY** (bottom nav) | `paray` | Tabs: Learn · Memory · Knowledge · Statistics |
| Learn session | `paray_learn_session` | Full-screen camera; no bottom bar |
| PARAY Home (unchanged) | `paray_home` | Settings or PARAY → Home |

## Learn queue

Includes only products where:

- `articles.isActive = 1`
- `barcode` non-empty
- `product_images.imageStatus = 'FOUND'` with valid `imagePath`

Stats: **Remaining** (ready count), **Learned**, **Pending** (ready − learned), **Partially learned**.

## Configurable thresholds (architecture)

Confidence values are **not hardcoded** in `ParayLearnEngine`. They load from:

**File:** `paray_home/memory/learn_settings.json`

| Setting | JSON key | Used for |
|---------|----------|----------|
| `frontConfirmationThreshold` | `frontConfirmationThreshold` | Min PNG↔camera similarity to confirm front (0..1) |
| `sideCaptureThreshold` | `sideCaptureThreshold` | Min view change for left/right auto-capture |
| `backCaptureThreshold` | `backCaptureThreshold` | Min view change for back auto-capture |

**Defaults** (written on first run only) live in `ParayLearnSettings.factoryDefaults()` — single source, tunable later via settings UI.

**Derived (not separate settings):**

- Front mismatch cutoff = `frontConfirmationThreshold × 0.45`
- Max prior similarity for sides = `1 − sideCaptureThreshold` (back uses `backCaptureThreshold`)

**Operational constants** (not confidence thresholds) stay in `ParayLearnEngine` companion:

- `STABLE_FRAMES_REQUIRED = 4`
- `MISMATCH_FRAME_LIMIT = 45`
- `FRAME_STABILITY_SIMILARITY = 0.92` (consecutive frame stability)

### Architecture flow

```
ParayLearnSettingsStore (JSON)
        ↓ StateFlow / get()
ParayAgent.loadLearnSettings() / learnEngine(settings)
        ↓
ParayLearnEngine(settings)  ← session reloads settings per product
        ↓
ParayLearnSessionViewModel.onFrameFeatures()
```

**Not built yet:** Settings UI sliders to edit thresholds (architecture + persistence only).

## Session flow

1. **Front confirmation** — PNG reference shown; live camera must match PNG using `frontConfirmationThreshold`.
2. **Left / Right** — auto-capture when stable and distinct using `sideCaptureThreshold`.
3. **Back** — auto-capture using `backCaptureThreshold` → **LEARNED**.

On mismatch: **Front mismatch — please verify product** (Retry / Skip).

## Storage

| File | Contents |
|------|----------|
| `paray_home/memory/learn_settings.json` | Tunable thresholds |
| `paray_home/memory/learn_index.json` | Per-article learn record (views, status, timestamps) |
| `paray_home/memory/learn_views/{articleId}/` | Optional captured view JPEGs |
| `paray_home/memory/visual_index.json` | Merged signature after LEARNED (AGENT recognition) |

### Status rules

| Status | Rule |
|--------|------|
| `NOT_LEARNED` | 0 views confirmed |
| `PARTIALLY_LEARNED` | 1–3 views |
| `LEARNED` | Front + Left + Right + Back |

## AGENT integration

`ParayCameraMatcher.identify()` scores live frames against:

- Existing `visual_index.json` signatures
- **Learned multi-view records** from `learn_index.json` (boost when fully learned)
- CLIP fingerprint presence (when imported)

AGENT UI unchanged — recognition-only path uses improved matcher scores.

## V1 scope (explicitly not built)

- Settings UI for threshold sliders
- Autonomous / cloud learning
- Model retraining pipelines
- Memory / Knowledge / Statistics tabs (placeholders only)

## Key code

```
domain/paray/
  ParayLearnModels.kt
  ParayLearnSettings.kt          ← threshold model + factoryDefaults()
  ParayLearnSettingsStore.kt     ← JSON persistence
  ParayLearnStore.kt
  ParayLearnQueue.kt
  ParayLearnEngine.kt            ← accepts ParayLearnSettings (no hardcoded confidence)
  ParayVisualSimilarity.kt

ui/screens/paraylearn/
  ParayMainScreen.kt
  ParayLearnSessionScreen.kt
  ParayLearnCameraPreview.kt
  ParayLearnViewModels.kt        ← reloadEngine() per session
```
