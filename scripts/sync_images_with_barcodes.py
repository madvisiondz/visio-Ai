"""
One command: match PNGs to Gestium CSV, rename to app protocol, embed barcode in file Details.

Usage (from project root or scripts/):
  python scripts/sync_images_with_barcodes.py

Put PNGs in product_images/ first (any names). Requires:
  imports/gestium_articles_2026-05-24.csv
"""

from __future__ import annotations

import struct
import subprocess
import sys
from pathlib import Path

PNG_SIG = b"\x89PNG\r\n\x1a\n"


def count_valid_pngs(folder: Path) -> tuple[int, int]:
    total = 0
    valid = 0
    for p in folder.glob("*.png"):
        total += 1
        data = p.read_bytes()
        if not data.startswith(PNG_SIG):
            continue
        offset = 8
        ok = False
        while offset + 12 <= len(data):
            length = int.from_bytes(data[offset : offset + 4], "big")
            chunk_type = data[offset + 4 : offset + 8]
            if chunk_type == b"IEND":
                ok = True
                break
            if length < 0 or length > 50_000_000:
                break
            offset += 12 + length
        if ok:
            valid += 1
    return total, valid


def main() -> int:
    scripts_dir = Path(__file__).resolve().parent
    root = scripts_dir.parent
    png_dir = root / "product_images"

    if not png_dir.is_dir():
        print(f"Create folder and add PNGs: {png_dir}")
        return 1

    total, valid = count_valid_pngs(png_dir)
    print(f"Found {total} PNGs in product_images/ ({valid} valid, {total - valid} corrupt)")
    if total == 0:
        print("Add your image files first, then run again.")
        return 1
    if valid == 0:
        print("All PNGs are corrupt. Restore originals (OneDrive version history) before running.")
        return 1

    print("\n--- Step 1: Match + rename + embed (sync_product_images.py) ---")
    r1 = subprocess.run([sys.executable, str(scripts_dir / "sync_product_images.py")], check=False)
    if r1.returncode != 0:
        return r1.returncode

    print("\n--- Step 2: Embed barcodes on all valid renamed files ---")
    r2 = subprocess.run([sys.executable, str(scripts_dir / "embed_all_product_images.py")], check=False)
    if r2.returncode != 0:
        return r2.returncode

    total2, valid2 = count_valid_pngs(png_dir)
    unmatched_dir = png_dir / "not found"
    unmatched_total, unmatched_valid = count_valid_pngs(unmatched_dir) if unmatched_dir.is_dir() else (0, 0)
    print(f"\n=== Done ===")
    print(f"Valid PNGs remaining: {valid2}/{total2}")
    print(f"Valid unmatched PNGs in not found/: {unmatched_valid}/{unmatched_total}")
    if valid2 + unmatched_valid < valid:
        print("WARNING: some files became invalid — report this immediately.")
    else:
        print("Safe to copy product_images/ to the phone. Review not found/ for unmatched files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
