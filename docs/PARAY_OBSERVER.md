# PARAY Observer — Phase 1 (Lightweight Curiosity Engine)

> PARAY reacts to change — it does not continuously scan the catalog.

## Purpose

Observation only. Not AI, not recognition, not learning.

PARAY builds awareness of what changed inside VisioAI:

- CSV import deltas
- PNG link changes
- Article metadata shifts
- PARAY Learn completions (event log only)

## Storage

```
paray_home/observer/
  observer_state.json      ← fingerprint between runs
  observer_events.jsonl    ← append-only observation log
  observer_knowledge.json  ← rolling last-summary knowledge
```

Separate from `learn_index.json`, `visual_index.json`, `brand_family_index.json`.

## State fingerprint

| Field | Tracks |
|-------|--------|
| `lastArticleCount` | Active articles |
| `lastPngCount` | FOUND linked PNGs |
| `lastMissingPngCount` | Active articles without PNG |
| `lastImportId` | Latest CSV import |
| `lastImportTimestamp` | Import time |
| `lastObservationRun` | Last wake time |
| `lastKnowledgeRefresh` | Last knowledge write |

## Triggers

| Trigger | When |
|---------|------|
| `APP_STARTUP` | Application `onCreate` (background) |
| `CSV_IMPORT_COMPLETED` | After successful Gestium import |
| `PNG_REINDEX_COMPLETED` | Settings re-index task |
| `PNG_LOAD_COMPLETED` | Load ready PNGs task |
| `PARAY_LEARN_COMPLETED` | Learn session completes one product |
| `ARTICLE_*` | Reserved for future hooks |

## Behavior

1. Load lightweight fingerprint (SQL `COUNT` queries only — no full table scans).
2. Compare to `observer_state.json`.
3. **If nothing changed** → update `lastObservationRun`, exit immediately.
4. **If changed** → inspect only the relevant delta, append `observer_events.jsonl`, update `observer_knowledge.json`.

### CSV curiosity

Uses import summary counts (new / price / renamed / removed) — no history re-scan.

### PNG curiosity

Estimates gained/lost PNG links from count delta + missing count.

## Performance

- All work on `Dispatchers.IO`
- Never blocks UI
- No continuous background loop
- No battery drain from polling

## PARAY Home performance

`ParayHomeViewModel` shows cached `home_display_cache.json` instantly, refreshes heavy stats on IO in background.

`learnEventCount` cached in memory after first read — incremented on append.

## Key code

```
domain/paray/
  ParayObserverModels.kt
  ParayObserverStore.kt
  ParayObserver.kt
```

Wired from: `OasisApp`, `ImportService`, `OasisBackgroundTaskService`, `ParayLearnSessionViewModel`.
