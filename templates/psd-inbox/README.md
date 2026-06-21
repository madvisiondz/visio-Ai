# PSD template inbox

Drop **Photoshop `.psd` templates** here (shelf, freezer, podium, promo board, etc.).

Then from the repo root:

```powershell
.\scripts\INSPECT-PSD.ps1 -Preview
```

Optional: cross-check with Node **ag-psd**:

```powershell
.\scripts\INSPECT-PSD.ps1 -Preview -NodeToo
```

## Outputs

| Folder | Content |
|--------|---------|
| `templates/psd-specs/` | `*.psd-spec.json` — layer tree, bounds, text, role hints |
| `templates/psd-previews/` | Flattened PNG + per-layer PNGs (with `-Layers`) |

## What we extract

- Document size and DPI
- Every layer: name, path, kind, visibility, opacity, blend mode
- **Bounds** (left, top, width, height) in pixels
- **Text layers**: content, font name/size/color when available
- **Role hints** (auto): `product_image`, `designation`, `price`, `original_price`, `background`, …

Name layers clearly in Photoshop (e.g. `PRICE`, `DESIGNATION`, `PRODUCT_IMAGE`) for best auto-tagging.

PSD files are gitignored (large). Commit the generated `*.psd-spec.json` when a template is approved.
