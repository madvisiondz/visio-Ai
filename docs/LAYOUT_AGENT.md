# Oasis AI — Layout Fit Agent

> Placement submodule of **PARAY** (`docs/PARAY.md`). Fits product cutouts inside print tickets; PARAY learns visual signatures from each placement.

## Purpose

Product PNGs often have large transparent margins. Scaling the full bitmap makes small packs (e.g. mousse 50g) look tiny on shelf tickets. The **Layout Fit Agent** detects visible product pixels and fits them into the white image slot (width **and** height) without touching neighbors or the yellow price block.

## Architecture (v1.6.0)

```
Design screen open
    → LayoutFitAgent.activateDesignSession()
    → GpuLearningProbe probes EGL/GPU + logs session

ShelfA4Renderer.renderPage()
    → ProductContentBounds.detect(bitmap)   # alpha bbox
    → LayoutFitMemory.loadHint()            # prior placements
    → contain-fit scale (max size in slot)
    → canvas.drawBitmap(src=bbox, dst=slot)
    → memory.saveHint() + gpu_learning.jsonl

Design screen closed
    → LayoutFitAgent.deactivateDesignSession()
```

## Modules

| File | Role |
|------|------|
| `AppLayoutKnowledge.kt` | Shelf_10up mm rules + Oasis product philosophy |
| `ProductContentBounds.kt` | Alpha scan → visible product rectangle |
| `LayoutFitAgent.kt` | Placement orchestrator |
| `LayoutFitMemory.kt` | `files/layout_agent/placement_memory.json` |
| `GpuLearningProbe.kt` | GPU probe + `gpu_learning.jsonl` event log |

## App rules encoded in agent

- Designation-first identity; offline-first.
- Shelf yellow block: 10 cm × 4 cm; image slot ~41.5 × 40 mm with 1.5 mm pad.
- Fit **visible cutout** inside white area (contain), centered.
- Designation: largest centered text that fits; price centered below.

## Phase 2 (planned)

- Train lightweight TFLite placement head on `gpu_learning.jsonl` + memory hints.
- NNAPI / GPU delegate when Design tab is active.
- Extend to freezer, podium, promo templates.
- Optional: user thumbs-up/down on placement → reinforcement signal.

## Data on device

| Path | Content |
|------|---------|
| `files/layout_agent/placement_memory.json` | Per-image scale hints |
| `files/layout_agent/gpu_learning.jsonl` | Session + placement events for future training |
