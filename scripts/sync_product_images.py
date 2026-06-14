"""
Sync product_images/ PNGs to Gestium CSV articles.
- Rename matches to app protocol: NameNormalizer.toFileKey(designation).png
- Move unmatched PNGs to product_images/not found/
"""

from __future__ import annotations

import csv
import re
import shutil
import unicodedata
from dataclasses import dataclass
from pathlib import Path

from png_metadata import read_barcode, write_barcode


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def to_file_key(designation: str) -> str:
    return normalize(designation).replace(" ", "_")


BARCODE_RE = r"\d{8,18}"


def extract_barcode(stem: str) -> str | None:
    if re.fullmatch(BARCODE_RE, stem):
        return stem
    m = re.search(rf"_({BARCODE_RE})$", stem)
    return m.group(1) if m else None


def stem_without_barcode(stem: str) -> str:
    return re.sub(rf"_{BARCODE_RE}$", "", stem)


@dataclass
class Article:
    designation: str
    barcode: str
    normalized: str
    file_key: str


def load_articles(csv_path: Path) -> tuple[list[Article], dict[str, list[Article]], dict[str, list[Article]]]:
    articles: list[Article] = []
    by_barcode: dict[str, list[Article]] = {}
    by_normalized: dict[str, list[Article]] = {}

    with csv_path.open(encoding="latin-1", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            des = (row.get("Désignation") or "").strip()
            bc = (row.get("Code-barres") or "").strip()
            if not des or not bc or bc == "0":
                continue
            if "OASIS MALL" in des.upper():
                continue
            art = Article(
                designation=des,
                barcode=bc,
                normalized=normalize(des),
                file_key=to_file_key(des),
            )
            articles.append(art)
            by_barcode.setdefault(bc, []).append(art)
            by_normalized.setdefault(art.normalized, []).append(art)

    return articles, by_barcode, by_normalized


def find_match(
    stem: str,
    by_barcode: dict[str, list[Article]],
    by_normalized: dict[str, list[Article]],
) -> tuple[Article | None, str]:
    """Return (article, reason) or (None, reason)."""
    bc = extract_barcode(stem)
    if bc and bc in by_barcode:
        candidates = by_barcode[bc]
        if len(candidates) == 1:
            return candidates[0], "barcode"
        return None, f"ambiguous_barcode:{bc}"

    des_part = stem_without_barcode(stem)
    key_from_stem = normalize(des_part.replace("_", " "))

    if key_from_stem in by_normalized:
        candidates = by_normalized[key_from_stem]
        if len(candidates) == 1:
            return candidates[0], "designation_exact"
        return None, f"ambiguous_designation:{key_from_stem}"

    fuzzy: list[Article] = []
    for norm, group in by_normalized.items():
        if key_from_stem in norm or norm in key_from_stem:
            fuzzy.extend(group)

    seen: set[str] = set()
    unique: list[Article] = []
    for a in fuzzy:
        if a.barcode not in seen:
            seen.add(a.barcode)
            unique.append(a)

    if len(unique) == 1:
        return unique[0], "designation_fuzzy"
    if len(unique) > 1:
        return None, f"ambiguous_fuzzy:{len(unique)}"
    return None, "no_csv_match"


def safe_target_path(target_dir: Path, file_key: str, barcode: str) -> Path:
    base = target_dir / f"{file_key}.png"
    if not base.exists():
        return base
    alt = target_dir / f"{file_key}_{barcode}.png"
    if not alt.exists():
        return alt
    return target_dir / f"{file_key}_{barcode}_dup.png"


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    csv_path = root / "imports" / "gestium_articles_2026-05-24.csv"
    png_dir = root / "product_images"
    not_found_dir = png_dir / "not found"
    not_found_dir.mkdir(parents=True, exist_ok=True)

    articles, by_barcode, by_normalized = load_articles(csv_path)
    print(f"Loaded {len(articles)} articles from CSV")

    png_files = sorted(png_dir.glob("*.png"))
    print(f"Found {len(png_files)} PNGs in product_images/ (top level)")

    renamed = 0
    moved_not_found = 0
    skipped_already = 0
    collisions = 0

    used_targets: set[str] = set()

    for png in png_files:
        stem = png.stem
        article, reason = find_match(stem, by_barcode, by_normalized)

        if article is None:
            dest = not_found_dir / png.name
            if dest.exists():
                dest = not_found_dir / f"{stem}_orphan.png"
            shutil.move(str(png), str(dest))
            moved_not_found += 1
            continue

        target_name = f"{article.file_key}.png"
        if target_name in used_targets:
            target_name = f"{article.file_key}_{article.barcode}.png"
            collisions += 1

        target = png_dir / target_name
        if target.resolve() == png.resolve():
            if read_barcode(target) != article.barcode:
                write_barcode(target, article.barcode, article.designation)
            skipped_already += 1
            used_targets.add(target_name)
            continue

        if target.exists():
            extra = not_found_dir / f"{stem}_duplicate_target.png"
            shutil.move(str(png), str(extra))
            moved_not_found += 1
            continue

        shutil.move(str(png), str(target))
        write_barcode(target, article.barcode, article.designation)
        used_targets.add(target_name)
        renamed += 1

    remaining = list(png_dir.glob("*.png"))
    not_found_count = len(list(not_found_dir.glob("*.png")))

    print()
    print("=== Sync complete ===")
    print(f"Renamed & linked: {renamed}")
    print(f"Already correct name: {skipped_already}")
    print(f"Filename collisions (barcode suffix): {collisions}")
    print(f"Moved to not found/: {moved_not_found}")
    print(f"Remaining in product_images/: {len(remaining)}")
    print(f"Total in not found/: {not_found_count}")
    if remaining:
        print("Sample remaining:", [p.name for p in remaining[:5]])


if __name__ == "__main__":
    main()
