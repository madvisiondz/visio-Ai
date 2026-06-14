"""
Update PNG metadata in IMAGE ASSETS from a Gestium articles CSV feed.
Keeps filenames unchanged; writes Barcode, Codeart, PriceNow, Rayon, Designation
into tEXt chunks (Windows Properties + Oasis AI / Oasis Print).
"""

from __future__ import annotations

import argparse
import csv
import re
import shutil
import struct
import unicodedata
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

from png_metadata import read_barcode_from_chunks, write_article_details

PNG_SIG = b"\x89PNG\r\n\x1a\n"
BARCODE_RE = r"\d{8,18}"
BARCODE_SUFFIX = re.compile(rf"_({BARCODE_RE})$")
BARCODE_ONLY = re.compile(rf"^{BARCODE_RE}$")

DEFAULT_CSV = Path(r"d:\oasis project\articles feed 31-5-2026.csv")
DEFAULT_IMAGES = Path(r"d:\oasis project\IMAGE ASSETS")


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def to_file_key(designation: str) -> str:
    return normalize(designation).replace(" ", "_")


def parse_price(raw: str | None) -> float | None:
    if not raw:
        return None
    s = raw.strip().replace(" ", "").replace(",", ".")
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def is_valid_png(path: Path) -> bool:
    data = path.read_bytes()
    if not data.startswith(PNG_SIG):
        return False
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        if chunk_type == b"IEND":
            return True
        if length < 0 or length > 50_000_000:
            return False
        offset += 12 + length
    return False


def extract_barcode_from_stem(stem: str) -> str | None:
    if BARCODE_ONLY.fullmatch(stem):
        return stem
    m = BARCODE_SUFFIX.search(stem)
    return m.group(1) if m else None


def stem_without_barcode(stem: str) -> str:
    return BARCODE_SUFFIX.sub("", stem)


@dataclass
class ArticleRow:
    designation: str
    barcode: str
    codeart: str
    rayon: str
    price_ttc: float | None
    normalized: str
    file_key: str


def _price_column(row: dict[str, str]) -> str | None:
    for key, value in row.items():
        if key and "prix" in key.lower() and "vente" in key.lower() and "ttc" in key.lower():
            return value
    return row.get("Prix de vente  TTC") or row.get("Prix de vente TTC")


def load_articles(
    csv_path: Path,
) -> tuple[dict[str, list[ArticleRow]], dict[str, list[ArticleRow]], dict[str, ArticleRow]]:
    by_normalized: dict[str, list[ArticleRow]] = {}
    by_barcode: dict[str, list[ArticleRow]] = {}
    groups: dict[str, list[ArticleRow]] = defaultdict(list)

    with csv_path.open(encoding="latin-1", newline="") as fh:
        for row in csv.DictReader(fh):
            des = (row.get("Désignation") or "").strip()
            bc = (row.get("Code-barres") or "").strip()
            code = (row.get("Code") or "").strip()
            rayon = (row.get("Rayon") or "").strip()
            if not des or not bc or bc == "0":
                continue
            if "OASIS MALL" in des.upper():
                continue

            art = ArticleRow(
                designation=des,
                barcode=bc,
                codeart=code,
                rayon=rayon,
                price_ttc=parse_price(_price_column(row)),
                normalized=normalize(des),
                file_key=to_file_key(des),
            )
            by_normalized.setdefault(art.normalized, []).append(art)
            by_barcode.setdefault(bc, []).append(art)
            groups[art.file_key].append(art)

    by_filename: dict[str, ArticleRow] = {}
    for key, items in groups.items():
        if len(items) == 1:
            by_filename[f"{key}.png"] = items[0]
        else:
            for art in items:
                by_filename[f"{key}_{art.barcode}.png"] = art

    return by_normalized, by_barcode, by_filename


def find_match(
    path: Path,
    by_normalized: dict[str, list[ArticleRow]],
    by_barcode: dict[str, list[ArticleRow]],
    by_filename: dict[str, ArticleRow],
) -> tuple[ArticleRow | None, str]:
    stem = path.stem

    bc_meta = read_barcode_from_chunks(path)
    if bc_meta and bc_meta in by_barcode:
        group = by_barcode[bc_meta]
        if len(group) == 1:
            return group[0], "metadata_barcode"

    bc_stem = extract_barcode_from_stem(stem)
    if bc_stem and bc_stem in by_barcode:
        group = by_barcode[bc_stem]
        if len(group) == 1:
            return group[0], "filename_barcode"

    if path.name in by_filename:
        return by_filename[path.name], "filename_exact"

    des_part = stem_without_barcode(stem)
    fk_name = f"{to_file_key(des_part)}.png"
    if fk_name in by_filename:
        return by_filename[fk_name], "file_key_converted"

    key = normalize(des_part.replace("_", " "))
    if key in by_normalized:
        group = by_normalized[key]
        if len(group) == 1:
            return group[0], "designation_exact"
        return None, f"ambiguous_designation:{len(group)}"

    fuzzy: list[ArticleRow] = []
    for norm, group in by_normalized.items():
        if key in norm or norm in key:
            fuzzy.extend(group)
    seen: set[str] = set()
    unique: list[ArticleRow] = []
    for art in fuzzy:
        if art.barcode not in seen:
            seen.add(art.barcode)
            unique.append(art)
    if len(unique) == 1:
        return unique[0], "designation_fuzzy"
    if len(unique) > 1:
        return None, f"ambiguous_fuzzy:{len(unique)}"
    return None, "no_csv_match"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--images", type=Path, default=DEFAULT_IMAGES)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.csv.is_file():
        raise SystemExit(f"CSV not found: {args.csv}")
    if not args.images.is_dir():
        raise SystemExit(f"Image folder not found: {args.images}")

    repo_imports = Path(__file__).resolve().parents[1] / "imports"
    repo_imports.mkdir(parents=True, exist_ok=True)
    archive_csv = repo_imports / "articles_feed_2026-05-31.csv"
    if not archive_csv.exists() or archive_csv.stat().st_mtime < args.csv.stat().st_mtime:
        shutil.copy2(args.csv, archive_csv)
        print(f"Archived CSV copy: {archive_csv}")

    by_normalized, by_barcode, by_filename = load_articles(args.csv)
    article_count = sum(len(v) for v in by_normalized.values())
    print(f"Loaded {article_count} articles from {args.csv.name}")

    png_files = sorted(args.images.glob("*.png"))
    print(f"Found {len(png_files)} PNGs in {args.images}")

    updated = 0
    corrupt = 0
    unmatched: list[tuple[str, str]] = []

    for png in png_files:
        if not is_valid_png(png):
            corrupt += 1
            continue
        art, reason = find_match(png, by_normalized, by_barcode, by_filename)
        if art is None:
            unmatched.append((png.name, reason))
            continue
        if args.dry_run:
            updated += 1
            continue
        write_article_details(
            png,
            barcode=art.barcode,
            designation=art.designation,
            codeart=art.codeart,
            price_now=art.price_ttc,
            rayon=art.rayon,
        )
        updated += 1

    report_path = args.images / "_metadata_update_report.txt"
    if not args.dry_run:
        lines = [
            f"CSV: {args.csv}",
            f"Updated metadata: {updated}",
            f"Corrupt PNG: {corrupt}",
            f"Unmatched: {len(unmatched)}",
            "",
        ]
        if unmatched:
            lines.append("--- Unmatched files ---")
            lines.extend(f"{name}\t{reason}" for name, reason in unmatched[:500])
            if len(unmatched) > 500:
                lines.append(f"... and {len(unmatched) - 500} more")
        report_path.write_text("\n".join(lines), encoding="utf-8")

    print()
    print("=== IMAGE ASSETS metadata update ===")
    print(f"Updated: {updated}" + (" (dry-run)" if args.dry_run else ""))
    print(f"Corrupt: {corrupt}")
    print(f"Unmatched: {len(unmatched)}")
    if not args.dry_run:
        print(f"Report: {report_path}")
    if unmatched[:8]:
        sample = ", ".join(unmatched[i][0] for i in range(min(8, len(unmatched))))
        print(f"Sample unmatched: {sample}")


if __name__ == "__main__":
    main()
