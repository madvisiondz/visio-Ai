"""
Embed Gestium barcodes into all valid product_images/*.png files.
Resolves collision filenames (DESIGNATION_BARCODE.png) via CSV.
"""

from __future__ import annotations

import csv
import re
import struct
import unicodedata
from collections import defaultdict
from pathlib import Path

from png_metadata import read_barcode_from_chunks, write_barcode

PNG_SIG = b"\x89PNG\r\n\x1a\n"
BARCODE_SUFFIX = re.compile(r"_(\d{8,18})$")
BARCODE_ONLY = re.compile(r"^\d{8,18}$")


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


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def to_file_key(designation: str) -> str:
    return normalize(designation).replace(" ", "_")


def load_csv_maps(csv_path: Path) -> tuple[dict[str, tuple[str, str]], dict[str, tuple[str, str]]]:
    """by_target filename, by_barcode."""
    groups: dict[str, list[tuple[str, str]]] = defaultdict(list)
    by_barcode: dict[str, tuple[str, str]] = {}

    with csv_path.open(encoding="latin-1", newline="") as fh:
        for row in csv.DictReader(fh):
            des = (row.get("Désignation") or "").strip()
            bc = (row.get("Code-barres") or "").strip()
            if not des or not bc or bc == "0":
                continue
            if "OASIS MALL" in des.upper():
                continue
            key = to_file_key(des)
            groups[key].append((bc, des))
            by_barcode[bc] = (bc, des)

    by_target: dict[str, tuple[str, str]] = {}
    for key, items in groups.items():
        if len(items) == 1:
            by_target[f"{key}.png"] = items[0]
        else:
            for bc, des in items:
                by_target[f"{key}_{bc}.png"] = (bc, des)

    return by_target, by_barcode


def resolve_png(
    path: Path,
    by_target: dict[str, tuple[str, str]],
    by_barcode: dict[str, tuple[str, str]],
) -> tuple[str, str] | None:
    if path.name in by_target:
        return by_target[path.name]
    bc = path.stem if BARCODE_ONLY.fullmatch(path.stem) else None
    m = BARCODE_SUFFIX.search(path.stem)
    if bc is None and m:
        bc = m.group(1)
    if bc and bc in by_barcode:
        return by_barcode[bc]
    return None


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    csv_path = root / "imports" / "gestium_articles_2026-05-24.csv"
    png_dir = root / "product_images"

    by_target, by_barcode = load_csv_maps(csv_path)

    embedded = 0
    skipped_ok = 0
    corrupt = 0
    unknown = 0

    for png in sorted(png_dir.glob("*.png")):
        if not is_valid_png(png):
            corrupt += 1
            continue
        info = resolve_png(png, by_target, by_barcode)
        if not info:
            unknown += 1
            continue
        bc, des = info
        if read_barcode_from_chunks(png) == bc:
            skipped_ok += 1
            continue
        write_barcode(png, bc, des)
        embedded += 1

    meta = sum(1 for p in png_dir.glob("*.png") if is_valid_png(p) and read_barcode_from_chunks(p))

    print("=== Embed all (valid PNGs only) ===")
    print(f"Embedded/updated: {embedded}")
    print(f"Already tagged: {skipped_ok}")
    print(f"Corrupt (need restore): {corrupt}")
    print(f"Unknown filename: {unknown}")
    print(f"Valid PNGs with barcode in Details: {meta}")


if __name__ == "__main__":
    main()
