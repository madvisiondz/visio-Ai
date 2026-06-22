# PARAY Knowledge Fusion (Phase 5)

> **Not backup. Not restore. Not migration.** Device ↔ device shared intelligence via **PKP** (PARAY Knowledge Package).

## Goal

Multiple PARAY instances exchange knowledge and grow smarter together. Knowledge is **merged incrementally** — never blindly overwritten, never deleted, never reset.

## Storage

```
paray_home/fusion/
├── fusion_history.json    # import audit trail
├── fusion_state.json      # deviceKnowledgeId + last export/import
└── fusion_conflicts.json  # resolved overlap log
```

## PKP package (`.pkp.zip`)

```
package_manifest.json
memory/
  learn_index.json
  visual_index.json
  brand_family_index.json
knowledge/
  knowledge_articles.json
  knowledge_brands.json
  knowledge_categories.json
  knowledge_summary.json
workflows/
  workflow_summary.json
  workflow_patterns.json
recognition/
  recognition_summary.json
  failure_patterns.json
observer/
  observer_knowledge.json
  observer_summary.json
```

**Excluded:** settings, images, Room DB, CSV, logs, device/user identity beyond `deviceKnowledgeId` (knowledge lineage only).

## Components

| Class | Role |
|-------|------|
| `ParayKnowledgePackage` | PKP v1 paths + manifest constants |
| `ParayKnowledgePackageExporter` | Build ZIP from `paray_home` knowledge files |
| `ParayKnowledgePackageValidator` | Manifest + file integrity checks |
| `ParayKnowledgeFusionEngine` | Preview + execute merge on `Dispatchers.IO` |
| `ParayFusionConflictResolver` | Richer learn state, higher visual quality, accumulate stats |
| `ParayFusionStore` | `fusion_*` persistence |

## Merge rules

- **learn_index** — by `articleId`; add missing; merge captures side-by-side; prefer richer learning state
- **visual_index** — dedupe signatures; keep highest quality
- **brand_family_index** — add missing brands/families; merge counts + signatures
- **knowledge_*** — add missing keys; never reduce local counts
- **workflow_patterns** — union transitions/sequences; accumulate feature counts
- **recognition** — accumulate failure pattern counts + summary totals
- **observer** — additive knowledge keys

## UI (PARAY Home)

- **Export Knowledge** — `CreateDocument` → `.pkp.zip`
- **Import Knowledge** — `OpenDocument` → validate → preview dialog → Merge / Cancel
- **Fusion History** — expandable list from `fusion_history.json`

## Future

PKP format is designed for later **Device ↔ PARAY Server** upload without redesign.
