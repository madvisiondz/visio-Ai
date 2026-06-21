#!/usr/bin/env python3
"""
Map affichage PSD files → VisioPRO CSV articles, export product PNGs, regenerate Kotlin catalog.

Source folder: D:\\Oasis Data\\affichage fruits
Verification: barcode code in PSD + filename fuzzy match + optional JPG visual similarity.
"""

from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from datetime import date
from difflib import SequenceMatcher
from pathlib import Path

from PIL import Image
from psd_tools import PSDImage

ROOT = Path(__file__).resolve().parents[1]
PSD_FOLDER = Path(r"d:\Oasis Data\affichage fruits")
CSV_MD = ROOT / "docs" / "VISIOPRO_CSV_ARTICLES.md"
PRODUCTS_OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "visiopro" / "fv_print" / "products"
PREVIEWS_OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "visiopro" / "fv_print" / "previews"
MAP_MD = ROOT / "docs" / "VISIOPRO_AFFICHAGE_MAP.md"
MAP_JSON = ROOT / "android" / "app" / "src" / "main" / "assets" / "visiopro" / "fv_print" / "product_map.json"
OUT_KOTLIN = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "oasismall" / "oasisai" / "domain" / "visiopro" / "VisioProCsvArticles.kt"

PHOTO_BBOX = (1046, 0, 2843, 1594)

MANUAL_PSD_TO_SLUG: dict[str, str] = {
    "ail": "ail",
    "aIl": "ail",
    "abricot": "abricot_frais",
    "ananas": "ananas_1pc",
    "artichaud vert": "artichaut_vert",
    "ARTICHAUT VERT": "artichaut_vert",
    "aubergines": "aubergine",
    "BANANE": "banane",
    "BETTERAVE": "betterave",
    "BROCOLI": "brocoli",
    "carotte": "carotte",
    "cerise": "cerise",
    "citron": "citron",
    "CITROUILLE": "citrouille",
    "COING": "coing",
    "CONCOMBRE": "concombre",
    "COURGETTE": "courgette",
    "DRAGON  copy": "pitaya_fruit_du_dragon",
    "FENOUIL": "fenouil",
    "FIGUE DE BARBARIE FRUIT": "figue_de_barbarie_fruit",
    "FIGUE DE": "figue",
    "fraise": "fraise",
    "GRENADE": "grenade",
    "HARICOT ROUGE": "haricot_rouge_legume",
    "HARICOT VERT": "haricot_vert_legume",
    "kiwano": "pitaya_fruit_du_dragon",
    "KIWI": "kiwi",
    "KRAFS": "celeri_krafs",
    "mandarine CC": "mandarine",
    "mandarine": "mandarine_wilki",
    "MMANDARINE": "mandarine_2",
    "MANGUE": "mangue_fruit",
    "MELON AFF": "melon",
    "melon cantaloupe": "melon_cantaloup",
    "NEFEL": "nefle",
    "OIGNON": "oignon",
    "orange": "orange_1er_choix",
    "pasteque": "pasteque",
    "PECH PLATE": "peche_plate",
    "peche": "peche",
    "poire copy2": "poire",
    "poire": "poire_2",
    "poivron": "poivron",
    "POIVRON ROUGE": "poivron_rouge",
    "PIMENT FORT": "piment_fort",
    "PIMENT FORT STARTEUR": "piment_fort_starteur",
    "PIMENT FORT   ROUGE copy": "piment_fort_2",
    "POMME JAUNE": "pomme_jaune",
    "POMME ROUGE": "pomme_rouge",
    "POMME ROUGE copy": "pomme_rouge",
    "POMME LITE": "pomme",
    "RAISON BLANC": "raisin_blanc",
    "RAISON NOIRE": "raisin",
    "RAISON ROUGE": "raisin_rouge",
    "RAISIN ROUGE AFF": "raisin_rouge",
    "tomate": "tomate",
    "TOMATE AFF": "tomate",
    "tomate copy cerise": "tomate",
}

SKIP_PSD = {
    "CREVETTE AFFICHAGE.psd",
    "SARDINE AFFICHAGE.psd",
    "CHATAIGNE.psd",
    "carde.psd",
    "FEVE.psd",
    "PETIT POIS.psd",
    "salades.psd",
    "TOPINAMBOUR.psd",
    "TCHWINA.psd",
    "PLAQUEMINE.psd",
    "Prune BLANC.psd",
    "Prune FORT copy.psd",
    "avocado.psd",
    "nectarine.psd",
    "navets.psd",
    "chau mauve.psd",
    "chau vert.psd",
    "chou vert.psd",
    "CHOUX VIOLET copy.psd",
    "CHOUX-FLEUR.psd",
    "olive vert.psd",
    "Pamplemousse  copy.psd",
    "patate douce a.psd",
    "POMME DE TERRE 1.psd",
    "POMME DE TERRE DOUCE.psd",
    "pomme de terre.psd",
    "poivron couleur.psd",
    "povron couleur.psd",
    "ARTICHAUT VERT.psd",
    "POMME ROUGE.psd",
    "RAISON ROUGE.psd",
    "kiwano.psd",
    "tomate.psd",
}

# CSV slugs without their own PSD → share exported asset from closest sibling
ASSET_ALIASES: dict[str, str] = {
    "banane_promo": "banane",
    "cerise_1ere_choix": "cerise",
    "figue_frais_gm": "figue",
    "figue_pm": "figue",
    "grenade_1er_choix": "grenade",
    "kiwi_fruit": "kiwi",
    "peche_gm": "peche",
    "orange_2eme_choix": "orange_1er_choix",
    "orange_p": "orange_1er_choix",
    "ail_vert": "ail",
    "ail_vert_2": "ail",
    "poivron_promo": "poivron",
}


def normalize_text(text: str) -> str:
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()
    return re.sub(r"\s+", " ", text)


def normalize_code(raw: str | None) -> str | None:
    if not raw:
        return None
    digits = re.sub(r"\D", "", raw)
    if not digits:
        return None
    return f"{int(digits):03d}"


def slugify(text: str) -> str:
    text = normalize_text(text)
    return re.sub(r"\s+", "_", text) or "article"


def parse_md_table(section_title: str) -> list[dict]:
    text = CSV_MD.read_text(encoding="utf-8")
    marker = f"## {section_title}"
    start = text.index(marker)
    rest = text[start + len(marker) :]
    end = rest.find("\n---\n")
    chunk = rest[:end] if end >= 0 else rest
    lines = [ln.strip() for ln in chunk.splitlines() if ln.strip().startswith("|") and ln.count("|") >= 5]
    rows = []
    for ln in lines[2:]:
        cols = [c.strip() for c in ln.strip("|").split("|")]
        if len(cols) < 5:
            continue
        des = cols[0]
        bc = cols[1] if cols[1] != "—" else ""
        rows.append({"designation": des, "barcode_suffix": normalize_code(bc) or "", "slug": slugify(des)})
    return rows


def dedupe_slugs(items: list[dict]) -> list[dict]:
    seen: dict[str, int] = {}
    out = []
    for it in items:
        slug = it["slug"]
        if slug in seen:
            seen[slug] += 1
            slug = f"{slug}_{seen[slug]}"
        else:
            seen[slug] = 1
        out.append({**it, "slug": slug})
    return out


@dataclass
class PsdInfo:
    path: Path
    stem: str
    code: str | None = None
    designation_hint: str | None = None
    product_layer: str | None = None
    width: int = 0
    height: int = 0


def extract_psd_info(path: Path) -> PsdInfo:
    psd = PSDImage.open(path)
    info = PsdInfo(path=path, stem=path.stem, width=psd.size[0], height=psd.size[1])
    best_area = 0
    for layer in psd:
        name = layer.name
        if layer.kind == "type":
            try:
                text = (layer.text or "").strip()
            except Exception:
                text = ""
            m = re.search(r"code\s*:\s*(\d+)", text, re.I)
            if m:
                info.code = normalize_code(m.group(1))
            elif text and text not in {"DA", "KG"} and not re.fullmatch(r"[\d\s.,]+", text):
                if not info.designation_hint or len(text) > len(info.designation_hint):
                    info.designation_hint = text.replace("\r", " ").strip()[:80]
        nl = name.lower()
        if layer.kind == "smartobject" and layer.bbox:
            l, t, r, b = layer.bbox
            area = max(0, r - l) * max(0, b - t)
            if t < psd.size[1] * 0.75 and area > best_area and "logo" not in nl:
                best_area = area
                info.product_layer = name
        if info.product_layer is None and nl in {"capture", "aaaaaaa"}:
            info.product_layer = name
    return info


def pick_product_image(psd: PSDImage, info: PsdInfo) -> Image.Image | None:
    if info.product_layer:
        for layer in psd:
            if layer.name == info.product_layer:
                img = layer.composite()
                if img:
                    return img.convert("RGBA")
    best = None
    best_area = 0
    for layer in psd:
        if layer.kind != "smartobject" or not layer.bbox:
            continue
        nl = layer.name.lower()
        if "logo" in nl or "fond" in nl:
            continue
        l, t, r, b = layer.bbox
        area = (r - l) * (b - t)
        if t < psd.size[1] * 0.8 and area > best_area:
            best_area = area
            best = layer
    if best:
        img = best.composite()
        if img:
            return img.convert("RGBA")
    flat = psd.composite()
    if flat:
        l, t, r, b = PHOTO_BBOX
        return flat.crop((l, max(0, t), r, b)).convert("RGBA")
    return None


def thumb(img: Image.Image, size: int = 128) -> Image.Image:
    im = img.convert("RGB")
    im.thumbnail((size, size), Image.Resampling.LANCZOS)
    return im


def histogram_similarity(a: Image.Image, b: Image.Image) -> float:
    ha = thumb(a).histogram()
    hb = thumb(b).histogram()
    if len(ha) != len(hb):
        return 0.0
    num = sum(x * y for x, y in zip(ha, hb))
    den_a = sum(x * x for x in ha) ** 0.5
    den_b = sum(y * y for y in hb) ** 0.5
    if den_a == 0 or den_b == 0:
        return 0.0
    return num / (den_a * den_b)


def find_companion_jpg(psd_path: Path) -> Path | None:
    stem_norm = normalize_text(psd_path.stem)
    best = None
    best_score = 0.0
    for jpg in psd_path.parent.glob("*.jpg"):
        jnorm = normalize_text(jpg.stem.replace(" copy", ""))
        score = SequenceMatcher(None, stem_norm, jnorm).ratio()
        if score > best_score:
            best_score = score
            best = jpg
    return best if best_score >= 0.55 else None


def score_match(psd: PsdInfo, article: dict, product_img: Image.Image | None) -> float:
    manual = MANUAL_PSD_TO_SLUG.get(psd.stem)
    if manual == article["slug"]:
        return 200.0

    score = 0.0
    if psd.code and article["barcode_suffix"] and psd.code == article["barcode_suffix"]:
        score += 80.0

    fn_sim = SequenceMatcher(None, normalize_text(psd.stem), normalize_text(article["designation"])).ratio()
    score += fn_sim * 40.0

    kw_sim = SequenceMatcher(None, normalize_text(psd.stem), article["slug"].replace("_", " ")).ratio()
    score += kw_sim * 20.0

    if product_img:
        jpg = find_companion_jpg(psd.path)
        if jpg:
            try:
                ref = Image.open(jpg)
                score += histogram_similarity(product_img, ref) * 25.0
            except Exception:
                pass

    return score


def assign_psds(articles: list[dict], psds: list[PsdInfo]) -> list[tuple[PsdInfo, dict, float, str]]:
    assignments: list[tuple[PsdInfo, dict, float, str]] = []
    used_psd: set[str] = set()
    used_slug: set[str] = set()

    for psd in psds:
        manual_slug = MANUAL_PSD_TO_SLUG.get(psd.stem)
        if not manual_slug:
            continue
        art = next((a for a in articles if a["slug"] == manual_slug), None)
        if art and psd.path.name not in used_psd and manual_slug not in used_slug:
            assignments.append((psd, art, 200.0, "manual"))
            used_psd.add(psd.path.name)
            used_slug.add(manual_slug)

    candidates: list[tuple[float, PsdInfo, dict, str]] = []
    for psd in psds:
        if psd.path.name in used_psd or psd.path.name in SKIP_PSD:
            continue
        try:
            psd_img = PSDImage.open(psd.path)
            product = pick_product_image(psd_img, psd)
        except Exception:
            product = None
        for art in articles:
            if art["slug"] in used_slug:
                continue
            s = score_match(psd, art, product)
            if s >= 55:
                method = "code+name" if psd.code == art["barcode_suffix"] else "name/visual"
                candidates.append((s, psd, art, method))

    candidates.sort(key=lambda x: -x[0])
    for score, psd, art, method in candidates:
        if psd.path.name in used_psd or art["slug"] in used_slug:
            continue
        assignments.append((psd, art, score, method))
        used_psd.add(psd.path.name)
        used_slug.add(art["slug"])

    return assignments


def export_product_png(psd: PsdInfo, slug: str) -> str:
    PRODUCTS_OUT.mkdir(parents=True, exist_ok=True)
    PREVIEWS_OUT.mkdir(parents=True, exist_ok=True)
    psd_img = PSDImage.open(psd.path)
    product = pick_product_image(psd_img, psd)
    if not product:
        raise RuntimeError(f"No product layer in {psd.path.name}")

    max_edge = 1800
    w, h = product.size
    if max(w, h) > max_edge:
        scale = max_edge / max(w, h)
        product = product.resize((int(w * scale), int(h * scale)), Image.Resampling.LANCZOS)

    rel = f"visiopro/fv_print/products/{slug}.png"
    product.save(PRODUCTS_OUT / f"{slug}.png", "PNG", optimize=True)

    flat = psd_img.composite()
    if flat:
        prev = flat.copy()
        prev.thumbnail((640, 480), Image.Resampling.LANCZOS)
        prev.save(PREVIEWS_OUT / f"{slug}.jpg", "JPEG", quality=88)

    return rel


def generate_kotlin(
    fruits: list[dict],
    vegetables: list[dict],
    asset_by_slug: dict[str, str],
    arabic: dict[str, str],
) -> None:
    lines = [
        "package com.oasismall.oasisai.domain.visiopro",
        "",
        "/**",
        " * Auto-generated from docs/VISIOPRO_CSV_ARTICLES.md + affichage PSD mapping.",
        f" * Generated: {date.today().isoformat()} — re-run scripts/map_visiopro_affichage_psd.py",
        " */",
        "object VisioProCsvArticles {",
        "",
        "    private fun article(",
        "        slug: String,",
        "        csvDesignation: String,",
        "        labelAr: String,",
        "        barcodeSuffix: String,",
        "        printProductAsset: String? = null,",
        "        vararg keywords: String,",
        "    ) = VisioProArticleDef(",
        "        slug = slug,",
        "        labelFr = csvDesignation,",
        "        designationKeywords = keywords.toList(),",
        "        csvDesignation = csvDesignation,",
        "        barcodeSuffix = barcodeSuffix.takeIf { it.isNotBlank() },",
        "        labelAr = labelAr.takeIf { it.isNotBlank() },",
        "        printProductAsset = printProductAsset,",
        "    )",
        "",
    ]

    def write_list(name: str, items: list[dict]) -> None:
        lines.append(f"    val {name}: List<VisioProArticleDef> = listOf(")
        for it in items:
            des = it["designation"].replace('"', '\\"')
            bc = it["barcode_suffix"]
            slug = it["slug"]
            ar = arabic.get(it["designation"], "").replace('"', '\\"')
            asset = asset_by_slug.get(slug)
            asset_arg = f'"{asset}"' if asset else "null"
            kw = it["designation"].lower().replace('"', '\\"')
            lines.append(f'        article("{slug}", "{des}", "{ar}", "{bc}", {asset_arg}, "{kw}"),')
        lines.append("    )")
        lines.append("")

    write_list("fruits", fruits)
    write_list("vegetables", vegetables)
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


def load_arabic_from_existing() -> dict[str, str]:
    arabic: dict[str, str] = {}
    if not OUT_KOTLIN.exists():
        return arabic
    text = OUT_KOTLIN.read_text(encoding="utf-8")
    for m in re.finditer(r'article\("[^"]+", "([^"]+)", "([^"]*)"', text):
        if m.group(2):
            arabic[m.group(1)] = m.group(2)
    return arabic


def write_map_report(assignments: list, unmatched_articles: list, unused_psds: list) -> None:
    lines = [
        "# VisioPRO — Affichage PSD mapping",
        "",
        f"Generated: {date.today().isoformat()}",
        "",
        f"Source folder: `{PSD_FOLDER}`",
        "",
        f"**Matched:** {len(assignments)} articles · **Unmatched CSV:** {len(unmatched_articles)} · **Unused PSDs:** {len(unused_psds)}",
        "",
        "## Matched (PSD → article → exported PNG)",
        "",
        "| PSD | Article | Code | Score | Method | Asset |",
        "|-----|---------|------|-------|--------|-------|",
    ]
    for psd, art, score, method in sorted(assignments, key=lambda x: x[1]["designation"]):
        lines.append(
            f"| `{psd.path.name}` | {art['designation']} | {art['barcode_suffix'] or '—'} | {score:.0f} | {method} | `{art['slug']}.png` |"
        )
    if unmatched_articles:
        lines.extend(["", "## CSV articles without PSD", ""])
        for a in unmatched_articles:
            lines.append(f"- {a['designation']} (`{a['slug']}`) code {a['barcode_suffix'] or '—'}")
    if unused_psds:
        lines.extend(["", "## PSD files not mapped to CSV fruits/légumes", ""])
        for p in unused_psds:
            lines.append(f"- `{p.path.name}` (code {p.code or '—'})")
    MAP_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    fruits = dedupe_slugs(parse_md_table("Fruits"))
    vegetables = dedupe_slugs(parse_md_table("Légumes"))
    articles = fruits + vegetables

    psds = [extract_psd_info(p) for p in sorted(PSD_FOLDER.glob("*.psd"))]
    assignments = assign_psds(articles, psds)
    asset_by_slug: dict[str, str] = {}
    map_entries = []

    for psd, art, score, method in assignments:
        rel = export_product_png(psd, art["slug"])
        asset_by_slug[art["slug"]] = rel
        map_entries.append(
            {
                "slug": art["slug"],
                "designation": art["designation"],
                "barcodeSuffix": art["barcode_suffix"],
                "psd": psd.path.name,
                "asset": rel,
                "score": score,
                "method": method,
            }
        )
        print(f"OK  {psd.path.name:35} → {art['designation']:30} ({method}, {score:.0f})")

    matched_slugs = {a[1]["slug"] for a in assignments}
    unmatched = [a for a in articles if a["slug"] not in matched_slugs]
    used_names = {a[0].path.name for a in assignments}
    unused = [p for p in psds if p.path.name not in used_names and p.path.name not in SKIP_PSD]

    MAP_JSON.parent.mkdir(parents=True, exist_ok=True)
    MAP_JSON.write_text(json.dumps(map_entries, indent=2, ensure_ascii=False), encoding="utf-8")

    for alias_slug, source_slug in ASSET_ALIASES.items():
        if source_slug in asset_by_slug and alias_slug not in asset_by_slug:
            src = PRODUCTS_OUT / f"{source_slug}.png"
            dst = PRODUCTS_OUT / f"{alias_slug}.png"
            if src.exists() and not dst.exists():
                dst.write_bytes(src.read_bytes())
                rel = f"visiopro/fv_print/products/{alias_slug}.png"
                asset_by_slug[alias_slug] = rel
                print(f"ALIAS {alias_slug} ← {source_slug}")

    generate_kotlin(fruits, vegetables, asset_by_slug, load_arabic_from_existing())
    write_map_report(assignments, unmatched, unused)

    print()
    print(f"Exported {len(asset_by_slug)} product PNGs → {PRODUCTS_OUT}")
    print(f"Map → {MAP_MD}")
    if unmatched:
        print(f"Unmatched ({len(unmatched)}):")
        for u in unmatched:
            print(f"  - {u['designation']}")


if __name__ == "__main__":
    main()
