#!/usr/bin/env python3
"""Extract VisioPRO rayon articles from Gestium CSV → docs/VISIOPRO_CSV_ARTICLES.md"""

import csv
import re
from datetime import date
from pathlib import Path

CSV_PATH = Path(r"c:\Users\Oasis-Mall\Desktop\19-06-26 10h MATIN.csv")
OUT_PATH = Path(__file__).resolve().parents[1] / "docs" / "VISIOPRO_CSV_ARTICLES.md"

DES_I, CODE_I, REF_I, PRICE_I, RAYON_I, BC_I = 0, 2, 3, 9, 17, 21

SECTIONS = [
    ("fruits_vegetables", "Fruits et Légumes", "Fruits et Légumes (CSV rayon)"),
    ("butcher", "Boucherie", "Boucherie"),
    ("fish", "POISSONNERIE", "Poissonnerie"),
]

FRUIT_KW = re.compile(
    r"\b(pomme|poire|banane|orange|mandarine|clementine|clémentine|raisin|fraise|"
    r"melon|pasteque|pastèque|abricot|peche|pêche|prune|cerise|kiwi|ananas|mangue|"
    r"grenade|figue|datte|nefle|nèfle|coing|citron|pamplemousse|kaki|litchi|fruit)\b",
    re.I,
)
VEG_KW = re.compile(
    r"\b(tomate|oignon|pomme de terre|patate|carotte|poivron|concombre|salade|"
    r"laitue|courgette|aubergine|haricot|chou|navet|betterave|radis|ail|persil|"
    r"coriandre|menthe|fenouil|artichaut|celeri|céleri|epinard|épinard|brocoli|"
    r"champignon|legume|légume|piment|butternut|potiron|citrouille)\b",
    re.I,
)


def parse_price(raw: str) -> float | None:
    s = (raw or "").strip().replace('"', "").replace(" ", "").replace(",", ".")
    try:
        return float(s) if s else None
    except ValueError:
        return None


def bc_suffix(barcode: str) -> str:
    digits = re.sub(r"\D", "", barcode or "")
    return digits[-3:] if len(digits) >= 3 else digits


def classify_fruit_veg(designation: str) -> str:
    is_fruit = bool(FRUIT_KW.search(designation))
    is_veg = bool(VEG_KW.search(designation))
    if is_fruit and not is_veg:
        return "fruit"
    if is_veg and not is_fruit:
        return "legume"
    return "review"


def visio_hint(designation: str) -> str:
    kind = classify_fruit_veg(designation)
    if kind == "fruit":
        return "fruit"
    if kind == "legume":
        return "légume"
    if FRUIT_KW.search(designation) and VEG_KW.search(designation):
        return "mixed?"
    return "—"


def fmt_price(price: float | None) -> str:
    if price is None:
        return "—"
    return f"{price:.2f}".replace(".", ",")


def load_rows() -> list[list[str]]:
    text = CSV_PATH.read_bytes().decode("cp1252")
    return list(csv.reader(text.splitlines()))


def load_section(all_rows: list[list[str]], rayon: str) -> list[dict]:
    items: list[dict] = []
    for row in all_rows[1:]:
        if len(row) <= RAYON_I or row[RAYON_I].strip() != rayon:
            continue
        designation = row[DES_I].strip().strip('"')
        if not designation:
            continue
        barcode = row[BC_I].strip().strip('"') if len(row) > BC_I else ""
        items.append(
            {
                "designation": designation,
                "codeart": row[CODE_I].strip().strip('"') if len(row) > CODE_I else "",
                "barcode": barcode,
                "bc_suffix": bc_suffix(barcode),
                "reference": row[REF_I].strip().strip('"') if len(row) > REF_I else "",
                "price_ttc": parse_price(row[PRICE_I] if len(row) > PRICE_I else ""),
                "hint": visio_hint(designation) if rayon == "Fruits et Légumes" else "",
                "fv_class": classify_fruit_veg(designation) if rayon == "Fruits et Légumes" else "",
            }
        )
    items.sort(key=lambda x: x["designation"].lower())
    return items


def main() -> None:
    rows = load_rows()
    lines: list[str] = [
        "# VisioPRO — CSV article mapping (Gestium export)",
        "",
        f"Source: `{CSV_PATH.name}` — extracted **{date.today().isoformat()}**.",
        "",
        "## Rules",
        "",
        "- **Rayon filter** (Gestium `Rayon` column):",
        "  - Fruits + légumes → `Fruits et Légumes`",
        "  - Boucherie → `Boucherie`",
        "  - Poisson → `POISSONNERIE`",
        "- **Code (3 chiffres)** = last 3 digits of `Code-barres` (not the Gestium `Code` column).",
        "- **Code Gestium** = `Code` column (e.g. `00123`) — cross-reference only.",
        "- Fruits vs légumes share one rayon in CSV; split below uses designation keywords.",
        "",
        "## Summary",
        "",
        "| VisioPRO section | Rayon CSV | Articles |",
        "|------------------|-----------|----------|",
    ]

    fv_items = load_section(rows, "Fruits et Légumes")
    fruits = [x for x in fv_items if x["fv_class"] == "fruit"]
    legumes = [x for x in fv_items if x["fv_class"] == "legume"]
    fv_review = [x for x in fv_items if x["fv_class"] == "review"]
    butcher_items = load_section(rows, "Boucherie")
    fish_items = load_section(rows, "POISSONNERIE")

    for label, rayon, count in [
        ("Fruits", "Fruits et Légumes", len(fruits)),
        ("Légumes", "Fruits et Légumes", len(legumes)),
        ("À classer (F&L)", "Fruits et Légumes", len(fv_review)),
        ("Boucherie", "Boucherie", len(butcher_items)),
        ("Poissonnerie", "POISSONNERIE", len(fish_items)),
    ]:
        lines.append(f"| {label} | `{rayon}` | {count} |")

    lines.extend(
        [
            "",
            f"**Rayon Fruits et Légumes (total):** {len(fv_items)} · **Boucherie:** {len(butcher_items)} · **Poissonnerie:** {len(fish_items)}",
            f"**Grand total (3 rayons):** {len(fv_items) + len(butcher_items) + len(fish_items)}",
            "",
        ]
    )

    def write_table(title: str, rayon: str, items: list[dict], show_hint: bool = False) -> None:
        lines.extend(["---", "", f"## {title}", "", f"**Rayon:** `{rayon}` · **{len(items)}** articles", ""])
        if show_hint:
            lines.append("| Désignation | Code 3 ch. | Code-barres | Code Gestium | Prix TTC | Note |")
            lines.append("|-------------|------------|-------------|--------------|----------|------|")
            for it in items:
                lines.append(
                    f"| {it['designation']} | {it['bc_suffix'] or '—'} | {it['barcode'] or '—'} | "
                    f"{it['codeart'] or '—'} | {fmt_price(it['price_ttc'])} | {it['hint']} |"
                )
        else:
            lines.append("| Désignation | Code 3 ch. | Code-barres | Code Gestium | Prix TTC |")
            lines.append("|-------------|------------|-------------|--------------|----------|")
            for it in items:
                lines.append(
                    f"| {it['designation']} | {it['bc_suffix'] or '—'} | {it['barcode'] or '—'} | "
                    f"{it['codeart'] or '—'} | {fmt_price(it['price_ttc'])} |"
                )
        lines.append("")

    write_table("Fruits", "Fruits et Légumes", fruits)
    write_table("Légumes", "Fruits et Légumes", legumes)
    if fv_review:
        write_table("Fruits et Légumes — à classer", "Fruits et Légumes", fv_review, show_hint=True)
    write_table("Boucherie", "Boucherie", butcher_items)
    write_table("Poissonnerie", "POISSONNERIE", fish_items)

    OUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT_PATH}")
    print(f"  Fruits: {len(fruits)}, Légumes: {len(legumes)}, Review: {len(fv_review)}")
    print(f"  Boucherie: {len(butcher_items)}, Poisson: {len(fish_items)}")


if __name__ == "__main__":
    main()
