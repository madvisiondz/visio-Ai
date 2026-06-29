# Oasis AI ??? Data Flow

## 1. GestiumERP import

```
CSV file (content URI from Settings or Import history screen)
    → CsvParser.validate + parse
    → OasisBackgroundTaskManager.enqueueCsvImport()
    → ImportService (foreground service)
        → **ArticleImportSnapshot** (compare fields only — no `rawData` blobs)
        → compare by barcode / codeart / normalized designation
        → persist designation, price, **rayon**, famille, catégorie (`rawData` null on save)
        → detect NEW / PRICE_CHANGED / RENAMED / REMOVED; skip unchanged row writes
        → write articles + import_changes + article_price_history
    → ImageMatcher.upsertImagesForArticles() — **new articles only** on re-import
    → UI: CsvImportBanner on nav host; Import history + ImportDetailScreen
    → When **Rayons importants** configured: Report summaries show only those rayons (full DB import unchanged)
```

## Device transfer (v2.15)

```
Settings → Device transfer
    → Purge Gestium catalog — archives sub-barcode flavor map first (`sub_barcode_registry.json`);
        then DB articles/imports/carts cleared; PNG files kept on device
    → Re-import CSV — ImportService auto-runs restoreLinkedFlavors() (re-links sub-barcodes to parents)
    → Restore sub-barcode flavors — manual retry in Settings (same restore logic)
    → Export full backup — VisioAi_backup_*.zip → Download/VisioAi/
        (database JSON + product_images + visio_pro_* + exports + settings)
    → Import full backup — pick ZIP; restores files + DB with barcode ID remapping
    → Export VisioPRO presets — per-category folder + ZIP (articles, photos, catalog PNGs, designs)
    → Export PNG database — all gallery PNGs incl. sub-barcode variants
```

## Background long tasks (v2.15.6)

```
Settings / Import history picker
    → OasisBackgroundTaskManager.enqueue(kind [, uri | csv parse | png uris])
    → OasisBackgroundTaskService (foreground + wake lock)
        → runs task on Dispatchers.IO; updates notification + shared StateFlow
    → UI observes progress overlay; user may lock screen — task continues
```

Kinds: sync sub-PNGs, re-index, PNG export, full backup import/export, VisioPRO bundle, purge Gestium, load ready PNGs, CSV import.

## PARAY (removed v2.36.0)

PARAY observer, learn, fusion, and ticket-verify flows were removed. Design shelf layout uses **LayoutFitAgent** (`domain/layoutagent/`). Legacy docs: `docs/PARAY_*.md` (historical only).

## PARAY Observer Phase 1 (v2.17.0) — removed

```
Trigger (startup / import / re-index / learn complete)
  → ParayObserver.onTrigger()
  → SQL COUNT fingerprint vs observer_state.json
  → unchanged? sleep
  → changed? delta observation → observer_events.jsonl + observer_knowledge.json
```

See `docs/PARAY_OBSERVER.md`.

## PARAY Knowledge Phase 2 (v2.18.0)

```
ParayObserver detects change
  → ParayKnowledgeObserver.onCatalogChange()
  → CSV: import_changes for that import only
  → PNG: product_images linked since last refresh
  → Learn: single article from learn_index
  → knowledge_articles.json + brand/category rollups
  → knowledge_summary.json (cached KPIs — UI reads file, never recomputes live)
```

See `docs/PARAY_KNOWLEDGE.md`.

## PARAY Workflow Phase 3 (v2.19.0)

```
NavController destination change / feature hook
  → ParayWorkflowTracker
  → screen_usage.json + workflow_patterns.json
  → workflow_summary.json (cached — no live recompute on screen open)
```

Workflow patterns only — never user text, barcodes, images, or camera frames. See `docs/PARAY_WORKFLOW.md`.

## PARAY Learn V1 (v2.16.0 → v2.16.1)

```
PARAY tab → Learn → Start learning
    → ParayLearnEligibility.filterReady(articles + barcode + FOUND PNG)
    → stats: ready, learned, partial, pending, coverage %
    → ParayLearnPreload.load(articleId)
        PNG features, fingerprint?, brand, category, family, existing record
    → session: front confirm (PNG vs camera) — front NOT stored as learned side
    → auto capture Left → Right → Back (settings-driven thresholds)
    → learn_index.json (visual knowledge, separate from Room)
    → on LEARNED: visual_index.json + brand_family_index.json
    → packaging drift → logs/packaging_variants.jsonl (V1 log-only)
```

AGENT recognition unchanged — `ParayCameraMatcher` boosts scores using PNG product signature + learned sides.

### Learn settings (configurable thresholds)

```
paray_home/memory/learn_settings.json
  frontConfirmationThreshold
  sideCaptureThreshold
  backCaptureThreshold
    → ParayLearnSettingsStore
    → ParayLearnEngine(settings)   // no hardcoded confidence in engine
```

### PARAY Memory tab (v2.20.2)

```
PARAY tab → Memory
    → ParayMemoryRepository.loadAll()
        → ParayLearnStore.allRecords()  // learn_index.json only — no Room
    → filter: All | Learned | Partial | Pending
    → search: barcode | designation | brand
    → display: PNG, metadata, status, front/L/R/B flags, last learning date
    → refresh when tab selected (after learn sessions)
```

### PARAY Knowledge tab (v2.20.3)

```
PARAY tab → Knowledge
    → ParayKnowledgeRepository.load()
        → knowledge_summary.json  (global KPIs)
        → knowledge_articles.json (per-article cache; group rollups in memory)
    → display: summary, brands, categories, families, recently learned
    → no Room / SQL aggregations
    → refresh when tab selected
```

### PARAY Statistics tab (v2.20.4)

```
PARAY tab → Statistics
    → ParayStatisticsRepository.load()
        → learn_index.json          (learned/partial counts; learning trend)
        → knowledge_summary.json    (coverage %, ready pool, brand/category/family totals)
        → workflow_summary.json     (top screen/feature, AGENT + Design export counts)
    → progress cards + coverage bar + learning trend list
    → no Room / SQL aggregations
    → refresh when tab selected
```

### PARAY Recognition curiosity (v2.21.0)

```
AGENT / Scanner / Learn packaging drift
    → ParayRecognitionTracker
        → ParayRecognitionObserver.record()
            → recognition_events.jsonl
            → unknown_products.json
            → failure_patterns.json
            → recognition_summary.json (cached rollups)
    → observation only — no prompts, no automatic actions
```

### PARAY Knowledge Fusion (v2.22.0)

```
PARAY Home → Export Knowledge
    → ParayKnowledgePackageExporter
        → staging dir (memory/knowledge/workflows/recognition/observer JSON)
        → package_manifest.json (PKP v1, deviceKnowledgeId)
        → PARAY_yyyy_MM_dd.pkp.zip → user-picked URI

PARAY Home → Import Knowledge
    → unzip → ParayKnowledgePackageValidator
    → ParayKnowledgeFusionEngine.previewFusion() → summary dialog
    → Merge → ParayKnowledgeFusionEngine.executeFusion() on Dispatchers.IO
        → ParayFusionConflictResolver (richer learn, higher visual quality, accumulate stats)
        → fusion_history.json + fusion_conflicts.json
    → never overwrites blindly; never deletes local knowledge
```

Settings UI to edit values — planned, not built.

## 2a. Sub-barcode flavor persistence (v2.15.5)

```
Save sub-barcode PNG (Check & Shoot / PhotoRoom)
    → filename: {designation}{altIndex}.png  (e.g. PommesGolden1.png)
    → PNG metadata: Barcode, ParentBarcode, VariantType=sub, designation, price…

Load PNGs / CSV import / Settings → Sync sub-PNGs
    → scan product_images — detect VariantType=sub or ParentBarcode (legacy sub_*.png too)
    → backfill metadata on old files; rename legacy sub_* → designation{n}.png
    → link article_alternate_barcodes — scanner + search + To share variants

Primary image re-index
    → sub-variant PNGs excluded (not matched as main article gallery)
```

## 2. Article search & catalog

```
User query
    → ArticleDao.searchWithImages(designation LIKE)
    → OasisRepository.buildSearchResults: append sub-barcode variant rows (same designation, sub imagePath)
    → Home / Catalog list (main + each sub-barcode as separate row)
```

## 2b. Sub-barcode acquisition (v2.4.0)

```
Scan sub-barcode (AGENT SUB-BC or Article → Add sub-barcode)
    → validateSubBarcodeLink (no DB write yet)
    → User choice:
        → **Shoot flavor photo** → Camera batch → JPEG → PhotoRoom PNG → importFromPhotoroom
            → registerSubBarcodeImage → article_alternate_barcodes.imagePath
        → **Link barcode only** → linkSubBarcodeToMainArticle (parent imagePath, no new photo)
    → importFromPhotoroom adds **sub-barcode flavor row** to To share (`variantBarcode` on preselection_items)
    → **Add PNG image** (article detail / AGENT / carts / Articles search) → GalleryPngAssignService
        → copy picked PNG → register main or sub variant → link DB + update cart row
    → **Pick PNG** on import screen when PhotoRoom export failed — user selects file; app renames to gallery PNG
    → Legacy sub-barcodes without imagePath: unchanged (fallback to main article image)
    → Tap sub-barcode chip → unlinkAlternateBarcode
```

## 3. Barcode scanner

```
Barcode (typed or camera future)
    ??? ArticleDao.getByBarcode()
    ??? ArticleWithImage join
    ??? Scanner result card
```

## 4. Image matching

```
files/product_images/*.png
    ??? read Barcode / Description PNG metadata when present
    ??? build in-memory barcode/designation maps
    ??? match barcode first, then normalized designation filename
    ??? replace ProductImageEntity index in one Room transaction
    ??? status: FOUND | MISSING | MULTIPLE_MATCHES
```

## 5. Pre-selection ??? print

```
Article ??? PreselectionItemEntity
    ??? user picks PrintTemplateEntity
    ??? PrintGenerator.generateFromPreselection()
        ??? PdfDocument ??? files/exports/*.pdf
        ??? PrintBatchEntity + PrintBatchItemEntity (snapshots)
    ??? optional promo dates ??? PromoService.refreshAlerts()
```

## 5a. Cart origin tags (`preselection_items.note`)

```
Add to cart from:
    Batch txt     ? SRC_BATCH_TXT (blue)
    Stamper/Done  ? SRC_STAMPER (green)
    Check & shoot ? SRC_CHECK_SHOOT (purple)
    Scanner tab   ? SRC_SCANNER (teal)
    Home search   ? SRC_HOME (orange)
    Article detail? SRC_ARTICLE (pink)
    (default)     ? SRC_MANUAL (gray)
UI: colored card border + label on To share and To shoot; legend on both carts

**v2.4.5 cart variants:** `preselection_items.variantBarcode` (empty = main article barcode). Unique key `(articleId, cartType, variantBarcode)` — parent and each sub-barcode flavor are separate rows. Cart queries resolve `imagePath` from `article_alternate_barcodes` when variant is set.
```

## 5a. PARAY PC fingerprints → phone (Option A)

```
PC: product_images/*.png (2,892 linked)
    → scripts/BUILD-PARAY-FINGERPRINTS.ps1
    → CLIP ONNX or paray-lite-v1 embedding per barcode
    → exports/paray/paray_fingerprint_index.json

Phone: Settings → Import PARAY fingerprints (after Gestium CSV import)
    → ParayFingerprintImporter maps barcode → articleId
    → files/paray/visual_index.json (shape/colors seeded)
    → files/paray/fingerprint_index.json (512-dim vectors)
```

## 5b. To share → Design (shelf labels)

```
To share cart (checked articles with PNG)
    → Add selected / Add all to Design
    → preselection_items cart_type = DESIGN
Design screen (To print queue, cart_type = DESIGN)
    → **− / +** copy stepper (1–99); DesignCartExpand repeats article on A4
    → **Standard / Promo** per row — promo uses promoPrice + promoOriginalPrice (prix-barrée on print)
    → **PARAY** active (visual learning + layout fit + GPU probe)
    → tap Shelf labels card → 2×6 grid (12 labels)
    → LayoutFitAgent: alpha bbox → contain-fit in white slot
    → Generate landscape A4 JPEG (ShelfA4Renderer) → `exports/yyyy-MM-dd/shelf_HHmmss_pN.jpg`
    → Auto-share file on create; record `print_batches` + item snapshots (Room v18)
    → Ready to print preview + Share as file → articles on page move to Done
    → **Historique** tab lists Design shelf prints (newest first) → batch detail:
         live catalog merged with snapshot; exclude rows; reprint selection; load queue;
         per-row **Design** / **To share**; share original JPEG
    → CSV import / price edit refreshes open batch + glows changed articles (`changeStatus`, `needsTicketUpdate`, price vs print snapshot)
    → **Send info** (text share) → all To print items move to Done (cart_type = DESIGN_DONE)
    → Done capped at **50** articles — oldest dropped when over limit
    → **Pull up** on Done row → back to DESIGN queue
    → **Share all as files** marks `product_images.lastSentAt` → To share row shows **Telegram: sent …**
    → Add to Design → row shows **Design: in print queue**; after print/Done → **Design: printed / done**
    → Shelf print batch → row shows **Printed: dd/MM/yyyy HH:mm**
    → CSV import price change → `changeStatus` + glow on ArticleCard / Design queue / Report cards
    → **Every CSV import** reloads VisioPRO rayon pools from DB; articles not yet enabled → pending in Settings (**Nouveau CSV**)
    → Gestium CSV (cp1252): rows without Code-barres import via Gestium **Code** (`CA:13145`) — e.g. PIEDS DE VEAU
    → **Settings → Rayons importants** — user checklist; scopes Articles rayon chips, dashboard stats, Catalog filters, CSV report (ignored rayons hidden)
```

## 5c. To share → Telegram/file share

```
Share cart ? Share all as files (N) or selected subset
    ??? copy each PNG to cache/share-export/ as DESIGNATION_KEY.png (or barcode stem if no name)
    ??? write CSV details into PNG metadata (designation, price, barcode, rayon, codeart)
    ??? ACTION_SEND_MULTIPLE with MIME application/octet-stream (document files, not compressed photos)
    ??? optional EXTRA_TEXT summary for whole batch
    ??? product_images.lastSentAt updated for batch
    ??? workflow_history SENT events
```

## 5d. Workflow history

```
New user actions only
    ??? search text after debounce
    ??? barcode scan / lookup
    ??? add to To shoot / To share
    ??? image link / Stamper Done
    ??? share selected files
    ??? workflow_history latest 500 events
```

## 6. Print audit

```
PrintBatchEntity (immutable snapshot record)
    ??? PrintBatchItemEntity per article
    ??? PrintHistoryScreen / PrintBatchDetailScreen
    ??? status: GENERATED ??? PRINTED ??? PLACED
```

## 7. Promo lifecycle

```
Print batch with isPromo=true + promoEnd
    ??? PromoService.refreshAlerts()
    ??? PromoAlertEntity (expires today / expired)
    ??? PromoScreen
```

## External inputs (pending)

| Input | Status |
|-------|--------|
| Real GestiumERP CSV | Ready ??? `imports/gestium_articles_2026-05-24.csv` (~27k rows) |
| Ready PNG product images | Done ??? `G:\DATA BASE` copied into repo; 2,892 linked, 1,064 unmatched in `product_images/not found/` |
| Camera barcode scan | Done ??? CameraX + ML Kit in Scanner tab |

## 8. Missing image ??? ready PNG link

```
External app removes background
    ??? ready PNG
    ??? To shoot ??? Stamper
        ??? Android gallery/photo picker selects many PNGs
        ??? UI keeps URI list only; visible image cards load lazily
        ??? barcode scanner stays active above card slider
        ??? scan fills current card with article data
        ??? active card is chosen from the card nearest screen center, so the last card gets its turn
        ??? sliding to next card resets active scan target without losing previous card assignments
        ??? Done batch-copies assigned images into files/product_images/
        ??? rename + Barcode/Description/CSV detail metadata + ProductImageEntity
        ??? remove completed articles from To shoot, add to To share, show in History
    ??? To shoot ??? tap article card ??? pick PNG
        ??? copy temp PNG into files/product_images/
        ??? rename using designation/barcode target
        ??? write PNG Barcode/Description metadata
        ??? save ProductImageEntity
        ??? remove from To shoot and add to To share
    ??? Settings ??? Load ready PNG images
        ??? copy selected PNGs into files/product_images/
        ??? ImageMatcher.syncImagesForArticles()
    ??? Oasis gallery link flow (planned for one-by-one naming)
        ??? scan barcode
        ??? lookup article designation
        ??? save PNG as designation filename + barcode metadata
        ??? ImageMatcher.syncImagesForArticles()
```

| Input | Status |
|-------|--------|
| PC synced PNG set | Ready ??? `product_images/` top-level linked PNGs |
| Android ready PNG loader | Done ??? Settings ??? Load IMAGE ASSETS folder / files; Oasis model tags (Barcode, Codeart, Designation, PriceNow, Rayon) |
| Android cloud article sync | **Removed** — use Gestium CSV import on phone |
| Android gallery link wizard | Planned ??? pick PNG ??? scan barcode ??? save/link |

## 8. Images and sharing

```
To share (Android) → user selects PNGs
    → Share to Telegram: bulk ACTION_SEND_MULTIPLE (metadata in PNG files)
    → Add to Design → shelf A4 JPEG on phone
    → Phone sync (hotspot): master/slave PNG delta (port 8776)
```

## 9. Articles smart search

```
Articles search box
    -> trim + normalize (accents stripped, uppercase tokens)
    -> SQL pre-filter on longest token (designation, normalizedName, barcode, codeart)
    -> all tokens must match anywhere in name/barcode/brand/category
    -> rank: exact > starts-with > contains; split with/without PNG
```

## 10. Report (Settings)

```
Settings -> Report
    -> Latest vs previous Gestium CSV import summary
    -> import_changes: NEW / PRICE_CHANGED / RENAMED / REMOVED (old -> new values)
    -> each row: join article + product_images; Add to To share (has PNG) or To shoot (no PNG)
    -> Design shelf JPEG exports logged as print_batches (templateName "Design — …")
```

## 11. AGENT (formerly Scan & shoot)

### Smart mode (default)

```
AGENT (bottom nav) — Smart
    -> CameraX preview stays bound; ML Kit barcode read toggles on lock/PARAY sheet
    -> resolve barcode: Gestium CSV, linked alternate, or unique 9-digit body match (drop last 5)
    -> lock primary CSV barcode -> locked session (shoot / To share)
    -> SUB-BC (while locked): toggle re-enables barcode scan; flavor/color barcodes saved to article_alternate_barcodes (not CSV)
    -> lock unknown barcode -> PARAY suggestions sheet
         -> gestiumBodyKey: drop last 5 digits, compare first 9 on the left
         -> user confirms -> alternate barcode linked + PARAY memory
         -> optional: Let PARAY look (system camera) -> visual fingerprint match
    -> unlock -> clear session; scanner resumes immediately
```

### AGENT Smart mode (v2.36+ — Ticket/Bulk removed from CheckShoot)

```
AGENT — barcode camera
    -> debounced scan -> article card
    -> lock if in Gestium catalog else "Not in catalog — search in Articles tab"
    -> optional: Link barcode to catalog (suffix picker -> linkAlternateBarcode)
    -> locked actions: share, design, shoot PNG (U2NetP), SUB-BC
    -> AgentSessionStore persists lock across restart
```

### Ticket mode (tap-to-capture, v2.31.0) — *removed from CheckShoot UI 2026-06-29*

```
AGENT — Ticket toggle
    -> camera ring buffer (best frame on tap)
    -> user taps screen on ticket → photo captured
    -> ParayTicketReader.processSnap()
         -> crop yellow block + product PNG from photo
         -> OCR designation + price + PARAY PNG match (50/30/20 fusion)
         -> auto-lock article card
    -> swipe unlock → tap next ticket
```

### Bulk mode (mall photo job) — *removed from CheckShoot UI 2026-06-29*

```
AGENT — Bulk toggle
    -> scan barcode only (no catalog / PARAY / designation checks)
    -> if bulk_images/{barcode}.png or legacy product_images/{barcode}.png exists:
         -> user Replace (camera + cutout) or Skip (keep scanning)
    -> else: Take photo -> U2NetP cutout -> save DCIM/BULK/{barcode}.png
    -> append barcode to DCIM/BULK/bulk_done.txt
    -> upsert bulk_captures (barcode PK, imagePath, capturedAt, replaced, syncStatus=PENDING)
    -> ready for future server sync via SQLite
```

## 11b. Design queue order

```
To print list ordered by sortOrder ASC, addedAt ASC
    -> Share as file / Send info / onPrintShared:
         moveDesignItemsToDone preserves each item's sortOrder
         items moved in current queue order (not re-numbered)
    -> Pull up from Done: restoreDesignItemFromDone keeps original sortOrder
```

## 12. Batch txt routing

```
Batch txt screen receives multi-line designations
    -> normalize each designation and lookup article
    -> if article has existing PNG: add to To share cart (SRC_BATCH_TXT)
    -> if article exists but PNG missing: add to To shoot cart (SRC_BATCH_TXT)
    -> if no article match: insert into batch_camera_queue (Room v13)
         -> Batch txt shows clickable queue list
         -> tap item -> camera_batch_shoot?queueItemId=
              scan barcode (lock) -> shoot -> Proceed -> mark queue item done -> next pending
              unknown barcode: Create designation + CSV match
                   Proceed -> linkSubBarcodeToMainArticle + continue shoot flow
                   Add to To share -> cart + unlock scan
    -> JPEG saved to Download/VisioAi/Batch_images[date]/ with barcode in sidecar metadata
```

## 13. IMAGE ASSETS folder load automation

```
Settings -> Load IMAGE ASSETS folder
    -> scan selected folder recursively for PNGs
    -> process copy in 500-file batches automatically until all files are handled
    -> if target file already exists and non-empty: skip (no copy)
    -> run image re-index after load
```

## 14. VisioPRO (v2.6.0+)

```
VisioPRO home (bottom nav)
    -> VisioProHome (categories: Fruits, Legumes, Boucherie, Poisson)
    -> VisioProCategory
        -> Social tab: manual price first, else CSV by designation keywords
        -> Print tab: CSV first, else manual price from social memory (shared slug)
        -> VisioProCardRenderer (1080 social / 1200x1697 print)
        -> product PNG from VisioProMediaStore (sideload) or catalog when keyword match has image
        -> export -> DCIM/VisioPRO/Social or Print
        -> VisioProStore (visio_pro_memory.json): manualPrice, lastSocialExportAt, lastPrintExportAt

Settings -> Install VisioPRO images (v2.37.0)
    -> pick VisioPRO-media.zip (from BUILD-VISIOPRO-MEDIA.ps1 on PC)
    -> VisioProMediaImporter unzips to filesDir/visiopro_media/visiopro/
    -> VisioProTemplateAssets.loadBitmap checks device files first, then lean APK assets (layout JSON only)
```

Preset definitions: `VisioProPresetCatalog.kt` (agent-maintained; 7 layout families). Fish = social only, no price on card.
