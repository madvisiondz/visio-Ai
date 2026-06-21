#!/usr/bin/env python3
"""Mirror CsvParser.kt skip logic on Gestium export — report why rows fail."""
import csv
import re
import unicodedata
from collections import Counter
from pathlib import Path

CSV = Path(r"c:\Users\Oasis-Mall\Desktop\19-06-26 10h MATIN.csv")


def normalize_header(header: str) -> str:
    s = unicodedata.normalize("NFD", header.replace('"', ""))
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    s = re.sub(r"\s+", " ", s).strip().lower()
    return s


def parse_french_number(raw: str | None) -> float | None:
    if not raw:
        return None
    cleaned = (
        raw.replace('"', "")
        .replace("\u00a0", " ")
        .replace(" ", "")
        .replace(",", ".")
    )
    cleaned = "".join(c for c in cleaned if c.isdigit() or c in ".-")
    if not cleaned or cleaned in (".", "-"):
        return None
    try:
        return float(cleaned)
    except ValueError:
        return None


def build_column_map(headers: list[str]) -> dict[str, int]:
    barcode_headers = {
        "code-barres", "code barres", "code_barres", "barcode", "code_barre", "ean", "gtin", "code barre",
    }
    designation_headers = {
        "designation", "désignation", "libelle", "libellé", "name", "article", "description", "nom",
    }

    def find_index(candidates: set[str]) -> int | None:
        for i, header in enumerate(headers):
            if any(header == c or c in header for c in candidates):
                return i
        return None

    def find_codeart() -> int | None:
        for i, header in enumerate(headers):
            if header in ("code", "codeart") or "code art" in header or header == "code article":
                return i
        return None

    def find_price() -> int | None:
        for i, header in enumerate(headers):
            if "prix de vente" in header and "ttc" in header:
                return i
        for i, header in enumerate(headers):
            if "prix de vente" in header and "ht" in header:
                return i
        for i, header in enumerate(headers):
            if "prix de vente" in header or header == "prix":
                return i
        return None

    m: dict[str, int] = {}
    if (idx := find_index(barcode_headers)) is not None:
        m["barcode"] = idx
    if (idx := find_index(designation_headers)) is not None:
        m["designation"] = idx
    if (idx := find_price()) is not None:
        m["price"] = idx
    if (idx := find_codeart()) is not None:
        m["codeart"] = idx
    for i, header in enumerate(headers):
        if header == "rayon":
            m["rayon"] = i
        if header == "reference" or header == "ref":
            m["reference"] = i
    return m


def resolve_barcode(barcode: str, codeart: str | None, designation: str) -> str:
    if barcode.strip():
        return barcode.strip()
    code = (codeart or "").strip()
    if code:
        return f"CA:{code}"
    norm = normalize_header(designation).upper()
    return f"DN:{norm.replace(' ', '_')}" if norm else ""


def analyze(charset: str) -> None:
    raw = CSV.read_bytes()
    try:
        text = raw.decode(charset)
    except UnicodeDecodeError:
        text = raw.decode(charset, errors="replace")
    rows = list(csv.reader(text.splitlines()))
    raw_headers = [h.strip() for h in rows[0]]
    headers = [normalize_header(h) for h in raw_headers]
    col = build_column_map(headers)
    print(f"\n=== {charset} column map: {col}")
    if not col:
        print("  headers:", headers[:12])

    reasons: Counter = Counter()
    parsed = []
    pieds = None

    for row in rows[1:]:
        if len(row) < 2:
            reasons["cells_lt_2"] += 1
            continue
        if "designation" not in col or "price" not in col:
            reasons["missing_columns"] += 1
            continue
        des = row[col["designation"]].strip().strip('"') if col["designation"] < len(row) else ""
        if not des:
            reasons["blank_designation"] += 1
            continue
        price_raw = row[col["price"]] if col["price"] < len(row) else ""
        price = parse_french_number(price_raw)
        if price is None:
            reasons["bad_price"] += 1
            if des.upper().startswith("PIEDS"):
                reasons[f"bad_price_sample:{des}:{price_raw!r}"] += 1
            continue
        bc = row[col["barcode"]].strip().strip('"') if "barcode" in col and col["barcode"] < len(row) else ""
        codeart = row[col["codeart"]].strip().strip('"') if "codeart" in col and col["codeart"] < len(row) else ""
        ref = row[col["reference"]].strip().strip('"') if "reference" in col and col["reference"] < len(row) else ""
        if not bc.strip() and not codeart.strip():
            reasons["no_barcode_no_codeart"] += 1
            if "BOUCHERIE" in str(row).upper() or (col.get("rayon") and col["rayon"] < len(row) and row[col["rayon"]].strip() == "Boucherie"):
                reasons[f"boucherie_no_id:{des[:40]}"] += 1
            continue
        resolved = resolve_barcode(bc, codeart, des)
        if not resolved:
            reasons["no_resolved_barcode"] += 1
            continue
        rayon = row[col["rayon"]].strip().strip('"') if "rayon" in col and col["rayon"] < len(row) else ""
        item = dict(designation=des, barcode=resolved, codeart=codeart, price=price, rayon=rayon, ref=ref)
        parsed.append(item)
        if des == "PIEDS DE VEAU":
            pieds = item

    print(f"parsed: {len(parsed)} skipped: {sum(reasons.values())}")
    print("top skip reasons:")
    for k, v in reasons.most_common(15):
        print(f"  {v:5d}  {k}")
    print("PIEDS DE VEAU:", pieds)


if __name__ == "__main__":
    for cs in ("utf-8", "cp1252", "iso-8859-1"):
        analyze(cs)
