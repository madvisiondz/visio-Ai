# PARAY Workflow Observer — Phase 3

**Version:** Visio Ai v2.19.0  
**Component:** `ParayWorkflowObserver` + `ParayWorkflowTracker`  
**Purpose:** Learn how VisioAI is used — workflows only, never personal content.

---

## Privacy rules (non-negotiable)

| Never stored | Allowed |
|--------------|---------|
| User typed text | Screen labels (Articles, AGENT, Design…) |
| Barcode values | Feature counts (barcode scan × N) |
| Designations / prices | Navigation transitions |
| Images / camera frames | Session durations (aggregated) |
| Screenshots | Workflow patterns |

PARAY monitors **workflows**, not people.

---

## Storage

```
paray_home/workflows/
├── workflow_events.jsonl      # append-only audit trail (trimmed)
├── workflow_patterns.json     # transitions, sequences, feature counts
├── workflow_summary.json      # cached KPIs for future PARAY Home
└── screen_usage.json          # visits + average time per screen
```

---

## What PARAY learns

### Screen awareness
Visit counts and average time per screen (e.g. AGENT 42 visits, ~4 min avg).

### Workflow transitions
Pairwise hops: `AGENT → To Shoot` with observation count and confidence (low / medium / high).

### Sequences
Three-step paths: `AGENT → To Shoot → Design` after repeated use.

### Feature usage
Counts only for: barcode scan, camera batch, design export, PDF generation, CSV import, PNG import, PARAY Learn session.

### Curiosity (observe only)
- Rarely used features (1–5 uses)
- Never used features / screens
- Bottleneck screen (highest avg time, min 3 visits)
- App map edges (transition weights)

No notifications. No recommendations in V1.

---

## Wiring

| Source | Event |
|--------|-------|
| `OasisNavHost` NavController listener | Screen visits, transitions, durations |
| `ImportService` | CSV import |
| `OasisBackgroundTaskService` | PNG import |
| `CheckShootViewModel` / `ScannerViewModel` | Barcode scan (count only) |
| `CameraBatchShootViewModel` | Camera batch session (once per lock) |
| `DesignViewModel` | Design shelf export |
| `PrintViewModel` | PDF generation |
| `ParayLearnSessionViewModel` | PARAY Learn session complete |

All writes on `Dispatchers.IO` via `appScope`. Mutex prevents corrupt JSON.

---

## Performance

- No accessibility hooks
- No screen recording
- No content capture
- Event log capped at 400 lines
- Summary precomputed on each update — UI reads `workflow_summary.json`

---

## Code map

| File | Role |
|------|------|
| `ParayWorkflowModels.kt` | Types |
| `ParayWorkflowScreens.kt` | Route → screen label |
| `ParayWorkflowStore.kt` | JSON persistence |
| `ParayWorkflowObserver.kt` | Aggregates + summary |
| `ParayWorkflowTracker.kt` | Nav + feature API |
| `ParayAgent.readWorkflowSummary()` | UI cache read |

---

## Future PARAY Home

Will display most-used features, workflow map, gaps — all from cached files.
