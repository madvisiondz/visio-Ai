# Visio Ai (Oasis AI) — App Briefing & Development Status

> **Last updated:** 2026-06-21  
> **Current release:** **v2.15.6** (build **294**)  
> **Install:** `VisioAI-debug.apk` at repository root — build with `.\BUILD-APK.ps1`  
> **Canonical spec:** [`PROJECT.md`](../PROJECT.md)

---

## Executive summary

**Visio Ai** (internal name: **Oasis AI**) is an **Android-only, offline-first** retail signage and product-image app built for **OASIS MALL**. It connects **GestiumERP** article data (~20,000+ rows) with a local **PNG image gallery**, barcode scanning, Telegram/file sharing, shelf-label printing, and a separate **VisioPRO** module for social/print promo cards (fruits, vegetables, butcher, fish).

The app replaces fragmented manual workflows (Photoshop tickets, ad-hoc Telegram sharing, spreadsheet hunting) with a single on-phone pipeline:

```
Gestium CSV → local database + diff → search/scan → carts → share or print → audit trail
```

**Latest milestone (v2.15.6):** All long-running tasks (CSV import, sync sub-PNGs, re-index, backups, exports, purge, load PNGs) now run in a **foreground service** with notification progress — safe to **lock the screen** while work continues.

---

## Scale & context

| Asset | Scale / state |
|-------|----------------|
| Articles (Gestium) | 20,000+ rows per CSV export |
| Linked product PNGs | ~2,900+ prepared on PC; stored in `files/product_images/` on device |
| Unmatched PNGs | ~1,064 kept for manual review (`product_images/not found/` on PC) |
| Retail sections | Boucherie, Boissons, Confiserie, Fruits et Légumes, Poissonnerie, etc. |
| Platform | Android 8+ (minSdk 26), target API 34 |
| Internet | **Not required** for core flows |

---

## Core product rules

These rules govern every feature decision:

1. **Designation/name is primary identity** — barcode is a lookup key, not the main link between article and image.
2. **Offline-first** — import, search, scanner, carts, Design print, background removal, VisioPRO all work without internet.
3. **Pre-selection workflow** — always `catalog → cart → template → export`. Never pick a template first and then hunt articles.
4. **Print traceability** — every print batch stores price, designation, and image snapshots at generation time.
5. **Build order** — import + database + search + scanner first; then carts, print, audit, promo.

---

## Technology stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (Oasis orange `#FF5E13`) |
| Architecture | ViewModel + StateFlow |
| Database | Room (SQLite) — schema v18+ |
| Navigation | Navigation Compose, swipeable bottom bar (8 tabs) |
| Barcode | CameraX + ML Kit Barcode Scanner |
| Images | Local PNG storage + Coil previews + PNG tEXt metadata |
| Background removal | U2NetP ONNX bundled in APK (~4.5 MB) |
| Print export | In-app JPEG/PDF (`ShelfA4Renderer`, VisioPRO layered renderer) |
| Long tasks | `OasisBackgroundTaskService` — foreground + wake lock |
| Package ID | `com.oasismall.visioai` |
| Display name | Visio Ai / MadVision (repo branding) |

---

## Bottom navigation — main entry points

Swipe horizontally to reach all tabs (76dp each):

| Tab | Route | Purpose |
|-----|-------|---------|
| **VisioPRO** | `visio_pro_home` | Social + print promo cards (F&L, butcher, fish) |
| **Articles** | `home` | Full catalog search + rayon filter chips |
| **To shoot** | `photoshoot_cart` | Articles missing images; camera batch / AGENT |
| **AGENT** | `check_shoot` | Full-screen scan + shoot + sub-barcode workflow |
| **Batch txt** | `batch_txt` | Paste designation lists → route to carts or camera queue |
| **To share** | `share_cart` | PNG sharing queue (Telegram document mode) |
| **Design** | `design` | Shelf label print queue + Historique |
| **Settings** | `settings` | Import, device transfer, tools, Report |

---

## Feature modules (detailed)

### 1. Gestium CSV import & change detection

**Status:** Done — optimized for ~28k rows; runs in background (v2.15.6).

- Pick Gestium CSV via system file picker.
- Flexible French/English column mapping (`Code-barres`, `Désignation`, `Prix`, `Rayon`, `Code`, etc.).
- Encoding fallback: Windows-1252 first (Gestium exports), then UTF-8.
- **Import diff engine** — never blind overwrite:
  - NEW, PRICE_CHANGED, RENAMED, REMOVED, UNCHANGED
  - Price history stored per article
- **Rayons importants** (Settings checklist) — when configured, preview/history/report UI shows only selected rayons (full DB still imports).
- Barcode-less rows imported via **codeart** (`CA:13145`) or normalized designation.
- Re-import performance: lightweight `ArticleImportSnapshot` (no `rawData` blobs), streaming parse, PNG index cache — target ~5s for unchanged re-imports.
- **Import detail screen** — thumbnails, Add to To share / To shoot from changed rows.

**Flow:**

```
CSV file → parse + validate → preview (scoped rayons) → Confirm
    → OasisBackgroundTaskService → ImportService diff → link new images → success notification
```

---

### 2. Article catalog & search

**Status:** Done.

- Smart token search — any word anywhere in designation.
- Filter chips: all rayons from CSV **Rayon** column (Boucherie, Boissons, …).
- Sub-barcode rows appear as separate searchable entries (same designation, different barcode).
- **CSV price-change glow** — articles with recent price changes highlighted across catalog, carts, Design, VisioPRO, Report.
- Article detail: full CSV fields, price/print history, sub-barcode chips, Create asset, Add to carts.

---

### 3. Barcode scanner & AGENT

**Status:** Done.

- **Settings → Barcode scanner** — lookup by camera scan.
- **AGENT tab** — full-screen Check & Shoot:
  - Lock barcode → show article info + existing PNG preview
  - Create asset → camera → U2NetP background removal → link transparent PNG
  - **SUB-BC** — scan flavor/color alternate barcodes; confirm → shoot → PhotoRoom → link
  - Manual barcode entry when label won't scan
- **Bulk mode** — rapid barcode-only captures to `bulk_images/`.
- Alternate barcode resolution: suffix picker, body-key matching (9 digits), PARAY suggestions.

---

### 4. Sub-barcode (flavor) system

**Status:** Done (v2.15.x series).

Retail products often have multiple barcodes (flavors, pack sizes). Oasis AI treats each as a **variant row** in carts and search.

| Mechanism | Purpose |
|-----------|---------|
| `article_alternate_barcodes` table | DB link: sub-barcode → parent article + image path |
| PNG metadata `ParentBarcode`, `VariantType=sub` | Self-describing files survive DB purge |
| Filename `{designation}{n}.png` (e.g. `PommesGolden1.png`) | Human-readable gallery names |
| `sub_barcode_registry.json` | Local JSON archive before Gestium purge; auto-restore on CSV re-import |
| **Sync sub-PNGs** (Settings) | Scan gallery, backfill legacy `sub_*.png`, link alternates |

Sub-variant PNGs are **excluded from the main article image index** (they don't replace the parent product photo).

---

### 5. Image management

**Status:** Done.

- PNGs stored in `files/product_images/` with embedded tEXt metadata: Barcode, Designation, PriceNow, Rayon, Codeart, ParentBarcode, etc.
- **ImageMatcher** — designation + barcode indexed matching; cached PNG scan.
- **Settings actions:**
  - Load IMAGE ASSETS folder (batch copy, skip existing)
  - Load ready PNGs from gallery or SAF folder tree
  - **Re-index images** — match all articles to gallery PNGs
  - **Export PNG database** — copy all PNGs incl. sub-barcode variants
  - **PhotoRoom folder** picker — camera batch import reads from chosen folder
- **To shoot → Stamper** — multi-pick gallery PNGs, scan barcodes onto cards, Done batch-copies + metadata.
- **Check & shoot** — verify existing PNG or capture missing shot to DCIM.

---

### 6. Carts & routing workflows

**Status:** Done.

Three primary production carts plus Design sub-carts:

| Cart | Purpose | Key actions |
|------|---------|-------------|
| **To share** | Share PNGs as Telegram **documents** (no recompression) | Share selected/all as files; Add to Design; origin color tags |
| **To shoot** | Articles needing photos | Open AGENT, camera batch, Stamper |
| **Design (À imprimer)** | Shelf label print queue | Edit price, copy count (+/−), promo ticket, print A4 JPEG |
| **Design (Done)** | Completed prints (max 50) | Mark sent/printed; Send info text for price round-trip |
| **Batch camera queue** | Scan-first multi-article shoot | Auto-advance; PhotoRoom import links |

**Batch txt** — paste a list of designations → auto-route each line to To share, To shoot, or camera batch queue.

**To share row footer** shows live status: Printed / Design queue|done / Telegram sent.

---

### 7. Design — shelf label printing

**Status:** Done (replaces removed Oasis Print PC app).

- **12-up landscape A4 JPEG** — 2×6 grid, yellow price block, large red price + DA.
- **300 DPI**, JPEG quality 100, full-res product decode.
- Promo tickets — dual price (prix barré) with `isPromoTicket`, `promoPrice`, `promoOriginalPrice`.
- Per-article **copy count** expands repeats on the sheet.
- Exports saved under `exports/yyyy-MM-dd/shelf_HHmmss_pN.jpg`.
- **Historique tab** — browse batches by date; batch detail with reprint, exclude rows, reload queue, send to Design/To share.
- **Print audit** — `print_batches` + `print_batch_items` with price/designation/image snapshots.

**Send info / Import prices** — share designation+barcode list as text; paste colleague's price-checked message back into catalog.

---

### 8. VisioPRO — social & print promo cards

**Status:** Done — actively maintained preset catalog.

Separate dark/gold UI for fresh-produce and butcher/fish signage.

**7 preset families:**

- Fruits social + print
- Légumes social + print
- Boucherie social + print
- Poisson social only (no price on fish social cards)

**Features:**

- Category lists from Gestium **rayon pools** (auto-resync after CSV import).
- Inline price editing with CSV baseline + manual override tracing.
- **Désignation (AR)** editor with live preview; swipe card for prev/next.
- **Taille désignation** slider per article.
- Print-tab photos use **contain** fit (no crop).
- Print codes: last 3 digits of barcode or Gestium codeart for `CA:` rows.
- **Studio designer** — pinch, resize handles, snap guides, align tools, Calques/Réglages tabs.
- **Exporter sélection** — checkbox per article on social + print lists → individual rendered PNGs (not A4 sheet).
- Gallery export paths: `DCIM/VisioPRO/Social` and `DCIM/VisioPRO/Print`.
- **Listes VisioPRO** in Settings — edit enabled articles per card, drag reorder.

**Settings → Device transfer → Export VisioPRO presets** — per-section ZIP with articles, photos, catalog PNGs, designs.

---

### 9. Device transfer & backup

**Status:** Done (v2.15.0–2.15.4).

Settings → **Device transfer**:

| Action | What it does |
|--------|--------------|
| **Purge Gestium catalog** | Clears articles, imports, carts, VisioPRO links; archives sub-barcode registry first; **keeps PNG files** |
| **Sync sub-PNGs** | Scan gallery metadata, rename legacy files, link alternates |
| **Export full backup** | `VisioAi_backup_*.zip` — DB JSON + PNGs + VisioPRO + PARAY + exports + settings |
| **Import full backup** | Restore on another phone with barcode ID remapping |
| **Export VisioPRO presets** | Per-category bundle ZIP |
| **Export PNG database** | All gallery PNGs incl. sub-barcode variants |

ZIP saves use the system **Save as** picker (v2.15.4) — user chooses destination; no silent Downloads bloat; stale export cache cleaned on launch.

---

### 10. Background long tasks (v2.15.6)

**Status:** Done.

`OasisBackgroundTaskService` + `OasisBackgroundTaskManager`:

- Foreground notification with progress bar
- Partial wake lock (up to 6 hours)
- Shared StateFlow — UI overlays observe progress; success/error messages on return

**Tasks that run in background:**

| Task kind | Trigger |
|-----------|---------|
| CSV import | Import Center → Confirm |
| Sync sub-PNGs | Settings → Device transfer |
| Re-index images | Settings |
| Export PNG database | Settings |
| Export / import full backup | Settings (after file picker) |
| Export VisioPRO bundle | Settings (after file picker) |
| Purge Gestium catalog | Settings |
| Load sample data | Settings / Import |
| Load ready PNGs | Settings (gallery or folder) |

**Not yet in background service:** VisioPRO **Exporter sélection** batch card export (still foreground).

PARAY neural fingerprint import already had its own foreground service since v1.7.1.

---

### 11. PARAY visual agent

**Status:** Done (foundation + neural load).

- Learns product shape/color/typography per article.
- Barcode body-key suggestions (drop last 4 digits, compare first 9).
- Camera teach flow in AGENT.
- PC script builds fingerprint index → import on phone via Settings.
- **PARAY home** — separate UI + `paray_home/` living folder.
- Neural load screen runs in foreground service (background-safe).

See [`docs/PARAY.md`](PARAY.md).

---

### 12. Background removal (U2NetP)

**Status:** Done — fully offline.

- Bundled `u2netp.onnx` in APK (~4.5 MB).
- ONNX Runtime primary; TFLite fallback.
- Used from AGENT Create asset, article detail, Settings tool screen.
- Saves transparent PNG + optional original backup.

---

### 13. Report, history & promo

**Status:** Done.

- **Report** (Settings) — CSV import summaries + Design print batches; scoped to Rayons importants when set.
- **Work history** — event log: searched, scanned, cart actions, linked, sent (starts empty; no backfill).
- **Promo tracker** — promo flags on print batches; expiry alerts (expires today / expired).

---

### 14. Phone sync (hotspot LAN)

**Status:** Done.

- Master/slave over phone hotspot (port 8776).
- Catalog compare + delta PNG push — no cloud required.
- See [`docs/PHONE_SYNC.md`](PHONE_SYNC.md).

---

## Data model (Room — key tables)

| Table | Role |
|-------|------|
| `articles` | Gestium catalog — barcode, designation, price, rayon, codeart, normalized_name |
| `imports` / `import_changes` | CSV import history + diff rows |
| `article_price_history` | Price change audit |
| `product_images` | Linked PNG paths + status (found/missing/review) |
| `article_alternate_barcodes` | Sub-barcode variants + image paths |
| `preselection_items` | Cart rows — type (SHARE, PHOTOSHOOT, DESIGN, DESIGN_DONE), variantBarcode, copyCount, promo fields |
| `print_batches` / `print_batch_items` | Print audit snapshots |
| `batch_camera_queue` | Camera batch shoot queue |
| `bulk_captures` | AGENT bulk mode captures |
| `workflow_history` | User action event log |
| `promo_alerts` | Expiry notifications |

Schema currently at **v18** (Design batch snapshots, catalog change glow).

---

## MVP status

| MVP | Name | Status |
|-----|------|--------|
| 1 | Data + Search | **Done** |
| 2 | Change Detection | **Done** |
| 3 | Pre-selection | **Done** |
| 4 | Simple Print Generator | **Done** (in-app Design) |
| 5 | More Templates | **Done** (freezer, podium, board + VisioPRO) |
| 6 | Audit + Promo | **Done** |
| 7 | Ready PNG Linking | **Mostly done** — gallery picker, Stamper, Check & shoot, PhotoRoom import; gallery link placeholder screen still planned |

---

## Recent release history (v2.14 → v2.15.6)

| Version | Build | Highlights |
|---------|-------|------------|
| **2.15.6** | 294 | Background foreground service for all long Settings/import tasks; sub-PNG sync scan optimized |
| **2.15.5** | 293 | Sub-PNG metadata-first naming; **Sync sub-PNGs**; excluded from main image index |
| **2.15.4** | 292 | ZIP exports via system Save-as picker; export cache cleanup |
| **2.15.3** | 291 | VisioPRO checkboxes + **Exporter sélection** (individual PNGs) |
| **2.15.2** | 290 | Sub-barcode flavor registry survives Gestium purge |
| **2.15.1** | 289 | VisioPRO print photos linked to catalog for To share |
| **2.15.0** | 288 | Device transfer — purge, full backup ZIP, VisioPRO bundle |
| **2.14.8** | 287 | CSV import speed restore — snapshot diff, streaming parse, PNG cache |
| **2.14.3** | 282 | VisioPRO list prices from CSV; manual override tracing |
| **2.13.0** | 278 | CSV reports scoped to Rayons importants |
| **2.11.0** | 266 | Design Historique tab + batch detail reprint |
| **2.6.0** | 250 | VisioPRO in-app module (7 preset families) |

Full changelog: [`PROJECT.md`](../PROJECT.md) § Changelog · [`docs/PROGRESS.md`](PROGRESS.md)

---

## Known gaps & planned work

| Item | Status |
|------|--------|
| Gallery link placeholder screen | Planned — unified pick PNGs → scan → rename workflow |
| VisioPRO **Exporter sélection** in background service | Not yet — still foreground |
| Legacy `sub_*.png` full metadata rewrite | Slow on first sync (~40s/file if rewrite needed); optimized scan in v2.15.6 |
| PSD pixel-perfect VisioPRO layouts | Waiting on template supply — inspect pipeline ready (`tools/psd/`) |
| Explicit VisioPRO designation/codeart mapping table | Keyword match today |
| Bulk import performance test (20k rows on phone) | Deferred |
| Demo second CSV for price-change tickets | Manual test pending |

**Removed / superseded:**

- Oasis Print desktop PC app (removed 2026-06-02) — replaced by in-app Design screen.
- In-app background-removal server — replaced by external PhotoRoom + on-device U2NetP.
- Supabase/LAN Oasis Print sync — stripped from Android.

---

## Daily operator workflows

### Morning price update

1. Export Gestium CSV on PC → transfer to phone.
2. Settings → Import CSV → preview → Confirm (runs in background).
3. Report → review price changes (filtered by Rayons importants if configured).
4. Add changed articles to To share or Design queue.

### New product photo

1. Scan barcode in AGENT or To shoot.
2. Shoot photo → PhotoRoom background removal → save PNG to PhotoRoom folder.
3. Camera batch import or PhotoRoom import screen links PNG → auto-adds to To share.

### Shelf ticket print

1. Add articles to To share → **Add to Design**.
2. Design tab → edit prices/copy counts → Print → A4 JPEG saved + shared.
3. Historique tab → audit what was printed and when.

### Fresh produce promo (VisioPRO)

1. CSV import refreshes rayon pools.
2. VisioPRO → pick category → edit prices/order.
3. Export social or print cards → **Exporter sélection** for individual PNGs.

### Phone migration

1. Settings → Export full backup → pick save location.
2. Install app on new phone → Import full backup.
3. **Sync sub-PNGs** if sub-barcode flavors need re-linking.

---

## Build & install

```powershell
# From repository root
.\BUILD-APK.ps1
```

Output: `VisioAI-debug.apk` (~234 MB) — includes U2NetP model, ONNX runtime, bundled assets.

Install steps: see [`android/INSTALL-ON-PHONE.txt`](../android/INSTALL-ON-PHONE.txt).

Dev environment: Android SDK API 34, Kotlin 2.2, Gradle 8.10. Build output cached at `%LOCALAPPDATA%\OasisAI-android-build` (avoids OneDrive sync locks).

---

## Documentation map

| Document | Contents |
|----------|----------|
| [`PROJECT.md`](../PROJECT.md) | Living spec — requirements, data model, MVP, changelog |
| [`docs/APP_BRIEFING.md`](APP_BRIEFING.md) | This file — features + current status |
| [`docs/WHAT_WE_BUILT.md`](WHAT_WE_BUILT.md) | Exhaustive technical reference (partially dated at v2.5.2 header) |
| [`docs/SCREENS.md`](SCREENS.md) | Screen/route tracker |
| [`docs/DATA_FLOW.md`](DATA_FLOW.md) | Step-by-step data flows |
| [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) | Stack + package layout |
| [`docs/PROGRESS.md`](PROGRESS.md) | Session-by-session build log |
| [`docs/VISIOPRO_CSV_ARTICLES.md`](VISIOPRO_CSV_ARTICLES.md) | VisioPRO article catalog notes |
| [`docs/PARAY.md`](PARAY.md) | PARAY visual agent |
| [`docs/CHAT_LOG.md`](CHAT_LOG.md) | Agent conversation summaries |

---

## Summary

Visio Ai is a **production-ready Android mall operations app** spanning Gestium import, image linking, barcode workflows, Telegram sharing, shelf printing, VisioPRO promo cards, device backup, and sub-barcode flavor management. The **v2.15.x series** focused on **device survivability** (purge-safe sub-barcodes, user-chosen ZIP export paths, background long tasks). Current stable build is **v2.15.6 (294)** with foreground-service background execution for all heavy operations.
