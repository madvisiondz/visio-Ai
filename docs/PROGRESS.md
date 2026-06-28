# Oasis AI — Progress Log

> What has been built. Update at end of each work session.

## 2026-06-14 — Visio Ai v2.34.2 Ticket → article card (no suggestions)

- [x] Best PARAY match opens standard article card (same as barcode Smart mode)
- [x] Hold 1s / lock button → actions (share, design, PNG, verify ticket, sub-BC…)
- [x] Swipe unlock → next ticket
- [x] Removed suggestions picker overlay
- [x] APK **2.34.2** (338)

---

## 2026-06-14 — Visio Ai v2.34.1 Ticket crop + capture polish

- [x] **No post-user sub-crop** — OCR uses full user crop; yellow-band re-crop removed
- [x] **Instant WYSIWYG capture** — removed 520ms burst; newest frame in last 400ms
- [x] **Full-crop OCR** — all black text lines (up to 4 rows); no yellowBandOnly filter
- [x] **Live quality hint** on tap overlay
- [x] APK **2.34.1** (337)

---

## 2026-06-14 — Visio Ai v2.34.0 Ticket stabilization + crop lock

- [x] **Stabilization burst** — ~520ms multi-frame pick sharpest frame (not latest/blur)
- [x] **User crop locked** — `analyzeUserValidatedCrop` skips visual re-scout; OCR on confirmed crop only
- [x] **Post-process tap fix** — removed buffer wipe on unlock; no false "frame too old"; clearer warmup messages
- [x] APK **2.34.0** (336)

---

## 2026-06-14 — Visio Ai v2.33.3 Ticket process bitmap crash fix

- [x] **`previewCopy`** — always returns UI-owned bitmap (never aliases pipeline working bitmap)
- [x] **Deferred recycle** — crop/progress previews recycled ~64ms after state cleared (Compose draw safe)
- [x] **Crop overlay** — owns display copy via `DisposableEffect`
- [x] APK **2.33.3** (335)

---

## 2026-06-14 — Visio Ai v2.33.2 Ticket crop confirm crash fix

- [x] **Recycled bitmap crash** — crop confirm copies frame for PARAY pipeline; UI bitmap recycled after Compose drops overlay
- [x] **`ParayTicketReader`** — owns input copy; never recycles caller's bitmap
- [x] APK **2.33.2** (334)

---

## 2026-06-14 — Visio Ai v2.32.2 Ticket walk auto-reset

- [x] **Instant dismiss** — progress overlay clears as soon as match is found (no 1.8–2.2s hang)
- [x] **Session guard** — stale OCR step updates ignored after terminal state
- [x] **IO offload** — catalog lookup + assessment off main thread
- [x] **Auto-unlock** — ~1.4s after match, agent resets for next ticket (swipe to skip wait)
- [x] **Buffer flush** — camera ring buffer cleared on unlock
- [x] APK **2.32.2** (330)

---

## 2026-06-14 — Visio Ai v2.32.1 Ticket camera + crop fix

- [x] **YUV stride fix** — `CameraFrameUtils.yuv420888ToNv21` copies luma/chroma with row/pixel stride (fixes vertical stripe / stretched crop preview)
- [x] **Best back camera** — `bestBackCameraSelector()` picks largest sensor + standard focal length (not ultrawide/macro)
- [x] **High-res Ticket mode** — up to 4K analysis + JPEG quality 95; faster buffer interval (150ms)
- [x] **Warm-block guard** — reject narrow vertical blobs; edge-density penalty on desk grain false positives
- [x] **Crop preview UI** — aspect-ratio locked preview in progress panel
- [x] APK **2.32.1** (329) → `VisioAI-debug.apk`

---

## 2026-06-14 — Visio Ai v2.31.0 Ticket tap-to-capture

- [x] **Tap to shoot** — tap camera screen captures photo instantly (replaces hold-to-snap)
- [x] **Rayon filter removed** — no picker, no soft rayon boost; matches any aisle
- [x] **Fusion weights** — designation 50%, price 30%, catalog PNG 20%
- [x] Simple status bar + shutter button overlay
- [x] APK **2.31.0** (325)

---

## 2026-06-14 — Visio Ai v2.30.0 PARAY Vision stabilization

- [x] **Ring buffer** — `TicketCameraBuffer` keeps 6 recent frames; `takeBestSnap()` picks sharpest + yellow signal
- [x] **Frame quality** — `ParayTicketFrameQuality` Laplacian sharpness + yellow presence; live meter on hold overlay
- [x] **Recovery** — boosted contrast/saturation pass (`boostForRecovery`); alternate-frame retry in ViewModel
- [x] **Match tiers** — Confirmed / High / Probable (`ParayTicketMatchTier`); badge on card + progress panel
- [x] **Anti-flip** — `ParayTicketSnapStabilizer` debounces rapid article switches on weak matches
- [x] **Haptics** — `TicketSnapHaptics` on hold complete, match tier, failure
- [x] **PARAY Vision UX** — freeze-frame preview, branded overlay, tier on `TicketVerifyBanner`
- [x] APK **2.30.0** (324)

---

## 2026-06-14 — Visio Ai v2.29.0 Ticket snap production optimization

- [x] **Parallel pipeline** — OCR on yellow crop + PARAY visual identify run concurrently
- [x] **Single visual pass** — `visualHints` map reused in fuzzy scoring (removed per-candidate `identifyFromCamera`)
- [x] **Fast OCR** — enhanced pass first; fallback only if confidence < 88%
- [x] **Memory** — camera buffer downscaled to 1280px; stale frame rejection; preview capped at 280px
- [x] **Yellow detect** — center ROI first, full-frame fallback; coarser sample step
- [x] **Catalog PNG decode** — `inSampleSize` for image similarity (no full-res loads)
- [x] **Cancellable** — snap job cancelled on unlock/mode switch; UI step throttle 45ms
- [x] **Metrics** — `ParayTicketSnapMetrics` logged to `Oasis/Paray`; warn if > 4s
- [x] APK **2.29.0** (323)

---

## 2026-06-14 — Visio Ai v2.28.0 Ticket hold-to-snap

- [x] Replaced continuous live OCR with **hold-to-snap** — user holds on camera ~0.75s → single frame capture
- [x] `ParayTicketReader.processSnap()` — step callbacks: capture → yellow crop → OCR → PNG crop → fuzzy match
- [x] `TicketSnapProgressPanel` — shows each step + crop previews + match %
- [x] `TicketCameraBuffer` — latest frame only (no background OCR load)
- [x] APK **2.28.0** (322)

---

## 2026-06-14 — Visio Ai v2.27.1 Ticket OCR pipeline fix

- [x] **Critical fix** — rotate camera bitmap to upright *before* yellow-block detect + OCR (was passing sensor rotation to cropped region → ML Kit read garbage)
- [x] **High-end OCR** — 720px upscale, contrast boost, dual-pass ML Kit; price regex + line-height parsing for magenta price
- [x] **Catalog** — `searchArticlesNearPrice()` for 290 DA-style matches; Levenshtein token fuzzy (FLECHA ↔ FLECHE)
- [x] **Live overlay** — ticket mode shows read status on camera; debug builds show `Logcat: Oasis/Paray`
- [x] Camera 1080p analysis frames @ 850ms; APK **2.27.1** (321)

---

## 2026-06-14 — Visio Ai v2.27.0 Ticket fuzzy fusion

- [x] **`ParayTicketFuzzyMatcher`** — designation (42%) + price (32%) + ticket PNG (26%); rayon is soft preference only (+5% boost, never blocks)
- [x] Product crop left of yellow block (`ParayShelfYellowDetector.productRectLeftOf`) — handles small/foggy printed PNG on shelf tickets
- [x] Candidate pool: designation search, token search, price proximity, PARAY visual index on crop
- [x] Article card shows **PARAY X%** + breakdown (text · price · PNG); rayon picker labeled “matches any rayon”
- [x] APK **2.27.0** (320)

---

## 2026-06-27 — Visio Ai v2.26.2 Ticket mode UX polish

- [x] Fixed AGENT header — `Column` layout (mode chips + rayon bar no longer overlap in `Box`)
- [x] Scrollable rayon picker — `ModalBottomSheet` + search + `LazyColumn`
- [x] Stable ticket walk status — single-flight OCR, delayed spinner, camera pauses while picker open
- [x] APK **2.26.2** (319)

---

## 2026-06-27 — Visio Ai v2.26.1 Ticket camera OCR

- [x] **Live ticket read** — yellow block detection (#FFE500) + ML Kit OCR on designation/price
- [x] `ParayTicketReader` / `ParayShelfYellowDetector` / `ParayTicketOcr` — PARAY-owned; catalog match by designation or barcode
- [x] AGENT Ticket mode: walk aisle with camera → card opens like barcode scan; optional **Pick rayon** filter
- [x] APK **2.26.1** (318)

---

## 2026-06-27 — Visio Ai v2.26.0 Rayon + AGENT Ticket verify

- [x] `ArticleRayonLine` on `ArticleCard`, `ArticleActionPanel`, import change cards, camera batch match picker
- [x] AGENT **Ticket** mode (`AgentCaptureMode.TICKET`) — scan shelf barcode → auto-lock → PARAY verdict
- [x] `ParayTicketAdvisor` + `ParayTicketStore` — catalog vs last print snapshot; audit log in `paray_home/workflows/ticket_events.jsonl`
- [x] Recognition events `TICKET_VERIFY_SCAN` / `TICKET_VERIFIED`; workflow feature `TICKET_VERIFY`
- [x] APK **2.26.0** (317)

---

## 2026-06-27 — Visio Ai v2.25.1 CSV import performance

- [x] Removed automatic `syncSubPngsFromMetadata` from CSV import (was ~5 min with flavor PNGs)
- [x] Sub-barcode barcodes excluded from “removed from catalog” detection
- [x] `SubBarcodeFlavorService` optimized (cached alt indices, skip already-linked, no upfront cache wipe)
- [x] Removed dead `ScanResultPanel`; clearer import preview progress labels
- [x] APK **2.25.1** (315)

---

## 2026-06-27 — Visio Ai v2.25.0 UX & options standardization

- [x] `ArticleActionMenu` + `ArticleCompactActionRow` — shared presets for Home, Cart, AGENT, Scanner, Detail
- [x] AGENT: smart barcode trust (catalog = 2 reads, same-article misread ignored); scan continues while card preview open
- [x] AGENT: long-press 1s to lock; swipe ←/→ to unlock; auto-unlock after cart actions; removed "Dismiss — keep scanning"
- [x] `VisioProBundleImporter` + Settings **Import VisioPRO presets** (format v1, export-compatible)
- [x] APK **2.25.0** (314)

---

## 2026-06-27 — Visio Ai v2.24.0 Phase C polish

- [x] Hilt — `@HiltAndroidApp`, `OasisDatabaseModule` (DB, repository, registries, backup security)
- [x] `AtomicJsonWriter` — all PARAY/registries/settings JSON persistence
- [x] `network_security_config.xml` — Supabase HTTPS-only; mall LAN cleartext for phone sync
- [x] Optional backup AES-256-GCM encryption (Settings toggle + password)
- [x] `LocalCrashReporter` + release log hook (Crashlytics deferred)
- [x] Unit tests: `AtomicJsonWriterTest`, `BackupCryptoTest`
- [x] APK **2.24.0** (313)

---

## 2026-06-27 — Visio Ai v2.23.1 Phase B scale & performance

- [x] Home rayon browse — Paging 3 (40/page, gallery-first sort)
- [x] PARAY learn index memory cache; PhotoRoom index single-flight + 24h TTL
- [x] Design render single-flight
- [x] APK **2.23.1** (312)

---

## 2026-06-27 — Visio Ai v2.23.0 Phase A production hardening

- [x] `fallbackToDestructiveMigration(false)` — DB never silently wiped on upgrade
- [x] `OasisDatabaseMigrations.kt` + Room schema export (`schemas/.../18.json`)
- [x] Migration instrumented tests (16→17 rayon backfill, 17→18 print snapshots)
- [x] Release R8 + ProGuard rules; optional release signing in `local.properties`
- [x] Debug APK suffix `.debug` for side-by-side install
- [x] Timber + `OasisLog` (Import, Share, Database, …)
- [x] Unit tests: `PngShareHelperTest`, `ImportCatalogMapsTest`
- [x] `testDebugUnitTest` + `assembleRelease` SUCCESS — APK **2.23.0** (311)

---

## 2026-06-14 — Visio Ai v2.22.3 Telegram spaced filenames (real fix)

- [x] Telegram reads FileProvider DISPLAY_NAME from `.oasis` cache file — spaced designation + index on cache filename
- [x] Fallback: spaced designation from PNG metadata when DB value is compact
- [x] APK **2.22.3** (code 310)

---

## 2026-06-14 — Visio Ai v2.22.2 Telegram sub-barcode share fix

- [x] **PngShareHelper** — unique export filename per variant PNG (`PommesGolden1.png`, …); was overwriting cache with same `DESIGNATION.png`
- [x] **compileDebugKotlin SUCCESS** — APK **2.22.2** (code 309)

---

## 2026-06-14 — Visio Ai v2.22.1 PNG assign + import perf + sub-barcode flow

- [x] Fast PNG metadata append (no full-file rewrite on fresh imports)
- [x] PhotoRoom import perf fixes (skip IDAT reads, IO matching, no rescan per import)
- [x] **Add PNG image** on article detail, AGENT, To shoot, To share, Articles search cards
- [x] Sub-barcode flow: scan → **Shoot flavor photo** OR **Link barcode only**
- [x] `GalleryPngAssignService` — pick PNG → gallery + cart
- [x] **compileDebugKotlin SUCCESS** — APK **2.22.1** (code 308)

---

## 2026-06-14 — Visio Ai v2.22.0 PARAY Knowledge Fusion Phase 5

- [x] PKP format (`.pkp.zip`) — memory, knowledge, workflows, recognition, observer JSON only
- [x] `ParayKnowledgeFusionEngine` + `ParayFusionConflictResolver` — incremental merge on `Dispatchers.IO`
- [x] `paray_home/fusion/` — `fusion_history.json`, `fusion_state.json`, `fusion_conflicts.json`, `deviceKnowledgeId`
- [x] PARAY Home — Export / Import / Fusion History + import preview (Merge / Cancel)
- [x] **compileDebugKotlin SUCCESS** — APK **2.22.0** (code 307)

---

## 2026-06-14 — Visio Ai v2.21.1 PARAY Living AI Presence V1

- [x] `ParayActivityMonitor` + `ParayActivityLed` on PARAY Home and PARAY tab screens
- [x] Recognition Curiosity card on PARAY Home; Recognition Intelligence on Statistics tab
- [x] Event model aligned to spec (`timestamp`, `source`, `metadata`)
- [x] APK **2.21.1** (code 306)

---

## 2026-06-14 — Visio Ai v2.21.0 PARAY Recognition Phase 4

- [x] `ParayRecognitionObserver` + `paray_home/recognition/` (events, unknown products, failure patterns, summary)
- [x] Observe: recognition failures, low confidence, manual corrections, packaging drift, unknown barcodes
- [x] Hooks: AGENT (`CheckShootViewModel`), Scanner, Learn packaging drift via `ParayAgent`
- [x] APK **2.21.0** (code 305)

---

## 2026-06-14 — Visio Ai v2.20.4 PARAY Statistics tab

- [x] **Statistics tab** — `ParayStatisticsRepository` reads `learn_index.json`, `knowledge_summary.json`, `workflow_summary.json` only
- [x] Learning KPI cards + coverage bar; knowledge brand/category/family counts; workflow screen/feature + AGENT/Design counts
- [x] Learning trend list from learn_index timestamps
- [x] Refresh when Statistics tab selected
- [x] APK **2.20.4** (code 304)

---

## 2026-06-14 — Visio Ai v2.20.3 PARAY Knowledge tab

- [x] **Knowledge tab** — `ParayKnowledgeRepository` reads `knowledge_summary.json` + `knowledge_articles.json` only (no Room)
- [x] Global summary: known articles, brands, categories, families
- [x] Brand / category / family sections with product counts (derived from articles cache)
- [x] Recently learned products (LEARNED status + timeline)
- [x] Refresh when Knowledge tab selected
- [x] APK **2.20.3** (code 303)

---

## 2026-06-14 — Visio Ai v2.20.2 PARAY Memory tab

- [x] **Memory tab** — `ParayMemoryRepository` reads `paray_home/memory/learn_index.json` only (no Room)
- [x] Per article: PNG, barcode, designation, brand, category, family, status, front/L/R/B flags, last learning date
- [x] Filter: All / Learned / Partial / Pending; search: barcode, designation, brand
- [x] Refresh when Memory tab selected (after learn sessions)
- [x] APK **2.20.2** (code 302)

---

## 2026-06-14 — Visio Ai v2.12.3 Fix 1118 barcode-less CSV rows skipped

- [x] Gestium rows with empty Code-barres but valid **Code** column no longer counted as skipped (e.g. PIEDS DE VEAU)
- [x] `resolveImportBarcode()` applied inside `parseRow`; Windows-1252 tried first
- [x] Import preview shows barcode-less count separately from true skips
- [x] APK **2.12.3** (code 271)

---

## 2026-06-14 — Visio Ai v2.12.2 Gestium CSV charset + full row import

- [x] `parseWithFallback` picks encoding with the most valid rows (cp1252 for Gestium exports)
- [x] Import keeps articles when matched by codeart or normalized designation (not only barcode)
- [x] Unit test: PIEDS DE VEAU → `CA:13145`, rayon Boucherie
- [x] APK **2.12.2** (code 270)

---

## 2026-06-14 — Visio Ai v2.12.1 VisioPRO full pool resync + codeart import

- [x] CSV import accepts rows without Code-barres — synthetic key from **codeart** (`CA:…`) or designation
- [x] After every CSV import, `syncRayonPoolsAfterCsvImport` reloads Boucherie / Poissonnerie / F&V pools; pending = pool − enabled
- [x] List editor + Settings refresh on resume; rayon query matches all DB rayon spellings
- [x] APK **2.12.1** (code 269)

---

## 2026-06-14 — Visio Ai v2.12.0 Rayons importants + VisioPRO CSV sync

- [x] Settings → **Rayons importants** checklist (persisted JSON); filters stats, Articles, Catalog, CSV report
- [x] After CSV import, new Boucherie / Poissonnerie / Fruits et Légumes articles → VisioPRO list **pending** (Nouveau CSV)
- [x] APK **2.12.0** (code 268)

---

## 2026-06-14 — Visio Ai v2.11.1 To share status + CSV glow fix

- [x] Fix To share cart mapping — pass real `changeStatus` / `needsTicketUpdate` to `ArticleCard`
- [x] `ArticleCard` + Report import cards use `catalogChangeGlow` for CSV changes
- [x] To share row footer: Printed / Design queue|done / Telegram sent (live from DB)
- [x] Cart SQL: `lastPrintedAt`, `inDesignQueue`, `inDesignDone` subqueries
- [x] APK **2.11.1** (code 267)

---

## 2026-06-14 — Visio Ai v2.11.0 Design print history

- [x] Shelf JPEG exports under `exports/yyyy-MM-dd/shelf_HHmmss_pN.jpg`; auto-share on print
- [x] Design tabs **À imprimer** / **Historique**; tap batch → detail with article list
- [x] Batch detail: exclude rows, reprint selection, load queue, send row to Design or To share, share JPEG
- [x] Room v18 — `print_batches.pageIndex`, batch item promo/copy snapshots; live catalog merge in `enrichDesignBatchItems`
- [x] `catalogChangeGlow` + **Modifié CSV** badge on queue, Done, and history rows when catalog changed
- [x] APK **2.11.0** (code 266)

---

## 2026-06-20 — Visio Ai v2.7.0 VisioPRO list settings

- [x] Settings → **Listes VisioPRO** route (`visio_pro_settings`, `visio_pro_list/{category}`)
- [x] Per card: checkbox articles from Gestium rayon (Fruits/Légumes → « Fruits et Légumes », Boucherie, POISSONNERIE)
- [x] **Ordre d'affichage** bottom sheet — long-press drag + Terminé
- [x] Config persisted offline (`visio_pro_catalog_config.json`); VisioPRO cards use saved order + live CSV prices
- [x] APK **2.7.0** (code 260)

---

## 2026-06-20 — Visio Ai v2.6.9 Articles rayon filter fix

- [x] **Root cause**: `CsvParser` mapped `category` from **Famille** (col 4) because header set included `famille`/`rayon` with wrong priority
- [x] Separate columns: **Famille**, **Catégorie**, **Rayon** (Gestium col 17 — Boucherie, Boissons, Confiserie, …)
- [x] DB v17: `articles.rayon`, `articles.famille`; migration backfills from rawData
- [x] Articles filter chips + scoped search use `rayon` column
- [x] APK **2.6.9** (code 259)

---

## 2026-06-19 — Visio Ai v2.6.3 Bottom nav fix + smoke tests

- [x] **Root cause**: `NavigationBarItem` inside `Row(horizontalScroll)` → zero-width / off-screen tabs on device
- [x] Custom scrollable bottom bar — fixed 76dp tabs, auto-scroll to selected route
- [x] Settings header shows `Visio Ai {version} ({code})`
- [x] JVM smoke tests: `BottomNavSelectionTest` (8 tabs, VisioPRO routes, bar visibility)
- [x] APK **2.6.3** (code 253)

---

## 2026-06-19 — Visio Ai v2.6.0–2.6.2 VisioPRO in-app module

- [x] **VisioPRO** first bottom-nav tab (not Settings)
- [x] 7 preset families: fruits/legumes/boucherie social+print; poisson social only
- [x] AIL PSD social cards; F&V print A4×4 batch + To share integration
- [x] Gallery export `DCIM/VisioPRO/Social|Print`
- [x] APK **2.6.2** (code 252)

---

## 2026-06-19 — Visio Ai v2.6.0 VisioPRO in-app module (initial)

- [x] Settings → **VisioPRO** — separate dark/gold UI (later moved to bottom nav)
- [x] 7 preset families: fruits/legumes/boucherie social+print; poisson social only (no price)
- [x] Article chip row + full card preview; CSV price + manual override + cross-channel memory
- [x] Gallery export `DCIM/VisioPRO/Social|Print`
- [x] `VisioProPresetCatalog.kt` — agent-maintained article list + layout themes
- [x] APK **2.6.0** (code 250)

---

## 2026-06-19 — PSD template inspection pipeline

- [x] `tools/psd/inspect_psd.py` (psd-tools) — layer tree, bounds, text, role hints → JSON
- [x] `tools/psd-node/inspect.mjs` (ag-psd) — cross-check reader
- [x] `scripts/INSPECT-PSD.ps1` — Windows one-shot runner
- [x] `templates/psd-inbox/` drop folder + `docs/PSD_TEMPLATES.md`

---

## 2026-06-19 — Visio Ai v2.5.1 Promo shelf ticket layout fix

- [x] DB v16: `isPromoTicket`, `promoPrice`, `promoOriginalPrice` on design queue rows
- [x] Design queue: **Standard / Promo** toggle + dual price fields
- [x] Shelf A4 print: promo price large pink left; original prix-barrée top-right (diagonal strike)
- [x] APK **2.5.0** (code 248)

---

## 2026-06-18 — Visio Ai v2.4.7 PhotoRoom manual PNG pick

- [x] **Pick PNG** on each PhotoRoom import card (SAF file picker)
- [x] `importFromManualPng` — same pipeline as auto import; gallery file renamed + metadata for article
- [x] APK **2.4.7** (code 247)

---

## 2026-06-17 — Visio Ai v2.4.6 Manual barcode entry on all scanners

- [x] Shared `ManualBarcodeEntrySection` + `ManualBarcodeEntryDialog`
- [x] **AGENT**: “Type barcode” in header → dialog (bypasses camera debounce; works in SUB-BC mode)
- [x] **Camera batch**: inline manual entry on scan step
- [x] **Settings scanner**: refactored to shared component
- [x] APK **2.4.6** (code 246)

---

## 2026-06-17 — Visio Ai v2.4.5 Cart flavor rows (sub-barcodes)

- [x] DB v15: `preselection_items.variantBarcode`; unique index `(articleId, cartType, variantBarcode)`
- [x] To share & Design lists show **parent + each sub-barcode** as separate cart rows (sub image from `article_alternate_barcodes`)
- [x] Remove/copy/move by `preselectionId` (no longer replaces parent when adding flavor)
- [x] PhotoRoom sub-barcode import → auto-add **that sub-barcode** to To share (not parent-only)
- [x] Articles search **Add to To share** passes scanned barcode as cart variant
- [x] APK **2.4.5** (code 245)

---

## 2026-06-17 — Visio Ai v2.4.4 Shelf print quality

- [x] Shelf A4 **3508×2480** (300 DPI, was 150 DPI)
- [x] JPEG export **quality 100** (was 80)
- [x] Product PNGs decoded at full slot resolution + bilinear filter on draw
- [x] APK **2.4.4** (code 244)

---

## 2026-06-17 — Visio Ai v2.4.3 Articles search crash fix

- [x] LazyColumn keys `id_barcode` for sub-barcode variant rows
- [x] Safer `observeArticles` — batch alternates, capped expansion, fallback
- [x] APK **2.4.3** (code 243)

---

## 2026-06-17 — Visio Ai v2.4.1 Sub-barcode always needs photo

- [x] Removed **add without photo** — confirm → shoot immediately
- [x] Sub-barcode linked only after PhotoRoom import with `imagePath`
- [x] Legacy sub-barcodes (no image) unchanged — still show parent image fallback
- [x] APK **2.4.1** (code 241)

---

## 2026-06-17 — Visio Ai v2.4.0 Sub-barcode confirm + per-barcode images

- [x] Sub-barcode scan → **confirm add** → **shoot this article?** (yes = batch camera + PhotoRoom wait; no = link without image)
- [x] Sub-barcode linked **after PhotoRoom import** when shooting (`pendingSubBarcodeLink` on `camera_batch_items`)
- [x] DB v14: `article_alternate_barcodes.imagePath`; per-sub-barcode PNG (`sub_{barcode}.png`)
- [x] Articles search shows **main + each sub-barcode** as separate rows (same designation, own image)
- [x] Scanner/resolve returns sub-barcode's own image
- [x] **Tap sub-barcode chip** to remove (AGENT, article detail, scanner, camera batch)
- [x] APK **2.4.0** (code 240)

---

## 2026-06-16 — Visio Ai v2.3.7 PhotoRoom → To share

- [x] After PhotoRoom import, articles auto-added to **To share** (tag `SRC_BATCH_CAMERA`)
- [x] Removed from **To shoot** when import completes
- [x] APK **2.3.7** (code 237)

---

## 2026-06-16 — Visio Ai v2.3.6 Article action panel + sub-barcode batch shoot

- [x] Shared **ArticleActionPanel** — scrollable, last price change + last printed, SUB-BC, all cart actions
- [x] Article detail **Add sub-barcode & batch shoot** → scan flavor barcode → auto-link → shoot
- [x] Camera batch auto-links unknown scan to known article as sub-barcode
- [x] Scanner resolves sub-barcodes via `resolveScannedBarcode`
- [x] APK **2.3.6** (code 236)

---

## 2026-06-16 — Visio Ai v2.3.5 Import detail empty list fix

- [x] Import detail queries **meaningful changes only** (excludes UNCHANGED) before image enrichment
- [x] New imports no longer write ~26k UNCHANGED rows to `import_changes`
- [x] APK **2.3.5** (code 235)

---

## 2026-06-16 — Visio Ai v2.3.4 Import change cart actions

- [x] **Import detail** + **Report** CSV change rows show linked product PNG or “No image”
- [x] **Add to To share** when article has gallery PNG; **Add to To shoot** when missing
- [x] Cart origin tag `SRC_IMPORT_CHANGE`
- [x] APK **2.3.4** (code 234)

---

## 2026-06-16 — Visio Ai v2.3.2 Design Done workflow fix

- [x] **Send info** / **Share as file** no longer auto-move to Done
- [x] **Mark as sent** (home queue) and **Mark as printed** (print screen) — manual only
- [x] Done list sorted by date/time (newest first); timestamp shown; **Remove** per row
- [x] APK **2.3.2** (code 232)

---

## 2026-06-16 — Visio Ai v2.3.1 PhotoRoom folder picker

- [x] Settings **PhotoRoom folder** — SAF folder selector (persisted URI)
- [x] Camera batch import + rescan use selected folder; reset to default `Pictures/Photoroom`
- [x] APK **2.3.1** (code 231)

---

## 2026-06-16 — Visio Ai v2.3.0 scan-first camera batch + queue

- [x] Batch txt **3-way routing**: PNG → To share, missing PNG → To shoot, not in CSV → **batch_camera_queue**
- [x] Camera batch **scan-first** (like AGENT): lock barcode → shoot → Proceed; JPEG + `.visio.json` sidecar
- [x] Unknown barcode: **Create designation** with CSV fuzzy match → **Proceed** (sub-barcode link) or **Add to To share**
- [x] Queue list on Batch txt; tap designation → camera batch focused item; auto-advance on Proceed
- [x] Room v13: `batch_camera_queue`; APK **2.3.0** (code 230)

---

## 2026-06-16 — Visio Ai v2.2.0 export + camera batch

- [x] Settings **Export PNG database** → `Download/VisioAi/Product_images[date]/` (metadata preserved)
- [x] Batch txt **Camera batch**: shoot → barcode → daily folder + `.visio.json` sidecar
- [x] **PhotoRoom import** screen: match `Pictures/Photoroom/` PNGs → product_images
- [x] APK **2.2.0** (code 220)

---

## 2026-06-16 — Visio Ai 2.0 white-label fork

- [x] Rebrand: **Visio Ai** (`com.oasismall.visioai`, v2.0.0 code 200), metallic theme, new launcher
- [x] **To shoot** bottom nav restored; wired from Articles, Article detail, Batch txt
- [x] AGENT scan card: designation, barcode, price, last price change date
- [x] APK: `VisioAI-debug.apk`

---

## 2026-06-07 — Bulk Download/BULK fix (v2.5.3)

- [x] Switched bulk PNG + tracker from DCIM to **Download/BULK** (`MediaStore.Downloads`)
- [x] APK **2.5.3** (code 53)

---

## 2026-06-07 — Bulk DCIM/BULK output (v2.5.2)

- [x] Bulk PNGs saved to **DCIM/BULK/{barcode}.png** (public gallery folder)
- [x] **DCIM/BULK/bulk_done.txt** — one line per barcode (`barcode`, `new`/`replaced`, timestamp)
- [x] APK **2.5.2** (code 52)

---

## 2026-06-07 — Bulk mode catalog PNG detection (v2.5.1)

- [x] Bulk scan checks `bulk_captures` + catalog `product_images` path (same resolution as Smart)
- [x] APK **2.5.1** (code 51)

---

## 2026-06-07 — Design queue order + AGENT Bulk mode (v2.5.0)

- [x] Design cart **sortOrder preserved** when moving To print ↔ Done (print share, Send info, pull up)
- [x] AGENT **Smart / Bulk** mode toggle (persisted in SharedPreferences)
- [x] **Bulk mode:** scan barcode → existing PNG → Replace or Skip → cutout saved as `{barcode}.png` in `files/bulk_images/`
- [x] Room **v11** — `bulk_captures` table (`syncStatus=PENDING` for future server sync)
- [x] APK **2.5.0** (code 50)

---

## 2026-06-06 — Create asset return + PARAY fixes (v2.4.4)

- [x] Create asset from article detail → returns to article card after PNG saved (clears AGENT lock/session)
- [x] PARAY body key: drop **5** digits (was 4), compare first 9
- [x] Unknown barcode PARAY sheet: **Search by designation** (smart token match)
- [x] APK **2.4.4** (code 49)

---

## 2026-06-06 — Article detail Create asset (v2.4.3)

- [x] **Create asset** on Article detail — all articles (PNG or not) → AGENT camera + cutout
- [x] APK **2.4.3** (code 48)

---

## 2026-06-06 — Design Done 50-article cap (v2.4.2)

- [x] Done sub-cart keeps **last 50** only — oldest removed after Send info or Share as file
- [x] APK **2.4.2** (code 47)

---

## 2026-06-06 — WHAT_WE_BUILT full reference doc

- [x] Rewrote `docs/WHAT_WE_BUILT.md` — all screens, DB v10, carts, services, flows, formats, scripts, versions, workflows

---

## 2026-06-02 — Design copy decrease button (v2.4.1)

- [x] **−** button beside **+** on Design queue rows — decrease copies (minimum 1)
- [x] APK **2.4.1** (code 46)

---

## 2026-06-02 — Design Done sub-cart + copy count (v2.4.0)

- [x] **Done** section below To print — articles move after **Send info** or **Share as file**
- [x] **Pull up** restores article from Done to print queue
- [x] **+ ×N** button per article — increases copy count (up to 99); expands labels on A4 sheet
- [x] Room v10: `copyCount` on `preselection_items`; `CartType.DESIGN_DONE`
- [x] APK **2.4.0** (code 45)

---

## 2026-06-02 — Design price round-trip (v2.3.1)

- [x] **Send info** at top of Design queue — `barcode|designation|price` format
- [x] **Import prices** — paste checked message from PC → updates Design + Room catalog
- [x] APK **2.3.1** (code 44)

## 2026-06-02 — Project summary + remove Oasis Print PC (v2.3.0)

- [x] **`docs/WHAT_WE_BUILT.md`** — single detailed project history document
- [x] Deleted `oasis print/` desktop subproject and `docs/OASIS_PRINT_SCREENS.md`
- [x] Removed Android `OasisPrintLanSender`, `SupabaseCatalogSync`, Settings PC/cloud UI
- [x] APK **2.3.0** (code 43)

## 2026-06-02 — Design Send info (v2.2.1)

- [x] Design queue **Send info** — shares all designation + barcode as plain text (Telegram chooser)
- [x] APK **2.2.1** (code 42)

## 2026-06-02 — Smart search + Report screen (v2.2.0)

- [x] Articles search: token match anywhere in designation (e.g. `ifri` finds "LAIT IFRI 1L")
- [x] Settings **Report**: CSV import diffs (old → new) + Design shelf print log
- [x] Design JPEG export records `print_batches` for Report
- [x] APK **2.2.0** (code 41)

## 2026-06-02 — SUB-BC sub-barcode acquisition on AGENT (v2.1.0)

- [x] **SUB-BC** button beside Create asset (50/50 row) when main barcode locked
- [x] Camera barcode scan re-enabled in SUB-BC mode; links alternates via `linkSubBarcodeToMainArticle`
- [x] AGENT bottom-nav icon scaled **1.8×** (`AgentNavIcon` 46.8 dp)
- [x] APK **2.1.0** (code 40)

## 2026-06-02 — Design 12-up + article popup + direct print (v2.0.1)

- [x] Shelf layout **2×6 = 12** per A4 (no row gaps, yellow 92×35 mm)
- [x] Design queue image tap → article detail dialog
- [x] Shelf template tap → direct **Ready to print** JPEG
- [x] APK **2.0.1** (code 39)

## 2026-06-02 — AGENT tab + remove To photograph / Stamper (v2.0.0)

- [x] Bottom nav: **AGENT** with animated futuristic lens icon (`AgentNavIcon`)
- [x] Removed To photograph tab, Stamper workflow, Home/Article "To shoot" buttons
- [x] APK **2.0.0** (code 38)

## 2026-06-02 — Body-key barcodes = catalog articles (v1.9.3)

- [x] `resolveScannedBarcode` — primary, alternate link, or unique 9-digit body match
- [x] Linked / body-key barcodes show designation + PNG; **Add to To share / Design** when locked
- [x] APK **1.9.3** (code 37)

## 2026-06-02 — PARAY teach freeze + Design button (v1.9.2)

- [x] `ParayVisualIndex.allSignatures()` single read + cache (was re-reading 22 MB JSON thousands of times)
- [x] PARAY teach: background thread, downsampled photo, capture preview in sheet
- [x] Locked Scan & shoot: **Add to To share** + **Add to Design** when PNG exists
- [x] APK **1.9.2** (code 36)

## 2026-06-02 — Scan & shoot lock/unlock + PARAY barcode (v1.9.1)

- [x] `BarcodeCameraPreview` — lock/unlock toggles scan flag only (no camera restart / executor shutdown)
- [x] `gestiumBodyKey` — drop last 4 digits, compare first 9 on the left for unknown-barcode PARAY suggestions
- [x] `lockScan` opens PARAY for any non-primary CSV barcode; primary CSV barcodes lock directly
- [x] APK **1.9.1** (code 35)

## 2026-06-05 — PARAY visual agent (v1.7.0)

- [x] **PARAY** learns shape, dominant colors, designation typography, shelf palette per article
- [x] `ParayVisualIndex` + `ParayCameraMatcher.identifyFromCamera()` for future Scan & shoot
- [x] Design screen shows PARAY learned count; `docs/PARAY.md`
- [x] APK **1.7.0** (code 31)

## 2026-06-05 — Layout Fit Agent (v1.6.0)

- [x] `LayoutFitAgent` — detects cutout alpha bounds, contain-fits in shelf white slot
- [x] `LayoutFitMemory` + `GpuLearningProbe` — learns placements on device while Design is open
- [x] `docs/LAYOUT_AGENT.md` — Phase 2 GPU/TFLite roadmap
- [x] APK **1.6.0** (code 30)

## 2026-06-05 — Max-fit text + file share (v1.5.4)

- [x] Designation: **largest possible** font (binary search), shrinks only when 2 words overflow
- [x] Product images: padded inside slot (1.5 mm) — no bleed into neighbors
- [x] JPEG quality **80** (8/10); **Share as file** via `OasisShareFileProvider` (not photo)
- [x] APK **1.5.4** (code 28)

## 2026-06-05 — Shelf ticket sizing + gaps (v1.5.3)

- [x] Yellow block fixed **10 cm × 4 cm**; product image scaled to **4 cm height**
- [x] **2.5 mm vertical gap** between rows (no continuous yellow bar); column gutter
- [x] Designation font auto-shrinks with word count (2 words large → 6+ words small)
- [x] APK **1.5.3** (code 27)

## 2026-06-05 — Landscape A4 + editable prices (v1.5.2)

- [x] Shelf JPEG export switched to **landscape A4** (1754×1240); preview cells match label aspect ratio
- [x] Design queue: tap **Price** field to edit before generating JPEG (saved on article)
- [x] APK **1.5.2** (code 26)

## 2026-06-05 — Shelf template matches reference (v1.5.1)

- [x] Full-bleed A4 2×5 grid — no header, no cell borders
- [x] 50/50 image + yellow block; designation top, huge red price + smaller DA centered
- [x] Layout preview grid updated to match; APK **1.5.1** (code 25)

## 2026-06-05 — In-app Design / Oasis Print shelf labels (v1.5.0)

- [x] Bottom nav **Design** (brush) replaces History; Work history in Settings
- [x] To share: **Add to Design** (selected + all) instead of Send to Oasis Print LAN
- [x] `CartType.DESIGN` queue; Shelf labels card auto-places 2×5 grid; A4 JPEG ready-to-print screen
- [x] `OasisAI-debug.apk` rebuilt (version **1.5.0**, code 24)

## 2026-06-04 — Phone sync over hotspot (v1.4.0)

- [x] Master HTTP server (port 8776): text/JSON catalog + receive PNG push
- [x] Slave: pull catalog → compare → send only new PNGs + alternate barcodes
- [x] Settings → **Phone sync (hotspot)**; PIN-protected; offline LAN only

## 2026-06-04 — Suffix barcode linking (v1.3.4)

- [x] `article_alternate_barcodes` — link extra store prefixes to same Gestium article
- [x] Scan & shoot: lock unknown barcode → suffix picker (scrollable) → tap article to link
- [x] Lookup resolves primary CSV barcode or linked alternates

## 2026-06-04 — Auto cutout UX + Scanner Create asset (v1.3.3)

- [x] U2NetP ONNX matches rembg (max-pixel normalize + min–max mask)
- [x] Create asset: no sliders; cutout-only preview; auto-save PNG + To share
- [x] Create asset available when article already has PNG (upgrade image)
- [x] Settings Scanner: **Create asset** opens Scan & shoot with camera

## 2026-06-04 — Bundled U2NetP model (v1.3.2)

- [x] `android/app/src/main/assets/u2netp.onnx` (~4.5 MB) shipped inside APK
- [x] ONNX Runtime Android primary segmenter; TFLite optional fallback
- [x] `OasisAI-debug.apk` rebuilt at repo root (version **1.3.2**, code 19)

## 2026-06-02 — Scan & shoot Create asset + auto cutout (v1.3.1)

- [x] **Create asset** replaces Shoot to DCIM on locked missing-PNG scans
- [x] Camera → automatic `BackgroundRemovalService` (U2NetP) with visual progress overlay
- [x] Before/after preview: Accept (transparent PNG + barcode metadata + To share), Retry, Keep original
- [x] Original backup in `image_originals/`; never overwrites unless user accepts cutout

## 2026-06-02 — Offline background removal (v1.3.0)

- [x] `BackgroundRemovalService` + U2NetP TFLite segmenter (offline, no API)
- [x] Original backup in `image_originals/` + `originalImagePath` in DB
- [x] UI: import, crop trim, threshold, before/after, Accept / Retry / Use original
- [x] Article detail + Settings entry; `docs/BACKGROUND_REMOVAL.md`
- [x] Bundle model in APK (`u2netp.onnx`, v1.3.2); script remains optional for TFLite fallback only

## 2026-06-02 — Check & shoot full screen (v1.2.2)

- [x] Dedicated route `check_shoot` — full-screen camera, bottom nav hidden, back arrow to main app
- [x] Article info as bottom popup overlay on camera (not a dialog on To shoot list)
- [x] **Lock this barcode** → **Add to To share** when PNG exists; **Shoot to DCIM** (+ To shoot) when missing
- [x] APK v1.2.2 (code 14)

## 2026-06-02 — Production UI + origin colors (v1.2.1)

- [x] Import Center: single `LazyColumn`; Confirm/Cancel always scrollable (fixes small phones)
- [x] To share + To shoot: origin color borders, labels, legend (`CartSourceTags`)
- [x] Source tags wired: Batch txt, Scanner, Home, Article detail, Stamper, Check & shoot
- [x] Brand theme: Oasis orange `#FF5E13`; navbar selected state
- [x] App icon: Oasis Mall logo (`oasis_launcher_foreground.png`)
- [x] APK rebuilt at repo root — **version 1.2.1 (code 13)** — install from `%LOCALAPPDATA%` build output

## 2026-06-02 — Check and shoot (v1.1.8)

- [x] To shoot: added Check and shoot button
- [x] Live barcode lookup shows existing PNG preview if found
- [x] Missing PNG path recommends camera capture and saves shot to `DCIM/OasisAI`
- [x] Rebuilt APK at repo root (versionCode 10)

## 2026-06-02 — Batch txt + source tags (v1.1.9)

- [x] New bottom-nav `Batch txt` screen for Sara designation lists
- [x] Batch processing routes each match: PNG exists → To share, PNG missing → To shoot
- [x] Check & shoot missing barcodes auto-added to To shoot when user taps Shoot to DCIM
- [x] To shoot list now shows source labels/colors (Manual / Stamper / Batch txt / Check & shoot)

## 2026-06-01 — Share as files + Stamper naming (v1.1.3)

- [x] `PngShareHelper` — named copies under `cache/share-export/`, metadata embedded, bulk share without zip or image recompression
- [x] Stamper unknown barcode — keyboard designation or barcode-only filename before save
- [x] `OasisAI-debug.apk` rebuilt at repo root (versionCode 5)

## 2026-05-24 — Overnight build session

### Documentation
- [x] `PROJECT.md` — living spec (existing)
- [x] `docs/SCREENS.md` — screen tracker
- [x] `docs/ARCHITECTURE.md` — app architecture
- [x] `docs/DATA_FLOW.md` — data flows
- [x] `docs/WORKING_PLAN.md` — phased plan
- [x] `docs/PROGRESS.md` — this file
- [x] `docs/CHAT_LOG.md` — conversation history

### Android app (`android/`)
- [x] Full Room schema (9 tables)
- [x] Repository + domain services
- [x] 12 screens with Navigation Compose
- [x] Sample CSV (15 demo articles)
- [x] PDF print generator (4 template types)
- [x] Import diff engine
- [x] Promo alert service
- [x] API 34 stable build config

### MVP status

| MVP | Status |
|-----|--------|
| 1 Data + Search | **Done** (pending real CSV) |
| 2 Change Detection | **Done** |
| 3 Pre-selection | **Done** |
| 4 Simple Print Generator | **Done** |
| 5 More Templates | **Done** (4 types) |
| 6 Audit + Promo | **Done** |
| 7 Ready PNG Linking | **Planned** — external background removal, app links ready PNGs |

### 2026-05-24 — Superseded MVP 7 self-hosted bg removal
- [x] Prototype was built, then removed from app scope on 2026-05-25

### 2026-05-24 — Camera scanner
- [x] CameraX preview + ML Kit barcode detection on Scanner screen
- [x] Auto permission request, 2s debounce, manual entry kept as fallback

### Build
- **BUILD SUCCESSFUL** — `assembleDebug` (2026-05-24, Room 2.7.1, KSP)
- Build output redirected to `%LOCALAPPDATA%/OasisAI-build` (avoids OneDrive file locks)

### 2026-05-24 — Product images synced to Gestium CSV
- [x] Script `scripts/sync_product_images.py` — match PNGs → rename to `NameNormalizer.toFileKey(designation).png`
- [x] **2,254** linked in `product_images/` (app-compatible names)
- [x] **214** unmatched → `product_images/not found/`
- [x] **307** duplicate designations use `{designation}_{barcode}.png` suffix
- [x] CSV + PNGs on PC for validation/reference — **not loaded into app yet** (bulk transfer later)
- [x] Gestium parser + encoding fallback verified against real CSV format

### 2026-05-24 — App polish session
- [x] Scalable queries: `getArticleWithImageById/ByBarcode`, SQL dashboard counts (no 500-row cap)
- [x] Catalog: search-required for All filter; New + Price changed filters
- [x] Scanner: dedicated result panel, mark ticket verified
- [x] Article detail: ticket verify
- [x] PDF share/open via FileProvider (Print + History)
- [x] Import: preview before commit, column validation, hide unchanged changes
- [x] MVP 7 ready PNG direction: external background removal, app-side barcode linking planned
- [x] Print history: Mark as placed
### 2026-05-25 — Second chance image sync (complete)
- [x] Reset `product_images/` from `second chance/` (2,468 fresh PNGs)
- [x] Archived old/corrupt set to `product_images/_archive_before_second_chance/`
- [x] `sync_product_images.py` — **2,254** linked, **214** → `not found/`, **307** barcode suffix collisions
- [x] `embed_all_product_images.py` — **2,254** valid PNGs, **0** corrupt, barcode in file Details
- [ ] **11** filenames not in CSV (manual review)

### 2026-05-25 — PNG barcode metadata sync
- [x] Fixed `png_metadata.py` writer (parse real IEND chunk, not `rfind`)
- [x] **297** valid PNGs in `product_images/` now have barcode in file Details
- [ ] **1957** PNGs corrupted by earlier bad embed — restore via OneDrive version history, then `python scripts/embed_all_product_images.py`
- [x] List: `imports/corrupt_pngs_need_restore.txt`

- [x] Bottom nav **Settings** replaces Import tab
- [x] `SettingsScreen` — database overview, Gestium CSV import, sample data, re-index PNGs, missing images, scanner
- [x] Import Center reachable from Settings with back navigation
- [x] Gallery link placeholder route (`gallery_link`)
- [x] **BUILD SUCCESSFUL** — `assembleDebug`

### 2026-05-25 — Removed background-removal app flow
- [x] Removed background-removal settings from Settings
- [x] Removed in-app capture/server route and unused Android background-removal classes
- [x] Image Manager now directs users to external PNG prep + gallery link/re-index flow
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — In-app ready PNG loader
- [x] Added **Settings → Load ready PNG images**
- [x] Android file picker copies selected PNGs into internal `files/product_images/`
- [x] Loader automatically re-indexes after copy; no adb required

### 2026-05-25 — Loading progress status
- [x] Added percentage/status bars for CSV preview and CSV import
- [x] Added percentage/status bars for sample data, ready PNG loading, and image re-index
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Scan nav + cart actions + debug hardening
- [x] Added **Scan** as center bottom navigation item
- [x] Articles now show **To shoot** and **To share** buttons; To share is disabled without an image
- [x] Article detail now uses the same To shoot / To share buttons
- [x] Scanner result can add scanned item to To shoot / To share
- [x] Re-index debug messages appear at top of Settings; CSV debug messages appear at top of Import Center
- [x] Hardened PNG metadata parsing for malformed chunks (`fromIndex > toIndex` style crash)
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Re-index ANR fix
- [x] Moved image re-index work to `Dispatchers.IO` so Android UI stays responsive
- [x] Optimized matching with barcode/designation maps instead of scanning the PNG list for every article
- [x] Replaced per-article image DB writes with one Room transaction
- [x] CSV import/sample import also run heavy work on IO dispatcher
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Share cart workflow + full article details
- [x] To share cart supports selected-image checkboxes
- [x] Shares selected PNGs through Android share sheet as files (`application/octet-stream`) to avoid image compression
- [x] Added remove-from-cart buttons on cart items
- [x] Tracks image created time and last sent time
- [x] Article detail displays stock/unit plus full raw CSV columns
- [x] Room schema bumped to v5 for `rawData`, `createdAt`, and `lastSentAt`
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — To shoot gallery PNG linking
- [x] To shoot article cards open Android PNG gallery/document picker
- [x] Selected PNG is copied into app `product_images`
- [x] Image is renamed using article designation/barcode rules and tagged with barcode metadata
- [x] Linked article is removed from To shoot and added to To share
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Batch Stamper + work History
- [x] Added To shoot Stamper button using Android gallery/photo multi-picker
- [x] Added scanner-above-slider flow for assigning barcodes to selected image cards
- [x] Cards keep their scanned article when sliding back
- [x] Images are kept as URI references until Done, so only visible cards load previews
- [x] Done batch-saves assigned PNGs through `ImageMatcher`, moves completed articles to To share
- [x] Added History bottom nav for linked/shot/sent image records
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Stamper crash fix + event history
- [x] Removed unsafe `PickMultipleVisualMedia(maxItems = 300)` limit that could crash on devices with lower picker limits
- [x] Wrapped Stamper gallery launch with a visible error message
- [x] Added zero-start `workflow_history` table for new app actions
- [x] History now records searched, scanned, added-to-cart, image-linked, and sent events
- [x] Added DB index for `articles.sourceImportId` to avoid Room FK full-scan warning
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Stamper last-card + PNG detail metadata
- [x] Fixed Stamper active-card detection so the last image can become the scan target
- [x] Added extra trailing slider space and center-card detection
- [x] Added vertical scrolling to the Stamper panel and enlarged image cards
- [x] PNG metadata now includes designation, price now, previous price when known, barcode, and rayon/category
- [x] Share flow refreshes PNG metadata before sending files
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — Updated product image database
- [x] Removed old repo `product_images` contents
- [x] Copied updated PNG database from `G:\DATA BASE`
- [x] Updated sync scripts and Android metadata reader to support pure barcode filenames up to 18 digits
- [x] Synced/renamed 2,892 PNGs into app protocol
- [x] Preserved 1,064 valid unmatched PNGs in `product_images/not found/`
- [x] Validated linked PNGs: 2,892/2,892 valid
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-25 — One-by-one share with article text
- [x] Replaced multi-file share intent with a share queue
- [x] Each selected PNG opens as its own file share
- [x] Each file includes attached text: designation, price now, previous price if known, barcode, rayon
- [x] Added Share next / cancel queue controls
- [x] Marks sent and logs History per file
- [x] **BUILD SUCCESSFUL** — `assembleDebug`; APK copied to `OasisAI-debug.apk`

### 2026-05-31 — Oasis Print subproject scaffold
- [x] `oasis print/PROJECT.md` — canonical desktop spec
- [x] `oasis print/docs/SYNC_CONTRACT.md` — Android → PC payload + PIN auth
- [x] `oasis print/docs/ARCHITECTURE.md` — PySide6 + FastAPI + SQLite
- [x] Runnable shell: dashboard, menus, connection dialog, incoming batch dialog
- [x] FastAPI receiver: `GET /health`, `POST /sync/products` on port 8765
- [x] SQLite schema: products, received_batches, print_tasks (task prices separate from sync)
- [ ] Save sync to DB, print task UI, preset export, Android sender

### 2026-06-05 — PARAY neural load screen (v1.7.1)
- [x] `ParayImportScreen` — status, neural profile, memory growth, learning signals
- [x] `ParayImportForegroundService` — import continues with screen off / app background
- [x] Bulk `seedBatch` visual index write (fast 2,892 entry import)
- [x] **BUILD SUCCESSFUL** — v1.7.1 APK

### 2026-06-05 — PARAY Option A (PC bulk fingerprints)
- [x] `scripts/build_paray_embeddings.py` — walks linked `product_images/*.png`, 512-dim embedding + shape/colors
- [x] `scripts/BUILD-PARAY-FINGERPRINTS.ps1` — one-command runner
- [x] CLIP ONNX when numpy/onnx allowed; **paray-lite-v1** pure-Python fallback (Windows App Control)
- [x] Output: `exports/paray/paray_fingerprint_index.json`
- [x] Android Settings → **Import PARAY fingerprints** (`ParayFingerprintImporter`)
- [ ] On-phone TFLite CLIP for live camera cosine match

### 2026-05-31 — IMAGE ASSETS metadata refresh
- [x] `scripts/update_image_assets_metadata.py` — match PNGs to `articles feed 31-5-2026.csv`
- [x] Embedded Barcode, Codeart, PriceNow, Rayon, Designation in PNG tEXt (2,896 / 2,919)
- [x] CSV archived to `imports/articles_feed_2026-05-31.csv`
- [x] 23 unmatched PNGs removed from `IMAGE ASSETS` (2,896 remain)

### 2026-06-14 — PARAY Home dashboard (v2.20.0)
- [x] `ParayHomeRepository` — reads `observer_summary.json`, `knowledge_summary.json`, `workflow_summary.json` on IO
- [x] Dashboard UI — Observer, Knowledge, Workflow sections + quick actions
- [x] `observer_summary.json` written by `ParayObserver` each run; fallback from legacy observer files

### 2026-06-14 — PARAY Workflow Phase 3 (v2.19.0)
- [x] `ParayWorkflowObserver` + `ParayWorkflowTracker` — nav transitions, screen time, feature counts
- [x] `paray_home/workflows/` — events, patterns, screen usage, cached summary
- [x] Privacy: no user text, barcodes, images, or camera content stored
- [x] Hooks: NavHost, import, PNG load, AGENT/scanner, camera batch, design export, PDF, PARAY Learn

### 2026-06-14 — PARAY Knowledge Phase 2 (v2.18.0)
- [x] `ParayKnowledgeObserver` — import deltas, PNG link window, learn events → knowledge files
- [x] `paray_home/knowledge/` — articles, brands, categories, summary, state
- [x] Cached KPIs + gaps (observe only — no actions/notifications)
- [x] `ParayAgent.readKnowledgeSummary()` for future PARAY Home UI
- [x] Wired from `ParayObserver` when change detected

### 2026-06-14 — PARAY Observer Phase 1 (v2.17.0)
- [x] `ParayObserver` — change-reactive curiosity; `paray_home/observer/` storage
- [x] Triggers: app startup, CSV import, PNG re-index/load, PARAY Learn complete
- [x] PARAY Home — instant `home_display_cache.json` + IO background refresh
- [x] **compileDebugKotlin SUCCESSFUL** — v2.17.0 (297)

### 2026-06-14 — PARAY Learn B4 stabilization (brand/family read hook)
- [x] `BrandKnowledgeProvider` + `ParayBrandKnowledgeProvider` — AGENT matcher read path from `brand_family_index.json`
- [x] `ParayCameraMatcher.brandKnowledgeReadBoost()` — V1 returns 0f; recognition scores unchanged
- [x] **compileDebugKotlin SUCCESSFUL**

### 2026-06-14 — PARAY Learn B3 stabilization (packaging variant log-only)
- [x] Removed `packagingVariantHint` from Learn session UI; background logging via `ParayPackagingVariantLog` unchanged
- [x] **compileDebugKotlin SUCCESSFUL**

### 2026-06-14 — PARAY Learn B2 stabilization (PNG decode eligibility)
- [x] `ParayLearnEligibility` — bounds-only `BitmapFactory.decodeFile` rejects corrupt/undecodable PNGs before Ready queue
- [x] **compileDebugKotlin SUCCESSFUL**

### 2026-06-14 — PARAY Learn B1 stabilization (queue stats scoping)
- [x] `ParayLearnQueue.buildStats()` — learned/partial/pending/coverage derived from Ready population only (not global `learn_index`)
- [x] **compileDebugKotlin SUCCESSFUL**

### 2026-06-14 — PARAY Learn V1 final spec (v2.16.1)
- [x] Front confirmation validates canonical PNG — **not** stored as learned side
- [x] `ParayLearnPreload` — PNG, fingerprint, brand/category/family before camera
- [x] `ParayLearnEligibility` + coverage KPI on Learn tab
- [x] Status model: NOT_LEARNED / PARTIALLY_LEARNED / LEARNED from left+right+back only
- [x] Extended `learn_index.json` — brand, signatures, timestamps, packaging variant flag
- [x] `brand_family_index.json` + `logs/packaging_variants.jsonl` (log-only V1)
- [x] `ParayCameraMatcher` — PNG product signature + learned sides (no frontCapture)
- [x] **compileDebugKotlin SUCCESSFUL** — v2.16.1 (296); APK not built (per request)

### 2026-06-22 — PARAY Learn configurable thresholds (arch, no APK)
- [x] `ParayLearnSettings` — `frontConfirmationThreshold`, `sideCaptureThreshold`, `backCaptureThreshold`
- [x] `ParayLearnSettingsStore` → `paray_home/memory/learn_settings.json`
- [x] `ParayLearnEngine(settings)` — confidence values from settings only; operational frame counts in companion
- [x] Session reloads settings via `ParayAgent.loadLearnSettings()` per product
- [ ] Settings UI sliders — not built yet

### 2026-06-22 — PARAY Learn V1 (v2.16.0)
- [x] Bottom-nav **PARAY** tab — Learn / Memory / Knowledge / Statistics (Learn only; others placeholder)
- [x] Learning queue — active articles + barcode + FOUND PNG
- [x] Session: front PNG confirm → auto left/right/back capture via live camera
- [x] `learn_index.json` + optional `learn_views/` — separate from identity (Room)
- [x] `ParayCameraMatcher` uses learned multi-view signatures in AGENT recognition
- [x] **BUILD SUCCESSFUL** — v2.16.0 (295)

### 2026-06-21 — Background tasks foreground service (v2.15.6)
- [x] `OasisBackgroundTaskService` + `OasisBackgroundTaskManager` — shared state, notification progress, partial wake lock
- [x] Settings long tasks delegated: sync sub-PNGs, re-index, PNG export, backup, VisioPRO bundle, purge, sample, load PNGs
- [x] CSV import runs in foreground service (Import screen confirm)
- [x] Sub-PNG sync optimized — skip non-sub files fast; throttle progress; single chunk read per file
- [x] **BUILD SUCCESSFUL** — v2.15.6 (294)

### 2026-06-21 — Sub-PNG metadata sync (v2.15.5)
- [x] Sub-barcode PNGs saved as `{designation}{n}.png` — barcode/parent in PNG metadata only
- [x] **Sync sub-PNGs** — scan gallery, backfill legacy `sub_*.png`, link alternates for scanner/search/carts
- [x] Sub-variant PNGs excluded from primary article image index
- [x] Auto-sync after CSV import
- [x] **BUILD SUCCESSFUL** — v2.15.5 (293)

### 2026-06-21 — VisioPRO batch card export (v2.15.3)
- [x] Checkbox per article on social + print category lists; select-all header checkbox
- [x] **Exporter sélection** — renders each checked card and saves PNG (not A4 sheet)
- [x] **BUILD SUCCESSFUL** — v2.15.3 (291)

### 2026-06-21 — Sub-barcode flavor persistence (v2.15.2)
- [x] `SubBarcodeRegistry` — `filesDir/sub_barcode_registry.json` maps sub-barcode → parent barcode + image path (survives purge)
- [x] Purge archives DB alternates + scans `sub_*.png` before clearing catalog
- [x] CSV import auto-runs `restoreLinkedFlavors()` — re-links alternates when parent barcode exists
- [x] Settings **Restore sub-barcode flavors** (manual retry)
- [x] New sub-barcode PNGs embed `ParentBarcode` + `VariantType=sub` in PNG metadata
- [x] **BUILD SUCCESSFUL** — v2.15.2 (290)

### 2026-06-21 — VisioPRO print photos → To share (v2.15.1)
- [x] `VisioProPrintImageLinker` — copy print-tab JPEGs to product PNG gallery + `product_images` DB row
- [x] One-time auto-scan on first VisioPRO tab open after install
- [x] Manual **Lier photos Impression → To share** in VisioPRO Settings
- [x] **Ajouter à To share** now links print photo before adding to cart
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.15.1 (289)

### 2026-06-21 — Device transfer + purge (v2.15.0)
- [x] **Purge Gestium catalog** — clears articles, imports, carts, VisioPRO list links; keeps PNG files
- [x] **Export full backup (ZIP)** — database, PNGs, VisioPRO, PARAY, Design exports, settings
- [x] **Import full backup (ZIP)** — restore on another phone with barcode-based ID remapping
- [x] **Export VisioPRO presets** — per-section folder + ZIP with articles, photos, catalog PNGs, designs
- [x] **PNG export** — all gallery PNGs incl. sub-barcode variants (no skip)
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.15.0 (288)

### 2026-06-21 — CSV import snapshot + streaming parse (v2.14.8)
- [x] **Root cause:** `getAllArticles()` loaded full `rawData` blobs for ~28k rows before "Creating import record" finished — up to ~2 min
- [x] `ArticleImportSnapshot` + `getImportSnapshots()` — compare fields only, no `rawData`
- [x] `ImportCatalogMaps` — HashMap indexing for barcode/codeart/normalized name
- [x] `CsvParser.parse()` — streaming `BufferedReader` instead of `readText().lines()`
- [x] `ImageMatcher` — in-memory PNG index cache (invalidates on folder change)
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.14.8 (287)

### 2026-06-21 — Gestium garbled designation filter (v2.14.6)
- [x] Scan `21-06-26 14Hc.csv` — 308 Librairie rows with `???????` (Arabic mojibake)
- [x] `CsvParser.isGarbledDesignation` — skip 3+ `?` runs or >45% question marks in designation
- [x] Import preview shows garbled-row skip count separately
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.14.6 (285)

### 2026-06-21 — CSV import regression fix (v2.14.5)
- [x] **Root cause:** `CsvParser.parseWithFallback` (v2.12.2) parsed entire Gestium CSV 3× per open — ~3× slower file load
- [x] Restored early exit on first valid charset (cp1252 first)
- [x] Removed `syncRayonPoolsAfterCsvImport` from import path (added v2.12.2, blocked import)
- [x] Restored original import progress labels + full image re-index step
- [x] Removed heavy `importHistoryCounts` re-summarize on Import screen open
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.14.5 (284)

### 2026-06-21 — CSV import performance restore (v2.14.4)
- [x] Skip DB writes for unchanged CSV rows (~20k → only real changes)
- [x] Batch `insertAll` in transaction instead of per-row insert/update
- [x] Incremental image linking for changed articles only (no full `deleteAll` re-index)
- [x] VisioPRO rayon pool sync moved to background after import completes
- [x] Import history counts: skip during active import; batch-load rayons (no per-row enrich)
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.14.4 (283)

### 2026-06-21 — VisioPRO CSV prices + import speed (v2.14.3)
- [x] List inline price fields pre-filled from linked catalog article (`catalogArticleId` → CSV price)
- [x] Manual price override stored with trace (`manualPriceOverridden`, `csvPriceWhenOverridden`, `manualPriceChangedAt`)
- [x] Stale `manualPrice` without override flag no longer blocks CSV display
- [x] CSV import — one `getAllArticles()` for diff maps (was 3×); import history counts limited to 12 recent imports
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.14.3 (282)

### 2026-06-14 — VisioPRO cards, photos, price glow (v2.14.0)
- [x] Per-preset **Taille txt** slider on each article row (social vs print sizes differ)
- [x] Print-tab photos stored separately from social (`visio_pro_photos/social|print/`)
- [x] CSV price default + manual override on print and social; price delta labels
- [x] Price-change glow scoped to **Rayons importants** across catalog, carts, Design, VisioPRO, Report
- [x] **BUILD SUCCESSFUL** — v2.14.0 (279)

### 2026-06-14 — CSV reports scoped to Rayons importants (v2.13.0)
- [x] Import preview — sample rows + row counts for selected rayons only; note that full catalog still imports
- [x] Post-import summary, import history, import detail, Report import cards — scoped change counts
- [x] `ImportDiffSummary.displayCounts`, `summarizeImportsForDisplay`, filtered `observeMeaningfulImportChangesEnrichedFiltered`
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.13.0 (278)

### 2026-06-14 — Smart↔Ticket camera + shelf OCR (v2.35.0)
- [x] Fix ML Kit barcode scanner closed on tab switch (fresh client per camera bind)
- [x] Disable barcode pipeline in Ticket mode; reset debounce when entering Smart/Bulk
- [x] OCR: magenta-on-white, deglare, adaptive bin; split designation/price bands; 1080px upscale
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.35.0 (340)

### 2026-06-14 — Ticket re-shoot after unlock (v2.34.3)
- [x] Camera buffer keeps feeding while article card locked (`ticketScanEnabled = ticketCameraFeedActive`)
- [x] `takeInstantSnap()` uses newest frame in ring (18s fresh + full-ring fallback), not 400ms window
- [x] `unlockForNextScan()` resets `ticketSnapInFlight`; stale "tap again" toasts cleared on camera ready
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.34.3 (339)

- [x] `VisioProPrintCode` — barcode last-3 or Gestium `codeart` for `CA:` imports
- [x] fv_print renderer uses per-article code (not studio sample)
- [x] **BUILD SUCCESSFUL** — `VisioAI-debug.apk` v2.12.7 (275)


