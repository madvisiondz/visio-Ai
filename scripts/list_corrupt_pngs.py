"""List corrupt product_images PNGs (need restore from backup / OneDrive version history)."""

from __future__ import annotations

import struct
from pathlib import Path

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


def main() -> None:
    png_dir = Path(__file__).resolve().parents[1] / "product_images"
    out = Path(__file__).resolve().parents[1] / "imports" / "corrupt_pngs_need_restore.txt"
    corrupt = sorted(p.name for p in png_dir.glob("*.png") if not is_valid_png(p))
    out.write_text("\n".join(corrupt), encoding="utf-8")
    print(f"Corrupt PNGs: {len(corrupt)}")
    print(f"List written to: {out}")


if __name__ == "__main__":
    main()
