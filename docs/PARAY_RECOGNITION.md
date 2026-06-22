# PARAY Recognition Curiosity — Phase 4

**Version:** Visio Ai v2.21.0  
**Component:** `ParayRecognitionObserver`  
**Storage:** `paray_home/recognition/`

---

## Purpose

Teach PARAY what it does **not** understand — recognition blind spots and weak matches.

Observation only:

- No notifications
- No automatic actions
- No user prompts

---

## Storage

```
paray_home/recognition/
├── recognition_events.jsonl    # append-only event log
├── unknown_products.json       # barcodes PARAY struggles with
├── failure_patterns.json       # aggregated failure counts
└── recognition_summary.json    # cached KPIs for future UI
```

---

## Observed signals

| Signal | When | Event type |
|--------|------|------------|
| Recognition failure | AGENT camera ID returns no matches | `RECOGNITION_FAILURE` |
| Low confidence match | Top match below 55% threshold | `LOW_CONFIDENCE_MATCH` |
| Manual correction | User picks a different product than offered top match, or manual search/suffix link | `MANUAL_CORRECTION` |
| Packaging drift | Learn front drift logged via `ParayPackagingVariantDetector` | `PACKAGING_DRIFT` |
| Unknown barcode | Barcode not in Gestium catalog (AGENT / Scanner) | `UNKNOWN_BARCODE` |

Repeated manual corrections are tracked via `correctionCount` on `unknown_products.json` (same barcode).

---

## Recognition summary (cached)

`recognition_summary.json` includes:

- Total events + per-type counts
- **Most problematic products** — highest event count per barcode
- **Most frequent failures** — counts by event type
- **Most common packaging drifts** — products with drift events
- **Most corrected products** — highest manual correction count

UI reads this file only — never recomputed on screen open.

---

## Wiring

| Hook | Location |
|------|----------|
| Unknown barcode | `CheckShootViewModel.openParaySuggestions`, `ScannerViewModel.lookup` |
| Recognition failure / low confidence | `CheckShootViewModel.onParayTeachCaptureFinished` |
| Manual correction | AGENT suggestion / visual / designation / suffix confirm |
| Packaging drift | `ParayAgent.logPackagingVariant` → `ParayRecognitionTracker` |

---

## Living AI Presence (v2.21.1)

- `ParayActivityMonitor` — `StateFlow<ParayActivityState>` (IDLE / OBSERVING / PROCESSING / DISCOVERY / LEARNING / WARNING)
- Event-driven pulses from `ParayRecognitionObserver` — no polling
- `ParayActivityLed` — top-right on PARAY Home + PARAY tab (all sub-tabs)
- Home dashboard: **Recognition curiosity** card from `recognition_summary.json`
- Statistics: **Recognition intelligence** section from cached summary

---

## Code map

| File | Role |
|------|------|
| `ParayRecognitionModels.kt` | Event + aggregate types |
| `ParayRecognitionSummary.kt` | Cached summary DTO |
| `ParayRecognitionStore.kt` | JSON persistence |
| `ParayRecognitionObserver.kt` | Event → aggregates → summary |
| `ParayRecognitionTracker.kt` | Feature hooks bridge |
| `ParayHome.kt` | `recognition/` paths |
| `ParayActivityMonitor.kt` | Activity state for LED |
| `ParayActivityLed.kt` | Compose neural-activity indicator |
| `ParayPresenceLed.kt` | Top-bar LED wrapper |

---

## Architecture rules

Same as Observer / Knowledge / Workflow phases:

- React to recognition events as they happen
- Background IO + mutex (no UI blocking)
- Never scan full catalog on idle
- No personal content in events (barcode + designation only)
