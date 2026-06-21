#!/usr/bin/env python3
"""Simulate Gestium CSV import rules for barcode-less articles."""
import csv
import re
import unicodedata
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
    try:
        return float(cleaned) if cleaned else None
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
    return m


def resolve_barcode(barcode: str, codeart: str | None, designation: str) -> str:
    if barcode.strip():
        return barcode.strip()
    code = (codeart or "").strip()
    if code:
        return f"CA:{code}"
    norm = normalize_header(designation).upper().replace(" ", " ")
    return f"DN:{norm}" if norm else ""


def main() -> None:
    for charset in ("utf-8", "cp1252"):
        text = CSV.read_bytes().decode(charset, errors="replace")
        raw_rows = list(csv.reader(text.splitlines()))
        raw_headers = [h.strip() for h in raw_rows[0]]
        headers = [normalize_header(h) for h in raw_headers]
        col = build_column_map(headers)
        print(f"\n=== {charset} ===")
        print("column map:", col)
        parsed = 0
        skipped_price = 0
        skipped_no_id = 0
        pieds = None
        empty_bc_boucherie = 0
        parsed_empty_bc_boucherie = 0
        for row in raw_rows[1:]:
            if len(row) < 2:
                continue
            des = row[col["designation"]].strip().strip('"') if "designation" in col else ""
            bc = row[col["barcode"]].strip().strip('"') if "barcode" in col else ""
            price_raw = row[col["price"]] if "price" in col else ""
            price = parse_french_number(price_raw)
            codeart = row[col["codeart"]].strip().strip('"') if "codeart" in col and col["codeart"] < len(row) else ""
            rayon = row[col["rayon"]].strip().strip('"') if "rayon" in col and col["rayon"] < len(row) else ""
            if des == "PIEDS DE VEAU":
                pieds = dict(des=des, bc=bc, codeart=codeart, price=price, price_raw=price_raw, rayon=rayon,
                             resolved=resolve_barcode(bc, codeart, des))
            if not des:
                continue
            if price is None:
                skipped_price += 1
                continue
            resolved = resolve_barcode(bc, codeart, des)
            if not resolved:
                skipped_no_id += 1
                continue
            parsed += 1
            if rayon == "Boucherie" and not bc:
                empty_bc_boucherie += 1
                parsed_empty_bc_boucherie += 1
        print("parsed rows:", parsed)
        print("skipped no price:", skipped_price)
        print("skipped no identity:", skipped_no_id)
        print("boucherie empty barcode parsed:", parsed_empty_bc_boucherie)
        print("PIEDS DE VEAU:", pieds)


if __name__ == "__main__":
    main()
