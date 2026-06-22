# PARAY Learn V1 — Final Specification

> Teach PARAY what trusted catalog products look like in the real world.  
> **Recognition stays in AGENT** — learning stays in **PARAY → Learn**.

## Architectural rule

PARAY already exists inside VisioAI. Responsibilities are split:

| Area | Role |
|------|------|
| **Settings → PARAY Home** | Fingerprint imports, memory, statistics, knowledge, maintenance |
| **AGENT** | Product recognition, barcode resolution, visual matching — **recognition only** |
| **PARAY tab → Learn** | Teaching workflow — where PARAY is taught |

Recognition and learning are **separate systems**. Do not convert AGENT into a learning workflow.

## Navigation

**Bottom nav:** Articles · AGENT · **PARAY** · To Share · Design · Settings (+ VisioPRO, To shoot, Batch txt)

**PARAY screen tabs:** Learn · Memory · Knowledge · Statistics — all four implemented (v2.20.4).

## Core vision

PARAY does **not** learn unknown products first. It learns **existing trusted products** already in VisioAI.

A product is eligible when:

- CSV article exists
- Barcode exists
- PNG asset exists
- Article is active

Identity already exists in Room. PARAY learns **real-world appearance** — not product identity.

The **official PNG is the canonical front reference**. Front confirmation validates the physical product matches the PNG; it is **not** stored as a learned side.

## Product eligibility

**Include:** active article + barcode + FOUND PNG (valid, uncorrupted file).

**Exclude:** missing PNG, missing barcode, inactive/deleted article, corrupted PNG.

Engine: `ParayLearnEligibility` + `listParayLearnReadyArticles()`.

## Learning queue

Auto-generated stats (PARAY KPI):

| Metric | Meaning |
|--------|---------|
| Ready for learning | Eligible trusted products |
| Learned | Left + Right + Back completed |
| Partially learned | 1–2 learned sides |
| Pending | Ready − learned |
| **Coverage %** | Learned / Ready × 100 |

## Configurable thresholds (architecture)

Confidence values are **not hardcoded** in `ParayLearnEngine`. They load from:

**File:** `paray_home/memory/learn_settings.json`

| Setting | Used for |
|---------|----------|
| `frontConfirmationThreshold` | Min PNG↔camera similarity to confirm front |
| `sideCaptureThreshold` | Min view change for left/right auto-capture |
| `backCaptureThreshold` | Min view change for back auto-capture |

Defaults: `ParayLearnSettings.factoryDefaults()` (written on first run).

**Derived (not separate settings):**

- Front mismatch cutoff = `frontConfirmationThreshold × 0.45`
- Max prior similarity for sides = `1 − sideCaptureThreshold` (back uses `backCaptureThreshold`)

**Operational constants** (not confidence thresholds) in `ParayLearnEngine` companion:

- `STABLE_FRAMES_REQUIRED = 4`
- `MISMATCH_FRAME_LIMIT = 45`
- `FRAME_STABILITY_SIMILARITY = 0.92`

Settings UI sliders — **not built in V1** (persistence + engine wiring only).

## Learning session

### Preload (before camera)

`ParayLearnPreload.load()` automatically loads:

- PNG image + shape/color features
- Existing CLIP fingerprint (if imported)
- Brand, category, family (rayon), barcode, designation
- Existing learn record + status

PARAY starts the session already knowing what product it expects.

### Step 1 — Front confirmation

Show official PNG. Open live camera. Operator: **Show Front Side**.

PARAY compares PNG asset vs live product to validate correct product, packaging, quantity, branding, variant.

When confirmed: **Front Confirmed ✓** — unlocks learning. Front is **not** stored as a learned view.

### Steps 2–4 — Left, Right, Back

After front confirmation:

| View | Progress |
|------|----------|
| Front | ✓ (validation only) |
| Left | auto-capture when stable + distinct |
| Right | auto-capture when stable + distinct |
| Back | auto-capture → **Learning Complete** |

**Camera behavior:** live analysis only — no shutter, no manual capture, no stop/start.

## Status model

| Status | Rule |
|--------|------|
| `NOT_LEARNED` | No learned sides (left/right/back) |
| `PARTIALLY_LEARNED` | 1–2 learned sides |
| `LEARNED` | Left + Right + Back completed |

Front confirmation does **not** count as a learned side.

## Stored visual knowledge

**Per product** (`learn_index.json`):

- Article ID, barcode, designation, brand, category, family
- PNG front reference path
- `frontConfirmed`, `frontConfidence` (validation metadata)
- Left / Right / Back captures (signatures + optional JPEG in `learn_views/`)
- Product / brand / family signatures
- Packaging variant flag
- Version, created/updated/learned timestamps

**Merged for AGENT** (`visual_index.json` on LEARNED):

- Best side signature merged into existing visual index

**Brand / family index** (`brand_family_index.json`):

- Aggregated brand and family relationships for future recognition

**Packaging variants** (`logs/packaging_variants.jsonl`):

- V1: log-only when front drift detected (same barcode, different packaging)
- Architecture supports future: Update Existing / Create Variant / Ignore

**Symmetric sides** (`symmetricSidesEligible` on record):

- Architecture for future skip-one-side optimization — not used in V1

## Storage layout

```
paray_home/memory/
  learn_settings.json       ← configurable thresholds
  learn_index.json          ← per-article learning status + views
  learn_views/{articleId}/  ← optional captured side JPEGs
  visual_index.json         ← merged knowledge for AGENT
  brand_family_index.json   ← brand/family relationships
  logs/packaging_variants.jsonl
```

## AGENT integration

AGENT UI unchanged. `ParayCameraMatcher.identify()` scores live frames using:

- Existing PNG / `visual_index.json` signatures
- Learned left / right / back + product signature from `learn_index.json`
- CLIP fingerprints when imported
- Boost when status = LEARNED or PARTIALLY_LEARNED

## V1 scope

**Built:**

1. PARAY screen (Learn · Memory · Knowledge · Statistics tabs)
2. Learning queue + coverage KPI
3. Product eligibility engine
4. PNG preload before camera
5. Front confirmation (PNG vs live)
6. Left / Right / Back auto-capture
7. Learning status tracking
8. Knowledge storage (`learn_index`, `visual_index`, brand/family index)
9. AGENT matcher hooks
10. Configurable thresholds (JSON + engine)
11. Packaging variant log architecture
12. **Memory tab** — read-only browse of `learn_index.json` (search + filter; no Room)
13. **Knowledge tab** — cached catalog knowledge (`knowledge_summary.json` + `knowledge_articles.json`; no Room)
14. **Statistics tab** — instant KPI dashboard from `learn_index.json`, `knowledge_summary.json`, `workflow_summary.json`

**Not built:**

- Autonomous / cloud / continuous learning
- Model retraining pipelines
- Packaging variant user actions (Update / Create / Ignore)

## Key code

```
domain/paray/
  ParayLearnModels.kt           ← status, queue stats, session context
  ParayLearnSettings.kt         ← threshold model + factoryDefaults()
  ParayLearnSettingsStore.kt    ← JSON persistence
  ParayLearnStore.kt            ← learn_index read/write
  ParayLearnQueue.kt            ← queue stats + coverage
  ParayLearnEligibility.kt      ← trusted product filter
  ParayLearnPreload.kt          ← preload before camera
  ParayLearnEngine.kt           ← state machine (settings-driven)
  ParayLearnKnowledgeModels.kt  ← signatures, variant events
  ParayBrandKnowledgeStore.kt   ← brand/family index
  ParayPackagingVariantDetector.kt
  ParayVisualSimilarity.kt
  ParayCameraMatcher.kt         ← AGENT multi-view boost

ui/screens/paraylearn/
  ParayMainScreen.kt
  ParayLearnSessionScreen.kt
  ParayLearnCameraPreview.kt
  ParayLearnViewModels.kt
  ParayMemoryRepository.kt      ← learn_index.json read-only
  ParayMemoryScreen.kt          ← Memory tab UI
  ParayMemoryViewModel.kt
  ParayKnowledgeRepository.kt   ← knowledge JSON read-only
  ParayKnowledgeScreen.kt       ← Knowledge tab UI
  ParayKnowledgeViewModel.kt
  ParayStatisticsRepository.kt  ← cached KPI JSON read-only
  ParayStatisticsScreen.kt      ← Statistics tab UI
  ParayStatisticsViewModel.kt
  ParayLearnSettingsScreen.kt   ← Settings → thresholds (v2.20.1)
```
