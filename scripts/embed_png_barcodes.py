"""
Write barcode into PNG file Details (tEXt Description + Barcode) for all linked product_images/.
Run after sync_product_images.py or when metadata was lost on rename.
"""

from __future__ import annotations

import csv
import re
import unicodedata
from pathlib import Path

from png_metadata import read_barcode_from_chunks, write_barcode

BARCODE_SUFFIX = re.compile(r"_(\d{8,18})$")
BARCODE_ONLY = re.compile(r"^\d{8,18}$")


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def to_file_key(designation: str) -> str:
    return normalize(designation).replace(" ", "_")


def load_by_file_key(csv_path: Path) -> dict[str, list[tuple[str, str]]]:
    """file_key -> list of (barcode, designation)"""
    out: dict[str, list[tuple[str, str]]] = {}
    with csv_path.open(encoding="latin-1", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            des = (row.get("Désignation") or "").strip()
            bc = (row.get("Code-barres") or "").strip()
            if not des or not bc or bc == "0":
                continue
            if "OASIS MALL" in des.upper():
                continue
            key = to_file_key(des)
            out.setdefault(key, []).append((bc, des))
    return out


def resolve_article(
    stem: str, by_key: dict[str, list[tuple[str, str]]]
) -> tuple[str, str] | None:
    if BARCODE_ONLY.fullmatch(stem):
        for group in by_key.values():
            for b, d in group:
                if b == stem:
                    return b, d
    bc_suffix = BARCODE_SUFFIX.search(stem)
    if bc_suffix:
        bc = bc_suffix.group(1)
        key = normalize(stem[: bc_suffix.start()].replace("_", " "))
        for file_key, group in by_key.items():
            if normalize(file_key.replace("_", " ")) == key or file_key == stem[: bc_suffix.start()]:
                for b, d in group:
                    if b == bc:
                        return b, d
        return bc, stem.replace("_", " ")

    key = stem
    group = by_key.get(key)
    if not group:
        norm = normalize(stem.replace("_", " "))
        for fk, g in by_key.items():
            if normalize(fk.replace("_", " ")) == norm:
                group = g
                break
    if not group:
        return None
    if len(group) == 1:
        return group[0]
    return None


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    csv_path = root / "imports" / "gestium_articles_2026-05-24.csv"
    png_dir = root / "product_images"
    by_key = load_by_file_key(csv_path)

    tagged = 0
    skipped = 0
    unknown = 0
    retagged = 0

    for png in sorted(png_dir.glob("*.png")):
        resolved = resolve_article(png.stem, by_key)
        if not resolved:
            unknown += 1
            continue
        bc, des = resolved
        had_chunks = read_barcode_from_chunks(png) is not None
        write_barcode(png, bc, des)
        if had_chunks:
            retagged += 1
        else:
            tagged += 1

    print(f"Newly tagged: {tagged}")
    print(f"Updated existing tags: {retagged}")
    print(f"Could not resolve from CSV: {unknown}")


if __name__ == "__main__":
    main()
