"""
Recover corrupted product_images PNGs from product_images/not found/ when possible,
then embed barcode metadata (fixed writer).
"""

from __future__ import annotations

import csv
import re
import shutil
import struct
import unicodedata
from pathlib import Path

from png_metadata import read_barcode_from_chunks, write_barcode

PNG_SIG = b"\x89PNG\r\n\x1a\n"


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
    s = re.sub(r"\s+", " ", s).strip()
    return s


def to_file_key(designation: str) -> str:
    return normalize(designation).replace(" ", "_")


def load_articles(csv_path: Path) -> dict[str, tuple[str, str]]:
    """target filename -> (barcode, designation)"""
    by_target: dict[str, tuple[str, str]] = {}
    counts: dict[str, int] = {}

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
            counts[key] = counts.get(key, 0) + 1
            target = f"{key}.png" if counts[key] == 1 else f"{key}_{bc}.png"
            by_target[target] = (bc, des)
    return by_target


def index_not_found(not_found_dir: Path) -> dict[str, list[Path]]:
    """normalized stem -> candidate source PNGs (valid only)."""
    index: dict[str, list[Path]] = {}
    for png in not_found_dir.glob("*.png"):
        if not is_valid_png(png):
            continue
        stem = png.stem
        stem = re.sub(r"_(duplicate_target|orphan|dup)$", "", stem)
        stem = re.sub(r"_\d{8,18}$", "", stem)
        norm = normalize(stem.replace("_", " "))
        index.setdefault(norm, []).append(png)
    return index


def find_recovery_source(
    target_name: str,
    nf_index: dict[str, list[Path]],
    by_target: dict[str, tuple[str, str]],
) -> Path | None:
    stem = Path(target_name).stem
    norm = normalize(stem.replace("_", " ").replace("_", " "))
    # strip barcode suffix from target
    norm = normalize(re.sub(r"_\d{8,18}$", "", stem).replace("_", " "))
    candidates = nf_index.get(norm, [])
    if not candidates:
        return None
    if len(candidates) == 1:
        return candidates[0]
    info = by_target.get(target_name)
    if not info:
        return candidates[0]
    bc, _ = info
    for c in candidates:
        if bc in c.stem:
            return c
    return candidates[0]


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    csv_path = root / "imports" / "gestium_articles_2026-05-24.csv"
    png_dir = root / "product_images"
    not_found_dir = png_dir / "not found"

    by_target = load_articles(csv_path)
    nf_index = index_not_found(not_found_dir) if not_found_dir.exists() else {}

    recovered = 0
    embedded = 0
    already_ok = 0
    still_corrupt = 0
    unknown = 0

    for target in sorted(png_dir.glob("*.png")):
        info = by_target.get(target.name)
        if not info:
            unknown += 1
            continue

        bc, des = info
        valid = is_valid_png(target)

        if not valid:
            src = find_recovery_source(target.name, nf_index, by_target)
            if src:
                shutil.copy2(src, target)
                recovered += 1
                valid = True
            else:
                still_corrupt += 1
                continue

        if read_barcode_from_chunks(target) == bc:
            already_ok += 1
            continue

        write_barcode(target, bc, des)
        embedded += 1

    print("=== Recover + embed ===")
    print(f"Recovered from not found/: {recovered}")
    print(f"Embedded/updated metadata: {embedded}")
    print(f"Already correct metadata: {already_ok}")
    print(f"Still corrupt (no source): {still_corrupt}")
    print(f"No CSV match for filename: {unknown}")

    valid_final = sum(1 for p in png_dir.glob("*.png") if is_valid_png(p))
    meta_final = sum(
        1 for p in png_dir.glob("*.png") if read_barcode_from_chunks(p)
    )
    print(f"Valid PNGs now: {valid_final}")
    print(f"With barcode in file Details: {meta_final}")


if __name__ == "__main__":
    main()
