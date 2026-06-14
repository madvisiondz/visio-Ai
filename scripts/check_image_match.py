import csv
import re
import unicodedata
from pathlib import Path


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    csv_path = root / "imports" / "gestium_articles_2026-05-24.csv"
    png_dir = root / "product_images"

    indexed: dict[str, list[str]] = {}
    for f in png_dir.glob("*.png"):
        key = normalize(f.stem)
        indexed.setdefault(key, []).append(f.name)

    articles: list[str] = []
    with csv_path.open(encoding="latin-1", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            des = (row.get("Désignation") or "").strip()
            bc = (row.get("Code-barres") or "").strip()
            if not des or not bc or bc == "0":
                continue
            if "OASIS MALL" in des.upper():
                continue
            articles.append(normalize(des))

    found = multiple = missing = 0
    for name in articles:
        exact = indexed.get(name, [])
        if exact:
            found += 1
            if len(exact) > 1:
                multiple += 1
            continue
        matches = [files for key, files in indexed.items() if name in key or key in name]
        flat = list(dict.fromkeys(f for sub in matches for f in sub))
        if not flat:
            missing += 1
        else:
            found += 1
            if len(flat) > 1:
                multiple += 1

    png_count = len(list(png_dir.glob("*.png")))
    print(f"CSV articles with barcode: {len(articles)}")
    print(f"PNG files: {png_count}")
    print(f"Would match (ImageMatcher logic): {found}")
    print(f"Multiple matches: {multiple}")
    print(f"Missing image: {missing}")
    print(f"Match rate: {found / len(articles) * 100:.1f}%")

    checks = ["SKOR SUCRE CEVITAL 1KG", "1001 ESPRESSO CAFE 250G", "7 UP 2L"]
    print("\nSpot checks:")
    for c in checks:
        n = normalize(c)
        exact = indexed.get(n, [])
        fuzzy = [files for key, files in indexed.items() if n in key or key in n]
        flat = list(dict.fromkeys(f for sub in fuzzy for f in sub))
        print(f"  {c!r} -> exact={len(exact)} fuzzy={len(flat)} sample={flat[:2]}")


if __name__ == "__main__":
    main()
