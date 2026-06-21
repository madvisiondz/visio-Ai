#!/usr/bin/env python3
"""Count rows with no barcode and no codeart using Kotlin-like CSV split."""
import re
import unicodedata
from pathlib import Path

CSV = Path(r"c:\Users\Oasis-Mall\Desktop\19-06-26 10h MATIN.csv")


def normalize_header(header: str) -> str:
    s = unicodedata.normalize("NFD", header.replace('"', ""))
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.upper()
    s = re.sub(r"[^A-Z0-9\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip().lower()


def split_line(line: str, delimiter: str = ",") -> list[str]:
    result = []
    current = []
    in_quotes = False
    for char in line:
        if char == '"':
            in_quotes = not in_quotes
        elif char == delimiter and not in_quotes:
            result.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    result.append("".join(current).strip())
    return result


def parse_french_number(raw: str | None) -> float | None:
    if not raw:
        return None
    cleaned = raw.replace('"', "").replace("\u00a0", " ").replace(" ", "").replace(",", ".")
    cleaned = "".join(c for c in cleaned if c.isdigit() or c in ".-")
    if not cleaned or cleaned in (".", "-"):
        return None
    try:
        return float(cleaned)
    except ValueError:
        return None


def main() -> None:
    text = CSV.read_bytes().decode("cp1252")
    lines = [ln for ln in text.splitlines() if ln.strip()]
    raw_headers = [h.replace('"', "").strip() for h in split_line(lines[0])]
    headers = [normalize_header(h) for h in raw_headers]

    def col(name_sub: str) -> int | None:
        for i, h in enumerate(headers):
            if h == name_sub:
                return i
        return None

    des_i, code_i, bc_i, price_i, rayon_i, ref_i = col("designation"), col("code"), None, None, col("rayon"), col("reference")
    for i, h in enumerate(headers):
        if "code barres" in h or h == "code barres":
            bc_i = i
        if "prix de vente" in h and "ttc" in h:
            price_i = i

    reasons = {}
    parsed = 0
    samples = []

    for line in lines[1:]:
        cells = split_line(line)
        if len(cells) < 2:
            reasons["cells"] = reasons.get("cells", 0) + 1
            continue
        des = cells[des_i].strip().strip('"') if des_i is not None and des_i < len(cells) else ""
        if not des:
            reasons["no_des"] = reasons.get("no_des", 0) + 1
            continue
        price = parse_french_number(cells[price_i] if price_i is not None and price_i < len(cells) else None)
        if price is None:
            reasons["no_price"] = reasons.get("no_price", 0) + 1
            if len(samples) < 5 and des.startswith("PIEDS"):
                samples.append((des, cells[price_i] if price_i < len(cells) else ""))
            continue
        bc = cells[bc_i].strip().strip('"') if bc_i is not None and bc_i < len(cells) else ""
        codeart = cells[code_i].strip().strip('"') if code_i is not None and code_i < len(cells) else ""
        ref = cells[ref_i].strip().strip('"') if ref_i is not None and ref_i < len(cells) else ""
        if not bc and not codeart:
            reasons["no_id"] = reasons.get("no_id", 0) + 1
            rayon = cells[rayon_i].strip().strip('"') if rayon_i is not None and rayon_i < len(cells) else ""
            if rayon == "Boucherie" and len(samples) < 20:
                samples.append(("NO_ID", des, ref, codeart, bc))
            continue
        parsed += 1
        if des == "PIEDS DE VEAU":
            samples.append(("FOUND", des, bc, codeart, price))

    print("parsed", parsed, "skipped", sum(reasons.values()), reasons)
    print("samples:", samples)


if __name__ == "__main__":
    main()
