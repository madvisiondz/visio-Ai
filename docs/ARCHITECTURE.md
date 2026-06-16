# Oasis AI — Architecture

## Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 (Oasis orange `#FF5E13`, dark surfaces) |
| Navigation | Navigation Compose |
| State | ViewModel + StateFlow |
| Database | Room (SQLite) |
| Images | Coil lazy previews + local `files/product_images/` + FileProvider sharing |
| Barcode | ML Kit (dependency) + manual entry |
| Print export | Android `PdfDocument` |
| Build | Gradle, API 34, Kotlin 2.2 |

## Repo layout

```
oasis ai/
├── android/                    # Android app (Oasis AI)
├── scripts/                    # CSV/image + PARAY preparation helpers
├── product_images/             # PC-side ready PNG set
├── docs/                       # Trackers
└── PROJECT.md
```

```
com.oasismall.oasisai/
├── OasisApp.kt              # Application, DI container
├── MainActivity.kt
├── data/
│   ├── db/                  # Room entities, DAOs, database
│   ├── model/               # Enums
│   └── repository/          # OasisRepository
├── domain/
│   ├── CsvParser.kt       # Parses known fields + stores full raw CSV detail
│   ├── ImportService.kt     # Import + diff
│   ├── ImageMatcher.kt      # IO-thread PNG matching + bulk image index update
│   ├── PrintGenerator.kt    # PDF layouts
│   ├── design/ShelfA4Renderer.kt  # In-app shelf 12-up A4 JPEG
│   ├── paray/               # PARAY visual agent — shape/color/typo + PC fingerprint import + camera ID
│   ├── layoutagent/         # Layout fit submodule (used by PARAY)
│   └── PromoService.kt      # Expiry alerts
├── ui/
│   ├── navigation/
│   ├── components/
│   └── screens/             # Per-feature screens + ViewModels
└── util/
```

## Core rules (from product spec)

1. **Designation-first** — article name is primary identity; barcode is lookup key
2. **Offline-first** — all core flows work without internet
3. **Pre-selection workflow** — catalog → pre-selection → template → export
4. **Import diff** — never blind overwrite; compare and log changes
5. **Print audit** — every batch stores price/designation/image snapshots

## Database tables

See `PROJECT.md` §16. Room schema is currently **v11** (`bulk_captures` for AGENT Bulk mode).

`articles.rawData` preserves all CSV columns for article detail display. `product_images.createdAt` and `product_images.lastSentAt` support the office workflow for tracking when PNGs were imported/created and last shared as files.

The To shoot Stamper keeps selected gallery images as URI references until the user presses Done. This avoids loading/copying all selected PNGs into memory at once; Coil previews only visible cards, and the final copy/link work runs on `Dispatchers.IO`. The slider uses center-card detection plus trailing space so edge cards, including the last card, can become the active scan target.

PNG metadata is enriched before linking/sharing with designation, price now, previous price when known, barcode, and rayon/category.

History is event-based through `workflow_history`, not backfilled from old rows. It starts empty after the v6 schema and records new searched/scanned/cart/linked/sent actions.

## Template types

| Type | Capacity | Output |
|------|----------|--------|
| SHELF | 10 / A4 | Multi-label sheet |
| FREEZER | 1 / A4 | Large single card |
| PODIUM | 1 / A4 | Promo signage |
| BOARD | 1 / A3 | Board layout |

Seeded on first launch in `OasisApp`.

## Print export (on phone)

Shelf labels: **Design** screen → `ShelfA4Renderer` → landscape A4 JPEG (12-up). Design uses two carts: `DESIGN` (To print) and `DESIGN_DONE`; `copyCount` expands repeats per article. Legacy PDF templates remain in Settings print flow.

Phone-to-phone PNG sync: **Phone sync** (hotspot LAN, port 8776) — see `docs/PHONE_SYNC.md`.

- **AGENT** tab: **Smart** (PARAY + catalog + SUB-BC) or **Bulk** (barcode-only mall captures → `bulk_images/` + `bulk_captures`).
- Bottom nav **Batch txt** for designation-list routing to To share / To shoot / **Camera batch queue** (not in CSV).

