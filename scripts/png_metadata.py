"""Embed/read barcode in PNG tEXt chunks (Windows file Properties → Description)."""

from __future__ import annotations

import re
import struct
import zlib
from pathlib import Path

PNG_SIG = b"\x89PNG\r\n\x1a\n"
KEY_BARCODE = b"Barcode"
KEY_DESCRIPTION = b"Description"
KEY_DESIGNATION = b"Designation"
KEY_PRICE_NOW = b"PriceNow"
KEY_PRICE_BEFORE = b"PriceBefore"
KEY_RAYON = b"Rayon"
KEY_CODEART = b"Codeart"

ARTICLE_TEXT_KEYS = {
    KEY_BARCODE,
    KEY_DESCRIPTION,
    KEY_DESIGNATION,
    KEY_PRICE_NOW,
    KEY_PRICE_BEFORE,
    KEY_RAYON,
    KEY_CODEART,
}
BARCODE_FILENAME_SUFFIX = re.compile(r"_(\d{8,18})$")
BARCODE_FILENAME_ONLY = re.compile(r"^\d{8,18}$")
BARCODE_IN_DESCRIPTION = re.compile(r"(?i)barcode\s*[=:]\s*([0-9]{8,18})")


def format_price_da(price: float) -> str:
    """Match Android PriceFormatter (French grouping + DA)."""
    negative = price < 0
    price = abs(price)
    whole = int(price)
    cents = int(round((price - whole) * 100))
    if cents == 100:
        whole += 1
        cents = 0
    s = str(whole)
    groups: list[str] = []
    while len(s) > 3:
        groups.insert(0, s[-3:])
        s = s[:-3]
    groups.insert(0, s)
    int_part = " ".join(groups)
    body = f"{int_part},{cents:02d} DA"
    return f"-{body}" if negative else body


def build_description(barcode: str, designation: str | None) -> str:
    des = (designation or "").strip()
    if des:
        return f"Barcode: {barcode} | {des}"
    return f"Barcode: {barcode}"


def read_barcode_from_chunks(path: Path) -> str | None:
    """Barcode from PNG tEXt only (not filename)."""
    data = path.read_bytes()
    if not data.startswith(PNG_SIG):
        return None
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"tEXt":
            zero = chunk_data.find(b"\x00")
            if zero >= 0:
                keyword = chunk_data[:zero]
                text = chunk_data[zero + 1 :].decode("latin-1", errors="replace")
                if keyword == KEY_BARCODE and text.strip():
                    return text.strip()
                if keyword == KEY_DESCRIPTION:
                    m = BARCODE_IN_DESCRIPTION.search(text)
                    if m:
                        return m.group(1)
        if chunk_type == b"IEND":
            break
    return None


def read_barcode(path: Path) -> str | None:
    return read_barcode_from_chunks(path) or _barcode_from_filename(path.stem)


def _barcode_from_filename(stem: str) -> str | None:
    if BARCODE_FILENAME_ONLY.fullmatch(stem):
        return stem
    m = BARCODE_FILENAME_SUFFIX.search(stem)
    return m.group(1) if m else None


def _crc(chunk_type: bytes, chunk_data: bytes) -> bytes:
    return struct.pack(">I", zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF)


def _text_chunk(keyword: str, text: str) -> bytes:
    chunk_data = keyword.encode("ascii") + b"\x00" + text.encode("latin-1", errors="replace")
    chunk_type = b"tEXt"
    return (
        struct.pack(">I", len(chunk_data))
        + chunk_type
        + chunk_data
        + _crc(chunk_type, chunk_data)
    )


def _strip_keywords(png: bytes, keywords: set[bytes]) -> bytes:
    if not png.startswith(PNG_SIG):
        return png
    out = bytearray(PNG_SIG)
    offset = 8
    while offset + 12 <= len(png):
        length = struct.unpack(">I", png[offset : offset + 4])[0]
        chunk_type = png[offset + 4 : offset + 8]
        total = 12 + length
        chunk = png[offset : offset + total]
        drop = False
        if chunk_type in (b"tEXt", b"iTXt"):
            chunk_data = png[offset + 8 : offset + 8 + length]
            if chunk_type == b"tEXt":
                zero = chunk_data.find(b"\x00")
                if zero >= 0:
                    kw = chunk_data[:zero]
                    if kw in keywords:
                        drop = True
        if not drop:
            out.extend(chunk)
        offset += total
        if chunk_type == b"IEND":
            break
    return bytes(out)


def _find_iend_offset(png: bytes) -> int | None:
    """Byte offset of the real IEND chunk (never use rfind — IEND can appear inside IDAT)."""
    offset = 8
    while offset + 12 <= len(png):
        length = struct.unpack(">I", png[offset : offset + 4])[0]
        chunk_type = png[offset + 4 : offset + 8]
        if chunk_type == b"IEND":
            return offset
        if length < 0 or length > 50_000_000:
            return None
        offset += 12 + length
    return None


def build_article_description(
    *,
    barcode: str,
    designation: str | None,
    codeart: str | None,
    price_now: float | None,
    price_before: float | None = None,
    rayon: str | None,
) -> str:
    parts: list[str] = []
    if designation and designation.strip():
        parts.append(f"Designation: {designation.strip()}")
    if price_now is not None:
        parts.append(f"Price now: {format_price_da(price_now)}")
    if price_before is not None:
        parts.append(f"Price before: {format_price_da(price_before)}")
    parts.append(f"Barcode: {barcode.strip()}")
    if codeart and codeart.strip():
        parts.append(f"Code: {codeart.strip()}")
    if rayon and rayon.strip():
        parts.append(f"Rayon: {rayon.strip()}")
    return " | ".join(parts)


def write_article_details(
    path: Path,
    *,
    barcode: str,
    designation: str | None,
    codeart: str | None,
    price_now: float | None,
    price_before: float | None = None,
    rayon: str | None,
) -> None:
    """Write tEXt chunks aligned with Android PngMetadata."""
    png = path.read_bytes()
    if not png.startswith(PNG_SIG):
        return
    stripped = _strip_keywords(png, ARTICLE_TEXT_KEYS)
    iend = _find_iend_offset(stripped)
    if iend is None:
        return

    description = build_article_description(
        barcode=barcode,
        designation=designation,
        codeart=codeart,
        price_now=price_now,
        price_before=price_before,
        rayon=rayon,
    )
    chunks = [_text_chunk("Barcode", barcode.strip()), _text_chunk("Description", description)]
    if designation and designation.strip():
        chunks.append(_text_chunk("Designation", designation.strip()))
    if codeart and codeart.strip():
        chunks.append(_text_chunk("Codeart", codeart.strip()))
    if price_now is not None:
        chunks.append(_text_chunk("PriceNow", format_price_da(price_now)))
    if price_before is not None:
        chunks.append(_text_chunk("PriceBefore", format_price_da(price_before)))
    if rayon and rayon.strip():
        chunks.append(_text_chunk("Rayon", rayon.strip()))

    updated = stripped[:iend] + b"".join(chunks) + stripped[iend:]
    path.write_bytes(updated)


def write_barcode(path: Path, barcode: str, designation: str | None) -> None:
    png = path.read_bytes()
    if not png.startswith(PNG_SIG):
        return
    stripped = _strip_keywords(png, {KEY_BARCODE, KEY_DESCRIPTION})
    iend = _find_iend_offset(stripped)
    if iend is None:
        return
    description = build_description(barcode, designation)
    new_chunks = _text_chunk("Barcode", barcode.strip()) + _text_chunk(
        "Description", description
    )
    updated = stripped[:iend] + new_chunks + stripped[iend:]
    path.write_bytes(updated)
