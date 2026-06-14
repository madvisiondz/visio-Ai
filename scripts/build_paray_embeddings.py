"""
Option A — bulk PARAY fingerprints on PC ($0).

Walks linked product_images/*.png, runs CLIP ViT-B/32 (ONNX), and writes
exports/paray/paray_fingerprint_index.json for import into the Android app.

Usage:
  python scripts/build_paray_embeddings.py
  python scripts/build_paray_embeddings.py --limit 20
  python scripts/build_paray_embeddings.py --include-not-found
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import time
import unicodedata
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from png_metadata import read_barcode
from paray_clip import (
    EMBED_DIM,
    _NUMPY_OK,
    _ONNX_OK,
    backend_name,
    composite_on_white,
    create_session,
    embed_image,
    ensure_model,
    extract_visual_features,
    is_valid_png,
    load_rgba,
)

BARCODE_SUFFIX = re.compile(r"_(\d{8,18})$")
BARCODE_ONLY = re.compile(r"^\d{8,18}$")


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def load_csv_by_barcode(csv_path: Path) -> dict[str, str]:
    by_barcode: dict[str, str] = {}
    with csv_path.open(encoding="latin-1", newline="") as fh:
        for row in csv.DictReader(fh):
            des = (row.get("Désignation") or "").strip()
            bc = (row.get("Code-barres") or "").strip()
            if not des or not bc or bc == "0":
                continue
            if "OASIS MALL" in des.upper():
                continue
            by_barcode[bc] = des
    return by_barcode


def resolve_designation(path: Path, barcode: str, by_barcode: dict[str, str]) -> str:
    if barcode in by_barcode:
        return by_barcode[barcode]
    stem = path.stem
    if BARCODE_SUFFIX.search(stem):
        key = BARCODE_SUFFIX.sub("", stem).replace("_", " ")
        return key
    return stem.replace("_", " ")


def typography(designation: str) -> tuple[int, int]:
    words = [w for w in designation.strip().split() if w]
    return len(words), len(designation.strip())


def iter_pngs(png_dir: Path, include_not_found: bool) -> list[Path]:
    paths = sorted(png_dir.glob("*.png"))
    if include_not_found:
        nf = png_dir / "not found"
        if nf.is_dir():
            paths.extend(sorted(nf.glob("*.png")))
    return paths


def merge_entries(existing: dict, fresh: dict) -> dict:
    n = existing.get("observationCount", 1)
    m = fresh.get("observationCount", 1)
    total = n + m

    def blend(a: float, b: float) -> float:
        return (a * n + b * m) / total

    emb_a = existing["embedding"]
    emb_b = fresh["embedding"]
    merged_emb = [(a * n + b * m) / total for a, b in zip(emb_a, emb_b)]
    norm = sum(x * x for x in merged_emb) ** 0.5
    if norm > 1e-8:
        merged_emb = [x / norm for x in merged_emb]

    colors = list(dict.fromkeys(existing["dominantColors"] + fresh["dominantColors"]))[:3]
    return {
        **fresh,
        "shapeAspect": round(blend(existing["shapeAspect"], fresh["shapeAspect"]), 4),
        "fillRatio": round(blend(existing["fillRatio"], fresh["fillRatio"]), 4),
        "dominantColors": colors,
        "embedding": [round(x, 6) for x in merged_emb],
        "observationCount": total,
        "imageFileName": fresh["imageFileName"],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Build PARAY CLIP fingerprint index")
    parser.add_argument("--limit", type=int, default=0, help="Process only N PNGs (0 = all)")
    parser.add_argument("--include-not-found", action="store_true", help="Also fingerprint not found/")
    parser.add_argument(
        "--csv",
        type=Path,
        default=None,
        help="Gestium CSV (default: imports/articles_feed_2026-05-31.csv)",
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    scripts_dir = Path(__file__).resolve().parent
    png_dir = root / "product_images"
    out_dir = root / "exports" / "paray"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "paray_fingerprint_index.json"

    csv_path = args.csv or (root / "imports" / "articles_feed_2026-05-31.csv")
    if not csv_path.exists():
        csv_path = root / "imports" / "gestium_articles_2026-05-24.csv"

    by_barcode = load_csv_by_barcode(csv_path)
    model_id = backend_name()
    if _ONNX_OK:
        onnx_path = ensure_model(scripts_dir)
        session = create_session(onnx_path)
        print(f"Backend: CLIP ONNX ({model_id})")
    else:
        session = None
        reason = "numpy blocked" if not _NUMPY_OK else "onnxruntime missing"
        print(f"Backend: PARAY lite embedder ({model_id}) - {reason}")
        print("Tip: allow Python DLLs in Windows Security to unlock full CLIP.")

    pngs = iter_pngs(png_dir, args.include_not_found)
    if args.limit > 0:
        pngs = pngs[: args.limit]

    by_key: dict[str, dict] = {}
    stats = {
        "processed": 0,
        "skipped_corrupt": 0,
        "skipped_no_barcode": 0,
        "merged_duplicates": 0,
    }
    t0 = time.perf_counter()

    try:
        from tqdm import tqdm

        iterator = tqdm(pngs, desc="PARAY fingerprints", unit="png")
    except ImportError:
        iterator = pngs

    for path in iterator:
        if not is_valid_png(path):
            stats["skipped_corrupt"] += 1
            continue
        barcode = read_barcode(path)
        if not barcode:
            stats["skipped_no_barcode"] += 1
            continue

        rgba = load_rgba(path)
        rgb = composite_on_white(rgba)
        embedding = embed_image(session, rgb, rgba)
        visual = extract_visual_features(rgba)
        designation = resolve_designation(path, barcode, by_barcode)
        words, chars = typography(designation)

        entry = {
            "barcode": barcode,
            "designation": designation,
            "imageFileName": path.name,
            "shapeAspect": visual["shapeAspect"],
            "fillRatio": visual["fillRatio"],
            "dominantColors": visual["dominantColors"],
            "designationWordCount": words,
            "designationCharCount": chars,
            "templateId": "shelf_10up",
            "observationCount": 1,
            "embedding": embedding,
        }

        if barcode in by_key:
            by_key[barcode] = merge_entries(by_key[barcode], entry)
            stats["merged_duplicates"] += 1
        else:
            by_key[barcode] = entry
        stats["processed"] += 1

    entries = sorted(by_key.values(), key=lambda e: e["designation"])
    payload = {
        "version": 1,
        "agent": "PARAY",
        "model": model_id,
        "dim": EMBED_DIM,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": "product_images",
        "csv": csv_path.name,
        "stats": stats,
        "count": len(entries),
        "entries": entries,
    }

    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    elapsed = time.perf_counter() - t0
    size_mb = out_path.stat().st_size / (1024 * 1024)

    print("\n=== PARAY fingerprint build ===")
    print(f"Output: {out_path}")
    print(f"Entries: {len(entries)}")
    print(f"Processed PNGs: {stats['processed']}")
    print(f"Corrupt skipped: {stats['skipped_corrupt']}")
    print(f"No barcode skipped: {stats['skipped_no_barcode']}")
    print(f"Duplicate barcodes merged: {stats['merged_duplicates']}")
    print(f"File size: {size_mb:.1f} MB")
    print(f"Elapsed: {elapsed / 60:.1f} min")
    print("\nCopy to phone: Settings > Import PARAY fingerprints > pick this JSON file.")


if __name__ == "__main__":
    main()
