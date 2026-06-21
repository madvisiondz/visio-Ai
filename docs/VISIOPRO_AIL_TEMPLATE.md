# VisioPRO — AIL social template (Fruits & Légumes)

Source PSD: `AIL(example).psd` on Desktop → exported to `android/app/src/main/assets/visiopro/ail_social/`.

## Layer stack (bottom → top)

| Order | PSD layer (current) | Role |
|-------|---------------------|------|
| 1 | Photo smart object | User photo — **new shoot daily** or reuse latest per article |
| 2 | `Design` | Frame, logo, Facebook banner, price badge shapes |
| 3 | `price` | Dynamic price number (دج / kg baked in design) |
| 4 | Arabic designation | Dynamic Arabic name (e.g. ثوم) |

**No barcode code on social** — the 3-digit code appears on **print** labels only (`abricot example.psd` — see `docs/VISIOPRO_PRINT_TEMPLATE.md`).

**Print** (Fruits & Légumes) uses **`abricot example.psd`** layout — catalog PNG + designation + red price + `code : XX`.

## Recommended PSD renames (for next export)

When you rename layers in Photoshop, use:

- `photo` — bottom photo placeholder
- `design` — middle overlay
- `designation_ar` — Arabic text
- `code` — 3-digit barcode suffix
- `price` — price number only

Then re-run:

```powershell
python scripts/build_visiopro_ail_assets.py
```

## Catalog

- **72 articles** from `docs/VISIOPRO_CSV_ARTICLES.md` (43 fruits + 29 légumes)
- Generated Kotlin: `VisioProCsvArticles.kt`
- Price lookup: barcode suffix (3 ch.) → exact CSV designation → keywords

## App behaviour (v2.6.1)

- **Settings → VisioPRO → Fruits / Légumes → Réseaux sociaux**
- Article chips load CSV-mapped presets with AIL layout (985×1311)
- **Photographier** or **Charger image** saves photo per article; last photo auto-loads next time
- Export → `DCIM/VisioPRO/Social/`
- **v2.6.4**: Price auto-fits yellow badge (bold, larger); no code on social
