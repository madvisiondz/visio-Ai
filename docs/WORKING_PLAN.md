# Oasis AI — Working Plan

## Phase 0 — Environment ✅

- [x] Android Studio / SDK API 34
- [x] Build output off OneDrive (`%LOCALAPPDATA%/OasisAI-build`)
- [x] Project stabilized (no SDK 36 pull)

## Phase 1 — Foundation ✅ (MVP 1–2)

- [x] Room database (all tables)
- [x] CSV import + flexible column mapping
- [x] Import diff (new / price / renamed / removed)
- [x] Article catalog + search + filters
- [x] Barcode lookup
- [x] Image matching by designation
- [x] Sample CSV in assets

## Phase 2 — Production workflow ✅ (MVP 3–6)

- [x] Pre-selection cart
- [x] Print templates (shelf, freezer, podium, board)
- [x] PDF export + print batch history
- [x] Print audit snapshots
- [x] Promo flags + expiry alerts

## Phase 3 — Polish (in progress)

- [x] **v2.16.0** PARAY Learn V1 — trusted-product queue, front confirm, multi-view capture, AGENT matcher boost
- [x] **v2.16.1** Final spec alignment — see `docs/PARAY_LEARN.md`
- [x] **v2.15.6** Background foreground service — all Settings long tasks + CSV import survive screen lock
- [x] **v2.15.2** Sub-barcode flavor registry — survives Gestium purge; auto-restore on CSV import
- [x] **v2.15.0** Device transfer — purge, full backup ZIP, VisioPRO bundle, PNG export all variants
- [x] **v2.4.0** Sub-barcode confirm + deferred PhotoRoom link + per-barcode images + removable chips (Room v14)
- [x] v2.11.0 Design Historique tab + batch detail reprint; dated export paths; CSV-change glow (Room v18)
- [x] v2.5.0 Design queue order preserved; AGENT Bulk mode + `bulk_captures` (Room v11)
- [x] v2.4.0 Design Done sub-cart + per-article copy count (+ button); Room v10 `copyCount`
- [x] v1.5.0 Design screen: in-app Oasis Print shelf labels; To share → Add to Design; A4 JPEG export; History → Settings
- [x] v1.2.1 production UI: Import scroll, cart origin colors, brand theme, logo icon
- [x] GestiumERP CSV column mapping + encoding fallback (UTF-8 / Windows-1252)
- [ ] Bulk import performance test (20k rows) — deferred until phone transfer
- [x] Live CameraX + ML Kit scanner UI
- [x] Share/open exported PDF via FileProvider
- [ ] Copy PNGs into app storage — deferred (user handles large files later)
- [ ] Import second CSV to demo price-change tickets
- [x] Scanner verification panel + mark ticket OK
- [x] Import preview + filter unchanged changes
- [x] Catalog search-required + New/Price-changed filters
- [x] Percentage/status progress for long-running tasks
- [x] Re-index ANR fix: IO dispatcher + indexed matching + bulk DB transaction
- [x] Share selected PNGs as files from To share cart
- [x] Article detail shows full parsed CSV details
- [x] To shoot card tap picks ready PNG and links it into app images
- [x] Batch Stamper links many gallery PNGs with continuous barcode scanning
- [x] History bottom nav shows linked/shot/sent image records
- [x] Stamper gallery launch crash fix and zero-start event history
- [x] Stamper last-card fix, larger scrollable cards, and enriched shared PNG metadata
- [x] Replace repo image database from `G:\DATA BASE` and support barcode-only PNG filenames
- [x] v2.5.0 Design promo shelf tickets (dual price + prix-barrée)
- [x] PSD inspect pipeline — psd-tools + ag-psd → JSON specs for template cloning

---
- [x] Batch txt screen routes designation lists to To share / To shoot / **Camera batch queue** automatically
- [x] To shoot source labels/colors (Manual / Batch txt / Check & shoot / Stamper)
- [x] **Visio Ai 2.0**: To shoot bottom nav restored (2026-06-16)

## Phase 4 — Ready PNG linking (planned)

- [x] PC scripts sync PNGs to Gestium designations and barcode metadata
- [x] Remove in-app background-removal server/settings flow
- [x] Android file picker: load ready PNGs into app storage
- [x] Check & shoot flow: barcode verify existing PNG, then capture to DCIM when missing
- [x] Re-index linked images on device after PNG load

## How to test tomorrow

1. Open `android` in Cursor
2. Run `.\dev.ps1`
3. Home → **Load sample data**
4. Catalog → search → add to pre-selection
5. Pre-selection → Print → **Shelf A4** → Generate PDF
6. Print History → verify batch
7. Import a second CSV with changed prices (when ready)

## Phase 4c — PARAY Learn ✅ (V1)

- [x] **v2.16.0** PARAY tab + Learn queue + session (front confirm, L/R/B capture)
- [x] **v2.16.1** Final spec — preload, eligibility, coverage KPI, front not learned, brand/family index, packaging variant log
- [x] Configurable thresholds architecture — `learn_settings.json`, engine reads settings
- [x] **v2.20.1** Settings UI sliders for front / side / back thresholds
- [x] **v2.20.0** PARAY Home dashboard — cached summary files only
- [x] **v2.20.2** Memory tab — browse `learn_index.json`; search + filter
- [x] **v2.20.3** Knowledge tab — cached catalog knowledge JSON
- [x] **v2.20.4** Statistics tab — cached learning/knowledge/workflow KPIs

## Phase 4f — PARAY Knowledge Fusion ✅ (V1)

- [x] **v2.22.0** PKP export/import on PARAY Home; incremental merge engine; `docs/PARAY_FUSION.md`

## Phase 4e — PARAY Recognition curiosity ✅ (V1)

- [x] **v2.21.0** `ParayRecognitionObserver` — recognition blind spots; `paray_home/recognition/`

## Phase 4d — PARAY Observer + Knowledge + Workflow ✅ (V1)

- [x] **v2.17.0** `ParayObserver` — change-only curiosity; `paray_home/observer/`
- [x] **v2.18.0** `ParayKnowledgeObserver` — catalog knowledge; `paray_home/knowledge/`
- [x] **v2.19.0** `ParayWorkflowObserver` — usage patterns; `paray_home/workflows/`; no personal data
- [x] **v2.20.0** PARAY Home dashboard — cached summaries only (`ParayHomeRepository`)

## Phase 4b — VisioPRO ✅ (v2.6.3)

- [x] In-app VisioPRO module (**bottom nav 1st tab**, not Settings)
- [x] Scrollable bottom bar fix (v2.6.3)
- [x] Preset catalog: 7 families, ~46 article cards
- [x] Social + print channels; fish social-only
- [ ] PSD-based pixel-perfect layouts (when templates supplied — agent inspects → updates catalog)
- [ ] Explicit designation/codeart mapping table (currently keyword match)

## Phase 5 — Oasis Print desktop (cancelled)

**Superseded by in-app Design screen (v1.5.0+).** The `oasis print/` PC subproject was removed 2026-06-02. Shelf labels export on phone; see `docs/WHAT_WE_BUILT.md`.
