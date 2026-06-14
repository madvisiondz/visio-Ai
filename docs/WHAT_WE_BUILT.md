# Oasis AI — Complete Project Reference

> **Single exhaustive reference** for everything built in this repository.  
> **Current release:** Android **v2.5.2** (APK `versionCode` **52**) · **2026-06-07**  
> **Install:** `OasisAI-debug.apk` at repository root (~129 MB) — build via `.\BUILD-APK.ps1`  
> **Living spec:** [`PROJECT.md`](../PROJECT.md) · **Trackers:** `SCREENS.md`, `DATA_FLOW.md`, `PROGRESS.md`, `CHAT_LOG.md`

---

## Table of contents

1. [What this project is](#1-what-this-project-is)
2. [Core product rules](#2-core-product-rules-non-negotiable)
3. [Architecture overview](#3-architecture-overview)
4. [Technology stack](#4-technology-stack)
5. [Database — Room v11](#5-database--room-v11)
6. [Cart types and pre-selection](#6-cart-types-and-pre-selection)
7. [All screens and routes](#7-all-screens-and-routes)
8. [Domain services (backend logic)](#8-domain-services-backend-logic)
9. [Data flows (step by step)](#9-data-flows-step-by-step)
10. [File formats and protocols](#10-file-formats-and-protocols)
11. [Shelf label rendering (Design)](#11-shelf-label-rendering-design)
12. [PARAY visual agent](#12-paray-visual-agent)
13. [Layout Fit Agent](#13-layout-fit-agent)
14. [Background removal (U2NetP)](#14-background-removal-u2netp)
15. [Phone sync (hotspot LAN)](#15-phone-sync-hotspot-lan)
16. [PC-side scripts and assets](#16-pc-side-scripts-and-assets)
17. [APK build and bundled assets](#17-apk-build-and-bundled-assets)
18. [Kotlin package map](#18-kotlin-package-map)
19. [Version history (full)](#19-version-history-full)
20. [Daily workflows (operator guide)](#20-daily-workflows-operator-guide)
21. [Removed and superseded features](#21-removed-and-superseded-features)
22. [Known gaps and orphan code](#22-known-gaps-and-orphan-code)

---

## 1. What this project is

**Oasis AI** is an **offline-first Android app** for **OASIS MALL** (Algeria retail hypermarket scale).

It solves fragmented workflows between **GestiumERP** article data, **product PNG images**, **shelf price tickets**, and **Telegram/file sharing** — all at mall scale (~20,000+ articles).

| Asset | Scale / state |
|-------|----------------|
| Articles | 20,000+ from Gestium CSV exports |
| Linked PNGs | ~2,900+ in `product_images/` (PC prep scripts) |
| Unmatched PNGs | ~1,064 kept in `product_images/not found/` for manual review |
| Platform | **Android only** — no desktop app (Oasis Print PC was removed) |
| Internet | Core flows work **without internet** |

### What the app does today

| Capability | How |
|------------|-----|
| Import Gestium CSV | Diff vs previous import; detect new / price changed / renamed / removed |
| Search articles | Smart token search on designation (any word, anywhere) |
| Scan barcodes | CameraX + ML Kit; resolve primary + alternate + body-key barcodes |
| Acquire product photos | AGENT tab: camera → U2NetP cutout → link PNG to article |
| Share PNGs | To share cart → Telegram **document mode** (no recompression) |
| Print shelf labels | Design tab → 12-up landscape A4 JPEG on phone |
| Price round-trip | Send info text → colleague checks prices → Import prices paste back |
| Track changes | Report screen: CSV diffs + Design print audit log |
| Sync phones | Hotspot LAN master/slave PNG delta (port 8776) |
| Visual AI (PARAY) | Learns product look; barcode suggestions; shelf placement |

---

## 2. Core product rules (non-negotiable)

1. **Designation/name = primary identity** — barcode is secondary lookup only.
2. **Offline-first** — import, search, scanner, carts, Design print, background removal work without internet.
3. **Pre-selection workflow:** catalog → cart (pre-selection) → template → export. **Never template-first.**
4. **Print traceability** — every generated batch stored in `print_batches` with price/image snapshots.
5. **Build order:** import + database + search + scanner first; then carts, print, audit, promo.

---

## 3. Architecture overview

```
Gestium CSV ──► Import Center ──► Room DB
                      │              ├── articles
                      │              ├── import_changes
                      │              ├── article_price_history
                      │              └── product_images
                      │
product_images/ ──► Settings: Load IMAGE ASSETS / Re-index
                      │
                      ▼
              ImageMatcher (codeart → barcode → designation)
                      │
Articles (Home) ◄── SearchQuery (smart token match)
                      │
        ┌─────────────┼─────────────┬──────────────┬─────────────┐
        ▼             ▼             ▼              ▼             ▼
     AGENT tab    To share cart   Design cart   Batch txt    Settings
   scan/PARAY/    PNG share       shelf JPEG    designation   import,
   SUB-BC/cutout  Add to Design   Done sub-cart  list route   PARAY,
                                                                  Report…
```

**Bottom navigation (6 tabs):** Articles · **AGENT** · Batch txt · To share · **Design** · Settings

**App singleton** (`OasisApp.kt`): wires `OasisRepository`, `ImageMatcher`, `ReadyPngLoader`, `ImportService`, `PrintGenerator`, `PromoService`, `BackgroundRemovalService`, `ParayHome`, `ParayAgent`, `ParayImportManager`.

---

## 4. Technology stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (SQLite), version **10** |
| Camera | CameraX |
| Barcode | ML Kit Barcode Scanning |
| Images | Coil; local PNG in `files/product_images/` |
| ML inference | ONNX Runtime (primary), TensorFlow Lite (fallback) |
| HTTP (LAN only) | NanoHTTPD (phone sync, port 8776) |
| Build | Gradle 8.10, AGP 8.7, compileSdk/targetSdk **34**, minSdk **26** |
| IDE | Cursor + Android Studio (SDK required) |

---

## 5. Database — Room v11

**File:** `OasisDatabase.kt` · DB name: `oasis_ai.db` · `exportSchema = false`

### 5.1 `articles`

| Column | Type | Notes |
|--------|------|-------|
| `id` | PK auto | |
| `barcode` | String | **Unique** index |
| `designation` | String | Primary display name |
| `normalizedName` | String | For image filename matching |
| `price` | Double | Current price |
| `previousPrice` | Double? | Before last price change |
| `codeart` | String? | Gestium `Code` column |
| `reference` | String? | |
| `category` | String? | Rayon / famille |
| `brand` | String? | |
| `stock` | Double? | |
| `unit` | String? | |
| `rawData` | String? | All CSV columns as `Label: value` lines |
| `sourceImportId` | Long? | FK → `imports` |
| `lastSeenAt` | Long | |
| `changeStatus` | String | `ArticleChangeStatus` enum name |
| `isActive` | Boolean | False when removed from CSV |
| `needsTicketUpdate` | Boolean | True after price change |

**Indices:** `barcode` (unique), `normalizedName`, `designation`, `sourceImportId`

### 5.2 `imports`

| Column | Notes |
|--------|-------|
| `id` | PK |
| `fileName` | Original CSV filename |
| `importedAt` | Timestamp |
| `rowCount` | Rows parsed |
| `status` | `ImportStatus` |
| `newCount`, `priceChangedCount`, `removedCount`, `renamedCount` | Summary counters |

### 5.3 `import_changes`

Per-row diff log for each import run.

| Column | Notes |
|--------|-------|
| `importId` | FK → imports |
| `articleId` | FK → articles (nullable if removed) |
| `barcode`, `designation` | Snapshots |
| `changeType` | `NEW`, `PRICE_CHANGED`, `RENAMED`, `REMOVED`, `UNCHANGED` |
| `oldValue`, `newValue` | Human-readable diff text |

### 5.4 `article_price_history`

| Column | Notes |
|--------|-------|
| `articleId`, `importId` | FKs |
| `oldPrice`, `newPrice` | |
| `changedAt` | |

### 5.5 `product_images`

One row per article (unique `articleId`).

| Column | Notes |
|--------|-------|
| `designationKey` | Normalized filename key |
| `barcode` | From PNG metadata |
| `imagePath` | Absolute path under app files dir |
| `imageStatus` | `FOUND`, `MISSING`, `NEEDS_REVIEW`, `MULTIPLE_MATCHES` |
| `originalImagePath` | Backup before bg removal |
| `createdAt` | When linked |
| `lastSentAt` | Last share-as-file timestamp |

### 5.6 `article_alternate_barcodes`

Flavor/color/packaging alternates for same article (SUB-BC workflow).

| Column | Notes |
|--------|-------|
| `articleId` | FK |
| `barcode` | **Unique** — alternate EAN |
| `addedAt` | |

### 5.7 `preselection_items`

Working carts — multiple cart types per article forbidden (unique `articleId + cartType`).

| Column | Notes |
|--------|-------|
| `articleId` | FK |
| `cartType` | `PHOTOSHOOT`, `SHARE`, `DESIGN`, `DESIGN_DONE` |
| `addedAt`, `sortOrder` | Queue ordering |
| `note` | **Cart source tag** (`SRC_HOME`, `SRC_CHECK_SHOOT`, etc.) |
| `intendedTemplateType` | Legacy print template hint |
| `copyCount` | **Design only** — shelf labels to print (1–99, default 1) |

### 5.8 `print_templates` (seeded on first launch)

| Template | Type | Size | Capacity |
|----------|------|------|----------|
| Shelf A4 | SHELF | A4 | 10 |
| Freezer Card A4 | FREEZER | A4 | 1 |
| Podium Signage A4 | PODIUM | A4 | 1 |
| Board A3 | BOARD | A3 | 1 |

### 5.9 `print_batches` + `print_batch_items`

Print audit trail. Design shelf exports use `templateName` like `Design — Shelf labels (page N)` with `templateId = null`.

**Batch fields:** `exportPath`, `previewPath`, `isPromo`, `promoStart/End`, `campaignName`, `status` (`GENERATED` / `PRINTED` / `PLACED`), `itemCount`

**Item snapshots:** `designationSnapshot`, `priceSnapshot`, `barcodeSnapshot`, `imageSnapshotPath`, `sortOrder`

### 5.10 `workflow_history`

Last **500** events. Types include: `SEARCHED`, `SCANNED`, `ADDED_TO_SHARE`, `ADDED_TO_DESIGN`, `LINKED_IMAGE`, `SENT`, `PHONE_SYNC`, `DESIGN_CART_MOVE`, etc.

### 5.11 `promo_alerts`

Linked to promo `print_batches`; expiry notifications via `PromoService`.

### 5.12 `bulk_captures`

Barcode-keyed mall captures from AGENT **Bulk** mode (separate from main `articles` / `product_images` workflow).

| Column | Notes |
|--------|-------|
| `barcode` | **Primary key** |
| `imagePath` | `DCIM/BULK/{barcode}.png` |
| `capturedAt` | Timestamp |
| `replaced` | True if user replaced an existing PNG |
| `syncStatus` | `PENDING` — for future server sync |

### 5.13 Migrations

| Step | Changes |
|------|---------|
| 1–3 | Initial schema (destructive fallback only) |
| **4 → 5** | `articles.rawData`; `product_images.createdAt`, `lastSentAt` |
| **5 → 6** | New `workflow_history` table |
| **6 → 7** | `articles.codeart` |
| **7 → 8** | `product_images.originalImagePath` |
| **8 → 9** | New `article_alternate_barcodes` |
| **9 → 10** | `preselection_items.copyCount` (default 1) |
| **10 → 11** | New `bulk_captures` table |

`.fallbackToDestructiveMigration(true)` — upgrading from very old versions may wipe local DB.

---

## 6. Cart types and pre-selection

```kotlin
enum class CartType { PHOTOSHOOT, SHARE, DESIGN, DESIGN_DONE }
```

### PHOTOSHOOT (legacy)

- **Status:** Removed from navigation v2.0.0. Route `photoshoot_cart` exists in `Routes.kt` but **not wired in NavHost**.
- Rows may still exist in DB from older app versions.
- Replaced by **AGENT** tab for photo acquisition.

### SHARE — “To share” / “Ready to share”

**Purpose:** Queue of articles with linked PNGs ready for Telegram/file sharing or transfer to Design.

**Entry points:**

| Source | Tag stored in `note` |
|--------|---------------------|
| Articles (Home) | `SRC_HOME` |
| AGENT tab | `SRC_CHECK_SHOOT` |
| Barcode scanner (Settings) | `SRC_SCANNER` |
| Batch txt | `SRC_BATCH_TXT` |
| Article detail | `SRC_ARTICLE` |
| Stamper (legacy) | `SRC_STAMPER` |
| Manual | `SRC_MANUAL` |

**UI colors:** Each source has a color in `CartSourceLegend` — colored card borders on To share list.

**Actions:**
- Checkbox select all / clear / share selected
- **Share all as files** / **Share selected as files (N)** — document mode via `OasisShareFileProvider`
- **Add to Design** (all or selected) → copies to `DESIGN` cart
- Remove per row; Clear cart
- Updates `product_images.lastSentAt` on share

### DESIGN — “To print”

**Purpose:** Shelf label print queue.

**Entry:** From To share (Add to Design) or AGENT (Add to Design when locked + PNG exists).

**Per-article controls:**
- Editable **Price** field (saved to `articles.price` on blur/Done)
- **− / ×N / +** copy stepper (1–99) — expands labels on A4 via `DesignCartExpand`
- Tap image → article detail dialog
- Remove; Clear print queue

**Queue actions:**
- **Send info** — shares `DesignPriceMessage` text via `ACTION_SEND`; moves all To print → Done
- **Import prices** — paste colleague's checked message; updates To print + Done + catalog

**Print:**
- Tap **Shelf labels** card → generates landscape A4 JPEG (12-up)
- **Share as file** on Ready to print → moves current page articles → Done

### DESIGN_DONE — “Done” sub-cart

**Purpose:** Articles already printed or sent for price check.

**Entry:** Automatic after **Send info** (all To print queue) or **Share as file** (current print page).

**Capacity:** **50 articles max** (`OasisRepository.DESIGN_DONE_MAX`). When exceeded, oldest Done rows (by `addedAt`) are dropped automatically after each move to Done.

**Actions:**
- **Pull up** → `restoreDesignItemFromDone` → back to DESIGN queue
- Still included in **Import prices** barcode matching

---

## 7. All screens and routes

**Navigation:** `ui/navigation/Routes.kt` + `OasisNavHost.kt`

### 7.1 Bottom navigation (active daily screens)

#### Articles — `home`

| Element | Behavior |
|---------|----------|
| Search field | Debounced 900ms; `SearchQuery` token match |
| “In Oasis gallery” | Articles with `product_images` linked |
| “Needs photo” | Articles without PNG |
| Row tap | Opens **Article detail** → **Create asset** (all articles), Add to To share (if PNG) |
| Row action | **Add to To share** (if PNG exists) |

**ViewModel:** `HomeViewModel` — `setQuery`, `addToShareCart`, logs `SEARCHED`

#### AGENT — `check_shoot?barcode={}&startCapture={}`

Full-screen camera workflow. Nav icon: animated lens at **1.8×** size (`AgentNavIcon`).

**Mode toggle:** **Smart** (default) · **Bulk** (mall photo job — persisted in SharedPreferences)

| Element | Behavior |
|---------|----------|
| `BarcodeCameraPreview` | CameraX + ML Kit continuous scan |
| **Smart mode** | Lock / unlock; PARAY; catalog checks; SUB-BC; teach |
| **Bulk mode** | Barcode only → Replace / Skip / Take photo → `bulk_images/{barcode}.png` + `bulk_captures` |
| Lock / unlock | Lock main barcode; unlock for next product (Smart only) |
| Article popup | Designation, price, PNG preview, actions (Smart only) |
| **Create asset** | Camera capture → U2NetP cutout → link PNG (Smart) |
| **SUB-BC** | While locked: scan flavor/color alternates → `article_alternate_barcodes` |
| **Add to To share** | If PNG linked |
| **Add to Design** | If PNG linked |
| **PARAY teach** | Camera photo → visual fingerprint |
| Suffix picker | Unknown barcode → scrollable catalog matches (9–10 digit suffix); editable digits; Drop 3/4 |

**Barcode resolution order:**
1. Primary `articles.barcode`
2. `article_alternate_barcodes`
3. Gestium **body key** (drop last 4 digits, compare first 9 left)
4. Suffix picker / PARAY suggestions

**ViewModel:** `CheckShootViewModel` — lock, SUB-BC, create asset, PARAY teach, cart adds, session persist in `files/paray_home/sessions/`

#### Batch txt — `batch_txt`

| Element | Behavior |
|---------|----------|
| Multiline paste | One designation per line |
| Process batch | Match by designation → PNG exists → To share; missing → count only |

**ViewModel:** `BatchTxtViewModel` — `setInput`, `processBatch`

#### To share — `share_cart` (CartType.SHARE)

| Element | Behavior |
|---------|----------|
| Origin legend | Color key for source tags |
| Checkboxes | Select for partial share |
| Share all / selected | Document PNG share |
| Add to Design | All or selected |
| Remove / Clear | |

**ViewModel:** `CartViewModel` — share, design transfer, selection

#### Design — `design`

Three internal steps: `HOME` → `READY_PRINT` (SHELF_LAYOUT step exists but routes to HOME).

| Section | Behavior |
|---------|----------|
| PARAY status | Learned product count |
| Shelf labels card | Tap → generate JPEG page 0 |
| **To print** | Queue rows with price, −/+ copies, remove |
| Send info / Import prices | Top action row |
| **Done (N)** | Pull up per row |
| Ready to print | JPEG preview; page nav; Share as file; back |

**ViewModel:** `DesignViewModel` — full design workflow (see §9.6)

#### Settings — `settings`

| Action | Route / behavior |
|--------|------------------|
| Database overview | Article/image counts |
| Import CSV | → `import` |
| Import PARAY fingerprints | SAF picker → `paray_import` |
| Load IMAGE ASSETS folder | 500-file batches until complete |
| Load ready PNGs (picker) | Up to 500 URIs |
| Re-index product images | `ImageMatcher` full sync |
| Phone sync | → `phone_sync` |
| Report | → `report` |
| Work history | → `history` |
| Background removal | → `background_removal/{articleId}` |
| Barcode scanner (legacy) | → `scanner` |
| Missing images | → `images` |
| Load sample data | Bundled `sample_articles.csv` |
| PARAY home | → `paray_home` |

### 7.2 Settings sub-screens

| Route | Screen | Purpose |
|-------|--------|---------|
| `import` | ImportScreen | CSV pick, preview, confirm |
| `import/{importId}` | ImportDetailScreen | Per-import change list |
| `article/{articleId}` | ArticleDetailScreen | Full CSV raw data; **Create asset** (all articles); Add to share; Remove background |
| `scanner` | ScannerScreen | Typed/scanned lookup; Create asset shortcut |
| `images` | ImageManagerRoute | Articles missing PNG |
| `gallery_link` | GalleryLinkPlaceholderScreen | **Placeholder** — “Coming soon” |
| `background_removal/{articleId}` | BackgroundRemovalScreen | Pick photo; U2NetP cutout preview |
| `phone_sync` | PhoneSyncScreen | Master/slave hotspot sync |
| `paray_home` | ParayHomeScreen | PARAY stats; links to Design/AGENT/import |
| `paray_import` | ParayImportScreen | Foreground service import progress |
| `history` | WorkHistoryScreen | Last 500 workflow events |
| `report` | ReportScreen | CSV diffs + Design print log |

### 7.3 Legacy screens (registered, no UI entry)

| Route | Screen | Status |
|-------|--------|--------|
| `catalog` | CatalogScreen | No nav link; old catalog browse |
| `preselection` | PreselectionScreen | No nav link; old print cart |
| `print` | PrintScreen | Reachable only from preselection; PDF generator |
| `print_history` | PrintHistoryScreen | No nav link |
| `print_batch/{batchId}` | PrintBatchDetailScreen | From print history only |
| `promo` | PromoScreen | No nav link |
| `photoshoot_cart` | — | Route defined; **not in NavHost** |

---

## 8. Domain services (backend logic)

### ImportService + CsvParser

- Parses Gestium CSV with flexible headers (French/English)
- Charsets: UTF-8 → Windows-1252 → ISO-8859-1 fallback
- French number format: `1 120,00` → `1120.0`
- Required: barcode, designation, price
- Diff engine: NEW, PRICE_CHANGED, RENAMED, REMOVED, UNCHANGED
- Price change sets `needsTicketUpdate = true`
- Triggers `ImageMatcher` after import

### ImageMatcher

- Scans `files/product_images/*.png`
- Match priority: **codeart** (PNG metadata) → **barcode** → **normalized designation**
- Reads/writes PNG tEXt metadata via `PngMetadata`
- Bulk transaction replace of `product_images` index
- Status: FOUND, MISSING, MULTIPLE_MATCHES, NEEDS_REVIEW

### ReadyPngLoader + ReadyPngModel

- Loads PNGs from SAF folder tree or URI list
- **500-file batches** with auto-continue for full folder
- Skips files already present (no overwrite)
- Validates Oasis PNG model tags before accepting

### PrintGenerator (legacy PDF)

- Templates: SHELF (10-up), FREEZER, PODIUM, BOARD
- Output: PDF in `files/exports/`
- Creates `print_batches` + items with snapshots
- Still in codebase; not linked from Settings menu

### ShelfA4Renderer (current print path)

- See §11 for full layout spec
- Uses `ParayAgent` for cutout placement per ticket slot
- JPEG quality 80; landscape 1754×1240 px

### DesignCartExpand

- Expands `copyCount` into repeated `PreselectionWithArticle` entries for A4 grid

### BackgroundRemovalService

- Model: `assets/u2netp.onnx` (~4.4 MB) via ONNX Runtime
- Fallback: TensorFlow Lite if ONNX fails
- Saves transparent PNG + `originalImagePath` backup
- Used in AGENT Create asset and Article detail flow

### ParayAgent (see §12)

- Visual index, CLIP embeddings, barcode memory, camera ID, design session learning

### LayoutFitAgent (see §13)

- Cutout bbox → contain-fit in shelf white slot; placement memory

### PhoneSync (see §15)

- NanoHTTPD port **8776**; catalog JSON + delta PNG multipart push

### PromoService

- Scans promo `print_batches` for expiry; creates `promo_alerts`

### OasisRepository

Central API for all DB operations, cart moves, workflow logging, `recordDesignShelfPrint`, `resolveScannedBarcode`, `linkSubBarcodeToMainArticle`, etc.

---

## 9. Data flows (step by step)

### 9.1 Gestium CSV import

```
Settings → Import CSV
  → SAF file picker
  → CsvParser.parseWithFallback
  → Preview sample rows (ImportScreen)
  → Confirm
  → ImportService.importFromStream
  → articles upsert + import_changes + price_history
  → removed articles: isActive=false
  → ImageMatcher.syncImagesForArticles
  → Report screen shows diffs on next open
```

### 9.2 Articles search

```
User types query
  → debounce 900ms
  → SearchQuery.prepare (lowercase tokens)
  → SQL LIKE pre-filter on designation
  → client: all tokens must appear in designation haystack
  → split: hasAppGalleryImage vs needsPhoto
  → workflow_history SEARCHED event
```

### 9.3 Barcode scanner (Settings)

```
Scan or type barcode
  → resolveScannedBarcode (primary + alternates + body key)
  → show ArticleWithImage card
  → Add to To share (SRC_SCANNER)
  → or navigate AGENT with startCapture=true
  → workflow_history SCANNED
```

### 9.4 AGENT (check_shoot)

**Smart mode:**

```
Continuous camera scan
  → resolve barcode (see §7.1 AGENT)
  → unknown: PARAY suggestions (CLIP + visual + memory)
  → user locks barcode
      ├─ Create asset: camera → bg removal → link PNG → offer To share
      ├─ SUB-BC: scan alternates → article_alternate_barcodes
      ├─ PARAY teach: camera → visual signature stored
      ├─ Add to To share / Add to Design
      └─ session saved: paray_home/sessions/check_shoot_session.json
```

**Bulk mode:**

```
Toggle Bulk → scan barcode
  → BulkCaptureStore.findExistingImagePath (bulk_images/ then product_images/)
  → popup: Take photo / Replace / Skip
  → camera → U2NetP → save DCIM/BULK/{barcode}.png
  → append DCIM/BULK/bulk_done.txt (barcode, new/replaced, timestamp)
  → upsert bulk_captures (syncStatus=PENDING)
  → scan next (no lock, no PARAY, no designation)
```

### 9.5 To share

```
Article added (any entry point)
  → preselection_items SHARE + source tag in note
  → Share selected/all:
      PngShareHelper → cache/share-export/DESIGNATION.png
      embed PNG metadata (designation, price, barcode, codeart, rayon)
      OasisShareFileProvider → application/octet-stream
      (Telegram receives as document, not compressed photo)
  → lastSentAt updated
  → Add to Design → DESIGN cart (copyCount=1)
```

### 9.6 Design (shelf labels)

```
DESIGN queue populated from To share or AGENT
  → edit price inline (saved to articles)
  → −/+ copyCount (1–99)
  → optional: Send info
      ExportShareHelper.shareDesignCartInfo (text/plain)
      moveDesignItemsToDone (all To print, **preserves sortOrder**)
  → optional: Import prices dialog
      DesignPriceMessage.parse pasted text
      updateArticlePrice for matching barcodes in To print + Done
  → tap Shelf labels:
      DesignCartExpand(copyCount) → N label slots
      ShelfA4Renderer.renderPage (Paray placement)
      JPEG → files/exports/shelf_a4_p{N}_{timestamp}.jpg
      recordDesignShelfPrint → print_batches audit
  → Ready to print screen:
      page navigation if >12 labels
      Share as file → onPrintShared → move page articles to DESIGN_DONE (queue order preserved)
  → Done section: Pull up → restoreDesignItemFromDone
```

### 9.7 Batch txt

```
Paste designation lines (one per line)
  → trim, skip empty
  → getArticleWithImageByDesignation for each
  → PNG linked → add SHARE (SRC_BATCH_TXT)
  → no PNG → increment missing count (user directed to AGENT)
  → show matched / missing / unmatched counts
```

### 9.8 Report

```
Settings → Report
  → latest import summary vs previous
  → observeRecentCsvChanges (excludes UNCHANGED)
  → observeDesignShelfPrints (templateName LIKE 'Design —%')
  → expand batch → loadPrintItems (designation, price, barcode per article)
```

### 9.9 Settings image pipeline

```
Load IMAGE ASSETS folder (SAF tree)
  → ReadyPngLoader in 500-file batches until folder exhausted
  → copies to files/product_images/
  → preserves existing Oasis PNG metadata tags
  → Re-index:
      ImageMatcher builds maps
      match codeart > barcode > designation
      write missing metadata to PNG files
      replace product_images table in one transaction
```

### 9.10 Phone sync

```
Master phone: Settings → Phone sync → Start server (:8776, PIN)
Slave phone: connect to master hotspot → Pull catalog
  → compare article barcodes + image hashes
  → PhoneSyncDeltaBuilder: list PNGs master lacks
  → POST multipart push to master
  → PhoneSyncApplyService links images on master
  → workflow_history PHONE_SYNC
```

---

## 10. File formats and protocols

### 10.1 Gestium CSV

**Required columns** (flexible header names):

| Field | Accepted headers |
|-------|------------------|
| Barcode | `Code-barres`, `barcode`, `ean`, `gtin` |
| Designation | `Désignation`, `designation`, `libelle`, `name` |
| Price | `Prix de vente TTC` (preferred), `price`, `prix` |

**Optional mapped:**

| CSV | DB column |
|-----|-----------|
| `Code` | `codeart` |
| Reference columns | `reference` |
| Rayon / famille / category | `category` |
| Brand | `brand` |
| Stock | `stock` |
| Unit | `unit` |

**Sample in APK:** `assets/sample_articles.csv` — semicolon-delimited.

**All other columns** stored in `articles.rawData` as multiline `Label: value` text for Article detail display.

### 10.2 Design price message (`DesignPriceMessage.kt`)

Used by **Send info** and **Import prices**.

```
OASIS-DESIGN-PRICES v1
# Edit the last number on each line, then paste back in Design → Import prices
1|{barcode}|{designation}|{price}
2|{barcode}|{designation}|{price}
...
```

- Pipe-delimited: `index|barcode|designation|price`
- Also parses legacy block format (designation line, barcode line, price line groups)
- Import updates `articles.price` and refreshes Design queue display

### 10.3 PNG metadata (tEXt chunks)

Aligned between Android `PngMetadata.kt` and Python `png_metadata.py`:

| Key | Content |
|-----|---------|
| `Barcode` | EAN-13 / product barcode |
| `Description` | Human-readable composite |
| `Designation` | Article name |
| `PriceNow` | e.g. `240,00 DA` |
| `PriceBefore` | Previous price if known |
| `Rayon` | Category / aisle |
| `Codeart` | Gestium internal code |

**Filename conventions:**
- `{BARCODE}.png` — barcode-only (supported)
- `{DESIGNATION_KEY}.png` — normalized designation
- `{DESIGNATION_KEY}_{BARCODE}.png` — collision resolution

### 10.4 PARAY fingerprint index JSON

**Built on PC:** `scripts/build_paray_embeddings.py` → `exports/paray/paray_fingerprint_index.json`

```json
{
  "version": 1,
  "agent": "PARAY",
  "model": "clip-vit-b32-onnx",
  "dim": 512,
  "generatedAt": "2026-06-05T...",
  "source": "product_images",
  "stats": { "processed": 2892, "skipped_corrupt": 0, ... },
  "count": 2892,
  "entries": [{
    "barcode": "...",
    "designation": "...",
    "imageFileName": "...",
    "shapeAspect": 1.2,
    "fillRatio": 0.65,
    "dominantColors": ["#FF0000", ...],
    "embedding": [512 floats]
  }]
}
```

**On phone after import:** `files/paray_home/memory/fingerprint_index.json`

### 10.5 Phone sync protocol

- **Port:** 8776 (HTTP)
- **Auth:** `X-Oasis-Pin` header
- **Endpoints:** catalog JSON GET; delta PNG multipart POST
- **No cloud** — hotspot LAN only

### 10.6 Share file providers

| Provider | Purpose |
|----------|---------|
| `OasisFileProvider` | General file access |
| `OasisShareFileProvider` | Forces `application/octet-stream` for Telegram document shares |

---

## 11. Shelf label rendering (Design)

**Class:** `ShelfA4Renderer.kt`

| Spec | Value |
|------|-------|
| Grid | **2 columns × 6 rows = 12 labels** per page |
| Page size | Landscape A4: **297 × 210 mm** |
| Pixel size | **1754 × 1240** (~150 DPI) |
| Row height | **35 mm** each (210 ÷ 6, **no row gaps**) |
| Column gap | **8 mm** between columns |
| Yellow block | **92 × 35 mm** (price area) |
| Image area | Remaining width per column; **full 35 mm height** for product cutout |
| Yellow color | `#FFE500` |
| Price color | `#E60000` (red) + `DA` suffix |
| Designation | Black uppercase; binary-search max font size to fit |
| JPEG quality | **80** (8/10) |
| Output path | `files/exports/shelf_a4_p{page}_{timestamp}.jpg` |
| Multi-page | `DesignCartExpand` may produce >12 slots → page buttons on Ready to print |

**Placement:** `ParayAgent` + `LayoutFitAgent` compute alpha bbox → contain-fit product image in white slot.

---

## 12. PARAY visual agent

**Purpose:** Learn product visual identity (shape, colors, typography) for future camera identification and smarter shelf layout.

| Component | Role |
|-----------|------|
| `ParayAgent` | Main facade; design session; identify; teach |
| `ParayHome` | Living folder `files/paray_home/` — manifest, memory, sessions, logs |
| `ParayVisualIndex` | On-device shape/color signatures per barcode |
| `ParayFingerprintStore` | PC-imported CLIP embeddings |
| `ParayCameraMatcher` | Embedding + visual similarity for unknown barcodes |
| `ParayBarcodeAdvisor` | Last-4 digit suggestions from barcode memory |
| `ParayImportManager` | Orchestrates JSON import |
| `ParayImportForegroundService` | Background-safe import with notification |

**PC bulk build:**
```powershell
.\scripts\BUILD-PARAY-FINGERPRINTS.ps1
# → exports/paray/paray_fingerprint_index.json
```

**Phone import:** Settings → Import PARAY fingerprints → `ParayImportScreen` with neural stats UI.

**Gestium body key:** For unknown barcodes, drop last 4 digits, compare first 9 digits left against catalog.

**See also:** `docs/PARAY.md`

---

## 13. Layout Fit Agent

**Purpose:** Cutout-aware product image placement on shelf label slots; learns placements over time.

| Component | Role |
|-----------|------|
| `LayoutFitAgent` | Alpha bbox detection → contain-fit in slot |
| `LayoutFitMemory` | Per-article placement history on device |
| `GpuLearningProbe` | Logs GPU capability signals for future optimization |
| `AppLayoutKnowledge` | Aggregated layout learning log |

Activated when Design screen is open and during `ShelfA4Renderer` draw.

**See also:** `docs/LAYOUT_AGENT.md`

---

## 14. Background removal (U2NetP)

| Item | Detail |
|------|--------|
| Model | `u2netp.onnx` (~4.4 MB) in `android/app/src/main/assets/` |
| Download | `.\scripts\download-u2netp-tflite.ps1` (not in git — must run before build) |
| Runtime | ONNX Runtime primary; TFLite `SaliencySegmenter` fallback |
| Preprocessing | rembg-compatible normalize (fixed v1.3.3) |
| Output | Transparent PNG; original saved to `originalImagePath` |
| Entry points | AGENT Create asset; Article detail Remove background; Settings |

**Fully offline** — phone never needs internet for cutout after APK install.

---

## 15. Phone sync (hotspot LAN)

| Item | Detail |
|------|--------|
| Port | **8776** |
| Server | `PhoneSyncServer` (NanoHTTPD) on master |
| Client | `PhoneSyncClient` on slave |
| PIN | Configurable; sent as `X-Oasis-Pin` |
| Flow | Slave pulls catalog → computes delta → pushes only missing PNGs |
| Apply | `PhoneSyncApplyService` on master links new images |
| Docs | `docs/PHONE_SYNC.md` (if present) |

**Use case:** Two phones on same hotspot — master has full gallery, slave photographed new products → push delta without cloud.

---

## 16. PC-side scripts and assets

### Repository folders

| Path | Role |
|------|------|
| `product_images/` | Linked PNGs (normalized names + metadata) |
| `product_images/not found/` | Unmatched PNGs for manual review |
| `imports/` | Archived Gestium CSV feeds |
| `exports/paray/paray_fingerprint_index.json` | PARAY bulk import payload (~large) |
| `android/` | Android Studio project |
| `OasisAI-debug.apk` | Built debug APK at repo root |

### Scripts (`scripts/`)

| Script | Purpose |
|--------|---------|
| `sync_product_images.py` | Match PNGs to CSV; rename via `NameNormalizer`; move unmatched to `not found/` |
| `sync_images_with_barcodes.py` | One-command: sync + embed barcodes + validate PNG integrity |
| `embed_png_barcodes.py` | Write barcode into PNG tEXt for all linked images |
| `embed_all_product_images.py` | Embed with collision filename resolution |
| `update_image_assets_metadata.py` | Update IMAGE ASSETS folder metadata from CSV without renaming |
| `recover_and_embed_pngs.py` | Recover corrupt PNGs from `not found/` |
| `list_corrupt_pngs.py` | List PNGs needing restore |
| `check_image_match.py` | Stats: CSV articles vs PNG index |
| `png_metadata.py` | Shared PNG tEXt read/write (mirrors Android) |
| `build_paray_embeddings.py` | CLIP ONNX embeddings → fingerprint JSON |
| `paray_clip.py` | CLIP session + visual features + fallback |
| `requirements-paray.txt` | Python deps: numpy, pillow, tqdm, onnxruntime |
| `BUILD-PARAY-FINGERPRINTS.ps1` | pip install + run embedding build |
| `download-u2netp-tflite.ps1` | Download `u2netp.onnx` → Android assets |

### Root build scripts

| Script | Purpose |
|--------|---------|
| `BUILD-APK.ps1` | `gradlew clean assembleDebug`; copy APK from `%LOCALAPPDATA%\OasisAI-android-build\` to repo root |
| `dev.ps1` | Dev environment helper |

---

## 17. APK build and bundled assets

| Item | Value |
|------|-------|
| `applicationId` | `com.oasismall.oasisai` |
| `versionName` | **2.4.3** |
| `versionCode` | **48** |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 34 / 34 |
| Build output dir | `%LOCALAPPDATA%\OasisAI-android-build\` (avoids OneDrive file locks) |
| **Correct APK path** | `%LOCALAPPDATA%\OasisAI-android-build\outputs\apk\debug\app-debug.apk` |
| **Wrong/stale path** | `android\app\build\outputs\apk\debug\` — do **not** copy from here |
| Typical size | **~129–135 MB** (includes ONNX + TFLite + ML Kit native libs) |
| Build command | `.\BUILD-APK.ps1` from repo root |

### Bundled assets (`android/app/src/main/assets/`)

| File | Role |
|------|------|
| `u2netp.onnx` | U2NetP segmentation (~4.4 MB) — **download via script before build** |
| `sample_articles.csv` | Demo import data |
| `README_BACKGROUND_MODEL.txt` | Model setup instructions |

### Native libraries in APK (all ABIs)

- `libonnxruntime.so`, `libonnxruntime4j_jni.so`
- `libtensorflowlite_jni.so`
- `libbarhopper_v3.so` (ML Kit)
- `libimage_processing_util_jni.so` (CameraX)

### AndroidManifest services

- `ParayImportForegroundService` — `foregroundServiceType="dataSync"`

---

## 18. Kotlin package map

**Root:** `com.oasismall.oasisai` (~119 Kotlin files)

| Package | Key contents |
|---------|--------------|
| *(root)* | `OasisApp.kt`, `MainActivity.kt` |
| `data.db` | `OasisDatabase.kt` |
| `data.db.entity` | `Entities.kt` — all Room entities |
| `data.db.dao` | `Daos.kt` — DAOs + `PreselectionWithArticle`, `ArticleWithImage` |
| `data.model` | `Enums.kt` — all enums |
| `data.repository` | `OasisRepository.kt` |
| `domain` | `ImportService`, `CsvParser`, `ImageMatcher`, `PrintGenerator`, `PromoService`, `ReadyPngLoader` |
| `domain.bulk` | `BulkCaptureStore` — barcode-keyed mall PNGs + `bulk_captures` DAO |
| `domain.backgroundremoval` | `BackgroundRemovalService`, ONNX/TFLite segmenters |
| `domain.design` | `ShelfA4Renderer`, `DesignCartExpand` |
| `domain.layoutagent` | `LayoutFitAgent`, `LayoutFitMemory`, `GpuLearningProbe` |
| `domain.paray` | `ParayAgent`, `ParayHome`, `ParayVisualIndex`, import service, camera matcher |
| `domain.phonesync` | Server, client, protocol, delta builder, apply service |
| `ui.navigation` | `Routes.kt`, `OasisNavHost.kt` |
| `ui.screens.*` | One folder per screen (home, checkshoot, design, cart, settings, report, …) |
| `ui.components` | `AgentNavIcon`, `CartSourceTags`, `ArticleCard`, `CartSourceLegend` |
| `util` | `PngMetadata`, `PngShareHelper`, `DesignPriceMessage`, `ExportShareHelper`, `SearchQuery`, `NameNormalizer`, `PriceFormatter`, file providers |

---

## 19. Version history (full)

| Version | Code | Date | Highlights |
|---------|------|------|------------|
| **2.5.2** | 52 | 2026-06-07 | Bulk output → **DCIM/BULK** PNGs + `bulk_done.txt` tracker |
| **2.5.1** | 51 | 2026-06-07 | Bulk detects catalog-linked PNGs (same as Smart) |
| **2.5.0** | 50 | 2026-06-07 | Design queue **sortOrder** preserved; AGENT **Smart/Bulk** mode; `bulk_captures` (Room v11) |
| **2.4.4** | 49 | 2026-06-06 | Create asset returns to article; PARAY drop **5** digits; designation search |
| **2.4.3** | 48 | 2026-06-06 | Article detail **Create asset** button (with or without PNG) |
| **2.4.2** | 47 | 2026-06-06 | Done list **max 50** — oldest auto-removed |
| **2.4.1** | 46 | 2026-06-06 | Design **− / +** copy stepper (min 1, max 99) |
| **2.4.0** | 45 | 2026-06-02 | **Done** sub-cart; `copyCount`; pull up; Send info / print → Done |
| **2.3.1** | 44 | 2026-06-02 | Send info includes price; **Import prices** round-trip |
| **2.3.0** | 43 | 2026-06-02 | **Removed Oasis Print PC**; stripped LAN/cloud sync |
| **2.2.1** | 42 | 2026-06-02 | Design **Send info** (text share) |
| **2.2.0** | 41 | 2026-06-02 | Smart Articles search (`SearchQuery`); **Report** screen |
| **2.1.0** | 40 | 2026-06-02 | AGENT **SUB-BC** alternate barcodes; nav icon 1.8× |
| **2.0.1** | 39 | 2026-06-02 | Design 12-up (2×6); article popup; direct print JPEG |
| **2.0.0** | 38 | 2026-06-02 | **AGENT** tab; removed To photograph + Stamper |
| **1.9.3** | — | 2026-06-02 | Body-key / linked barcode resolve; Add to share/Design |
| **1.9.2** | — | 2026-06-02 | PARAY teach freeze fix; Add to Design on lock |
| **1.9.1** | — | 2026-06-02 | Lock/unlock camera fix; PARAY body-key |
| **1.9.0** | — | 2026-06-06 | PARAY home UI; `paray_home/` folder |
| **1.8.0** | — | 2026-06-06 | PARAY in scan flow; persistent session |
| **1.7.1** | 31+ | 2026-06-05 | PARAY neural load screen; foreground service |
| **1.7.0** | — | 2026-06-05 | PARAY visual agent foundation |
| **1.6.0** | 30 | 2026-06-05 | Layout Fit Agent |
| **1.5.5** | 29 | 2026-06-05 | Centered shelf text; full-height image |
| **1.5.4** | 28 | 2026-06-05 | Max-fit designation; JPEG quality 80; share as document |
| **1.5.3** | 27 | 2026-06-05 | Yellow 10×4 cm tickets; row gaps (later removed in 2×6) |
| **1.5.2** | 26 | 2026-06-05 | Landscape A4; editable Design queue prices |
| **1.5.1** | 25 | 2026-06-05 | Shelf JPEG matches reference template |
| **1.5.0** | 24 | 2026-06-05 | **Design screen** replaces PC print; History → Settings |
| **1.4.0** | — | 2026-06-04 | Phone sync hotspot LAN :8776 |
| **1.3.5** | — | 2026-06-04 | Search barcode+codeart; suffix picker improvements |
| **1.3.4** | — | 2026-06-04 | Alternate barcodes table; suffix link picker |
| **1.3.3** | — | 2026-06-04 | U2NetP rembg-correct; auto cutout; Scanner Create asset |
| **1.3.2** | — | 2026-06-04 | **u2netp.onnx bundled** (~135 MB APK) |
| **1.3.1** | — | 2026-06-02 | Create asset + auto bg removal |
| **1.3.0** | — | 2026-06-02 | Offline background removal module |
| **1.2.x** | 13–14 | 2026-06-02 | Production UI; Check & shoot; Batch txt; source tags |
| **1.1.x** | 5–10 | 2026-06-01 | Share as document files; Stamper (later removed) |
| **1.0** | — | 2026-05-24 | Room schema, import diff, PDF print, initial screens |

---

## 20. Daily workflows (operator guide)

### Morning: CSV price update

```
1. Export CSV from GestiumERP
2. Copy to phone (or cloud download)
3. Oasis AI → Settings → Import CSV
4. Preview → Confirm
5. Settings → Report → review price changes
6. Articles with needsTicketUpdate need new shelf tickets
```

### New product with photo

```
1. AGENT tab
2. Scan barcode → Lock
3. Create asset → take photo → auto cutout → Accept
4. Add to To share
5. (Optional) To share → Add to Design → print shelf label
```

### Flavor/color alternate barcode

```
1. AGENT → scan main CSV barcode → Lock
2. Tap SUB-BC
3. Scan alternate EANs (same product, different packaging)
4. Stored in article_alternate_barcodes
5. Cashiers scanning any alternate resolve to same article
```

### Shelf label batch

```
1. To share → select articles → Add to Design
2. Design → adjust prices → set copies (−/+)
3. Tap Shelf labels → preview A4 JPEG
4. Share as file → print at shop
5. Articles move to Done
6. Pull up any that need reprint
```

### Price check round-trip (colleague on PC)

```
1. Design → Send info → Telegram text list
2. Colleague checks/edits prices on PC
3. Colleague sends message back (same pipe format)
4. Design → Import prices → paste → Apply
5. Catalog + queue prices updated
```

### Bulk designation list

```
1. Batch txt → paste list from Excel
2. Process → matched PNGs go to To share
3. Missing PNGs → use AGENT to photograph
```

### Multi-phone PNG sync

```
1. Master: enable hotspot, Settings → Phone sync → Start server
2. Slave: join hotspot, enter master IP + PIN
3. Slave → Push delta
4. Master receives new PNGs only
```

### PARAY fingerprint refresh

```
PC:  .\scripts\BUILD-PARAY-FINGERPRINTS.ps1
Phone: Settings → Import PARAY fingerprints → pick JSON
```

---

## 21. Removed and superseded features

| Feature | Removed | Replacement |
|---------|---------|-------------|
| **Oasis Print desktop** (`oasis print/`) | 2026-06-02 | Design screen on phone |
| Send to Oasis Print (LAN) | 2026-06-02 | Add to Design |
| Supabase catalog sync | 2026-06-02 | Not needed (PC app gone) |
| `OasisPrintLanSender.kt` | 2026-06-02 | — |
| `SupabaseCatalogSync.kt` | 2026-06-02 | — |
| **To photograph tab** | v2.0.0 | AGENT tab |
| **Stamper** batch UI | v2.0.0 | AGENT Create asset |
| Bottom nav **History** | v1.5.0 | Settings → Work history |
| Template-first print (main flow) | v1.5.0 | Design pre-selection |
| In-app bg removal server | 2026-05-25 | On-device U2NetP |
| Online bg removal API | Deferred | On-device ONNX |

---

## 22. Known gaps and orphan code

| Item | Status |
|------|--------|
| `catalog`, `preselection`, `print_history`, `promo` routes | Registered in NavHost; **no Settings/menu link** |
| `photoshoot_cart` route | Defined; **not in NavHost** |
| `gallery_link` | Placeholder screen only |
| `BUILD-APK.ps1` banner | Still prints v1.5.0 text (script stale; build works) |
| `u2netp.onnx` in git | Usually gitignored; must run download script before build |
| `android/app/build/outputs/` | Stale small APK (~9 MB); **ignore** — use LOCALAPPDATA path |
| Supabase `BuildConfig` fields | Present in gradle; UI removed |
| MVP 7 (gallery link workflow) | Partially done via AGENT; gallery_link screen not built |

---

## Document maintenance

- Update this file when features, schema, routes, or workflows change.
- Keep version table in sync with `android/app/build.gradle.kts`.
- Cross-reference: [`PROJECT.md`](../PROJECT.md) for roadmap and changelog.

**Last expanded:** 2026-06-06 — full detail pass (v2.4.1).
