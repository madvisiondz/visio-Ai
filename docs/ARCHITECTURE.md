# Oasis AI — Architecture

## Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 (Oasis orange `#FF5E13`, dark surfaces) |
| Navigation | Navigation Compose |
| State | ViewModel + StateFlow |
| Database | Room (SQLite) — **v18** (`exportSchema = true`; migrations in `OasisDatabaseMigrations.kt`; **no destructive fallback**) |
| Images | Coil lazy previews + local `files/product_images/` + FileProvider sharing |
| Barcode | ML Kit (dependency) + manual entry |
| Print export | Android `PdfDocument` |
| Build | Gradle, API 34, Kotlin 2.2 |

## Repo layout

```
oasis ai/
├── android/                    # Android app (Oasis AI)
├── scripts/                    # CSV/image + PARAY preparation helpers
├── product_images/             # PC-side ready PNG set
├── docs/                       # Trackers
└── PROJECT.md
```

```
com.oasismall.oasisai/
├── OasisApp.kt              # Application, DI container
├── MainActivity.kt
├── data/
│   ├── db/                  # Room entities, DAOs, database
│   ├── model/               # Enums
│   └── repository/          # OasisRepository
├── domain/
│   ├── CsvParser.kt       # Streaming parse; flexible French/English headers
│   ├── ImportCatalogMaps.kt # Lightweight snapshot → barcode/codeart/name maps
│   ├── ImportService.kt     # Import + diff via ArticleImportSnapshot (no rawData load)
│   ├── ImageMatcher.kt      # IO-thread PNG matching; cached PNG index; upsert for new articles only
│   ├── flavors/             # Sub-barcode registry + purge/archive/restore (v2.15.2)
│   ├── background/          # OasisBackgroundTaskService — long tasks survive screen off (v2.15.6)
│   ├── transfer/            # Device backup ZIP, Gestium purge, VisioPRO bundle export
│   ├── design/ShelfA4Renderer.kt  # In-app shelf 12-up A4 JPEG
│   ├── paray/               # Visual agent — Learn V1, Observer, camera ID, fingerprints
│   │                        # memory/ + observer/ (observation ≠ learn/visual indexes)
│   ├── layoutagent/         # Layout fit submodule (used by PARAY)
│   └── PromoService.kt      # Expiry alerts
├── ui/
│   ├── navigation/
│   ├── components/
│   └── screens/             # Per-feature screens + ViewModels
└── util/
```

## Core rules (from product spec)

1. **Designation-first** — article name is primary identity; barcode is lookup key
2. **Offline-first** — all core flows work without internet
3. **Pre-selection workflow** — catalog → pre-selection → template → export
4. **Import diff** — never blind overwrite; compare and log changes; enriched change UI routes rows to To share / To shoot carts
5. **Print audit** — every batch stores price/designation/image snapshots

## Database tables

See `PROJECT.md` §16. Room schema is currently **v11** (`bulk_captures` for AGENT Bulk mode).

`articles.rawData` optional (null on CSV import since v2.14.7); import diff uses `ArticleImportSnapshot` without loading blobs. `product_images.createdAt` and `product_images.lastSentAt` support the office workflow for tracking when PNGs were imported/created and last shared as files.

The To shoot Stamper keeps selected gallery images as URI references until the user presses Done. This avoids loading/copying all selected PNGs into memory at once; Coil previews only visible cards, and the final copy/link work runs on `Dispatchers.IO`. The slider uses center-card detection plus trailing space so edge cards, including the last card, can become the active scan target.

PNG metadata is enriched before linking/sharing with designation, price now, previous price when known, barcode, and rayon/category.

History is event-based through `workflow_history`, not backfilled from old rows. It starts empty after the v6 schema and records new searched/scanned/cart/linked/sent actions.

## Template types

| Type | Capacity | Output |
|------|----------|--------|
| SHELF | 10 / A4 | Multi-label sheet |
| FREEZER | 1 / A4 | Large single card |
| PODIUM | 1 / A4 | Promo signage |
| BOARD | 1 / A3 | Board layout |

Seeded on first launch in `OasisApp`.

## Print export (on phone)

Shelf labels: **Design** screen → `ShelfA4Renderer` → landscape A4 JPEG (12-up) under `exports/yyyy-MM-dd/`. Design uses `DESIGN` (To print), `DESIGN_DONE`, and **Historique** (`print_batches` with templateName `Design — Shelf A4 12-up`); batch detail supports reprint with live catalog merge. `copyCount` expands repeats per article. Legacy PDF templates remain in Settings print flow.

**PSD cloning (in progress):** drop `.psd` in `templates/psd-inbox/` → `scripts/INSPECT-PSD.ps1` → JSON specs in `templates/psd-specs/` → map to Kotlin renderers. See [`docs/PSD_TEMPLATES.md`](PSD_TEMPLATES.md).

**VisioPRO (v2.6.x):** in-app module at `domain/visiopro/` + `ui/screens/visiopro/` — preset catalog, card renderer, JSON price memory; **first bottom-nav tab**. **v2.37.0:** preset product PNGs (~100 MB) **not in APK** — sideload `VisioPRO-media.zip` via Settings → `VisioProMediaStore` (`filesDir/visiopro_media/`); layout JSON stays in assets.

Phone-to-phone PNG sync: **Phone sync** (hotspot LAN, port 8776) — see `docs/PHONE_SYNC.md`.

**Architecture diagrams:** [`docs/BLOCK_DIAGRAM.html`](BLOCK_DIAGRAM.html) (A3 portrait) · [`docs/BLOCK_DIAGRAM_A4_LANDSCAPE.html`](BLOCK_DIAGRAM_A4_LANDSCAPE.html) (A4 landscape) · [`docs/FEATURES_API_A4.html`](FEATURES_API_A4.html) (features × APIs, A4 landscape) · [`docs/INVESTOR_PARAY_CLOUD.html`](INVESTOR_PARAY_CLOUD.html) / [`FR`](INVESTOR_PARAY_CLOUD_FR.html) (investor brief, PARAY cloud).

- **AGENT** tab (**v2.36+ Smart-only**): barcode scan → lock → share/design/shoot PNG; `AgentSessionStore` for lock persist; suffix picker links via `linkAlternateBarcode`; SUB-BC. (Ticket/Bulk modes removed from CheckShoot UI.)
- **Article cards**: all list/detail panels show **Rayon** (`ArticleRayonLine`) for mall location when printing or verifying tickets.
- **PARAY Ticket** (v2.30.0): `ParayTicketReader` + `ParayTicketFuzzyMatcher` + `ParayTicketFrameQuality` + `ParayTicketSnapStabilizer` — ring buffer, recovery pass, tiers, anti-flip; `ParayTicketAdvisor`; `ticket_events.jsonl`.
- **PARAY home** (`paray_home/`): memory (learn/visual), **observer**, **knowledge**, **workflows**, **recognition**, **fusion** (PKP lineage, v2.22.0), sessions, logs.
- **PARAY tab → Memory** (v2.20.2): `ParayMemoryRepository` reads `learn_index.json` only — UI never queries Room for learning status.
- **PARAY tab → Knowledge** (v2.20.3): `ParayKnowledgeRepository` reads `knowledge_summary.json` + `knowledge_articles.json` — rollups derived in memory, no SQL.
- **PARAY tab → Statistics** (v2.20.4): `ParayStatisticsRepository` reads `learn_index.json`, `knowledge_summary.json`, `workflow_summary.json` — instant KPI dashboard, no live aggregation.
- **PARAY Recognition** (v2.21.0): `ParayRecognitionObserver` → `paray_home/recognition/` on AGENT/Scanner/Learn drift events; cached `recognition_summary.json`.
- **PARAY Knowledge Fusion** (v2.22.0): `ParayKnowledgeFusionEngine` — PKP `.pkp.zip` device ↔ device merge; `paray_home/fusion/`; see `docs/PARAY_FUSION.md`.
- Bottom nav **Batch txt** for designation-list routing to To share / To shoot / **Camera batch queue** (not in CSV).

