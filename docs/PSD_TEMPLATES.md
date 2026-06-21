# PSD template cloning pipeline

Goal: read Oasis Mall **Photoshop shelf/promo templates**, extract layer layout, and reproduce them in the Android **Design** renderer (replacing hand-coded `ShelfA4Renderer` per format).

## Tooling (repo)

| Tool | Path | Engine |
|------|------|--------|
| **Primary inspector** | `tools/psd/inspect_psd.py` | [psd-tools](https://github.com/psd-tools/psd-tools) (Python) |
| **Cross-check** | `tools/psd-node/inspect.mjs` | [ag-psd](https://github.com/Agamnentzar/ag-psd) (Node) |
| **Windows runner** | `scripts/INSPECT-PSD.ps1` | installs deps + runs both |

### Quick start

1. Copy `.psd` files into `templates/psd-inbox/`
2. Run:

```powershell
.\scripts\INSPECT-PSD.ps1 -Preview
```

3. Open `templates/psd-specs/<name>.psd-spec.json`
4. Share PSD + JSON with the agent to implement the matching Kotlin renderer

### Options

| Flag | Effect |
|------|--------|
| `-Preview` | Flattened composite PNG in `templates/psd-previews/` |
| `-Layers` | PNG per visible layer (slow, useful for QA) |
| `-NodeToo` | Also emit `*.ag-psd-spec.json` for comparison |
| `-File path.psd` | Inspect one file instead of inbox |

## JSON spec (clone input)

Each spec includes:

- `document`: width, height, DPI
- `layers[]`: hierarchical path, bounds, text styles, `role_hint`
- `role_summary`: grouped layer paths by inferred role

**Role hints** (from layer names):

- `product_image`, `designation`, `price`, `original_price`, `barcode`, `background`, `logo`

## Workflow with shared PSDs

```
Photoshop template (.psd)
    → INSPECT-PSD.ps1
    → *.psd-spec.json + preview PNG
    → Agent maps roles → ShelfA4Renderer / new *Renderer.kt
    → Design screen template picker (future)
    → Print JPEG/PDF on phone
```

## Android target

Today: single hard-coded **shelf 12-up** in `ShelfA4Renderer.kt`.

Next: one renderer per approved PSD spec, driven by `layoutConfig` JSON stored in `print_templates.layoutConfig`.

## Dependencies

```powershell
pip install -r tools/psd/requirements.txt
cd tools/psd-node && npm install
```

Python **3.10+** and **Node 18+** recommended (tested on 3.14 / 24).
