# VisioPRO — F&V print template (abricot example)

Source PSD: `abricot example.psd` on Desktop → exported to `android/app/src/main/assets/visiopro/fv_print/`.

## Canvas

- **3508 × 2480** px — landscape A4 @ 300 DPI (same as batch export page)
- Used for **Fruits** and **Légumes → Impression magasin** (all 72 CSV articles)

## Layer stack (bottom → top)

| Order | PSD layer | Role |
|-------|-----------|------|
| 1 | `fruit example` | **Catalog PNG** (static product image from database) |
| 2 | `design` | Frame, branding, currency badge (exported as `design_overlay.png`) |
| 3 | `Designition` | Arabic designation (dynamic) |
| 4 | `Price in red…` | Bold red price number (dynamic, auto-fit) |
| 5 | `code : 32` | Barcode suffix — `code : {3 ch.}` (dynamic) |

**No daily photoshoot** on print — uses **affichage PSD product export** per article when mapped, else catalog PNG fallback.

See **`docs/VISIOPRO_AFFICHAGE_MAP.md`** for PSD ↔ CSV mapping audit trail.

Re-map after new PSDs:

```powershell
python scripts/map_visiopro_affichage_psd.py
```

## Re-export after PSD edits

```powershell
python scripts/build_visiopro_print_assets.py
```

## A4 × 4 batch

Each label renders at full PSD size, then **4 labels** are scaled into quadrants on one landscape A4 sheet (`VisioProPrintA4Renderer`).

## App path

**VisioPRO → Fruits / Légumes → Impression magasin**
