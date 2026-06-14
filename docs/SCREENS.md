# Oasis AI — Screens Tracker

> Update when a screen is added, changed, or completed.

| Screen | Route | Status | Notes |
|--------|-------|--------|-------|
| Home / Articles | `home` | Done | **Smart search** (any word in designation); split has image / needs photo |
| PARAY home | `paray_home` | Done | PARAY's own UI + `paray_home/` folder; office links to Design / AGENT / import |
| PARAY neural load | `paray_import` | Done | Fingerprint import progress, neural profile, memory growth; foreground service (background-safe) |
| Settings | `settings` | Done | IMAGE ASSETS folder load, Oasis PNG model, **Import PARAY fingerprints**, phone sync, re-index, Report |
| Gallery link (placeholder) | `gallery_link` | Planned | Pick PNGs → scan → rename workflow |
| Import Center | `import` | Done | From Settings; CSV picker, preview, Confirm/Cancel in scroll (all screen sizes); history |
| Import Detail | `import/{importId}` | Done | Per-import change log |
| Article Catalog | `catalog` | Done | Search, filters, add to pre-selection |
| Article Detail | `article/{articleId}` | Done | **Create asset** (AGENT camera + cutout); image, CSV details, Remove background, Add to To share |
| Background removal | `background_removal/{articleId}` | Done | U2NetP offline cutout; crop/threshold; before/after; Accept links to article |
| Barcode Scanner | `scanner` | Done | Settings only (legacy lookup); add to To share |
| Batch txt | `batch_txt` | Done | Paste designation list; PNG → To share, missing PNG → use AGENT |
| AGENT | `check_shoot` | Done | **Smart/Bulk** mode toggle; **Bulk** — barcode-only scan → replace/skip → `bulk_images/`; **Smart** — PARAY, lock, SUB-BC, teach, share/design |
| To share cart | `share_cart` | Done | **Add all/selected to Design** + **Share all as files**; origin color legend + colored card borders; full-page scroll |
| Design | `design` | Done | **Bottom nav** (brush); **To print** + **Done** sub-cart (max 50, pull up, **queue order preserved**); **− / +** copy stepper; **Send info** / **Import prices**; 12-up A4 (2×6) JPEG |
| Report | `report` | Done | **Settings → Report**; CSV old→new changes + Design shelf prints (scrollable) |
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
