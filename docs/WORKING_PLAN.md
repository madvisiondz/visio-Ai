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
- [x] One-by-one Share cart queue with article text attached per PNG file
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

## Phase 5 — Oasis Print desktop (cancelled)

**Superseded by in-app Design screen (v1.5.0+).** The `oasis print/` PC subproject was removed 2026-06-02. Shelf labels export on phone; see `docs/WHAT_WE_BUILT.md`.
