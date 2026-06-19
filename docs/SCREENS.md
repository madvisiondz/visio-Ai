# Oasis AI — Screens Tracker

> Update when a screen is added, changed, or completed.

| Screen | Route | Status | Notes |
|--------|-------|--------|-------|
| Home / Articles | `home` | Done | **Smart search**; split has image / needs photo; **Add to To shoot**; **Open camera batch** on each result |
| PARAY home | `paray_home` | Done | PARAY's own UI + `paray_home/` folder; office links to Design / AGENT / import |
| PARAY neural load | `paray_import` | Done | Fingerprint import progress, neural profile, memory growth; foreground service (background-safe) |
| Settings | `settings` | Done | IMAGE ASSETS load, **PhotoRoom folder** picker, Export PNG database, re-index, Report |
| Gallery link (placeholder) | `gallery_link` | Planned | Pick PNGs → scan → rename workflow |
| Import Center | `import` | Done | From Settings; CSV picker, preview, Confirm/Cancel in scroll (all screen sizes); history |
| Import Detail | `import/{importId}` | Done | Per-import change log; thumbnail + **Add to To share** / **To shoot** |
| Article Catalog | `catalog` | Done | Search, filters, add to pre-selection |
| Article Detail | `article/{articleId}` | Done | Scrollable panel; **Add sub-barcode & batch shoot** (confirm flow); tap sub-barcode chip to remove |
| Background removal | `background_removal/{articleId}` | Done | U2NetP offline cutout; crop/threshold; before/after; Accept links to article |
| Barcode Scanner | `scanner` | Done | Settings; camera + **manual barcode entry** |
| Batch txt | `batch_txt` | Done | Paste designation list → **To share** / **To shoot** / **Camera batch queue** (tap line to capture) |
| Camera batch shoot | `camera_batch_shoot?queueItemId=` | Done | Sub-barcode: **confirm** → **shoot?** → save → PhotoRoom → import links sub-bc; removable chips |
| PhotoRoom import | `camera_batch_import` | Done | Pending list; Import / **Remove** per card; import all matched |
| To shoot cart | `photoshoot_cart` | Done | **Bottom nav**; **Open camera batch** per article; Open AGENT; origin colors |
| AGENT | `check_shoot` | Done | SUB-BC: confirm → shoot? → batch camera; tap sub-barcode chip to remove |
| To share cart | `share_cart` | Done | **Add all/selected to Design** + **Share all as files**; **parent + sub-barcode flavors** as separate rows (v2.4.5); origin color legend |
| Design | `design` | Done | **Bottom nav** (brush); **To print** + **Done**; each cart flavor row independent (remove/copy by preselectionId) |
| Report | `report` | Done | **Settings → Report**; CSV changes with image + cart actions + Design shelf prints |
| Work History | `history` | Done | **Settings → Work history**; records searched/scanned/cart/linked/sent actions |
| Pre-selection | `preselection` | Legacy | Older print cart workflow |
| Print Generator | `print` | Done | Template pick, promo options, PDF export |
| Print History | `print_history` | Done | All batches list |
| Print Batch Detail | `print_batch/{batchId}` | Done | Snapshots, mark printed |
| Promo Tracker | `promo` | Done | Alerts, expiry status |
| Image Manager | `images` | Done | Missing images; ready PNGs handled through gallery link/re-index |

## Bottom navigation

Articles · **AGENT** · Batch txt · To share · **Design** · Settings

## From Settings

Work history · Report · Import CSV · Load IMAGE ASSETS · Re-index · **Import PARAY fingerprints** · Phone sync · Scanner
