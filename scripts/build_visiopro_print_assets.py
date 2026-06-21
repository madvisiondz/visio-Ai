#!/usr/bin/env python3
"""Export abricot print PSD → VisioPRO fv_print assets (Fruits + Légumes impression)."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

from PIL import Image
from psd_tools import PSDImage

ROOT = Path(__file__).resolve().parents[1]
PSD_PATH = Path(r"c:\Users\Oasis-Mall\Desktop\abricot example.psd")
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets" / "visiopro" / "fv_print"


def layer_by_name(psd: PSDImage, name: str):
    name_lower = name.lower()
    for layer in psd:
        if layer.name.lower() == name_lower:
            return layer
    return None


def export_psd_assets() -> dict:
    psd = PSDImage.open(PSD_PATH)
    doc_w, doc_h = psd.size
    ASSETS.mkdir(parents=True, exist_ok=True)

    fruit = layer_by_name(psd, "fruit example")
    design = layer_by_name(psd, "design")
    price_layer = layer_by_name(psd, "Price in red and bold font fits between the currency and the designition")
    des_layer = layer_by_name(psd, "Designition")
    code_layer = layer_by_name(psd, "code : 32")

    if not all([fruit, design, price_layer, des_layer, code_layer]):
        raise SystemExit("Missing expected layers in abricot example.psd")

    fl, ft, fr, fb = fruit.bbox
    photo_top = max(0, ft)
    photo = {"left": fl, "top": photo_top, "right": fr, "bottom": fb, "fit": "cover"}

    def slot(layer, *, color: str, font_size_ratio: float, align: str, auto_fit: bool = False, extra: dict | None = None):
        left, top, right, bottom = layer.bbox
        data = {
            "left": left,
            "top": top,
            "right": right,
            "bottom": bottom,
            "color": color,
            "fontSizeRatio": font_size_ratio,
            "align": align,
            "autoFit": auto_fit,
        }
        if extra:
            data.update(extra)
        return data

    design_canvas = Image.new("RGBA", (doc_w, doc_h), (0, 0, 0, 0))
    img = design.composite()
    if img:
        dl, dt, _, _ = design.bbox
        design_canvas.paste(img, (dl, dt), img if img.mode == "RGBA" else None)
    design_canvas.save(ASSETS / "design_overlay.png")

    layout = {
        "templateId": "fv_print",
        "sourcePsd": PSD_PATH.name,
        "generated": date.today().isoformat(),
        "width": doc_w,
        "height": doc_h,
        "photo": photo,
        "designAsset": "design_overlay.png",
        "text": {
            "designation": slot(
                des_layer,
                color="#FFFFFF",
                font_size_ratio=0.055,
                align="center",
                auto_fit=True,
                extra={
                    "locale": "ar",
                    "strokeColor": "#000000",
                    "strokeWidthRatio": 0.0025,
                },
            ),
            "price": slot(
                price_layer,
                color="#E53935",
                font_size_ratio=0.09,
                align="center",
                auto_fit=True,
            ),
        },
        "code": slot(
            code_layer,
            color="#FFFFFF",
            font_size_ratio=0.028,
            align="center",
            auto_fit=False,
            extra={
                "strokeColor": "#000000",
                "strokeWidthRatio": 0.002,
                "format": "code : {value}",
            },
        ),
    }
    (ASSETS / "layout.json").write_text(json.dumps(layout, indent=2, ensure_ascii=False), encoding="utf-8")
    return layout


def main() -> None:
    layout = export_psd_assets()
    print(f"Assets → {ASSETS}")
    print(f"Layout {layout['width']}x{layout['height']} (fv_print — Fruits & Légumes impression)")


if __name__ == "__main__":
    main()
