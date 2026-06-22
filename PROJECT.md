# Oasis AI — Project Report

> **Living document.** This file is the single source of truth for Oasis AI scope, architecture, and roadmap. Update it whenever requirements, modules, data models, or MVP status change.

---

## 1. Project Name

**Oasis AI** — Android-only app for **OASIS MALL**.

A local Android app to manage product images, prices, barcode lookup, printable shelf labels, promo signage, and print history. **Shelf labels and print export run on the phone** via the **Design** screen (the former Oasis Print desktop PC app was removed).

**Full build history:** see [`docs/WHAT_WE_BUILT.md`](docs/WHAT_WE_BUILT.md).

---

## 2. Main Problem

OASIS MALL operates at scale with fragmented workflows:

| Asset | Scale / State |
|-------|---------------|
| Articles | 20,000+ |
| Product PNG images | ~4,000 (background removed) |
| Price & article data | Inside **GestiumERP** |
| Label production | Manual Photoshop workflow |
| Print formats | Shelves, freezers, podiums, promo boards |

**Synchronization pain points:** article name, price, barcode, image, and printable label are hard to keep aligned.

**Biggest pain:** Manual synchronization is too slow and risky at mall scale.

---

## 3. Core Philosophy

### Old system

- **Barcode** = main identity

### New Oasis AI logic

- **Article designation/name** = primary identity
- **Barcode** = secondary lookup key

**Why?** Barcodes change when brands change packaging, seasonal designs, promo packs, new batches, or new versions. Oasis AI uses barcode mainly to *find* the article, then uses **designation/name** to connect article data with the image database.

---

## 4. Technical Direction

| Area | Choice |
|------|--------|
| 2026-06-19 | **VisioPRO**: in-app preset cards (agent-maintained catalog); CSV/manual prices; social + print export |
| 2026-06-19 | **PSD pipeline**: `tools/psd` (psd-tools) + `tools/psd-node` (ag-psd) — inspect layers/bounds → JSON for template cloning |
| 2026-06-19 | **PSD template pipeline**: Python psd-tools + Node ag-psd inspectors → `templates/psd-specs/*.json` for Design cloning |
| 2026-06-14 | **Visio Ai v2.22.0**: **PARAY Knowledge Fusion Phase 5** — PKP `.pkp.zip` export/import; incremental merge; `paray_home/fusion/`; PARAY Home Knowledge Fusion section; see `docs/PARAY_FUSION.md` |
| 2026-06-14 | **Visio Ai v2.21.1**: **PARAY Living AI Presence V1** — activity LED + `ParayActivityMonitor`; Recognition dashboard on Home + Statistics |
| 2026-06-14 | **Visio Ai v2.21.0**: **PARAY Recognition Phase 4** — `ParayRecognitionObserver`; `paray_home/recognition/`; AGENT blind-spot curiosity |
| 2026-06-14 | **Visio Ai v2.20.4**: **PARAY Statistics tab** — learning/knowledge/workflow KPIs from cached JSON; coverage bar; learning trend |
| 2026-06-14 | **Visio Ai v2.20.3**: **PARAY Knowledge tab** — browse `knowledge_summary.json` + `knowledge_articles.json`; brands/categories/families; recently learned |
| 2026-06-14 | **Visio Ai v2.20.2**: **PARAY Memory tab** — inspect `learn_index.json`; search + filter; no Room |
| 2026-06-14 | **Visio Ai v2.20.1**: **PARAY Learn settings UI** — sliders for front/side/back thresholds; persists to `learn_settings.json` |
| 2026-06-14 | **Visio Ai v2.20.0**: **PARAY Home dashboard** — reads cached `observer_summary.json`, `knowledge_summary.json`, `workflow_summary.json` only; no live aggregation |
| 2026-06-14 | **Visio Ai v2.19.0**: **PARAY Workflow Phase 3** — `ParayWorkflowObserver`; `paray_home/workflows/`; screen usage, transitions, feature counts; no personal content |
| 2026-06-14 | **Visio Ai v2.18.0**: **PARAY Knowledge Phase 2** — `ParayKnowledgeObserver`; `paray_home/knowledge/`; import deltas → article/brand/category knowledge; cached summary for future PARAY Home |
| 2026-06-14 | **Visio Ai v2.17.0**: **PARAY Observer Phase 1** — change-reactive curiosity engine; `paray_home/observer/`; PARAY Home instant cache + background refresh |
| 2026-06-14 | **PARAY Learn B4 fix**: `BrandKnowledgeProvider` read-side hook wired into `ParayCameraMatcher` (0f boost V1; scores unchanged) |
| 2026-06-14 | **PARAY Learn B3 fix**: Packaging variant detection log-only — removed UI hint from Learn session |
| 2026-06-14 | **PARAY Learn B2 fix**: Eligibility rejects undecodable PNGs via bounds-only `BitmapFactory` check — corrupt files excluded from Ready queue |
| 2026-06-14 | **PARAY Learn B1 fix**: Queue KPIs (learned/partial/pending/coverage) scoped to Ready population only — not global `learn_index` |
| 2026-06-14 | **Visio Ai v2.16.1**: **PARAY Learn V1 final spec** — PNG canonical front (not learned side); preload; coverage KPI; eligibility engine; brand/family index; packaging variant log; status from L+R+B only |
| 2026-06-22 | **Visio Ai v2.16.0**: **PARAY Learn V1** — bottom-nav PARAY tab; trusted-product queue; front PNG confirmation; auto left/right/back capture; `learn_index.json`; AGENT matcher uses multi-view knowledge |
| 2026-06-22 | **PARAY Learn (arch)**: Configurable thresholds in `learn_settings.json` — `frontConfirmationThreshold`, `sideCaptureThreshold`, `backCaptureThreshold`; engine reads settings (no hardcoded confidence) |
| 2026-06-21 | **Visio Ai v2.15.6**: Background foreground service for all long tasks — sync sub-PNGs, re-index, exports, backup, CSV import, purge, load PNGs; safe to lock screen; notification progress |
| 2026-06-21 | **Visio Ai v2.15.5**: Sub-PNG metadata-first — `Designation1.png` naming; auto-detect from PNG tags; **Sync sub-PNGs**; excluded from main image index |
| 2026-06-21 | **Visio Ai v2.15.4**: ZIP exports (backup + VisioPRO) — user picks save location via system picker; stream copy; cache cleanup |
| 2026-06-21 | **Visio Ai v2.15.3**: VisioPRO — checkbox selection on social + print lists; **Exporter sélection** saves each rendered card as PNG (not A4) |
| 2026-06-21 | **Visio Ai v2.15.2**: Sub-barcode flavor registry — survives Gestium purge; auto-restore after CSV import; PNG `ParentBarcode` metadata |
| 2026-06-21 | **Visio Ai v2.15.1**: VisioPRO print-tab photos linked to catalog PNGs for To share (one-time scan + manual sync) |
| 2026-06-21 | **Visio Ai v2.15.0**: Device transfer — purge Gestium catalog; full backup ZIP import/export; VisioPRO preset bundle; PNG export includes all sub-barcode variants |
| 2026-06-21 | **Visio Ai v2.14.8**: CSV import speed — lightweight `ArticleImportSnapshot` (no `rawData` blobs); streaming CSV parse; PNG index cache; re-import should reach ~5s again |
| 2026-06-21 | **Visio Ai v2.14.7**: CSV import speed — drop per-row `rawData` build (~28k rows); skip full image re-index on re-import (only link new articles) |
| 2026-06-21 | **Visio Ai v2.14.3**: VisioPRO list prices from CSV via catalog link; manual override traced (`manualPriceOverridden` + CSV baseline); CSV import — single catalog load (was 3×) + faster import history counts |
| 2026-06-14 | **Visio Ai v2.13.0**: CSV import reports scoped to **Rayons importants** — preview, last import, history, detail, Report summaries |
| 2026-06-14 | **Visio Ai v2.12.9**: VisioPRO article card — adjustable **Taille désignation** slider per article (saved in memory) |
| 2026-06-14 | **Visio Ai v2.12.8**: VisioPRO print preset photos use **contain** fit (full image visible, no crop) |
| 2026-06-14 | **Visio Ai v2.12.7**: VisioPRO print codes from CSV — last 3 of Code-barres; Gestium Code for CA: rows; fix studio sampleCode overriding all labels |
| 2026-06-14 | **Visio Ai v2.12.5**: VisioPRO inline prix sur liste catégorie · clavier décimal · scroll vers champ · Enregistrer fixe en haut · ordre popup non dismissible + brouillon live |
| 2026-06-14 | **Visio Ai v2.12.3**: Fix 1118 Gestium rows skipped (empty Code-barres) — identity from Gestium Code inside parseRow; cp1252 first |
| 2026-06-14 | **Visio Ai v2.12.1**: VisioPRO rayon pools **full resync on every CSV import**; import barcode-less Gestium rows via **codeart** (e.g. PIEDS DE VEAU); list editor reloads live pool |
| 2026-06-14 | **Visio Ai v2.12.0**: VisioPRO lists auto-sync new CSV articles (pending in Settings); **Rayons importants** checklist filters stats + CSV report |
| 2026-06-14 | **Visio Ai v2.11.1**: Fix CSV price-change glow (ArticleCard + cart mapping); To share footer — Printed / Design / Telegram status |
| 2026-06-14 | **Visio Ai v2.11.0**: Design **Historique** tab — print batches by date/time folder; batch detail with reprint, load queue, send to Design/To share; CSV-changed articles glow |
| 2026-06-20 | **Visio Ai v2.10.0**: Studio preset pro+ — pinch zoom, resize handles, snap guides, align tools, tab Calques/Réglages |
| 2026-06-20 | **Visio Ai v2.9.0**: Studio preset pro — hub cards, undo/redo, debounced preview, dirty guard, inspecteur enrichi |
| 2026-06-20 | **Visio Ai v2.8.1**: Designer price slider wired to render; print tab **Charger l'image**; désignation + code noir (impression) |
| 2026-06-20 | **Visio Ai v2.7.1**: Fix VisioPRO order sheet — drag on ⋮⋮ handle works; ↑↓ fallback |
| 2026-06-20 | **Visio Ai v2.7.0**: VisioPRO Settings — edit article lists per card (rayon pools) + drag reorder popup |
| 2026-06-20 | **Visio Ai v2.6.9**: Fix Articles rayon filter — CSV **Rayon** column (not Famille); DB `rayon` + migration backfill |
| 2026-06-19 | **Visio Ai v2.6.8**: Articles screen — rayon filter chips (all rayons from CSV); search scoped to selected rayon |
| 2026-06-19 | **Visio Ai v2.6.5**: F&V print preset from `abricot example.psd` — Arabic designation, red price, `code : XX`, catalog photo |
| 2026-06-19 | **Visio Ai v2.6.3**: Fix bottom nav — custom scrollable tab row (8 tabs visible + swipe); version in Settings; nav smoke tests |
| 2026-06-19 | **Visio Ai v2.6.2**: VisioPRO first in bottom nav; swipeable bar; F&V A4×4 print queue + To share; exit without losing data |
| 2026-06-19 | **Visio Ai v2.6.1**: AIL PSD template for F&L social (985×1311); 72 CSV articles; daily photo + layered render |
| 2026-06-19 | **Visio Ai v2.6.0**: **VisioPRO** in-app module — fruits/légumes/boucherie/poisson presets (7 families); social + print; CSV/manual prices; gallery export |
| 2026-06-19 | **Visio Ai v2.5.1**: Promo shelf ticket — standard red, grouped prix-barrée |
| 2026-06-18 | **Visio Ai v2.4.7**: PhotoRoom import **Pick PNG** — manual file picker when PhotoRoom export fails; renames to article gallery |
| 2026-06-17 | **Visio Ai v2.4.6**: Manual barcode entry on every camera scanner (AGENT, Camera batch, Settings scanner) |
| 2026-06-17 | **Visio Ai v2.4.5**: Cart rows keyed by **variant barcode** — parent + each sub-flavor coexist in To share & Design; PhotoRoom sub-bc import auto-adds that flavor to To share |
| 2026-06-17 | **Visio Ai v2.4.4**: Shelf label export **300 DPI** + **JPEG 100%**; full-res product decode on tickets |
| 2026-06-17 | **Visio Ai v2.4.3**: Fix Articles search crash — unique list keys for sub-barcode rows; safer search expansion |
| 2026-06-17 | **Visio Ai v2.4.2**: PhotoRoom import list — **Remove** button per pending card |
| 2026-06-17 | **Visio Ai v2.4.1**: New sub-barcodes always require a photo (no add-without-image); existing sub-barcodes unchanged |
| 2026-06-17 | **Visio Ai v2.4.0**: Sub-barcode **confirm → shoot? → PhotoRoom import → link**; per-sub-barcode images; articles search shows designation twice; removable sub-barcode chips |
| 2026-06-16 | **Visio Ai v2.3.7**: PhotoRoom import auto-adds linked PNGs to **To share** (removed from pending list only after cart add) |
| 2026-06-16 | **Visio Ai v2.3.6**: Unified scrollable **ArticleActionPanel** (price/print history, SUB-BC, batch shoot); sub-barcode auto-link on camera batch scan |
| 2026-06-16 | **Visio Ai v2.3.5**: Fix Import detail empty list — skip enriching 26k UNCHANGED rows; meaningful-only query |
| 2026-06-16 | **Visio Ai v2.3.4**: Import change list shows product thumbnail (or “No image”); **Add to To share** / **Add to To shoot** from Import detail + Report |
| 2026-06-16 | **Visio Ai v2.3.3**: Camera batch **known-article fast path** (skip scan → shoot → PhotoRoom wait); PhotoRoom import index + all-pending queue |
| 2026-06-16 | **Visio Ai v2.3.1**: Settings **PhotoRoom folder** selector (SAF tree URI); Camera batch import reads from chosen folder or default `Pictures/Photoroom` |
| 2026-06-16 | **Visio Ai v2.3.0**: Batch txt **3-way routing** (To share / To shoot / **Camera batch queue**); scan-first camera batch (like AGENT) with create designation, CSV match → Proceed (sub-barcode) or Add to To share; queue auto-advance; Room `batch_camera_queue` (v13) |
| 2026-06-16 | **Visio Ai v2.2.0**: Settings **Export PNG database** → `Download/VisioAi/Product_images[date]/`; **Camera batch** on Batch txt → shoot to `Batch_images[date]/`, PhotoRoom import from `Pictures/Photoroom/` |
| 2026-06-14 | **MadVision** private GitHub repo initialized; app display name → MadVision; source-only `.gitignore` |
| 2026-06-07 | Android v2.5.2: Bulk saves PNGs to **DCIM/BULK** + `bulk_done.txt` tracker |
| 2026-06-07 | Android v2.5.1: Bulk mode detects existing catalog PNGs via `product_images` (same as Smart) |
| 2026-06-07 | Android v2.5.0: Design queue **sortOrder preserved** on print/Done; AGENT **Smart/Bulk** mode — bulk barcode-only captures in `bulk_captures` + `bulk_images/` |
| 2026-06-16 | **Visio Ai 2.0**: white-label fork (`com.oasismall.visioai`); metallic theme + logo; To shoot tab restored; Batch txt → To shoot; AGENT scan card shows price + last price change; APK `VisioAI-debug.apk` |
| 2026-06-06 | Android v2.4.4: Create asset returns to article detail; PARAY body key **5 digits**; designation search on unknown barcode |
| 2026-06-06 | Android v2.4.3: Article detail **Create asset** — opens AGENT camera + cutout for any article |
| 2026-06-06 | Android v2.4.2: Design **Done** list capped at **50** articles (oldest auto-removed) |
| 2026-06-06 | Expanded `docs/WHAT_WE_BUILT.md` — full reference (screens, DB, flows, scripts, versions) |
| 2026-06-02 | Android v2.4.1: Design copy stepper **− / +** (decrease/increase, min 1, max 99) |
| 2026-06-02 | Android v2.4.0: Design **Done** sub-cart (print/send → Done, pull up); per-article **copy count** (+) expands shelf labels |
| 2026-06-02 | Android v2.3.1: Design **Send info** includes price; **Import prices** parses PC-checked message back into catalog |
| 2026-06-02 | **Removed Oasis Print PC** (`oasis print/`); `docs/WHAT_WE_BUILT.md` single project summary; stripped PC LAN/cloud sync from Android |
| 2026-06-02 | Android v2.2.1: Design queue **Send info** — share designation + barcode list as text (Telegram) |
| 2026-06-02 | Android v2.2.0: Articles **smart search** (any-word match); **Report** screen — CSV diffs + Design prints |
| 2026-06-02 | Android v2.1.0: AGENT **SUB-BC** — lock main barcode, scan flavor/color alternates into `article_alternate_barcodes`; nav icon 1.8× |
| 2026-06-02 | Android v2.0.1: Design — 12-up shelf (2×6), tap image for details, template → direct print output |
| 2026-06-02 | Android v2.0.0: **AGENT** tab replaces Scan & shoot; removed To photograph + Stamper; animated lens nav icon |
| 2026-06-02 | Android v1.9.3: Body-key / linked barcodes resolve as catalog articles (Add to share/Design); `resolveScannedBarcode` |
| 2026-06-02 | Android v1.9.2: PARAY teach no longer freezes (visual index cache + background identify); Add to Design on locked Scan & shoot |
| 2026-06-02 | Android v1.9.1: Scan & shoot lock/unlock camera fix; PARAY barcode body-key (drop last 4, compare first 9 left) |
| 2026-06-06 | Android v1.9.0: **PARAY home** — `paray_home/` living folder, unique UI, Oasis office link; repo `paray/` |
| 2026-06-06 | Android v1.8.0: PARAY in Scan & shoot — persistent lock/session, barcode suggestions (last-4), camera teach, friendlier UX |
| 2026-06-05 | Android v1.7.1: **PARAY neural load screen** — foreground service import; background-safe; neural stats + growth UI |
| 2026-06-05 | **PARAY Option A (PC):** `scripts/BUILD-PARAY-FINGERPRINTS.ps1` bulk-fingerprints `product_images/` → `exports/paray/paray_fingerprint_index.json`; Settings import on phone |
| 2026-06-05 | Android v1.7.0: **PARAY** visual agent — learns shape/colors/typography per article; camera ID foundation; see `docs/PARAY.md` |
| 2026-06-05 | Android v1.6.0: **Layout Fit Agent** — cutout-aware image fit, placement memory, GPU learning log; see `docs/LAYOUT_AGENT.md` |
| 2026-06-05 | Android v1.5.5: Centered designation/price on yellow ticket; images always scale to full 4 cm height |
| 2026-06-05 | Android v1.5.4: Designation max-fit text; image padding; JPEG quality 80; share print as document file |
| 2026-06-05 | Android v1.5.3: Shelf tickets — yellow 10×4 cm, image height 4 cm, row gaps, designation auto-size by word count |
| 2026-06-05 | Android v1.5.2: Shelf export **landscape A4** JPEG; Design queue editable price per article before print |
| 2026-06-05 | Android v1.5.1: Shelf JPEG matches reference template — full-bleed 2×5, 50/50 image+yellow, huge red price + DA, no header/borders |
| 2026-06-05 | Android v1.5.0: **Design** screen (in-app Oasis Print shelf labels) — bottom nav replaces History; To share → Add to Design; A4 JPEG export (`shelf_10up` 2×5) |
| 2026-06-04 | Android v1.4.0: **Phone sync (hotspot)** — master/slave LAN send-receive; catalog compare + delta PNG push (port 8776, no cloud) |
| 2026-06-04 | Android v1.3.5: Articles search by designation + barcode + codeart; Scan & shoot suffix picker full-screen with editable barcode digits + Drop 3/4 + Search |
| 2026-06-04 | Android v1.3.4: Scan & shoot suffix barcode linking — unknown barcode on lock → scrollable picker of catalog articles sharing 9–10 digit suffix; saves alternate barcode |
| 2026-06-04 | Android v1.3.3: Create asset auto cutout (rembg-correct ONNX), cutout-only preview, no sliders; Create asset when PNG exists; Scanner → Create asset |
| 2026-06-04 | Android v1.3.2: **U2NetP ONNX model bundled in APK** (`assets/u2netp.onnx`); ONNX Runtime primary, TFLite fallback; no download script required for bg removal |
| 2026-06-02 | Android v1.3.1: Scan & shoot **Create asset** → camera → auto offline bg removal → accept links PNG to barcode/To share |
| 2026-06-02 | Android v1.3.0: Offline U2NetP background removal module (`BackgroundRemovalService`), transparent PNG + original backup, article detail + Settings UI |
| 2026-06-02 | Android v1.2.4: Bottom nav **Scan** replaced with **Scan & shoot** (full-screen check_shoot); legacy scanner remains in Settings |
| 2026-06-02 | Android v1.2.3: App launcher icon updated to official Oasis orange-on-white logo |
| 2026-06-02 | Android v1.2.2: Check & shoot full-screen camera route; lock barcode → Add to To share |
| 2026-06-02 | Android v1.2.1: production UI (Oasis orange theme, logo icon); Import scroll fix; To share origin colors; cart source tags wired from all entry points |
| 2026-06-02 | Android v1.2.0: IMAGE ASSETS folder load now auto-processes entire folder in 500-file batches until complete; existing PNGs are skipped (no copy) |
| 2026-06-02 | Android v1.1.8: **To shoot** adds Check & shoot (live barcode check for existing PNG preview; if missing, capture via system camera and save shot to DCIM/OasisAI) |
| 2026-06-02 | Android v1.1.7: shared document filenames now keep designation words with spaces; only forbidden filename characters are stripped |
| 2026-06-02 | Android v1.1.6: Dedicated `OasisShareFileProvider` + `.oasis` internal share files force Telegram document mode for selected/all shares (displayed as `.png`) |
| 2026-06-02 | Android v1.1.5: To share adds **Share selected as files** button; checked articles are shared as document files (not compressed photos) |
| Platform | Android-only |
| Data model | Fully local / offline-first (main app works without internet) |
| Image prep | Ready PNGs from import/Stamper; optional **on-device** U2NetP background removal (v1.3, offline) |
| compileSdk / targetSdk | **34** (Android 14 — stable) |
| minSdk | 26 |
| Language | Kotlin |
| UI | Jetpack Compose |
| Database | Room |
| Scanner | CameraX + ML Kit Barcode Scanner |
| Images | Local file storage (PNG) |
| Export | PDF / image for printing |

**Dev environment:** Cursor as main coding environment; Android SDK and build tools still required.

---

## 5. Data Source — GestiumERP

POS/cashier system: **GestiumERP** (exports article data).

### Preferred export format

- CSV / TSV / delimiter-based / Excel-compatible table

### Avoid if possible

- Plain TXT based on spacing (fragile parsing)

### Ideal CSV structure

One row per article. Possible columns:

| Column | Notes |
|--------|-------|
| `barcode` | Lookup key |
| `designation` | Primary identity |
| `price` | Current price |
| `reference` / `code` | Internal ref |
| `category` | |
| `stock` | |
| `brand` | |
| `unit` | |
| `last_update_date` | |

---

## 6. Daily Import + Comparison System

Oasis AI must **not** simply replace yesterday’s file. It should:

1. Import new GestiumERP export
2. Keep old database version
3. Compare old vs new
4. Detect changes

### Change detection

| Change type | Example |
|-------------|---------|
| Price changed | Nutrifit Peanut Butter: 390 DA → 420 DA → **Needs new ticket** |
| New articles added | |
| Removed / inactive articles | |
| Renamed articles | |
| Missing images | |
| Articles needing new print | |

This is one of the most important modules.

---

## 7. Barcode Scanner Workflow

**Use case:** Walking shelves, verifying tickets.

1. Open scanner
2. Scan product barcode
3. Search barcode in local GestiumERP database
4. Find designation and price
5. Match image from local image database using **designation**

### Result screen shows

- Product image
- Designation
- Today’s price
- Barcode
- Change status
- Whether ticket may need update
- Whether image exists or is missing
- Actions: add to **To shoot** list or **To share** list

**Rule:** **To share** is disabled when the article has no linked image.

**Purpose:** Instantly check if a shelf ticket is outdated.

---

## 8. Product Image System

### Existing assets

- Updated image database copied from `G:\DATA BASE`
- 2,892 PNGs linked/renamed into top-level `product_images/`
- 1,064 valid PNGs kept in `product_images/not found/` for unmatched/manual review
- Source files may be pure barcode names; scripts and Android metadata reader support barcode-only filenames and barcode suffixes

### Matching logic

| Layer | Role |
|-------|------|
| Product designation | Primary |
| Barcode | Links to article |
| Image file | Links to normalized designation |

**Example filename:** `COCA COLA 3L.png`

### App states

- Image found
- Image missing
- Image needs review
- Multiple possible image matches

---

## 9. Missing Image Workflow

When an article has no image:

1. Scan barcode
2. App finds designation/info from GestiumERP database
3. User prepares/removes background in an external app
4. User opens **To shoot**
5. User taps **Stamper** and selects the latest ready PNGs from the gallery
6. Scanner stays open while the user slides through image cards
7. Each scanned barcode fills the current image card; previous cards keep their saved barcode/article if the user slides back
8. **Done** copies linked PNGs into `files/product_images/`, renames with designation/barcode rules, writes barcode + CSV detail metadata, removes completed articles from **To shoot**, moves them to **To share**, and makes them visible in **History**

---

## 10. Printable Formats

Oasis AI manages a full retail signage ecosystem, not only small price labels.

### Template categories

| Category | Use | Notes |
|----------|-----|-------|
| **Shelf labels** | Normal shelves | Small; ~10 articles per A4 sheet; high quantity; minimize paper waste |
| **Freezer price cards** | Open freezers | Larger; better visibility for cold sections |
| **Promotional podium signage** | Podiums in aisle centers | ~1 m × 1 m podium; A4 holder/frame; bigger image + price; marketing visibility |
| **Boards / cards / custom** | Various | A7, A4, A3, B2, custom dimensions |

---

## 11. Pre-selection Workflow

**Critical rule:** User must **not** choose a template first.

### Correct workflow

```
Full catalog → Pre-selection → Template → Export
```

| Step | Action |
|------|--------|
| 2026-06-02 | Android v1.2.0: IMAGE ASSETS folder load now auto-processes entire folder in 500-file batches until complete; existing PNGs are skipped (no copy) |
| 2026-06-02 | Android v1.1.8: **To shoot** adds Check & shoot (live barcode check for existing PNG preview; if missing, capture via system camera and save shot to DCIM/OasisAI) |
| 2026-06-02 | Android v1.1.7: shared document filenames now keep designation words with spaces; only forbidden filename characters are stripped |
| 2026-06-02 | Android v1.1.6: Dedicated `OasisShareFileProvider` + `.oasis` internal share files force Telegram document mode for selected/all shares (displayed as `.png`) |
| 2026-06-02 | Android v1.1.5: To share adds **Share selected as files** button; checked articles are shared as document files (not compressed photos) |
| 1 | Search / scan articles from full 20,000+ catalog |
| 2 | Add to **Pre-selection** (working cart) |
| 3 | Choose template **after** selecting articles |
| 4 | Template uses **only** pre-selected items |
| 5 | Adjust placement — remove, reorder, pick items for final print |
| 6 | Export ready-to-print file |

**Wrong workflow:** Template → full catalog search

---

## 12. Print Generator

Replaces much of the Photoshop workflow.

### Outputs

- A4 shelf sheets
- Freezer cards
- Promo cards
- Boards
- Podium signage
- Custom print layouts

### Export formats

- PDF
- PNG / JPG (print-ready image)

### Template capacity examples

| Template | Capacity |
|----------|----------|
| Shelf A4 | 10 articles per sheet; auto-fill; multiple sheets if >10 |
| Freezer card | 1 article per card |
| Promo board | 1 article or campaign per board |

---

## 13. Print Audit / Traceability

Every generated/printed batch is stored in history — a **security camera for printing**.

### Print history records

- Batch ID
- Date/time
- Template used
- Articles included
- Prices at that moment
- Product images used
- Exported file preview
- Promo or normal status
- User/device (if needed)
- Print status: generated / printed / placed

**Purpose:** Prove when and what was printed (e.g. “Marketing did not print this ticket” → show batch from yesterday with price snapshot).

**Feature names:** Print Traceability / Print Audit Log

---

## 14. Promo Lifecycle System

Promo prints are temporary.

### Print flags

- Promo / Campaign / Offer / Temporary

### Promo fields

- Promo flag/toggle
- Start date
- Expiry date
- Days remaining
- Campaign name
- Status

### Status examples

- Active
- Expires tomorrow
- Expired
- Needs removal
- Needs replacement

### Notifications

- “Promo expires today”
- “3 promo signs expired”
- “Check this article”
- “This promo ticket is dead now”

Oasis AI is a **signage monitoring system**, not only a print app.

---

## 15. App Modules

| # | Module | Purpose | Key features |
|---|--------|---------|--------------|
| 1 | **Import Center** | GestiumERP CSV import | Import latest file; validate columns; preview rows; compare with previous import; show changed/new articles |
| 2 | **Article Catalog** | Searchable database | Search by designation; filter by price changes, missing image, new articles; article details |
| 3 | **Barcode Scanner** | Shelf verification | Scan; show article info, price, image, update status |
| 4 | **Image Manager** | Product image DB | Match by designation + barcode metadata; missing images; attach ready PNGs manually |
| 5 | **Pre-selection** | Working cart | Add/remove/reorder; group by purpose; prepare for template |
| 6 | **Template Builder / Print Generator** | Print output | Shelf/freezer/podium/board templates; auto-fill from pre-selection; preview; export PDF/image |
| 7 | **Print History** | Audit | Store every batch; search by article/date/template; preview old exports; proof records |
| 8 | **Promo Tracker** | Temporary offers | Promo flag; expiry; notifications; expired promo list |

---

## 16. Local Database Tables (Room)

### `articles`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `barcode` | |
| `designation` | |
| `price` | |
| `category` | |
| `brand` | |
| `stock` | |
| `unit` | |
| `raw_data` | All parsed CSV columns for full article detail display |
| `source_import_id` | FK |
| `last_seen_at` | |
| `normalized_name` | For image matching |

### `imports`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `file_name` | |
| `imported_at` | |
| `row_count` | |
| `status` | |

### `article_price_history`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `article_id` | FK |
| `old_price` | |
| `new_price` | |
| `import_id` | FK |
| `changed_at` | |

### `product_images`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `article_id` | FK |
| `designation_key` | |
| `barcode` | Barcode stored/read from PNG metadata |
| `image_path` | |
| `image_status` | found / missing / review |
| `created_at` | Image file creation/import timestamp |
| `last_sent_at` | Last time image was shared as a file |

### `workflow_history`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `event_type` | searched / scanned / added to cart / linked image / sent |
| `article_id` | Optional FK |
| `designation_snapshot` | Optional article name at time of event |
| `barcode_snapshot` | Optional barcode at time of event |
| `detail` | Search text or event detail |
| `created_at` | Event time; starts empty on new schema and records new actions only |

### `preselection_items`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `article_id` | FK |
| `added_at` | |
| `note` | |
| `intended_template_type` | |

### `print_templates`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `name` | |
| `type` | shelf / freezer / podium / board |
| `size` | |
| `capacity` | |
| `layout_config` | JSON |

### `print_batches`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `template_id` | FK |
| `created_at` | |
| `export_path` | |
| `preview_path` | |
| `is_promo` | |
| `promo_start` | |
| `promo_end` | |
| `status` | generated / printed / placed |

### `print_batch_items`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `batch_id` | FK |
| `article_id` | FK |
| `designation_snapshot` | |
| `price_snapshot` | |
| `image_snapshot_path` | |

### `promo_alerts`

| Column | Type / notes |
|--------|----------------|
| `id` | PK |
| `batch_id` | FK |
| `alert_date` | |
| `status` | |

---

## 17. MVP Plan

| MVP | Name | Goal | Deliverables |
|-----|------|------|--------------|
| **1** | Data + Search | Prove core database works | Import GestiumERP CSV; save articles locally; search by designation; scan barcode; show name + price; basic image matching |
| **2** | Change Detection | Daily diff | Import new file; compare with old; detect price changes & new articles; “needs ticket update” |
| **3** | Pre-selection | Working cart | Add/remove articles; manage selected list; prepare for printing |
| **4** | Simple Print Generator | First printable output | One A4 shelf template; 10 articles/sheet; export PDF or image; save print batch history |
| **5** | More Templates | Full signage | Freezer cards; podium A4; boards; promo layouts |
| **6** | Audit + Promo | Traceability & lifecycle | Print history; promo expiry; expired alerts; searchable proof records |
| **7** | Ready PNG Linking | Missing image workflow | Pick ready PNG; scan barcode; save with designation name + barcode metadata |

### MVP status tracker

| MVP | Status | Notes |
|-----|--------|-------|
| 1 | Done | Import, search, scanner, image match — pending real CSV |
| 2 | Done | Import diff engine + change log |
| 3 | Done | Pre-selection cart |
| 4 | Done | Shelf A4 PDF + batch history |
| 5 | Done | Freezer, podium, board templates |
| 6 | Done | Print audit + promo alerts |
| 7 | Planned | Gallery picker + barcode scan naming/linking; background removal is external |

---

## 18. Development Strategy

**Do not start with everything.**

### Phase 1 (foundation)

GestiumERP import → local article database → search → barcode scanner

Everything else depends on this.

### Phase 2 (production)

Pre-selection → print generator → audit log → promo tracking

This order avoids rebuilding later.

---

## 19. Final Definition

**Oasis AI** is an Android-only, local, retail signage management app for OASIS MALL.

It:

- Imports article data from GestiumERP
- Compares daily price changes
- Lets users search or scan products
- Links articles to product images (designation-first)
- Builds pre-selected print lists
- Generates ready-to-print shelf / freezer / podium signage
- Tracks print history as proof
- Manages promo expiry alerts

---

## Changelog

| Date | Change |
|------|--------|
| 2026-06-14 | **Visio Ai v2.22.0**: PARAY Knowledge Fusion Phase 5 — PKP `.pkp.zip`; `ParayKnowledgeFusionEngine` + `ParayFusionConflictResolver`; export/import on PARAY Home; `paray_home/fusion/`; incremental merge only; see `docs/PARAY_FUSION.md` |
| 2026-06-14 | **Visio Ai v2.21.0**: PARAY Recognition Phase 4 — `ParayRecognitionObserver` + `paray_home/recognition/`; failures, low confidence, corrections, drift, unknown barcodes; see `docs/PARAY_RECOGNITION.md` |
| 2026-06-14 | **Visio Ai v2.20.4**: PARAY Statistics tab — learn_index + knowledge_summary + workflow_summary KPIs; coverage bar; learning trend list |
| 2026-06-14 | **Visio Ai v2.20.3**: PARAY Knowledge tab — global summary, brand/category/family rollups, recently learned; cached JSON only |
| 2026-06-14 | **Visio Ai v2.20.2**: PARAY Memory tab — browse `learn_index.json`; search (barcode/designation/brand); filter All/Learned/Partial/Pending; side flags + last date; `ParayMemoryRepository` (no Room) |
| 2026-06-14 | **Visio Ai v2.20.1**: PARAY Learn settings screen — front/side/back threshold sliders; immediate save to `learn_settings.json` |
| 2026-06-14 | **Visio Ai v2.20.0**: PARAY Home intelligence dashboard — Observer / Knowledge / Workflow summary cards + quick actions; file-read only via `ParayHomeRepository` |
| 2026-06-14 | **Visio Ai v2.19.0**: PARAY Workflow Phase 3 — `ParayWorkflowObserver` + `paray_home/workflows/`; navigation/feature patterns only; cached summary; see `docs/PARAY_WORKFLOW.md` |
| 2026-06-14 | **Visio Ai v2.18.0**: PARAY Knowledge Phase 2 — `ParayKnowledgeObserver` + `paray_home/knowledge/`; article timelines; brand/category rollups; gaps + cached summary; change-only via `ParayObserver`; see `docs/PARAY_KNOWLEDGE.md` |
| 2026-06-14 | **PARAY Learn V1 final spec (v2.16.1)**: Front = canonical PNG only; `ParayLearnPreload`; `ParayLearnEligibility`; coverage KPI; brand/family index; packaging variant log; status L+R+B; AGENT uses productSignature + sides |
| 2026-06-22 | **PARAY Learn thresholds (arch)**: `ParayLearnSettings` + `paray_home/memory/learn_settings.json` — `frontConfirmationThreshold`, `sideCaptureThreshold`, `backCaptureThreshold`; `ParayLearnEngine(settings)` |
| 2026-06-22 | **Visio Ai v2.16.0**: PARAY Learn V1 — `paray` bottom nav; queue from articles+PNG+barcode; front confirm vs PNG; auto L/R/B capture; `learn_index.json`; AGENT `ParayCameraMatcher` multi-view boost |
| 2026-06-21 | **Visio Ai v2.15.6**: `OasisBackgroundTaskService` — foreground + wake lock for sync sub-PNGs, re-index, PNG export, backup import/export, VisioPRO bundle, purge, sample data, load PNGs, CSV import; notification progress; screen-off safe |
| 2026-06-21 | **Visio Ai v2.15.4**: Device transfer ZIP exports — **CreateDocument** picker (user chooses folder/file); no more silent Downloads/MediaStore; stale export cache cleaned on launch |
| 2026-06-21 | **Visio Ai v2.15.3**: VisioPRO category list — checkboxes on social + print tabs; **Exporter sélection** → individual rendered PNGs to `DCIM/VisioPRO/Social` or `Print` |
| 2026-06-21 | **Visio Ai v2.15.2**: Sub-barcode flavor registry (`sub_barcode_registry.json`) — archived before purge; auto-restore on CSV import; Settings **Restore sub-barcode flavors**; PNG metadata `ParentBarcode` + `VariantType=sub` |
| 2026-06-21 | **Visio Ai v2.15.0**: Settings **Device transfer** — purge Gestium catalog; export/import full `VisioAi_backup_*.zip`; VisioPRO preset bundle per section; PNG export all variants |
| 2026-06-21 | **Visio Ai v2.14.8**: CSV import — `getArticlesImportSnapshot()` (no `rawData`); streaming line parse; PNG index cache; re-import ~5s target restored |
| 2026-06-21 | **Visio Ai v2.14.7**: CSV import — drop per-row `rawData` build; image linking only for new articles on re-import |
| 2026-06-14 | **Visio Ai v2.13.0**: CSV import/report UI shows only **Rayons importants** when configured — preview sample rows, post-import counts, import history, detail list, Report import summaries (full catalog still imported) |
| 2026-06-14 | **Visio Ai v2.12.8**: fv_print photo slot **`contain`** — scale to fit width or height without cropping |
| 2026-06-14 | **Visio Ai v2.12.6**: VisioPRO article card — **Désignation (AR)** editor with live preview; swipe card RTL/LTR for next/prev in filtered list; persisted in `visio_pro_memory.json` |
| 2026-06-14 | **Visio Ai v2.12.5**: VisioPRO — inline price on category list rows; decimal keyboard on all price fields; scroll-into-view; fixed **Enregistrer** top-right on list editor; order sheet blocks drag-dismiss, live order draft, **Terminé** top-right |
| 2026-06-14 | **Visio Ai v2.12.0**: Settings **Rayons importants** — checklist scopes Articles chips, Settings stats, Catalog filters, CSV report; VisioPRO list editors show **Nouveau CSV** pending after import |
| 2026-06-14 | **Visio Ai v2.11.1**: `ArticleCard` glow + **Price changed** chip from live `changeStatus`; To share row footer (printed / Design queue / Telegram sent) |
| 2026-06-14 | **Visio Ai v2.11.0**: Design **Historique** — dated export folders (`exports/yyyy-MM-dd/shelf_HHmmss_pN.jpg`); batch detail with exclude/reprint/load queue/send to Design or To share; **catalogChangeGlow** on CSV-changed rows (Room v18 batch snapshots) |
| 2026-06-20 | **Visio Ai v2.6.9**: Articles rayon filter fixed — import maps Gestium **Rayon** column (col 17), not **Famille**; `articles.rayon` column + v17 migration backfill from rawData |
| 2026-06-19 | **Visio Ai v2.6.8**: Articles — rayon filter chips (tous les rayons + distinct CSV rayons); browse/search scoped to filter |
| 2026-06-19 | **Visio Ai v2.6.3**: Bottom nav layout fix — `NavigationBarItem` inside horizontal `Row` hid all tabs; replaced with fixed-width scroll tabs; Settings shows version; unit smoke tests |
| 2026-06-19 | **Visio Ai v2.6.2**: VisioPRO first tab; swipeable bottom bar; A4×4 print batch; removed Settings entry |
| 2026-06-19 | **Visio Ai v2.6.1**: AIL PSD F&L social cards; 72 CSV articles; photo store + layered renderer |
| 2026-06-19 | **Visio Ai v2.6.0**: **VisioPRO** — Settings entry; separate UI; 7 preset families; article chips + full card; CSV price + manual memory; DCIM/VisioPRO export |
| 2026-06-19 | **Visio Ai v2.5.0**: DB v16 design promo tickets — `isPromoTicket`, `promoPrice`, `promoOriginalPrice`; shelf print prix-barrée layout |
| 2026-06-18 | **Visio Ai v2.4.7**: PhotoRoom import screen — **Pick PNG** per pending card; manual PNG → gallery rename + To share |
| 2026-06-17 | **Visio Ai v2.4.6**: Shared `ManualBarcodeEntry` — type digits when label won't scan (AGENT dialog, Camera batch inline, Scanner) |
| 2026-06-17 | **Visio Ai v2.4.5**: DB v15 `preselection_items.variantBarcode`; cart unique `(articleId, cartType, variantBarcode)`; sub-bc image in cart queries; PhotoRoom sub-bc → To share as flavor row |
| 2026-06-17 | **Visio Ai v2.4.4**: Design shelf preset — 300 DPI page, JPEG quality 100, sharper product PNG decode |
| 2026-06-17 | **Visio Ai v2.4.3**: Articles search crash fix — `id+barcode` list keys; batch sub-bc lookup; search fallback |
| 2026-06-17 | **Visio Ai v2.4.2**: PhotoRoom import pending cards — **Remove** drops queue row + batch JPEG |
| 2026-06-17 | **Visio Ai v2.4.1**: Sub-barcode add always shoots photo; legacy sub-barcodes without images kept as-is |
| 2026-06-17 | **Visio Ai v2.4.0**: Sub-barcode confirm + optional batch shoot; link deferred until PhotoRoom import; `article_alternate_barcodes.imagePath`; search duplicate designation rows; tap sub-barcode chip to remove |
| 2026-06-16 | **Visio Ai v2.3.7**: PhotoRoom import auto-adds imported PNGs to **To share** cart |
| 2026-06-16 | **Visio Ai v2.3.6**: Article detail **Add sub-barcode & batch shoot**; shared scrollable article panel on AGENT/Scanner/Camera batch with last price + last printed |
| 2026-06-16 | **Visio Ai v2.3.5**: Import detail fix — meaningful changes query; stop storing UNCHANGED rows |
| 2026-06-16 | **Visio Ai v2.3.4**: Import detail + Report CSV change rows show linked PNG (or “No image”); route to To share (has image) or To shoot (no image) |
| 2026-06-16 | **Visio Ai v2.3.3**: Known-article camera batch skips scan; PhotoRoom import hardened (barcode/designation/PNG metadata matching) |
| 2026-06-16 | **Visio Ai v2.3.2**: Design Done manual only (Mark as sent / Mark as printed); Done list by date + remove row |
| 2026-06-16 | **Visio Ai v2.3.1**: Settings PhotoRoom folder picker (persisted SAF URI); import/rescan uses selected folder |
| 2026-06-16 | **Visio Ai v2.3.0**: Batch txt routes not-in-CSV lines to **batch_camera_queue**; Camera batch scan-first (lock barcode → shoot → proceed); unknown barcode → create designation + CSV fuzzy match (Proceed sub-barcode / Add to To share); APK 2.3.0 (230) |
| 2026-06-07 | Android v2.5.2: Bulk PNGs → `DCIM/BULK/{barcode}.png`; `bulk_done.txt` logs completed barcodes |
| 2026-06-07 | Android v2.5.1: Bulk mode **Replace/Skip** now sees catalog-linked PNGs (`resolveScannedBarcode` + `product_images`), not only `{barcode}.png` on disk |
| 2026-06-07 | Android v2.5.0: Design cart keeps **sortOrder** when moving To print ↔ Done; AGENT **Bulk** mode — scan barcode → replace/skip → `{barcode}.png` in `bulk_images/` + `bulk_captures` (Room v11) |
| 2026-06-06 | Android v2.4.4: Asset flow `returnArticleId` — pop to article; PARAY drop **5** digits; designation search in PARAY sheet |
| 2026-06-06 | Android v2.4.3: Article detail **Create asset** → AGENT with barcode locked + camera |
| 2026-06-06 | Android v2.4.2: `DESIGN_DONE_MAX = 50`; trim oldest Done after Send info / print share |
| 2026-06-06 | `docs/WHAT_WE_BUILT.md` rewritten as exhaustive project reference (v2.4.1) |
| 2026-06-02 | Android v2.4.1: Design queue **− / +** copy stepper on each article row |
| 2026-06-02 | Android v2.4.0: `CartType.DESIGN_DONE`; `preselection_items.copyCount`; Design Done section + pull up; + button for copies |
| 2026-06-02 | Removed `oasis print/` PC app; `docs/WHAT_WE_BUILT.md`; stripped OasisPrintLanSender + SupabaseCatalogSync |
| 2026-06-02 | Android v2.2.1: Design **Send info** button — `ACTION_SEND` text/plain with queue designation + barcode |
| 2026-06-02 | Android v2.2.0: `SearchQuery` token match on Articles; Settings **Report** — import_changes + Design shelf print batches |
| 2026-06-02 | Android v2.1.0: AGENT **SUB-BC** button beside Create asset; re-enables camera scan while locked; `linkSubBarcodeToMainArticle` |
| 2026-06-02 | Android v2.0.1: `ShelfA4Renderer` 2×6 (12 labels); Design template skips layout preview → print JPEG |
| 2026-06-02 | Android v2.0.0: Bottom nav **AGENT** (`check_shoot`); removed `photoshoot_cart` tab + Stamper workflow |
| 2026-06-02 | Android v1.9.3: `getArticleWithImageByBarcode` + alternate links; auto-resolve unique 9-digit body match as existing catalog article |
| 2026-06-02 | Android v1.9.2: Fixed `ParayVisualIndex.allSignatures()` re-reading JSON per article; PARAY teach on `Dispatchers.Default`; locked popup **Add to Design** |
| 2026-06-02 | Android v1.9.1: `BarcodeCameraPreview` no longer restarts camera on lock/unlock; `gestiumBodyKey` (9 digits after dropping last 4) for PARAY unknown-barcode suggestions |
| 2026-06-06 | Android v1.8.0: `ParayBarcodeAdvisor` + teach flow in Scan & shoot; session/barcode memory persisted under `files/paray/` |
| 2026-06-05 | Android v1.7.1: `ParayImportScreen` + foreground service — import survives screen off; shows neural profile, memory growth, learning signals |
| 2026-06-05 | PARAY Option A: PC script `build_paray_embeddings.py` (CLIP ONNX or lite fallback); Android Settings import seeds visual + embedding index |
| 2026-06-05 | Android v1.7.0: `ParayAgent` — visual index + `identifyFromCamera()` stub for future Scan & shoot |
| 2026-06-05 | Android v1.6.0: `LayoutFitAgent` — cutout bbox fit, placement memory, GPU learning log; see `docs/LAYOUT_AGENT.md` |
| 2026-06-05 | Android v1.5.5: Centered shelf text; full-height image scaling |
| 2026-06-05 | Android v1.5.4: Shelf JPEG quality 8/10; `shareJpegAsFile` (octet-stream); designation binary max-fit |
| 2026-06-05 | Android v1.5.3: `ShelfA4Renderer` mm layout — 10×4 cm yellow, 2.5 mm row gap, adaptive designation |
| 2026-06-05 | Android v1.5.2: Landscape A4 shelf JPEG (1754×1240); Design queue inline price edit |
| 2026-06-05 | Android v1.5.1: `ShelfA4Renderer` aligned to reference shelves template (full page grid, yellow price block, large red price) |
| 2026-06-05 | Android v1.5.0: Design tab (brush icon) — in-app shelf label layout from To share queue; Work history moved to Settings; `CartType.DESIGN` + `ShelfA4Renderer` JPEG A4 export |
| 2026-06-04 | Android v1.4.0: Multi-phone hotspot sync (master catalog + slave PNG delta); see `docs/PHONE_SYNC.md` |
| 2026-06-04 | Android v1.3.5: Home search across designation/barcode/code; manual barcode trim in suffix link picker |
| 2026-06-04 | Android v1.3.4: Alternate barcodes table; suffix match picker on Scan & shoot lock for barcodes not in CSV |
| 2026-06-04 | Android v1.3.3: Fix U2NetP inference (rembg preprocess); auto-save transparent PNG; Scanner Create asset; upgrade existing images |
| 2026-06-04 | Android v1.3.2: Bundle `u2netp.onnx` (~4.5 MB) in APK; ONNX Runtime for on-device cutout; refreshed `OasisAI-debug.apk` |
| 2026-06-02 | Android v1.3.1: Scan & shoot Create asset + automatic offline background removal |
| 2026-06-02 | Android v1.3.0: On-device background removal (U2NetP TFLite), no cloud |
| 2026-06-02 | Android v1.2.4: Bottom nav Scan → Scan & shoot (full-screen camera flow) |
| 2026-06-02 | Android v1.2.3: Launcher icon — Oasis orange logo on white (user-provided asset) |
| 2026-06-02 | Android v1.2.2: Check & shoot full-screen camera with overlay article popup; lock barcode → Add to To share or Shoot to DCIM |
| 2026-06-02 | Android v1.2.1: Import Center uses scrollable list with Confirm/Cancel always reachable; brand orange theme + Oasis logo launcher; To share articles show origin color (Batch txt, Stamper, Check & shoot, Scanner, Home search, Article detail); full-page scroll on cart screens |
| 2026-06-02 | Android v1.2.0: IMAGE ASSETS folder load now auto-processes entire folder in 500-file batches until complete; existing PNGs are skipped (no copy) |
| 2026-06-02 | Android v1.1.9: new **Batch txt** bottom-nav screen (designation list routing to To share/To shoot); Check & shoot missing scans auto-added to To shoot on capture; To shoot source labels/colors |
| 2026-06-02 | Android v1.1.8: **To shoot** adds Check & shoot (live barcode check for existing PNG preview; if missing, capture via system camera and save shot to DCIM/OasisAI) |
| 2026-06-02 | Android v1.1.7: shared document filenames now keep designation words with spaces; only forbidden filename characters are stripped |
| 2026-06-02 | Android v1.1.6: Dedicated `OasisShareFileProvider` + `.oasis` internal share files force Telegram document mode for selected/all shares (displayed as `.png`) |
| 2026-06-02 | Android v1.1.5: To share adds **Share selected as files** button; checked articles are shared as document files (not compressed photos) |
| 2026-06-01 | Android v1.1.4: **Share as document** fix — custom `OasisFileProvider` returns `application/octet-stream` for share URIs so Telegram uses document path (not `image/png` photo compression) |
| 2026-06-01 | Android v1.1.3: **Share all as files** copies named PNGs (`application/octet-stream`, no image recompression); embedded metadata; Stamper unknown barcode → keyboard designation or barcode-only filename |
| 2026-05-31 | Oasis Print portable: fix missing Qt DLLs/plugins in PyInstaller build; frozen paths + crash log; MADVISION sign + admin trust script; Launch-Oasis-Print.bat |
| 2026-05-31 | Android: Oasis IMAGE ASSETS PNG model (read/write Codeart+tags), folder bulk load, codeart-first re-index, Supabase catalog sync, Oasis Print LAN settings |
| 2026-05-31 | Oasis Print Windows build: PyInstaller + `OasisPrint-Setup-1.0.0.exe` (see `oasis print/docs/BUILD_WINDOWS.md`) |
| 2026-05-31 | Supabase schema applied (`articles_catalog`, `catalog_feed_meta`, `sync_batches`); pooler host fix `aws-1-eu-north-1`; verify script passes |
| 2026-05-31 | Added **Oasis Print** subproject; chose **Supabase** cloud relay (Free tier) for Android→PC sync when not on same Wi‑Fi |
| 2026-05-25 | Changed Share cart to one-by-one PNG file sharing so each image carries its own article detail text |
| 2026-05-25 | Replaced repo product image database from `G:\DATA BASE`; adjusted sync scripts and Android barcode parsing for barcode-only filenames |
| 2026-05-25 | Fixed Stamper last-card activation, enlarged image cards with vertical scroll, and enriched shared PNG metadata with designation/price/barcode/rayon |
| 2026-05-25 | Fixed Stamper gallery launch stability by removing unsafe picker item limit; changed History to zero-start workflow events for searched/scanned/cart/sent actions |
| 2026-05-25 | Added batch Stamper in To shoot with gallery multi-pick, continuous barcode scanner, lazy image cards, Done batch save, and History bottom nav |
| 2026-05-25 | Added To shoot gallery-pick linking: tap article, select ready PNG, rename/copy/tag it, then move article to To share |
| 2026-05-25 | Added Share cart file-sharing workflow, selected-image checkboxes, remove buttons, image created/sent timestamps, and raw CSV detail display |
| 2026-05-25 | Fixed re-index ANR risk: image matching now runs on IO dispatcher, uses barcode/designation maps, and saves image index in one transaction |
| 2026-05-25 | Added center Scan bottom nav item, To shoot/To share actions on article and scan results, and hardened PNG metadata parsing debug |
| 2026-05-25 | Added percentage/status progress UI for CSV preview/import, sample load, ready PNG load, and image re-index |
| 2026-05-25 | Added in-app **Load ready PNG images** action in Settings; copies selected PNGs into app storage and re-indexes |
| 2026-05-25 | Removed in-app background-removal server/capture scope; ready PNGs will be linked by barcode scan |
| 2026-05-24 | Pivot: Home = split article search (has/no image), Photoshoot + Share carts; print/bg removal deferred |
| 2026-05-24 | MVP 7 decision: background removal may use online API (high quality); core app stays offline |
| 2026-05-24 | Full app scaffold: MVPs 1–6, docs trackers, sample CSV, all screens |
| 2026-05-24 | Stabilized Android project: API 34, pinned Compose/libs, Cursor tasks + dev.ps1 |
| 2026-05-23 | Initial project report structured from planning session |
