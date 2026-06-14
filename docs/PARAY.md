# PARAY — Oasis Visual Agent

> **PARAY** learns how each article *looks* (shape, colors, typography, label design) so it can later recognize products from the camera without a barcode.

## Mission

PARAY is the on-device visual intelligence layer for Oasis AI. It watches every product PNG placed on shelf tickets in **Design**, builds a visual signature per article, and will power **camera-first article detection** in Scan & shoot.

## What PARAY learns (v1.7.0)

| Signal | Source | Used for |
|--------|--------|----------|
| **Shape** | Alpha cutout bbox — aspect ratio, fill ratio | Pack silhouette matching |
| **Colors** | Top 3 dominant RGB from visible pixels | Brand/pack color matching |
| **Typography** | Designation word count + length | Label text density |
| **Design** | Shelf template palette (yellow/red/black) | Oasis label style context |

Each export **merges** observations — more prints = better memory.

## PARAY home (v1.9.0)

PARAY lives as his **own object** inside Oasis AI:

- **Home folder:** `files/paray_home/` (memory, sessions, logs, manifest)
- **Home UI:** Settings → **PARAY home** — violet/teal theme, separate from Oasis yellow office
- **Office link:** Design, Scan & shoot, import fingerprints — PARAY records each visit in `office_link.json`

Legacy `files/paray/` is migrated into `paray_home/` on first launch.

Repo mirror: [`paray/README.md`](../paray/README.md)

## Architecture

```
ParayAgent (singleton) + ParayHome (living folder)
├── LayoutFitAgent      → ticket placement + GPU log
├── ParayVisualIndex    → files/paray/visual_index.json
├── ParayCameraMatcher  → offline shape+color match (v1)
└── learn_events.jsonl  → training feed for v2 embeddings

Design → Generate JPEG
    → PARAY fits cutout + learns signature per article

Future Scan & shoot
    → camera frame → ParayAgent.identifyFromCamera()
    → top matches by confidence (barcode optional confirm)
```

## Option A — PC bulk fingerprints ($0)

On a PC with the repo `product_images/` folder:

```powershell
.\scripts\BUILD-PARAY-FINGERPRINTS.ps1
```

Output: `exports/paray/paray_fingerprint_index.json` (~2,892 linked PNGs).

| Backend | When | Model id |
|---------|------|----------|
| **CLIP ONNX** | numpy + onnxruntime allowed | `openai/clip-vit-base-patch32` |
| **PARAY lite** | Windows blocks Python DLLs (fallback) | `paray-lite-v1` |

**Phone import:** Settings → **Import PARAY fingerprints** → pick JSON → opens **PARAY neural load** screen. Import runs in a **foreground service** (safe with screen off or app in background). Import Gestium CSV first so barcodes resolve to articles.

Each entry includes: barcode, designation, shape/colors, and a **512-dim embedding**.

## On-device files

| Path | Content |
|------|---------|
| `files/paray/visual_index.json` | One merged signature per `articleId` |
| `files/paray/fingerprint_index.json` | CLIP/lite embeddings keyed by barcode |
| `files/paray/learn_events.jsonl` | Learn events with shape/color/typo metadata |
| `files/layout_agent/*` | Placement memory + GPU probe (shared with layout fit) |

## API (Kotlin)

```kotlin
app.paray.learnedProductCount()
app.paray.identifyFromCamera(bitmap)  // List<ParayMatch>
app.paray.getSignature(articleId)
```

## Scan & shoot (v1.8.0+)

- Lock unknown barcode → **PARAY suggestions** sheet always opens (not only when already linked)
- **Barcode rule (v1.9.1):** drop last 4 digits, compare first 9 digits on the left (`gestiumBodyKey`)
- **Let PARAY look at product** — camera teach + visual match list
- Camera preview stays bound on lock/unlock; only ML Kit barcode reading pauses
- Locked session + barcode patterns persist under `paray_home/` (survives screen off)
- Asset capture reinforces PARAY visual memory

## Phase 2 — camera recognition

- Deeper embedding cosine match on live frames
- TFLite CLIP embedder on NNAPI GPU delegate (cosine match vs `fingerprint_index.json`)
- Fuse PARAY visual match + barcode when both available
- User confirm → reinforcement

## Phase 3 — full Oasis knowledge

- Freezer / podium / promo templates
- Price typography snapshots from rendered tickets
- Cross-phone sync of PARAY index (optional, LAN)
