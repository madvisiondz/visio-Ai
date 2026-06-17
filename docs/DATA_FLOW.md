# Oasis AI ??? Data Flow

## 1. GestiumERP import

```
CSV file (content URI or assets sample)
    ??? CsvParser (flexible French/English headers)
    ??? ImportService
        ??? compare by barcode vs existing articles
        ??? detect NEW / PRICE_CHANGED / RENAMED / REMOVED
        ??? write articles + import_changes + article_price_history
    ??? ImageMatcher.syncImagesForArticles()
    ??? UI: Import summary + ImportDetailScreen (enriched rows: PNG thumbnail, Add to To share / To shoot)
```

## 2. Article search & catalog

```
User query
    → ArticleDao.searchWithImages(designation LIKE)
    → OasisRepository.buildSearchResults: append sub-barcode variant rows (same designation, sub imagePath)
    → Home / Catalog list (main + each sub-barcode as separate row)
```

## 2a. Sub-barcode acquisition (v2.4.0)

```
Scan sub-barcode (AGENT SUB-BC or Article → Add sub-barcode & batch shoot)
    → validateSubBarcodeLink (no DB write yet)
    → Confirm add?
    → Confirm add? → shoot photo (required for new sub-barcodes)
        → Camera batch → JPEG → PhotoRoom PNG → importFromPhotoroom
        → registerSubBarcodeImage → article_alternate_barcodes.imagePath
    → importFromPhotoroom adds **sub-barcode flavor row** to To share (`variantBarcode` on preselection_items)
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
    → **PARAY** active (visual learning + layout fit + GPU probe)
    → tap Shelf labels card → 2×6 grid (12 labels)
    → LayoutFitAgent: alpha bbox → contain-fit in white slot
    → Generate landscape A4 JPEG (ShelfA4Renderer)
    → Ready to print preview + Share as file → articles on page move to Done
    → **Send info** (text share) → all To print items move to Done (cart_type = DESIGN_DONE)
    → Done capped at **50** articles — oldest dropped when over limit
    → **Pull up** on Done row → back to DESIGN queue
    → **Import prices** matches barcodes in both To print and Done
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

### Bulk mode (mall photo job)

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
