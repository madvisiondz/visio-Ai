#!/usr/bin/env python3
"""
Inspect Photoshop PSD files → JSON layer spec + preview PNGs.

Uses psd-tools (primary). Output feeds future Android template renderers.

Usage:
  python tools/psd/inspect_psd.py path/to/template.psd
  python tools/psd/inspect_psd.py --all templates/psd-inbox
  python tools/psd/inspect_psd.py template.psd --preview --layers
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from PIL import Image
from psd_tools import PSDImage
from psd_tools.api.layers import Group, TypeLayer

from psd_schema import Bounds, DocumentSpec, LayerSpec, PsdTemplateSpec, TextInfo

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INBOX = ROOT / "templates" / "psd-inbox"
DEFAULT_SPECS = ROOT / "templates" / "psd-specs"
DEFAULT_PREVIEWS = ROOT / "templates" / "psd-previews"

ROLE_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("product_image", re.compile(r"(product|photo|image|pack|packshot|png|visuel)", re.I)),
    ("designation", re.compile(r"(designat|name|title|libell|product.?name|nom)", re.I)),
    ("price", re.compile(r"(^price$|prix|tarif|promo.?price|new.?price|prix.?promo)", re.I)),
    ("original_price", re.compile(r"(old.?price|prix.?bar|barr|was|ancien|before|orig)", re.I)),
    ("barcode", re.compile(r"(barcode|ean|code.?barre|cb)", re.I)),
    ("background", re.compile(r"(background|bg|fond|yellow|jaune)", re.I)),
    ("logo", re.compile(r"(logo|brand|marque)", re.I)),
]


def infer_role(name: str, text_value: str | None) -> str | None:
    hay = f"{name} {text_value or ''}"
    for role, pattern in ROLE_PATTERNS:
        if pattern.search(hay):
            return role
    return None


def bounds_from_bbox(bbox) -> Bounds | None:
    if not bbox or len(bbox) != 4:
        return None
    left, top, right, bottom = (int(v) for v in bbox)
    if right <= left or bottom <= top:
        return None
    return Bounds(
        left=left,
        top=top,
        right=right,
        bottom=bottom,
        width=right - left,
        height=bottom - top,
    )


def extract_text(layer: TypeLayer) -> TextInfo | None:
    raw = getattr(layer, "text", None)
    if not raw:
        return None
    value = str(raw).replace("\r", "\n").strip()
    if not value:
        return None
    font_name = None
    font_size = None
    color = None
    alignment = None
    try:
        engine = layer.engine_dict
        if engine:
            style = engine.get("StyleRun") or engine.get("StyleSheet") or {}
            if isinstance(style, dict):
                data = style.get("StyleSheetData") or style
                if isinstance(data, dict):
                    font_name = data.get("Font") or data.get("FontName")
                    font_size = data.get("FontSize")
                    fill = data.get("FillColor") or data.get("Color")
                    if isinstance(fill, dict):
                        values = fill.get("Values")
                        if values and len(values) >= 3:
                            r = int(max(0, min(255, values[0] * 255)))
                            g = int(max(0, min(255, values[1] * 255)))
                            b = int(max(0, min(255, values[2] * 255)))
                            color = f"#{r:02X}{g:02X}{b:02X}"
                    alignment = data.get("Justification") or data.get("Alignment")
    except Exception:
        pass
    return TextInfo(
        value=value,
        font_name=str(font_name) if font_name else None,
        font_size=float(font_size) if font_size else None,
        color=color,
        alignment=str(alignment) if alignment else None,
    )


def walk_layers(psd: PSDImage) -> list[LayerSpec]:
    specs: list[LayerSpec] = []
    index = 0

    def visit(layer, path: str, depth: int) -> None:
        nonlocal index
        kind = getattr(layer, "kind", layer.__class__.__name__)
        bbox = bounds_from_bbox(getattr(layer, "bbox", None))
        text_info = None
        if isinstance(layer, TypeLayer):
            text_info = extract_text(layer)
        name = getattr(layer, "name", "") or f"layer_{index}"
        role = infer_role(name, text_info.value if text_info else None)
        child_count = len(layer) if isinstance(layer, Group) else 0
        specs.append(
            LayerSpec(
                index=index,
                name=name,
                path=path,
                depth=depth,
                kind=str(kind),
                visible=bool(getattr(layer, "visible", True)),
                opacity=round(float(getattr(layer, "opacity", 255)) / 255.0, 3),
                blend_mode=str(getattr(layer, "blend_mode", "normal")),
                bounds=bbox,
                text=text_info,
                role_hint=role,
                children_count=child_count,
            ),
        )
        index += 1
        if isinstance(layer, Group):
            for child in layer:
                child_name = getattr(child, "name", "unnamed")
                visit(child, f"{path}/{child_name}", depth + 1)

    for layer in psd:
        layer_name = getattr(layer, "name", "root")
        visit(layer, layer_name, 0)

    return specs


def build_role_summary(layers: list[LayerSpec]) -> dict[str, list[str]]:
    summary: dict[str, list[str]] = {}
    for layer in layers:
        if not layer.role_hint:
            continue
        summary.setdefault(layer.role_hint, []).append(layer.path)
    return summary


def inspect_file(
    psd_path: Path,
    *,
    write_preview: bool,
    write_layer_previews: bool,
    specs_dir: Path,
    previews_dir: Path,
) -> PsdTemplateSpec:
    psd = PSDImage.open(psd_path)
    width, height = psd.size
    warnings: list[str] = []

    dpi_x = dpi_y = None
    try:
        if psd.image_resources:
            res = psd.image_resources.get_data(1005)  # RESOLUTIONINFO
            if res:
                dpi_x = float(getattr(res, "horizontal_resolution", 0) or 0) or None
                dpi_y = float(getattr(res, "vertical_resolution", 0) or 0) or None
    except Exception as exc:
        warnings.append(f"Could not read DPI: {exc}")

    layers = walk_layers(psd)
    spec = PsdTemplateSpec(
        source_file=psd_path.name,
        engine="psd-tools",
        document=DocumentSpec(
            width=int(width),
            height=int(height),
            channels=getattr(psd, "channels", None),
            color_mode=str(getattr(psd, "color_mode", "")) or None,
            dpi_x=dpi_x,
            dpi_y=dpi_y,
        ),
        layers=layers,
        role_summary=build_role_summary(layers),
        warnings=warnings,
    )

    stem = psd_path.stem
    specs_dir.mkdir(parents=True, exist_ok=True)
    json_path = specs_dir / f"{stem}.psd-spec.json"
    json_path.write_text(json.dumps(spec.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"  spec  → {json_path.relative_to(ROOT)}")

    if write_preview:
        previews_dir.mkdir(parents=True, exist_ok=True)
        try:
            composite = psd.composite()
            preview_path = previews_dir / f"{stem}.preview.png"
            composite.save(preview_path)
            print(f"  preview → {preview_path.relative_to(ROOT)}")
        except Exception as exc:
            warnings.append(f"Composite preview failed: {exc}")
            print(f"  preview FAILED: {exc}", file=sys.stderr)

    if write_layer_previews:
        layer_dir = previews_dir / stem / "layers"
        layer_dir.mkdir(parents=True, exist_ok=True)
        for layer in layers:
            if not layer.visible or not layer.bounds:
                continue
            try:
                psd_layer = _find_layer_by_path(psd, layer.path)
                if psd_layer is None:
                    continue
                img = psd_layer.composite()
                if img is None:
                    continue
                safe = re.sub(r"[^\w\-]+", "_", layer.path)[:80]
                out = layer_dir / f"{layer.index:03d}_{safe}.png"
                img.save(out)
            except Exception:
                continue
        print(f"  layers → {layer_dir.relative_to(ROOT)}/")

    return spec


def _find_layer_by_path(psd: PSDImage, path: str):
    parts = path.split("/")

    def search(layers, idx: int):
        if idx >= len(parts):
            return None
        for layer in layers:
            if getattr(layer, "name", "") == parts[idx]:
                if idx == len(parts) - 1:
                    return layer
                if isinstance(layer, Group):
                    found = search(layer, idx + 1)
                    if found:
                        return found
        return None

    return search(psd, 0)


def main() -> int:
    parser = argparse.ArgumentParser(description="Inspect PSD templates for Visio Ai cloning")
    parser.add_argument("psd", nargs="?", help="Path to a .psd file")
    parser.add_argument("--all", action="store_true", help=f"Inspect every .psd in {DEFAULT_INBOX}")
    parser.add_argument("--preview", action="store_true", help="Export flattened composite PNG")
    parser.add_argument("--layers", action="store_true", help="Export individual layer PNGs (slow)")
    parser.add_argument("--specs-dir", type=Path, default=DEFAULT_SPECS)
    parser.add_argument("--previews-dir", type=Path, default=DEFAULT_PREVIEWS)
    args = parser.parse_args()

    if args.all:
        inbox = DEFAULT_INBOX
        files = sorted(inbox.glob("*.psd")) + sorted(inbox.glob("**/*.psd"))
        files = [f for f in files if f.is_file()]
        if not files:
            print(f"No .psd files in {inbox.relative_to(ROOT)} — drop templates there first.")
            return 1
    elif args.psd:
        files = [Path(args.psd)]
        if not files[0].is_file():
            print(f"File not found: {args.psd}", file=sys.stderr)
            return 1
    else:
        parser.print_help()
        return 1

    print(f"Inspecting {len(files)} PSD file(s)…")
    for psd_path in files:
        print(f"\n{psd_path.name}")
        try:
            spec = inspect_file(
                psd_path.resolve(),
                write_preview=args.preview,
                write_layer_previews=args.layers,
                specs_dir=args.specs_dir,
                previews_dir=args.previews_dir,
            )
            print(
                f"  {spec.document.width}×{spec.document.height}px"
                f" · {len(spec.layers)} layers"
                f" · roles: {', '.join(spec.role_summary.keys()) or 'none'}"
            )
        except Exception as exc:
            print(f"  ERROR: {exc}", file=sys.stderr)
            return 1

    print("\nDone.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
