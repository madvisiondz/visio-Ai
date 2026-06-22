# PARAY Knowledge Observer — Phase 2

**Version:** Visio Ai v2.18.0  
**Component:** `ParayKnowledgeObserver`  
**Trigger:** Only when `ParayObserver` detects catalog change (never on idle startup).

---

## Purpose

Transform catalog changes into lightweight **knowledge records** — not image learning, not recognition, not AI.

PARAY becomes catalog-aware by observing:

- CSV import deltas
- PNG link changes
- Learn completion events

If nothing changed → **do nothing**.

---

## Storage

```
paray_home/knowledge/
├── knowledge_state.json       # last import processed, import history
├── knowledge_articles.json      # per-article knowledge + timeline
├── knowledge_brands.json        # brand rollups (known articles only)
├── knowledge_categories.json    # category rollups (known articles only)
└── knowledge_summary.json       # cached KPIs for future PARAY Home UI
```

---

## Article knowledge

When an article appears or changes (via import delta, PNG link, or learn):

| Field | Source |
|-------|--------|
| Barcode, designation | Room article |
| Brand, category | Room article |
| Family | Room `famille` or learn record |
| PNG status | Linked `FOUND` image |
| Learn status | `learn_index.json` |
| Timeline | Created, Price Changed, Designation Changed, PNG Added, Learned, Removed |

No image processing. No embeddings.

---

## Brand / category rollups

Rebuilt from `knowledge_articles.json` after each knowledge refresh — in-memory only, not a Room scan.

Example brand summary:

- Products (known to PARAY)
- PNG coverage
- Learned count
- Missing PNG

---

## Knowledge gaps (observe only)

Cached in `knowledge_summary.json`:

- Missing PNG
- Missing learn (PNG present, not learned)
- Missing brand / category / family

**No actions, no notifications, no tasks.**

---

## Import change history

Each processed import appends to `knowledge_state.json`:

```
Import #49 — New: 34, Price: 212, Designation: 18, Removed: 2
```

Uses `import_changes` table for that import only — not a full catalog scan.

---

## Performance rules

| Rule | Implementation |
|------|----------------|
| No full rescans | Import deltas + PNG `createdAt > since` window |
| No startup scan | APP_STARTUP exits if fingerprint unchanged |
| Background only | `Dispatchers.IO` |
| UI reads cache | `ParayAgent.readKnowledgeSummary()` — prebuilt file |
| Knowledge tab UI | `ParayKnowledgeRepository` — `knowledge_summary.json` + `knowledge_articles.json` only (v2.20.3) |

---

## Wiring

| Trigger | Knowledge action |
|---------|------------------|
| CSV import | Process `getImportChanges(importId)` |
| PNG load / re-index | Update articles linked since last refresh |
| Learn complete | Update single article + timeline |
| No change | Skip |

Called from `ParayObserver.onTrigger()` after observer state is persisted.

---

## Code map

| File | Role |
|------|------|
| `ParayKnowledgeModels.kt` | Data types |
| `ParayKnowledgeStore.kt` | JSON persistence |
| `ParayKnowledgeObserver.kt` | Change → knowledge logic |
| `ParayObserver.kt` | Invokes knowledge on change |
| `ParayHome.kt` | `knowledge/` paths |
| `ParayAgent.kt` | `readKnowledgeSummary()` |

---

## Future PARAY Home

Screen will read:

- `knowledge_summary.json` — totals, coverage, gaps
- `knowledge_state.json` — recent imports
- Never recompute on screen open
