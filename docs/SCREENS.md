# Oasis AI — Screens Tracker

> Update when a screen is added, changed, or completed.

| Screen | Route | Status | Notes |
|--------|-------|--------|-------|
| VisioPRO home | `visio_pro_home` | Done | **Simple list** — 4 categories (counts from user catalog config) |
| VisioPRO designer hub | `visio_pro_designer` → `visio_pro_designer/{presetKey}` | Done | **Studio pro+** — pinch · resize · snap · align · onglets Calques/Réglages |
| VisioPRO list settings | `visio_pro_settings` → `visio_pro_list/{category}` | Done | Full rayon pool after each CSV import · pending badge · resume refresh |
| VisioPRO category | `visio_pro/{category}` | Done | **Full article list** + search · checkbox selection · **Exporter sélection** (PNG per card) · inline prix · tap row → bottom sheet · **Désignation AR** + swipe card for prev/next |
| VisioPRO list editor | `visio_pro_list/{category}` | Done | **Enregistrer** fixed top-right · order sheet no drag-dismiss · live order draft |
| Home / Articles | `home` | Done | **Rayon filter** (Gestium **Rayon** column — Boucherie, Boissons, Confiserie, …) · search scoped to filter · smart search · **Add PNG image** per result row |
| PARAY Memory tab | `paray` tab 2 | Done | Browse `learn_index.json`; search/filter; side progress (v2.20.2) |
| PARAY Knowledge tab | `paray` tab 3 | Done | Cached `knowledge_summary.json` + `knowledge_articles.json`; rollups + recently learned (v2.20.3) |
| PARAY Statistics tab | `paray` tab 4 | Done | Learning/knowledge/workflow KPIs from cached JSON; coverage bar + trend (v2.20.4) |
| PARAY Learn | `paray` → `paray_learn_session` | Done | Learn queue + coverage KPI; preload; front PNG confirm; auto L/R/B |
| PARAY Learn settings | `paray_learn_settings` | Done | Front/side/back threshold sliders → `learn_settings.json` (v2.20.1) |
| PARAY home | `paray_home` | Done | Intelligence dashboard + **Knowledge Fusion** export/import (PKP v1, v2.22.0) |
| PARAY neural load | `paray_import` | Done | Fingerprint import progress, neural profile, memory growth; foreground service (background-safe) |
| Settings | `settings` | Done | Shows **app version**; long tasks run in **foreground service** (notification, screen-off safe); **Device transfer** (purge w/ flavor archive, **Sync sub-PNGs**, full ZIP backup, VisioPRO export); **Rayons importants**; **Listes VisioPRO**; IMAGE ASSETS load, **PhotoRoom folder** picker, Export PNG database, re-index, Report |
| Gallery link (placeholder) | `gallery_link` | Planned | Pick PNGs → scan → rename workflow |
| Import Center | `import` | Done | From Settings; CSV picker, preview (scoped to **Rayons importants** when set), Confirm/Cancel — **import runs in background**; last import + history show scoped change counts |
| Import Detail | `import/{importId}` | Done | Per-import change log filtered to **Rayons importants**; thumbnail + cart actions |
| Article Catalog | `catalog` | Done | Search, filters, add to pre-selection |
| Article Detail | `article/{articleId}` | Done | Scrollable panel; **Add PNG image**; **Add sub-barcode** (scan → shoot flavor OR link barcode only); tap sub-barcode chip to remove |
| Background removal | `background_removal/{articleId}` | Done | U2NetP offline cutout; crop/threshold; before/after; Accept links to article |
| Barcode Scanner | `scanner` | Done | Settings; camera + **manual barcode entry** |
| Batch txt | `batch_txt` | Done | Paste designation list → **To share** / **To shoot** / **Camera batch queue** (tap line to capture) |
| Camera batch shoot | `camera_batch_shoot?queueItemId=` | Done | Sub-barcode: **confirm** → **shoot?** → save → PhotoRoom → import links sub-bc; removable chips |
| PhotoRoom import | `camera_batch_import` | Done | Pending list; Import / **Remove** per card; import all matched |
| To shoot cart | `photoshoot_cart` | Done | **Bottom nav**; **Open camera batch** per article; **Add PNG image** per row; Open AGENT; origin colors |
| AGENT | `check_shoot` | Done | **Smart** / **Ticket** / **Bulk**; Ticket = **tap-to-capture** + crop/OCR/PNG fusion (50/30/20) |
| To share cart | `share_cart` | Done | Per-row footer: **Printed**, **Design** queue/done, **Telegram** sent; **Add PNG image** per row (incl. sub-barcode variants); CSV price-change glow; Add all/selected to Design + Share as files |
| Design | `design` | Done | **Bottom nav** (brush); tabs **À imprimer** / **Historique**; print exports under `exports/yyyy-MM-dd/`; batch detail reprint + glow on CSV changes |
| Report | `report` | Done | **Settings → Report**; CSV changes + import summaries scoped to **Rayons importants**; Design shelf prints |
| Work History | `history` | Done | **Settings → Work history**; records searched/scanned/cart/linked/sent actions |
| Pre-selection | `preselection` | Legacy | Older print cart workflow |
| Print Generator | `print` | Done | Template pick, promo options, PDF export |
| Print History | `print_history` | Done | All batches list |
| Print Batch Detail | `print_batch/{batchId}` | Done | Snapshots, mark printed |
| Promo Tracker | `promo` | Done | Alerts, expiry status |
| Image Manager | `images` | Done | Missing images; ready PNGs handled through gallery link/re-index |

## Bottom navigation

**VisioPRO** · Articles · To shoot · **AGENT** · **PARAY** · Batch txt · To share · **Design** · Settings

**Swipe** the bottom bar horizontally to reach all tabs (each tab is 76dp wide — **VisioPRO** is leftmost).

Fixed in **v2.6.3**: tabs no longer render off-screen (Material3 `NavigationBarItem` cannot live inside a scrolling `Row`).

## From Settings

Work history · Report · Import CSV · Load IMAGE ASSETS · Re-index · **Import PARAY fingerprints** · Phone sync · Scanner

(VisioPRO moved to bottom navigation — no longer under Settings.)
