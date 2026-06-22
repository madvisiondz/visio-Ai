# Oasis AI ? Chat Log

> Discussion history with the AI assistant. **Update at the end of every prompt.**

---

## Session 1 ? 2026-05-23

**User:** Shared full Oasis AI project plan (GestiumERP, 20k articles, designation-first, pre-selection workflow, print audit, promo lifecycle, MVP 1?7).

**Assistant:** Restructured plan into `PROJECT.md` + Cursor rule `.cursor/rules/oasis-ai-project.mdc` (always apply, keep PROJECT.md updated).

---

## Session 2 ? 2026-05-23 / 24

**User:** Asked for amateur-level Android SDK setup steps.

**Assistant:** Step-by-step Windows guide: install Android Studio, API 34, emulator Pixel_7, create project, open in Cursor.

**User:** SDK Manager screenshot ? install API 14 (34) for stability?

**Assistant:** Yes ? install API 34; explained Studio defaults to latest SDK.

**User:** Double-check API setup.

**Assistant:** Confirmed android-34 platform + emulator on API 34.

---

## Session 3 ? 2026-05-24

**User:** Created Android project ? what's next?

**Assistant:** Run app, open in Cursor, pin compileSdk 34, start MVP 1; need sample GestiumERP CSV.

**User:** Why is Android installing SDK 36 on its own?

**Assistant:** Explained `compileSdk`/`targetSdk` 36 in `build.gradle.kts` causes Gradle to download SDK 36.

**User:** Stabilize project ? tired of Android Studio; agent has full access.

**Assistant:**
- Pinned API 34 in Gradle
- Downgraded Compose BOM for API 34 compatibility
- Added `.vscode/`, `dev.ps1`
- Verified `assembleDebug` BUILD SUCCESSFUL

---

## Session 4 ? 2026-05-24 (overnight)

**User:** No CSV yet (maybe later). Build everything possible in the project. Create `.md` trackers (screens, API/data flow, architecture, working plan, progress, **chat log**). Update trackers every prompt. Do **more than MVP 1** ? as much as possible while user sleeps.

**Assistant:** Full implementation session:
- Complete Room DB + all 8 modules
- CSV import with diff, sample data
- All main screens + navigation
- PDF print generator (shelf/freezer/podium/board)
- Print history + promo tracker
- Documentation pack in `docs/`
- Updated `PROJECT.md` MVP tracker

**Next for user:** Run `.\dev.ps1` ? Load sample data ? explore app ? share real GestiumERP CSV when ready.

---

## Session 5 ? 2026-05-24

**User:** Camera scanner UI not fully wired (manual barcode works).

**Assistant:** Implemented live camera scanning:
- `BarcodeCameraPreview.kt` ? CameraX + ML Kit (EAN/UPC/Code128/etc.)
- `ScannerScreen.kt` ? camera preview, permission flow, manual fallback
- `ScannerViewModel.kt` ? 2s debounce for repeated scans

---

## Session 6 ? 2026-05-24

**User:** OK with background removal needing internet (no problem if that part is online).

**Assistant:** Recorded decision in `PROJECT.md`:
- MVP 7 will use **online API** for high-quality bg removal
- Core app remains offline-first; only capture/removal step needs network
- Next: pick API provider + API key when ready to build MVP 7

### Known gaps
- Real GestiumERP export not yet tested
- Product PNG library not loaded (folder ready)
- Mall PC server not deployed yet (run `services/background-removal` manually)

---

## Session 7 ? 2026-05-24

**User:** Wants the API built ourselves, working inside Oasis AI (not third-party SaaS).

**Assistant:** Built self-hosted stack:
- `services/background-removal/` ? FastAPI + rembg on port 8765
- Android: `BackgroundRemovalClient`, settings, capture screen, Image Manager flow

---

## Session 8 ? 2026-05-24

**User:** Placed Gestium CSV at `imports/gestium_articles_2026-05-24.csv`.

**Assistant:** Validated file (~27k rows). Updated `CsvParser` for Gestium columns (`D?signation`, `Code-barres`, `Prix de vente TTC`, French numbers like `1 120,00`).

**Next:** Copy CSV to phone ? Import Center ? Pick CSV file.

---

## Session 9 ? 2026-05-24

**User:** Placed PNG folder; asked to check. Clarified files are for reference only ? bulk transfer later; keep building app as designed.

**Assistant:** Verified 2,468 PNGs on PC (naming compatible with ImageMatcher). Continued app build: scalable DB queries, scanner verification panel, PDF share/open, import preview, catalog filters, MVP 7 capture polish. BUILD SUCCESSFUL.

---

*End of log ? append new entries below after each future prompt.*

---

## Session 10 ? 2026-05-25

**User:** Remove background-removal server settings from the app; background removal will be done externally and Oasis AI will receive ready PNGs to name/link by barcode scan.

**Assistant:** Removed the background-removal settings/capture flow from Android navigation/UI, deleted unused Android server/capture classes, and updated docs/project scope to ready PNG linking.

---

## Session 11 ? 2026-05-25

**User:** Asked for a Settings button to load ready PNG images in-app instead of using adb.

**Assistant:** Added **Settings ? Load ready PNG images**. It opens Android's file picker, copies selected PNGs into internal `files/product_images/`, and automatically re-indexes images.

---

## Session 12 ? 2026-05-25

**User:** Asked for status percentages on CSV loading and other in-app tasks.

**Assistant:** Added percentage/status progress bars for CSV preview/import, sample data load, ready PNG loading, and image re-index. Rebuilt APK successfully.

---

## Session 13 ? 2026-05-25

**User:** Asked for center Scanner nav, article To shoot/To share buttons, Share disabled without image, and top-screen debug messages after a `1523 > 1522` re-index crash.

**Assistant:** Added center Scan bottom nav, explicit To shoot/To share buttons on article search and scan results, top debug banners for Settings/Import errors, and hardened PNG metadata parsing against malformed chunks.

---

## Session 14 ? 2026-05-25

**User:** Reported Android "not responding" during Re-index product images.

**Assistant:** Fixed the re-index path by moving heavy work to `Dispatchers.IO`, building barcode/designation image maps once, and replacing product image rows in one Room transaction. CSV import/sample import heavy work also runs off the UI thread now.

---

## Session 15 ? 2026-05-25

**User:** Asked for selected-image sharing through Telegram as files/no compression, selector tickers, remove-from-cart buttons, full CSV detail parsing/display, and image created/last sent dates.

**Assistant:** Added Share cart checkboxes, select all/clear/share selected actions, PNG file sharing via Android share sheet, last-sent timestamp updates, remove buttons, raw CSV detail storage/display, and image created timestamps. Rebuilt APK successfully.

---

## Session 16 ? 2026-05-25

**User:** Asked for To shoot article cards to open gallery selection and, after PNG selection, rename/copy the PNG into the in-app image database.

**Assistant:** Connected To shoot card taps to Android PNG picker, copies selected PNGs into app storage via `ImageMatcher`, writes barcode metadata, renames by article designation/barcode, moves the article to To share, and rebuilt the APK.

---

## Session 17 ? 2026-05-25

**User:** Described the full Stamper workflow: select many PhotoRoom PNGs from gallery, keep scanner open, slide through image cards, scan barcode to fill each card, save everything on Done, keep memory stable, and add History.

**Assistant:** Added batch Stamper inside To shoot with Android gallery multi-pick, continuous scanner, lazy image card slider, per-card article assignments, Done batch save, automatic To shoot ? To share movement, and History bottom nav for linked/sent records.

---

## Session 18 ? 2026-05-25

**User:** Reported the app closes when tapping Stamper and asked for enterprise-level stability. Also asked History to start at zero and include sent, added-to-cart, and searched history types.

**Assistant:** Removed the unsafe photo picker max item limit, wrapped gallery launch errors into visible messages, added Room v6 `workflow_history` event table, changed History to zero-start event records, and logged searched/scanned/cart/linked/sent actions.

---

## Session 19 ? 2026-05-25

**User:** Reported the last Stamper card cannot get its turn, requested bigger image cards with vertical scrolling, and asked shared PNGs to include CSV details.

**Assistant:** Changed Stamper active-card logic to use the card nearest screen center, added trailing slider space for edge cards, made the Stamper panel vertically scrollable with larger image cards, and enriched PNG metadata with designation, current price, previous price when known, barcode, and rayon/category before linking and sharing.

---

## Session 20 ? 2026-05-25

**User:** Pointed to `G:\DATA BASE` as the updated image database and asked to remove the old repository images and adjust/add the new database to `product_images`.

**Assistant:** Replaced the repo `product_images` contents from `G:\DATA BASE`, fixed sync scripts and Android barcode parsing for pure barcode filenames, reran sync after the removable disk interruption, linked 2,892 PNGs, preserved 1,064 unmatched PNGs in `not found/`, validated PNG structure, and rebuilt the APK.

---

## Session 21 ? 2026-05-25

**User:** Reported that sharing selected images together did not send article details alongside each file and asked to send them one by one.

**Assistant:** Reworked Share cart into a one-by-one share queue. Each selected PNG is prepared with metadata, opened through its own file share intent with article detail text attached, then marked sent and logged in History per file.

---

## Session 22 ? 2026-05-31

**User:** Introduced **Oasis Print** subproject (`oasis print/`) ? desktop companion that receives ready synced products from Oasis AI over LAN. Spec: top menus, dashboard with preset cards, incoming-products popup, print task with mandatory manual price edit, local Wi?Fi receiver (v1 IP + PIN). Sync contract JSON with codeart, designation, family, price_ttc, barcodes, image filename.

**Assistant:** Created Oasis Print scaffold: `PROJECT.md`, sync contract, architecture docs, Python/PySide6/FastAPI app with dashboard, connection dialog, incoming batch dialog, SQLite schema, and LAN receiver on port 8765. Updated root trackers and changelog.

---

## Session 23 ? 2026-05-31

**User:** Asked if mobile data + PC internet can use a direct link; then chose **Supabase** as recommended cloud and asked if it stays free within quota.

**Assistant:** Explained LAN vs internet (NAT); recommended Supabase as temporary batch relay (not full cloud catalog). Confirmed **$0 on Free plan** within limits (1 GB storage, 500 MB DB, 5 GB egress); noted 1-week inactivity pause and delete-after-download to stay free. Documented in `oasis print/docs/CLOUD_SYNC.md`.

---

## Session 24 ? 2026-05-31

**User:** Created Supabase account and project **oasis mall ai** (Free, North EU Stockholm).

**Assistant:** Added `supabase/001_sync_batches.sql`, `supabase/SETUP.md`, `.env.example`, and PC cloud client stub wired to project URL `https://wjtawsojtoiovovreeri.supabase.co`.

---

## Session 25 ? 2026-05-31

**User:** Update `d:\oasis project\IMAGE ASSETS` PNG metadata from `articles feed 31-5-2026.csv` ? keep same images, embed barcode, price, code, rayon.

**Assistant:** Extended `png_metadata.py` with `write_article_details`; added `update_image_assets_metadata.py`. Ran on 2,919 PNGs: **2,896 updated**, **23 unmatched** (ambiguous names). Report at `IMAGE ASSETS\_metadata_update_report.txt`. CSV archived to `imports/articles_feed_2026-05-31.csv`.

**User:** Remove the unmatched files.

**Assistant:** Deleted all 23 unmatched PNGs from `IMAGE ASSETS`. **2,896** images remain with full metadata.

---

## Session 26 ? 2026-05-31

**User:** Add Codeart to app PNG metadata; switch To share from one-by-one to bulk share.

**Assistant:** Mapped CSV `Code` ? `articles.codeart` (DB v7); `PngMetadata.writeArticleDetails` now writes Codeart; Stamper/re-index embed full metadata; To share uses bulk `ACTION_SEND_MULTIPLE` with summary text and metadata in each PNG.

---

## Session 27 ? 2026-05-31

**User:** Shared A4 preset mockups (shelf 10-up, freezer 6-up, podium 1/1 promo/3-strip/2 promo) for high-end Oasis Print desktop app; all printing on A4, cut after print.

**Assistant:** Documented `oasis print/docs/PRINT_PRESETS.md` (grids, colors, fields, engine plan) and `print/presets.py` registry; updated dashboard preset cards to match mockups.

---

## Session 28 ? 2026-05-31

**User:** Articles feed PC?Supabase?mobile; no images in cloud; bulk share (not one-by-one); Send to Oasis Print + Telegram; Oasis Print side panel with Shared / Articles / Cart tabs and search + add to cart.

**Assistant:** Rewrote `CLOUD_SYNC.md`; added `002_articles_catalog.sql`; Oasis Print side panel + print_cart table; Android bulk Telegram + LAN Send to Oasis Print; Settings connection dialog.

---

## Session 29 ? 2026-05-31

**User:** Provided Supabase secret key (`sb_secret_...`) to configure cloud sync.

**Assistant:** Saved key in `oasis print/.env` (gitignored); verified REST API reachable; clarified `sb_secret_` cannot run DDL (needs `SUPABASE_DB_PASSWORD`, `sbp_` token, or SQL Editor). Fixed `run.py` ROOT and `cloud/config.py` `.env` loading; added `verify_supabase.py`, `catalog.publish_catalog_csv`, and Management API path in `setup_supabase.py`. Advised key rotation after chat exposure.

---

## Session 30 ? 2026-05-31

**User:** Provided database password for Supabase setup.

**Assistant:** Added `SUPABASE_DB_PASSWORD` to `.env`; fixed URL encoding for special chars and pooler host (`aws-1-eu-north-1`). Ran `setup_supabase.py` ? all three tables created; `verify_supabase.py` exit 0. Advised password rotation (shared in chat).

---

## Session 31 ? 2026-05-31

**User:** Create desktop `.exe` setup file for Oasis Print.

**Assistant:** Added PyInstaller spec (`build/oasis_print.spec`), Inno Setup script, `scripts/build_windows.ps1`, 7-Zip SFX fallback (`make_7zip_setup.ps1`). Built `dist/OasisPrint/OasisPrint.exe` and `dist/OasisPrint-Setup-1.0.0.exe` (~170 MB). Documented in `docs/BUILD_WINDOWS.md`.

---

## Session 32 ? 2026-05-31

**User:** Make Load ready PNG compatible with IMAGE ASSETS PNGs from PC script; align app with Oasis Print; add what's needed.

**Assistant:** `ReadyPngModel` + full `PngMetadata.readArticleDetails`; `ReadyPngLoader` (folder tree + files); re-index match Codeart?barcode?designation; preserve complete Oasis model tags; `SupabaseCatalogSync`; Settings UI updates; `android/local.properties.example`.

---

## Session 33 ? 2026-05-31

**User:** Black screen when loading ready PNG database in Oasis AI.

**Assistant:** Fixed ANR/OOM path: lightweight PNG count in Settings overview; batched DB writes; suspend IO with yield; 500 PNG cap per load; skip duplicate copies; full-screen progress overlay; removed double metadata scan during copy; optimized re-index (no rewrite Oasis-model PNGs).

---

## Session 34 ? 2026-05-31

**User:** Oasis Print still doesn't work; sign with open-source educational signature; make it run.

**Assistant:** Root cause: PyInstaller build had **no Qt DLLs** (`qwindows.dll` missing) ? GUI could not start. Fixed `build/oasis_print.spec` (explicit Qt6 + platform plugins, no full QML collect_all), `paths.py` frozen layout, `run.py` crash dialog + log. Rebuilt `dist/OasisPrint`. Signing: MADVISION self-signed + `install_trust_admin.ps1`. Dev fallback: `Start-Oasis-Print-Dev.bat`. Note: global OSS certs (SignPath) are not instant; SmartScreen/App Control may still need Unblock + trust on each PC.

---

## Session 35 ? 2026-05-31

**User:** To share screen must have Send all; send all PNGs at once as files, no compression, not one-by-one.

**Assistant:** To share: primary **Send all (N) to Oasis Print** and **Share all files (N)** buttons; one multipart LAN batch + `ACTION_SEND_MULTIPLE` with `image/png`; optional send selected subset; longer OkHttp timeouts for large batches.

---

## Session 36 ? 2026-06-01

**User:** Frustrated ? installed APK many times but no updates (Send all, stamper, Supabase); builds failing.

**Assistant:** Confirmed features were in source but `OasisAI-debug.apk` was stale (May 25); CLI builds failed on AGP 9.2 AAPT2/UCRT. Downgraded AGP 8.7.3 + Gradle 8.10.2, bumped app to v1.1.0, added `BUILD-APK.ps1`. **BUILD SUCCESSFUL** ? fresh `OasisAI-debug.apk` (Jun 1, 2026, 38 MB).

---

## Session 37 ? 2026-06-01

**User:** Share with no **image** compression ? send as files so Telegram on PC keeps filename + metadata; unknown articles should allow naming via keyboard.

**Assistant:** `PngShareHelper`: copy to `cache/share-export/` as `DESIGNATION.png`, write PNG metadata, share as `application/octet-stream` (document files, not gallery photos). Stamper dialog: `OutlinedTextField` + **Save with typed name** / **Barcode only** / **Scan again**. APK **v1.1.3** at repo root `OasisAI-debug.apk`.

---

## Session 38 ? 2026-06-02

**User:** To share must send selected articles as document files; image mode still happens.

**Assistant:** Added explicit **Share selected as files (N)** button in To share. It uses the same document-share path (`PngShareHelper` + `OasisFileProvider`) so checked items are sent as documents, not compressed image posts. Bumped APK to **v1.1.5**.

---

## Session 39 ? 2026-06-02

**User:** Add To shoot **Check & shoot** camera flow: scan/verify existing PNG and recommend shooting when missing; captured image should go to DCIM.

**Assistant:** Added **Check & shoot** in To shoot. Live barcode scan checks PNG DB by barcode and shows popup preview if found. If missing, UI recommends shooting and opens system camera; capture is saved to `DCIM/OasisAI` via MediaStore. APK bumped to **v1.1.8**.

---

## Session 40 ? 2026-06-02

**User:** Add three features: (1) when Check & shoot barcode has no PNG and user taps Shoot to DCIM, auto-add that article to To shoot cart; (2) new bottom-nav **Batch txt** screen to paste Sara designation list and auto-route items (PNG exists -> To share, missing PNG -> To shoot); (3) color/source separation in To shoot list (stamper/batch/manual).

**Assistant:** Implemented all three: Check & shoot now auto-queues missing scans to To shoot before camera launch; added new `batch_txt` nav screen + processing viewmodel; To shoot list now displays source labels/colors via cart note tags. APK bumped to **v1.1.9**.

---

## Session 41 ? 2026-06-02

**User:** Automate IMAGE ASSETS folder load: process all PNGs by repeating 500-per-run batches automatically and skip already existing files.

**Assistant:** Updated folder loader to scan all PNGs and process them in automatic 500-file batches until complete; existing target PNGs are passed as no-copy (`alreadyPresent`). Picker-based manual file loads still keep the explicit 500 safety cap.

---

## Session 42 ? 2026-06-02

**User:** Import Center Confirm import not visible on friend's phone; make all UI production-ready; use Oasis logo as app icon; To share articles should show origin colors (batch, to shoot, check&shoot, scanner, etc.).

**Assistant:** Import Center rebuilt as one scrollable `LazyColumn` with Confirm/Cancel as separate items + nav-bar padding. To share/To shoot use `CartSourceTags` (colored borders + legend); tags wired from Batch txt, Scanner, Home, Article detail, Stamper, Check & shoot. Brand orange theme + navbar polish; Oasis logo as adaptive launcher foreground. APK **v1.2.1 (code 13)** at repo root `OasisAI-debug.apk` (build output in `%LOCALAPPDATA%\OasisAI-android-build\`).

---

## Session 43 ? 2026-06-02

**User:** Check & shoot should not be a small dialog on To shoot ? open a full-screen camera page (no navbar, back arrow), camera always visible, articles only as popups on the camera. When barcode is locked, show **Add to To share** button.

**Assistant:** New `CheckShootScreen` route: full-screen `BarcodeCameraPreview`, article card popup at bottom, Lock ? Add to To share (if PNG) or Shoot to DCIM (+ To shoot). Removed AlertDialog from To shoot cart. APK **v1.2.2 (code 14)**.

---

## Session 44 ? 2026-06-04

**User:** Why didn't you install the background-removal model yourself?

**Assistant:** Earlier builds only documented `scripts/download-u2netp-tflite.ps1` because many upstream URLs returned HTML stubs or were blocked; fixed by downloading rembg `u2netp.onnx`, placing it in `android/app/src/main/assets/`, and shipping **v1.3.2** APK with the model inside (~135 MB). Uninstall old app, install fresh `OasisAI-debug.apk`.

---

## Session 45 ? 2026-06-05

**User:** Move History from navbar to Settings; replace with Design (brush). Embed Oasis Print as in-app screen ? shelves template only. To share: replace Send to Oasis Print with Add to Design; auto-place articles when tapping Shelf card; final screen JPEG A4.

**Assistant:** **v1.5.0**: `Design` bottom-nav tab + `DesignScreen` (home ? shelf 2?5 layout ? ready-to-print JPEG). `CartType.DESIGN` queue; To share **Add to Design**; `ShelfA4Renderer` exports A4 JPEG (shelf_10up). Work history under Settings. APK at repo root `OasisAI-debug.apk`.

---

## Session 46 ? 2026-06-05

**User:** Shared reference shelves template + app Ready to print screenshot ? output should match reference.

**Assistant:** **v1.5.1**: `ShelfA4Renderer` rewritten ? full-bleed 2?5 (no header, no borders), 50/50 image/yellow, black uppercase designation, huge red price + smaller `DA`. Layout preview updated. APK rebuilt.

---

## Session 47 ? 2026-06-05

**User:** Shelf labels are landscape A4 but export was portrait; allow user to edit price in Design queue list.

**Assistant:** **v1.5.2**: JPEG export switched to landscape A4 (1754?1240); preview cells use label aspect ratio. Design queue rows have editable Price field (saved on blur/Done). APK rebuilt.

---

## Session 48 ? 2026-06-05

**User:** Images still not fitting ticket white space; wants an AI agent that sees images, fits them, learns from mobile GPU while app is open, knows app rules for later use.

**Assistant:** **v1.6.0**: `LayoutFitAgent` ? alpha cutout detection, contain-fit in slot, placement memory, GPU probe + `gpu_learning.jsonl`. Wired to Design screen + shelf export. `docs/LAYOUT_AGENT.md` for Phase 2 ML.

---

## Session 49 ? 2026-06-05

**User:** PARAY should learn shapes, colors, typography, design; later detect articles from camera.

**Assistant:** **v1.7.0**: `ParayAgent` (PARAY) ? visual index per article, learn on Design export, `identifyFromCamera()` for future Scan & shoot. `docs/PARAY.md`.

---

## Session 50 ? 2026-06-05

**User:** Let's do Option A on this PC ($0 bulk CLIP fingerprints).

**Assistant:** Added `scripts/BUILD-PARAY-FINGERPRINTS.ps1` + `build_paray_embeddings.py` (CLIP ONNX or paray-lite fallback). Ran bulk fingerprint on 2,892 linked PNGs ? `exports/paray/paray_fingerprint_index.json`. Android Settings ? Import PARAY fingerprints.

---

## Session 51 ? 2026-06-05

**User:** Import PARAY fingerprints should open a dedicated screen with full status, neural info, growth; load in background even if screen is off.

**Assistant:** **v1.7.1**: `ParayImportScreen` + `ParayImportForegroundService`. Shows import progress, neural profile, memory growth, learning signals. Background-safe with notification.

---

## Session 52 ? 2026-06-02

**User:** Lock then unlock blocks entire scanner; PARAY article recognition not launching; unknown-barcode matching should drop last 4 digits and compare first 9 on the left.

**Assistant:** **v1.9.1**: Fixed `BarcodeCameraPreview` (camera no longer restarts on lock/unlock). `gestiumBodyKey` + `findByGestiumBodyKey` for PARAY suggestions. `lockScan` opens PARAY sheet for any non-primary CSV barcode.

---

## Session 53 ? 2026-06-02

**User:** Let PARAY look at product ? black screen after photo; where did Add to share / Design buttons go?

**Assistant:** **v1.9.2**: Fixed PARAY teach freeze (`allSignatures` was re-reading full JSON per article; identify now on background thread). Teach sheet shows photo preview. **Add to Design** restored on locked popup; buttons appear after picking a PARAY match (PARAY sheet hides them until lock).

---

## Session 54 ? 2026-06-02

**User:** Barcodes resolved via last-4 / 9-digit body match still show as not in CSV; no Add to share/Design.

**Assistant:** **v1.9.3**: `resolveScannedBarcode` ? lookup alternate barcodes + auto-resolve unique body-key match; treated barcodes count as catalog articles with share/design when PNG exists.

---

## Session 55 ? 2026-06-02

**User:** Remove To photograph screen/button and Stamper; rename Scan & shoot to AGENT with futuristic animated camera icon.

**Assistant:** **v2.0.0**: Removed `photoshoot_cart` bottom nav + Stamper; renamed tab to **AGENT** with `AgentNavIcon` (gradient rotating lens). Home/Article/Batch txt point to AGENT for photos.

---

## Session 56 ? 2026-06-02

**User:** Acquisition stores flavor/color alternates as hidden sub-barcodes (same price, different barcodes). On AGENT: bigger icon (1.8?), SUB-BC button beside Create asset when locked to scan sub-barcodes without new articles.

**Assistant:** **v2.1.0**: `SUB-BC` toggle on locked popup (50/50 with Create asset); camera scan resumes for alternate barcodes ? `article_alternate_barcodes`; `AgentNavIcon` 46.8 dp. APK code 40.

---

## Session 57 ? 2026-06-02

**User:** Articles search should match words anywhere (not only prefix); add REPORT screen for CSV changes and Design prints.

**Assistant:** **v2.2.0**: `SearchQuery` smart token search on Articles; Settings **Report** screen (import_changes + Design print_batches). APK code 41.

---

## Session 58 ? 2026-06-02

**User:** Design cart ? button **Send info** to share all designation + barcode as text (Telegram).

**Assistant:** **v2.2.1**: Design queue **Send info** ? `ACTION_SEND` text/plain chooser. APK code 42.

---

## Session 59 ? 2026-06-02

**User:** One detailed file of what's been done; Oasis Print PC idea removed ? delete all related files.

**Assistant:** Created **`docs/WHAT_WE_BUILT.md`**; deleted `oasis print/` + PC sync Android code; updated trackers. **v2.3.0** APK code 43.

---

## Session 60 ? 2026-06-02

**User:** Printed/sent Design articles should move to a **Done** sub-cart below To print (pull up to restore); add **+** button to increase copy count per article on shelf labels.

**Assistant:** **v2.4.0**: `CartType.DESIGN_DONE`; `preselection_items.copyCount` (Room v10); Design **Done** section with **Pull up**; **+ ?N** on queue rows; Send info / Share as file ? Done; `DesignCartExpand` for A4 repeats. APK code 45.

---

## Session 61 ? 2026-06-02

**User:** Add decrease-copies button alongside the increase button on Design queue articles.

**Assistant:** **v2.4.1**: **? / +** copy stepper per row (min 1, max 99). APK code 46.

---

## Session 62 ? 2026-06-06

**User:** Put 100% details in WHAT_WE_BUILT for everything.

**Assistant:** Rewrote `docs/WHAT_WE_BUILT.md` as exhaustive reference (~22 sections): all routes/screens, Room v10 schema, cart workflows, domain services, step-by-step data flows, file formats, shelf renderer specs, PARAY/Layout/Phone sync, all PC scripts, APK build notes, full version history, operator workflows, removed features, known gaps.

---

## Session 63 — 2026-06-06

**User:** Send info moves articles to Done (like print) — cap Done list at 50 articles max.

**Assistant:** **v2.4.2**: `DESIGN_DONE_MAX = 50`; `trimDesignDoneCart` drops oldest Done rows after each move. APK code 47.

---

## Session 64 — 2026-06-06

**User:** On Articles search → article card, add **Create asset** for all articles (with or without PNG).

**Assistant:** **v2.4.3**: Article detail **Create asset** button → AGENT with barcode locked + camera launch. APK code 48.

---

## Session 65 — 2026-06-06

**User:** Create asset from Articles stuck nav loop / broken AGENT scanner; return to article after save. PARAY body key 5 digits (not 4). Designation search on unknown barcode.

**Assistant:** **v2.4.4**: `returnArticleId` route param — pop to article detail after asset save + full unlock; PARAY `gestiumBodyKey` drops last **5**; designation search in PARAY sheet. APK code 49.

---

## Session 66 — 2026-06-07

**User:** Design cart loses order when printing from list; mall photo job needs stable **Bulk** mode (barcode-only, replace/skip) separate from Smart PARAY flow.

**Assistant:** **v2.5.0**: `moveCartItem` preserves `sortOrder`; print/Send info move in queue order; AGENT **Smart/Bulk** toggle; `bulk_captures` + `bulk_images/` for barcode-keyed mall captures. APK code 50.

---

## Session 67 — 2026-06-07

**User:** Bulk mode does not see existing PNGs that Smart mode finds on scan.

**Assistant:** **v2.5.1**: `refreshBulkScan` falls back to `resolveScannedBarcode` → `product_images.imagePath` (catalog-linked designation filenames), plus `bulk_captures` row lookup. APK code 51.

---

## Session 68 — 2026-06-16

**User:** Rebrand to **Visio Ai 2.0** (separate app from Oasis AI until paid); new logo; restore **To shoot** wired with Articles + Batch; AGENT card shows designation + barcode + price + last price change; metallic theme from logo.

**Assistant:** **Visio Ai 2.0** (`com.oasismall.visioai`, code 200): metallic charcoal/steel theme, launcher from `app logo.png`, **To shoot** bottom nav + Batch txt routing + Article/Home buttons, AGENT popup enriched with price history, APK `VisioAI-debug.apk`.

---

## Session 69 — 2026-06-16

**User:** Batch camera scan-first (like AGENT); unknown barcode create designation with CSV match (Proceed sub-barcode / Add to To share); Batch txt 3-way routing with clickable camera batch queue.

**Assistant:** **Visio Ai v2.3.0** (code 230): `batch_camera_queue` + scan-first camera batch; Batch txt To share / To shoot / queue list; designation fuzzy match actions; APK `VisioAI-debug.apk`.

---

## Session 70 — 2026-06-16

**User:** PhotoRoom image folder selector in Settings.

**Assistant:** **Visio Ai v2.3.1**: Settings row to pick PhotoRoom folder via SAF; persisted URI used by Camera batch import; reset to default `Pictures/Photoroom`.

---

## Session 71 — 2026-06-16

**User:** Design Send info should not auto-mark Done; add Mark as printed/sent buttons; Done list by date; remove from Done.

**Assistant:** **Visio Ai v2.3.2**: Manual Done workflow; Done sorted newest-first with timestamp; Remove on Done rows.

---

## Session 72 — 2026-06-16

**User:** After CSV import, price changes listed in history but no way to add them anywhere; want images on change rows and Add to To shoot / To share.

**Assistant:** **Visio Ai v2.3.4**: `ImportChangeCard` on Import detail + Report — thumbnail or “No image”; **Add to To share** (has PNG) / **Add to To shoot** (no PNG); cart tag `SRC_IMPORT_CHANGE`.

---

## Session 73 — 2026-06-16

**User:** Import history shows 18 price changes but Import #2 detail shows 0 changes (broken after v2.3.4).

**Assistant:** **Visio Ai v2.3.5**: v2.3.4 tried to enrich all ~26k UNCHANGED rows; fixed with meaningful-only query + stop storing UNCHANGED on new imports.

---

## Session 74 — 2026-06-16

**User:** Article cards need sub-barcode batch shoot, scrollable AGENT-style options everywhere, auto sub-barcode link on scan, last price + last printed.

**Assistant:** **Visio Ai v2.3.6**: `ArticleActionPanel` on Article detail / AGENT / Scanner / Camera batch; **Add sub-barcode & batch shoot** from article; camera batch auto-links scanned barcode to parent article.

---

## Session 75 — 2026-06-16

**User:** After PhotoRoom import, articles disappear — should auto go to To share.

**Assistant:** **Visio Ai v2.3.7**: `importFromPhotoroom` now adds article to **To share** after PNG is linked; removes from To shoot only.

---

## Session 76 — 2026-06-17

**User:** Sub-barcode needs final confirmation; always ask shoot?; link only after PhotoRoom import with per-sub-barcode image; designation shows twice in search; sub-barcode chips removable.

**Assistant:** **Visio Ai v2.4.0**: confirm → shoot? dialogs (AGENT + camera batch); `pendingSubBarcodeLink` on batch items; DB v14 `imagePath` on alternates; search variants; tap chip to remove mistaken sub-barcodes.

---

## Session 77 — 2026-06-17

**User:** AGENT barcode reader too sensitive; lock button as round corner FAB (~¼ inside card).

**Assistant:** Scan stability (4 consecutive reads + 3s debounce); hold card until lock/dismiss; corner lock/unlock FAB on AGENT article popup.

---

## Session 78 — 2026-06-17

**User:** Every new sub-barcode must have its own image; adding = shoot photo; keep existing sub-barcodes as before.

**Assistant:** **Visio Ai v2.4.1**: Removed add-without-photo; confirm → shoot directly; link only after PhotoRoom import with image.

---

## Session 79 — 2026-06-17

**User:** Remove button on PhotoRoom import list cards.

**Assistant:** **Visio Ai v2.4.2**: **Remove** on each pending card — deletes batch JPEG + drops queue row (PhotoRoom PNG untouched).

---

## Session 80 — 2026-06-17

**User:** Articles search crashes — likely sub-barcode duplicate rows.

**Assistant:** **Visio Ai v2.4.3**: LazyColumn keys `id+barcode`; batch alternate lookup; try/catch fallback; cap expansion at 60 mains.

---

## Session 81 — 2026-06-17

**User:** Shelf preset printed images low quality — want 100% quality.

**Assistant:** **Visio Ai v2.4.4**: Shelf A4 **3508×2480** (300 DPI), JPEG **100%**, full-resolution PNG decode per ticket slot.

---

## Session 82 — 2026-06-17

**User:** To-share & Design should load sub-articles (flavors) without replacing parent; PhotoRoom sub-bc PNG import should go to To-share directly.

**Assistant:** **Visio Ai v2.4.5**: DB v15 `preselection_items.variantBarcode`; unique `(articleId, cartType, variantBarcode)`; cart SQL resolves sub-bc images; remove/copy/move by `preselectionId`; PhotoRoom sub-bc import adds flavor row to To share; Home/Scanner pass barcode when adding to cart.

---

## Session 83 — 2026-06-17

**User:** Manual barcode entry on every camera scanner — some product barcodes won't scan.

**Assistant:** **Visio Ai v2.4.6**: Shared `ManualBarcodeEntry` component; AGENT “Type barcode” dialog; Camera batch inline entry; Settings scanner refactored; manual bypasses AGENT scan debounce.

---

## Session 84 — 2026-06-18

**User:** PhotoRoom fails on some images (long designation?) — need manual PNG import on PhotoRoom import screen; system renames to corresponding article.

**Assistant:** **Visio Ai v2.4.7**: **Pick PNG** per pending card on PhotoRoom import; `importFromManualPng` uses same gallery rename/metadata pipeline as auto import → To share.

---

## Session 85 — 2026-06-19

**User:** Promo/discount shelf tickets in Design — dual price (original + promo), prix-barrée like reference photo, same layout otherwise.

**Assistant:** **Visio Ai v2.5.0**: Design **Promo** toggle per queue row; promo + original price fields; shelf renderer pink promo price left + diagonal prix-barrée on original top-right.

---

## Session 86 — 2026-06-19

**User:** Promo print result — price not same red as standard; promo and barré too far apart.

**Assistant:** **Visio Ai v2.5.1**: Promo price uses standard `#E60000` red; promo + prix-barrée laid out as one cluster (~2.5 mm gap) slightly left of center.

---

## Session 87 — 2026-06-19

**User:** Set up Photoshop reading tools to clone PSD templates from layer configuration and position (many templates coming).

**Assistant:** PSD pipeline — `tools/psd` (psd-tools), `tools/psd-node` (ag-psd), `scripts/INSPECT-PSD.ps1`, inbox at `templates/psd-inbox/`, docs `PSD_TEMPLATES.md`.

---

## Session 88 — 2026-06-19

**User:** VisioPRO lives inside VisioAI (not separate AI inspector app). Agent creates presets; app shows cards, edits prices (CSV + manual), exports social/print. Seven families: fruits/veg/butcher social+print; fish social only. Article buttons top, one card per screen.

**Assistant:** **Visio Ai v2.6.0**: VisioPRO module — Settings entry, own UI; `VisioProPresetCatalog` (7 themes, 46 articles); Social/Print tabs; price memory; gallery export; fish without prices.

---

## Session 89 — 2026-06-19

**User:** Map articles from Gestium CSV (`19-06-26 10h MATIN.csv`) by Rayon — Fruits et Légumes, Boucherie, POISSONNERIE; code = last 3 barcode digits; save as .md for VisioPRO mapping memory.

**Assistant:** Extracted **278 articles** → `docs/VISIOPRO_CSV_ARTICLES.md` (Fruits 43, Légumes 29, à classer 37, Boucherie 67, Poisson 102). Re-run: `scripts/extract_visiopro_csv_articles.py`.

---

## Session 90 — 2026-06-19

**User:** Apply `AIL(example).psd` to all fruits & légumes VisioPRO presets; social = photo (daily shoot or latest) + design + designation/code/price; print = no photoshoot (static images later).

**Assistant:** **Visio Ai v2.6.1** — PSD inspected; assets in `visiopro/ail_social/`; 72 CSV articles in `VisioProCsvArticles.kt`; AIL layered renderer + per-article photo store + camera button on social F&L. See `docs/VISIOPRO_AIL_TEMPLATE.md`.

---

## Session 91 — 2026-06-19

**User:** Nothing changed in the app; can't find VisioPRO.

**Assistant:** Code existed but no APK was installed — built **VisioAI-debug.apk v2.6.2** for manual install.

---

## Session 92 — 2026-06-19

**User:** Bottom nav empty / tabs off-screen; request smoke/stability evaluation.

**Assistant:** **v2.6.3** — fixed broken layout (`NavigationBarItem` inside scrolling `Row`); custom 76dp scroll tabs; Settings shows version; `BottomNavSelectionTest` smoke tests; fresh APK built.

---

## Session 93 — 2026-06-19

**User:** Use `abricot example.psd` as print template; presets ready for fruits and vegetables.

**Assistant:** **v2.6.5** — exported `fv_print` assets from abricot PSD (3508×2480); fruits + légumes print presets use PSD renderer (catalog photo, Arabic designation, red auto-fit price, `code : XX`); A4×4 batch unchanged. Script: `scripts/build_visiopro_print_assets.py`.

---

## Session 94 — 2026-06-19

**User:** Map `D:\Oasis Data\affichage fruits` PSDs to CSV articles; export product images; assign to VisioPRO print presets without mismatches.

**Assistant:** **v2.6.6** — `scripts/map_visiopro_affichage_psd.py`: code-in-PSD + filename + JPG histogram verification; 51 PSDs → 63 product PNGs (aliases for promos/variants); `docs/VISIOPRO_AFFICHAGE_MAP.md` audit trail; print renderer uses bundled product before catalog fallback.

---

## Session 95 — 2026-06-20

**User:** Rayon filter shows Famille codes (4000, 001) — wanted Gestium **Rayon** list (Boucherie, Boissons, Confiserie, …).

**Assistant:** **v2.6.9** — fixed `CsvParser` column mapping (Rayon / Catégorie / Famille separate); added `articles.rayon` + migration v17 backfill from rawData; Articles filter queries use `rayon` column. Re-import CSV for full fix on all rows.

---

## Session 96 — 2026-06-20

**User:** VisioPRO lists are great; want Settings route to pick which rayon articles show in each of the 4 cards + drag reorder popup.

**Assistant:** **v2.7.0** — Settings → **Listes VisioPRO**; per-card editor from Gestium rayon pool; save selection + **Ordre d'affichage** sheet (drag handles); VisioPRO home/category lists read saved config with live prices.

---

## Session 97 — 2026-06-14

**User:** Design print should save under date/time; Historique tab with batch list; open batch to modify/reprint; CSV updates refresh lists; changed articles glow.

**Assistant:** **v2.11.0** — `exports/yyyy-MM-dd/shelf_HHmmss_pN.jpg`; Design **Historique** tab + batch detail (exclude, reprint, load queue, → Design/To share); Room v18 batch snapshots; `catalogChangeGlow` on changed rows; APK build OK.

---

## Session 98 — 2026-06-14

**User:** CSV price-changed articles must glow; To share list should show printed / sent to Design / Telegram sent at bottom — existing status didn't work.

**Assistant:** **v2.11.1** — fixed cart `toArticleWithImage()` hardcoding UNCHANGED; `ArticleCard` + Report import cards glow; To share **ShareCartStatusFooter** (Printed / Design / Telegram); cart SQL adds `lastPrintedAt`, `inDesignQueue`, `inDesignDone`.

---

## Session 99 — 2026-06-14

**User:** VisioPRO lists should stay linked to CSV rayons (new butcher/fish/F&V articles appear in Settings); important rayons checklist to filter CSV report and all stats.

**Assistant:** **v2.12.0** — post-import `syncNewArticlesAfterImport` → pending in VisioPRO config; Settings **Rayons importants** screen; filtered dashboard stats, Articles chips, Catalog, CSV report.

---

## Session 100 — 2026-06-14

**User:** VisioPRO lists still missing articles (e.g. PIEDS DE VEAU in Boucherie); lists must reload on every CSV import.

**Assistant:** **v2.12.1** — import barcode-less rows via codeart; `syncRayonPoolsAfterCsvImport` full rayon resync; live pool in list editor; multi-spelling rayon query; Settings resume refresh.

---

## Session 101 — 2026-06-14

**User:** Still cannot find PIEDS DE VEAU in Articles or VisioPRO — CSV loader may be ignoring rows.

**Assistant:** **v2.12.2** — root cause: UTF-8 mis-read Gestium cp1252 CSV (0 rows vs 28k); `parseWithFallback` now picks charset with most valid rows; removal detection uses codeart + designation; unit test for PIEDS DE VEAU row.

---

## Session 102 — 2026-06-14

**User:** Still 1117 rows skipped; PIEDS DE VEAU not in Articles.

**Assistant:** **v2.12.3** — 1118 Gestium rows have empty Code-barres but valid Code (13145 for PIEDS DE VEAU); old filter counted them as skipped. Identity resolved inside `parseRow`; cp1252 first; preview shows barcode-less import count.

---

## Session 103 — 2026-06-14

**User:** VisioPRO UX — inline price on category list, decimal keyboard, scroll to price field, fixed Save top-right, fix order popup closing on scroll (losing unsaved order).

**Assistant:** **v2.12.5** — `VisioProNumericPriceField` (decimal IME + bringIntoView); inline price on list rows; list editor **Enregistrer** in TopAppBar; order sheet `confirmValueChange` blocks drag-dismiss, live `updateOrderDraft`, **Terminé** / **Annuler** in header.

---

## Session 104 — 2026-06-14

**User:** VisioPRO opened card — editable Arabic designation on print/social; swipe card right-to-left next, left-to-right previous.

**Assistant:** **v2.12.6** — `manualDesignation` in VisioProStore; designation field with RTL input + live preview; horizontal swipe on card preview; auto-save on navigate/export.

---

## Session 105 — 2026-06-14

**User:** VisioPRO print articles not loading codes from CSV.

**Assistant:** **v2.12.7** — `VisioProPrintCode` resolves last 3 digits of Code-barres (or full Gestium Code for `CA:` rows); `codeart` on `ArticleWithImage`; fv_print renderer no longer uses designer `sampleCode` for real cards.

---

## Session 106 — 2026-06-14

**User:** CSV reports after loading new CSV should show only selected rayons; unselected rayons don't concern the app user.

**Assistant:** **v2.13.0** — Import preview, last-import card, import history, import detail, and Report import summaries now scope counts and change lists to **Rayons importants** (Settings). Full catalog still imports to DB; only user-facing reports are filtered.

---

## Session 107 — 2026-06-14

**User:** Per-card designation size per preset; separate print vs social images; CSV price with manual override + tracking; price-change glow everywhere (selected rayons only).

**Assistant:** **v2.14.0** — VisioPRO list rows get per-preset text-size sliders; photos split by channel; price resolver prefers manual when set else CSV; glow + "Prix modifié" badge on price changes filtered by Rayons importants app-wide.

---

## Session 108 — 2026-06-21

**User:** VisioPRO list prices not loading from CSV; manual edits should be cached with trace; CSV import loader very slow/stuck.

**Assistant:** **v2.14.3** — Prices load from linked catalog row in one batch query; user override only when explicitly committed (`manualPriceOverridden` + CSV baseline snapshot); import loads existing catalog once instead of three times; history counts capped to 12 recent imports when Rayons filter active.

---

## Session 109 — 2026-06-21

**User:** CSV import still 15× slower; find troublemaker and restore stable speed.

**Assistant:** **v2.14.4** — Root causes: writing every unchanged row individually (~20k updates), full image re-index (`deleteAll` + 20k matches) on every import, VisioPRO pool sync blocking import path, import history enrich competing with import. Fixed: only persist changed rows, batch saves, incremental image upsert, background pool sync, pause history counts during import.

---

## Session 110 — 2026-06-21

**User:** Still very slow; wants old loader back — find the regression not loader tweaks.

**Assistant:** **v2.14.5** — Real troublemaker: v2.12.2 `parseWithFallback` always parsed CSV 3× (was early-exit 1×). Also v2.12.2 added blocking VisioPRO pool sync on every import + v2.13 import history re-summarize. Reverted loader to original steps; fixed triple parse; removed pool sync from import path.

---

## Session 111 — 2026-06-21

**User:** Scan Gestium CSV `21-06-26 14Hc.csv`; filter unwanted `???????` rows while keeping valid articles.

**Assistant:** **v2.14.6** — 308 Librairie rows are Arabic titles exported as question marks (encoding mojibake). Added `CsvParser.isGarbledDesignation` to skip designation with 3+ `?` in a row or >45% question marks; valid French/Latin rows kept; preview shows garbled skip count.

---

## Session 112 — 2026-06-21

**User:** "Creating import record" step used to take ~5s, now up to 2 minutes.

**Assistant:** **v2.14.8** — Bottleneck was loading all articles with huge `rawData` blobs before diffing. Added lightweight `ArticleImportSnapshot` query, `ImportCatalogMaps`, streaming CSV parse, PNG index cache. Re-import (no new articles) skips image step. APK v2.14.8 (287).

---

## Session 113 — 2026-06-21

**User:** Purge Gestium data; export VisioPRO presets with images; PNG export all sub-barcodes; all-in-one device transfer.

**Assistant:** **v2.15.0** — Settings **Device transfer**: Purge Gestium catalog; Export/Import full `VisioAi_backup_*.zip`; Export VisioPRO presets per section; PNG export includes all sub-barcode variants.

---

## Session 114 — 2026-06-21

**User:** Can sub-barcode flavor PNGs survive purge? Build in-app automation so the app knows where they belong after re-import.

**Assistant:** **v2.15.2** — `sub_barcode_registry.json` archives flavor links before purge; CSV import auto-restores; Settings **Restore sub-barcode flavors**; PNG metadata tags parent barcode on new saves.

---

## Session 115 — 2026-06-21

**User:** To share from print tabs uses rendered images not article PNGs — add checkmarks on social + print lists and export checked as individual images (not A4).

**Assistant:** **v2.15.3** — VisioPRO category list checkboxes + **Exporter sélection** → one PNG per rendered card to DCIM/VisioPRO/Social or Print.

---

## Session 116 — 2026-06-21

**User:** Sub-barcode PNGs should auto-detect from metadata (designation+n filename); Sync sub-PNGs for existing files; PNG database first.

**Assistant:** **v2.15.5** — `{designation}{n}.png` + metadata tags; **Sync sub-PNGs** in Settings; auto-sync on CSV import; sub PNGs excluded from main image index.

---

## Session 117 — 2026-06-21

**User:** Let all app tasks run in background (screen can lock during sync).

**Assistant:** **v2.15.6** — `OasisBackgroundTaskService` foreground service for sync sub-PNGs, re-index, exports, backup, CSV import, purge, load PNGs; notification progress; sync scan optimized.

---

## Session 118 — 2026-06-21

**User:** Briefing of app features and last development status as a detailed MD file.

**Assistant:** Created **`docs/APP_BRIEFING.md`** — executive summary, feature modules, v2.15.6 status, workflows, gaps, doc map.

---

## Session 119 — 2026-06-22

**User:** PARAY Learn V1 spec — teach trusted catalog products (CSV + barcode + PNG); separate Learn from AGENT recognition.

**Assistant:** **v2.16.0** — PARAY bottom-nav tab; Learn queue + session (front confirm, auto L/R/B); `learn_index.json`; AGENT matcher multi-view integration. See `docs/PARAY_LEARN.md`.

---

## Session 120 — 2026-06-22

**User:** PARAY Learn architecture — configurable thresholds (`frontConfirmationThreshold`, `sideCaptureThreshold`, `backCaptureThreshold`); no hardcoded confidence; don't build APK; push for tracker review.

**Assistant:** `ParayLearnSettings` + `ParayLearnSettingsStore` → `learn_settings.json`; `ParayLearnEngine(settings)`; session reloads settings; docs updated; pushed (no APK).

---

## Session 121 — 2026-06-14

**User:** PARAY Learn V1 **final specification** — PNG canonical front; preload; coverage KPI; packaging variant architecture; brand/family knowledge; status from L+R+B only; separate Learn from AGENT.

**Assistant:** **v2.16.1** — aligned domain + UI to final spec: `ParayLearnEligibility`, `ParayLearnPreload`, extended `learn_index.json`, `brand_family_index.json`, `packaging_variants.jsonl`; front confirmation no longer stored as learned side; coverage on Learn tab; compile verified (no APK). Canonical doc: `docs/PARAY_LEARN.md`.

---

## Session 122 — 2026-06-14

**User:** PARAY Learn V1 stabilization fix B1 — scope queue KPIs to Ready population only.

**Assistant:** `ParayLearnQueue.buildStats()` — learned/partial/pending/coverage from Ready ∩ learn status; no storage or workflow changes.

---

## Session 123 — 2026-06-14

**User:** PARAY Learn V1 stabilization fix B2 — reject undecodable PNGs at eligibility.

**Assistant:** `ParayLearnEligibility.isDecodablePng()` — bounds-only BitmapFactory check; corrupt PNGs excluded from Ready queue.

---

## Session 124 — 2026-06-14

**User:** PARAY Learn V1 stabilization fix B3 — packaging variant log-only, no UI.

**Assistant:** Removed packaging variant hint from `ParayLearnScreens` / session UI state; `maybeLogPackagingVariant()` + `packaging_variants.jsonl` unchanged.

---

## Session 125 — 2026-06-14

**User:** PARAY Learn V1 stabilization fix B4 — brand/family read-side integration hook for AGENT.

**Assistant:** `BrandKnowledgeProvider` + `ParayBrandKnowledgeProvider`; `ParayCameraMatcher` calls read hook with 0f boost; no score/storage format changes.

---

## Session 126 — 2026-06-14

**User:** PARAY V1 Evolution Phase 1 — lightweight ParayObserver curiosity engine + PARAY Home performance.

**Assistant:** **v2.17.0** — `ParayObserver` + `paray_home/observer/`; change-only observations; PARAY Home cached display + async refresh. See `docs/PARAY_OBSERVER.md`.

---

## Session 127 — 2026-06-14

**User:** PARAY V1 Evolution Phase 2 — product catalog knowledge via observation (not image learning).

**Assistant:** **v2.18.0** — `ParayKnowledgeObserver` + `paray_home/knowledge/`; article timelines; brand/category rollups; knowledge gaps; import history; cached `knowledge_summary.json`. Wired from `ParayObserver` on change only. See `docs/PARAY_KNOWLEDGE.md`.

---

## Session 128 — 2026-06-14

**User:** PARAY V1 Evolution Phase 3 — user workflow curiosity (patterns only, no personal data).

**Assistant:** **v2.19.0** — `ParayWorkflowObserver` + `paray_home/workflows/`; screen usage, transitions, feature counts; NavController + feature hooks. See `docs/PARAY_WORKFLOW.md`.

---

## Session 129 — 2026-06-14

**User:** Implement PARAY Home Dashboard UI — read cached summaries only.

**Assistant:** **v2.20.0** — `ParayHomeRepository` + dashboard screen; `observer_summary.json`; extended knowledge/workflow summaries.

---

## Session 130 — 2026-06-14

**User:** Implement PARAY Learn Settings UI — threshold sliders in Settings.

**Assistant:** **v2.20.1** — `ParayLearnSettingsScreen` + ViewModel; Settings → PARAY Learn settings; immediate save to `learn_settings.json`.

---

## Session 131 — 2026-06-14

**User:** Implement PARAY Memory Tab — inspect `learn_index.json`; search + filter; no Room.

**Assistant:** **v2.20.2** — `ParayMemoryRepository`, `ParayMemoryScreen`, `ParayMemoryViewModel`; Memory tab on PARAY screen; refresh on tab select.

---

## Session 132 — 2026-06-14

**User:** Implement PARAY Knowledge Tab — expose catalog knowledge from cached JSON.

**Assistant:** **v2.20.3** — `ParayKnowledgeRepository`, `ParayKnowledgeScreen`, `ParayKnowledgeViewModel`; summary + rollups + recently learned; no SQL.

---

## Session 133 — 2026-06-14

**User:** Implement PARAY Statistics Tab — learning/intelligence KPIs from cached JSON.

**Assistant:** **v2.20.4** — `ParayStatisticsRepository`, `ParayStatisticsScreen`, `ParayStatisticsViewModel`; progress cards, coverage bar, learning trend; all four PARAY tabs complete.

---

## Session 134 — 2026-06-14

**User:** PARAY Recognition Curiosity Phase 4 — observe recognition weaknesses.

**Assistant:** **v2.21.0** — `ParayRecognitionObserver`, `paray_home/recognition/`; AGENT + Scanner hooks; see `docs/PARAY_RECOGNITION.md`.

---

## Session 135 — 2026-06-14

**User:** PARAY Phase 4 full spec — Recognition Curiosity + Living AI Presence V1 (LED, dashboards).

---

## Session 136 — 2026-06-14

**User:** PARAY Phase 5 — Knowledge Fusion System (offline shared intelligence via PKP).

**Assistant:** **v2.22.0** — `ParayKnowledgeFusionEngine`, PKP `.pkp.zip`, PARAY Home Export/Import/Fusion History, `paray_home/fusion/`; see `docs/PARAY_FUSION.md`.
