#!/usr/bin/env python3
"""Export AIL PSD assets + generate VisioPRO fruits/legumes catalog from CSV mapping doc."""

from __future__ import annotations

import csv
import json
import re
import unicodedata
from datetime import date
from pathlib import Path

from PIL import Image
from psd_tools import PSDImage

ROOT = Path(__file__).resolve().parents[1]
PSD_PATH = Path(r"c:\Users\Oasis-Mall\Desktop\AIL(example).psd")
CSV_MD = ROOT / "docs" / "VISIOPRO_CSV_ARTICLES.md"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets" / "visiopro" / "ail_social"
OUT_KOTLIN = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "oasismall" / "oasisai" / "domain" / "visiopro" / "VisioProCsvArticles.kt"

# Arabic labels — extend as needed; fallback = French designation on card.
ARABIC: dict[str, str] = {
    "ABRICOT FRAIS": "مشمش",
    "AIL": "ثوم",
    "AIL VERT": "ثوم أخضر",
    "ANANAS 1PC": "أناناس",
    "ARTICHAUT Rouge": "خرشوف",
    "ARTICHAUT VERT": "خرشوف",
    "AUBERGINE": "باذنجان",
    "BANANE": "موز",
    "BANANE PROMO": "موز",
    "BETTERAVE": "شمندر",
    "BROCOLI": "بروكلي",
    "CAROTTE": "جزر",
    "CELERI (KRAFS)": "كرافس",
    "CERISE": "كرز",
    "CERISE 1ERE CHOIX": "كرز",
    "CITRON": "ليمون",
    "CITROUILLE": "قرع",
    "COING": "سفرجل",
    "CONCOMBRE": "خيار",
    "COURGETTE": "كوسة",
    "EPINARD 1PCS": "سبانخ",
    "FENOUIL": "شمر",
    "FIGUE": "تين",
    "FIGUE DE BARBARIE FRUIT": "تين شوكي",
    "FIGUE FRAIS GM": "تين",
    "FIGUE PM": "تين",
    "FRAISE": "فراولة",
    "GRENADE": "رمان",
    "GRENADE 1ER CHOIX": "رمان",
    "HARICOT BLANC LEGUME": "فاصوليا بيضاء",
    "HARICOT ROUGE LEGUME": "فاصوليا حمراء",
    "HARICOT VERT LEGUME": "فاصوليا خضراء",
    "KIWI": "كيوي",
    "KIWI FRUIT": "كيوي",
    "LEGUME BIO": "خضر",
    "LEGUME PROMO": "خضر",
    "MANDARINE": "يوسفي",
    "MANGUE FRUIT": "مانجو",
    "MELON": "شمام",
    "MELON CANTALOUP": "شمام",
    "MENTHE / THYME": "نعناع",
    "NEFLE": "إسكدينية",
    "NOIX DE COCO FRUIT": "جوز الهند",
    "OIGNON": "بصل",
    "ORANGE 1ER CHOIX": "برتقال",
    "ORANGE 2EME CHOIX": "برتقال",
    "ORANGE P": "برتقال",
    "PASTEQUE": "بطيخ",
    "PECHE": "خوخ",
    "PECHE GM": "خوخ",
    "PECHE PLATE": "خوخ",
    "PIMENT FORT": "فلفل حار",
    "PIMENT FORT STARTEUR": "فلفل حار",
    "PITAYA/ FRUIT DU DRAGON": "فruit de dragon",
    "POIRE": "إجاص",
    "POIRE 2": "إجاص",
    "POIVRON": "فلفل",
    "POIVRON PROMO": "فلفل",
    "POIVRON ROUGE": "فلفل أحمر",
    "POMME": "تفاح",
    "POMME JAUNE": "تفاح",
    "POMME ROUGE": "تفاح",
    "PRUNES JAPONAISE/ ABRICOT": "برقوق",
    "RAISIN": "عنب",
    "RAISIN BLANC": "عنب",
    "RAISIN ROUGE": "عنب",
    "TOMATE": "طماطم",
}


def slugify(text: str) -> str:
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^a-zA-Z0-9]+", "_", text.lower()).strip("_")
    return text or "article"


def export_psd_assets() -> dict:
    psd = PSDImage.open(PSD_PATH)
    doc_w, doc_h = psd.size
    ASSETS.mkdir(parents=True, exist_ok=True)

    design_canvas = Image.new("RGBA", (doc_w, doc_h), (0, 0, 0, 0))
    layout_layers: dict = {}

    for layer in psd:
        name = layer.name
        bbox = layer.bbox
        left, top, right, bottom = bbox
        if name == "Design":
            img = layer.composite()
            if img:
                design_canvas.paste(img, (left, top), img if img.mode == "RGBA" else None)
        elif name == "price":
            layout_layers["price"] = {
                "left": left,
                "top": top,
                "right": right,
                "bottom": bottom,
                "sample": "550",
                "color": "#000000",
                "fontSizeRatio": 0.085,
                "align": "center",
                "autoFit": True,
            }
        elif "designition" in name.lower() or name.startswith("ث"):
            layout_layers["designation"] = {
                "left": left,
                "top": top,
                "right": right,
                "bottom": bottom,
                "locale": "ar",
                "color": "#FFFFFF",
                "strokeColor": "#000000",
                "strokeWidthRatio": 0.004,
                "fontSizeRatio": 0.042,
                "align": "center",
            }

    design_canvas.save(ASSETS / "design_overlay.png")

    layout = {
        "templateId": "ail_social",
        "sourcePsd": PSD_PATH.name,
        "generated": date.today().isoformat(),
        "width": doc_w,
        "height": doc_h,
        "photo": {"left": 0, "top": 0, "right": doc_w, "bottom": doc_h, "fit": "cover"},
        "designAsset": "design_overlay.png",
        "text": layout_layers,
        "code": {
            "left": 683,
            "top": 1095,
            "right": 728,
            "bottom": 1143,
            "color": "#FFFFFF",
            "strokeColor": "#000000",
            "strokeWidthRatio": 0.003,
            "fontSizeRatio": 0.035,
            "align": "center",
        },
    }
    (ASSETS / "layout.json").write_text(json.dumps(layout, indent=2, ensure_ascii=False), encoding="utf-8")
    return layout


def parse_md_table(section_title: str) -> list[dict]:
    text = CSV_MD.read_text(encoding="utf-8")
    marker = f"## {section_title}"
    start = text.index(marker)
    rest = text[start + len(marker) :]
    end = rest.find("\n---\n")
    chunk = rest[:end] if end >= 0 else rest
    lines = [ln.strip() for ln in chunk.splitlines() if ln.strip().startswith("|") and ln.count("|") >= 5]
    if len(lines) < 3:
        return []
    rows = []
    for ln in lines[2:]:
        cols = [c.strip() for c in ln.strip("|").split("|")]
        if len(cols) < 5:
            continue
        rows.append(
            {
                "designation": cols[0],
                "bc_suffix": cols[1] if cols[1] != "—" else "",
                "barcode": cols[2] if cols[2] != "—" else "",
                "codeart": cols[3] if cols[3] != "—" else "",
            }
        )
    return rows


def kotlin_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace("\"", "\\\"")


def generate_kotlin(fruits: list[dict], legumes: list[dict]) -> None:
    lines = [
        "package com.oasismall.oasisai.domain.visiopro",
        "",
        "/**",
        " * Auto-generated from docs/VISIOPRO_CSV_ARTICLES.md + AIL(example).psd.",
        f" * Generated: {date.today().isoformat()} — do not hand-edit; re-run scripts/build_visiopro_ail_assets.py",
        " */",
        "object VisioProCsvArticles {",
        "",
    ]

    def write_list(name: str, category: str, items: list[dict]) -> None:
        lines.append(f"    val {name}: List<VisioProArticleDef> = listOf(")
        seen_slugs: set[str] = set()
        for it in items:
            des = it["designation"]
            base_slug = slugify(des)
            slug = base_slug
            n = 2
            while slug in seen_slugs:
                slug = f"{base_slug}_{n}"
                n += 1
            seen_slugs.add(slug)
            kw = des.lower()
            ar = ARABIC.get(des, "")
            bc = it["bc_suffix"]
            lines.append(
                f'        article("{slug}", "{kotlin_escape(des)}", "{kotlin_escape(ar)}", '
                f'"{kotlin_escape(bc)}", "{kotlin_escape(kw)}"),'
            )
        lines.append("    )")
        lines.append("")

    lines.append("    private fun article(")
    lines.append("        slug: String,")
    lines.append("        csvDesignation: String,")
    lines.append("        labelAr: String,")
    lines.append("        barcodeSuffix: String,")
    lines.append("        vararg keywords: String,")
    lines.append("    ) = VisioProArticleDef(")
    lines.append("        slug = slug,")
    lines.append("        labelFr = csvDesignation,")
    lines.append("        designationKeywords = keywords.toList(),")
    lines.append("        csvDesignation = csvDesignation,")
    lines.append("        barcodeSuffix = barcodeSuffix.takeIf { it.isNotBlank() },")
    lines.append("        labelAr = labelAr.takeIf { it.isNotBlank() },")
    lines.append("    )")
    lines.append("")

    write_list("fruits", "FRUITS", fruits)
    write_list("vegetables", "VEGETABLES", legumes)

    lines.extend(
        [
            "    fun fruitsAndVegetables(category: VisioProCategory): List<VisioProArticleDef> = when (category) {",
            "        VisioProCategory.FRUITS -> fruits",
            "        VisioProCategory.VEGETABLES -> vegetables",
            "        else -> emptyList()",
            "    }",
            "}",
        ]
    )

    OUT_KOTLIN.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    layout = export_psd_assets()
    fruits = parse_md_table("Fruits")
    legumes = parse_md_table("Légumes")
    generate_kotlin(fruits, legumes)
    print(f"Assets → {ASSETS}")
    print(f"Layout {layout['width']}x{layout['height']}")
    print(f"Kotlin catalog: {len(fruits)} fruits, {len(legumes)} légumes → {OUT_KOTLIN}")


if __name__ == "__main__":
    main()
