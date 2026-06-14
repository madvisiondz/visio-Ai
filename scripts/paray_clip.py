"""PARAY image fingerprints — CLIP ONNX when available, pure-Python fallback otherwise."""

from __future__ import annotations

import math
import struct
import urllib.request
from pathlib import Path

from PIL import Image

MODEL_URL = (
    "https://huggingface.co/inference4j/clip-vit-base-patch32/"
    "resolve/main/vision_model.onnx"
)
MODEL_NAME = "clip-vit-b32-vision.onnx"
EMBED_DIM = 512
ALPHA_MIN = 24

_NUMPY_OK = False
_ONNX_OK = False
_np = None
_ort = None

try:
    import numpy as _np

    _np.zeros(1)
    _NUMPY_OK = True
except Exception:
    _NUMPY_OK = False

if _NUMPY_OK:
    try:
        import onnxruntime as _ort

        _ONNX_OK = True
    except Exception:
        _ONNX_OK = False

CLIP_MEAN = (0.48145466, 0.4578275, 0.40821073)
CLIP_STD = (0.26862954, 0.26130258, 0.27577711)


def backend_name() -> str:
    return "openai/clip-vit-base-patch32" if _ONNX_OK else "paray-lite-v1"


def model_path(scripts_dir: Path) -> Path:
    return scripts_dir / "models" / MODEL_NAME


def ensure_model(scripts_dir: Path) -> Path | None:
    if not _ONNX_OK:
        return None
    path = model_path(scripts_dir)
    if path.exists() and path.stat().st_size > 10_000_000:
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    print(f"Downloading CLIP vision model (~340 MB) to {path} ...")
    urllib.request.urlretrieve(MODEL_URL, path)
    print("Download complete.")
    return path


def create_session(onnx_path: Path | None):
    if not _ONNX_OK or onnx_path is None:
        return None
    providers = ["CPUExecutionProvider"]
    available = _ort.get_available_providers()
    if "CUDAExecutionProvider" in available:
        providers.insert(0, "CUDAExecutionProvider")
    return _ort.InferenceSession(str(onnx_path), providers=providers)


def _resize_shortest_side(img: Image.Image, size: int) -> Image.Image:
    w, h = img.size
    if w == 0 or h == 0:
        return img
    if w < h:
        new_w = size
        new_h = max(1, int(round(h * size / w)))
    else:
        new_h = size
        new_w = max(1, int(round(w * size / h)))
    return img.resize((new_w, new_h), Image.Resampling.BICUBIC)


def _center_crop(img: Image.Image, size: int) -> Image.Image:
    w, h = img.size
    left = max(0, (w - size) // 2)
    top = max(0, (h - size) // 2)
    return img.crop((left, top, left + size, top + size))


def load_rgba(path: Path) -> Image.Image:
    with Image.open(path) as img:
        return img.convert("RGBA")


def composite_on_white(rgba: Image.Image) -> Image.Image:
    bg = Image.new("RGBA", rgba.size, (255, 255, 255, 255))
    return Image.alpha_composite(bg, rgba).convert("RGB")


def detect_content_bounds(rgba: Image.Image) -> tuple[int, int, int, int]:
    w, h = rgba.size
    if w == 0 or h == 0:
        return 0, 0, 0, 0
    alpha = rgba.split()[-1]
    step = 4 if w * h > 2_000_000 else (2 if w * h > 500_000 else 1)
    min_x, min_y = w, h
    max_x, max_y = 0, 0
    found = False
    for y in range(0, h, step):
        for x in range(0, w, step):
            if alpha.getpixel((x, y)) > ALPHA_MIN:
                found = True
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if not found:
        return 0, 0, w, h
    return min_x, min_y, min(max_x + step, w), min(max_y + step, h)


def quantize_color(r: int, g: int, b: int) -> int:
    r = r & 0xF8
    g = g & 0xF8
    b = b & 0xF8
    return (r << 16) | (g << 8) | b


def extract_visual_features(rgba: Image.Image) -> dict[str, object]:
    left, top, right, bottom = detect_content_bounds(rgba)
    cw = max(1, right - left)
    ch = max(1, bottom - top)
    w, h = rgba.size
    shape_aspect = cw / ch
    fill_ratio = (cw * ch) / max(1, w * h)

    pixels = rgba.load()
    area = cw * ch
    step = 6 if area > 80_000 else (4 if area > 20_000 else 2)
    counts: dict[int, int] = {}
    y = top
    while y < bottom:
        x = left
        while x < right:
            px = pixels[min(x, w - 1), min(y, h - 1)]
            if px[3] > ALPHA_MIN:
                key = quantize_color(px[0], px[1], px[2])
                counts[key] = counts.get(key, 0) + 1
            x += step
        y += step

    dominant = [c for c, _ in sorted(counts.items(), key=lambda kv: kv[1], reverse=True)[:3]]
    if not dominant:
        dominant = [0x808080]

    return {
        "shapeAspect": round(shape_aspect, 4),
        "fillRatio": round(fill_ratio, 4),
        "dominantColors": dominant,
    }


def _l2_normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vec))
    if norm < 1e-8:
        return vec
    return [x / norm for x in vec]


def _embed_lite(rgb: Image.Image, bounds: tuple[int, int, int, int]) -> list[float]:
    """512-dim pure-Python embedder: RGB cube histogram + spatial grid + edge bins."""
    left, top, right, bottom = bounds
    crop = rgb.crop((left, top, right, bottom))
    thumb = crop.resize((64, 64), Image.Resampling.BILINEAR)
    gray = thumb.convert("L")
    px_rgb = thumb.load()
    px_gray = gray.load()

    hist = [0.0] * 512
    # 8×8×8 RGB histogram (dims 0..511)
    for y in range(64):
        for x in range(64):
            r, g, b = px_rgb[x, y]
            ri = min(7, r * 8 // 256)
            gi = min(7, g * 8 // 256)
            bi = min(7, b * 8 // 256)
            hist[ri * 64 + gi * 8 + bi] += 1.0

    # Overwrite tail with spatial 4×4 dominant-bin features if histogram sparse
    spatial = [0.0] * 64
    for gy in range(4):
        for gx in range(4):
            r_sum = g_sum = b_sum = n = 0
            for y in range(gy * 16, (gy + 1) * 16):
                for x in range(gx * 16, (gx + 1) * 16):
                    r, g, b = px_rgb[x, y]
                    r_sum += r
                    g_sum += g
                    b_sum += b
                    n += 1
            if n:
                idx = gx + gy * 4
                spatial[idx] = ((r_sum // n) + (g_sum // n) + (b_sum // n)) / (3.0 * 255.0)

    # Simple edge energy on 32×32 grayscale (reuse first 64 hist bins as blend)
    small = gray.resize((32, 32), Image.Resampling.BILINEAR)
    spx = small.load()
    edges = [0.0] * 32
    for y in range(1, 31):
        for x in range(1, 31):
            gx_val = abs(spx[x + 1, y] - spx[x - 1, y])
            gy_val = abs(spx[x, y + 1] - spx[x, y - 1])
            bucket = min(31, (gx_val + gy_val) // 8)
            edges[bucket] += 1.0

    # Merge: normalize each block then concatenate into 512
    hist = _l2_normalize(hist)
    spatial = _l2_normalize(spatial)
    edges = _l2_normalize(edges)
    merged = hist[:416] + spatial + edges
    return _l2_normalize(merged[:EMBED_DIM])


def _preprocess_clip_numpy(rgb: Image.Image):
    img = _center_crop(_resize_shortest_side(rgb, 224), 224)
    arr = _np.asarray(img, dtype=_np.float32) / 255.0
    mean = _np.array(CLIP_MEAN, dtype=_np.float32)
    std = _np.array(CLIP_STD, dtype=_np.float32)
    arr = (arr - mean) / std
    arr = _np.transpose(arr, (2, 0, 1))
    return arr[_np.newaxis, ...]


def _embed_clip(session, rgb: Image.Image) -> list[float]:
    tensor = _preprocess_clip_numpy(rgb)
    input_name = session.get_inputs()[0].name
    outputs = session.run(None, {input_name: tensor})
    vec = outputs[0].reshape(-1).astype(_np.float32)
    norm = _np.linalg.norm(vec)
    if norm > 1e-8:
        vec = vec / norm
    return [round(float(x), 6) for x in vec[:EMBED_DIM].tolist()]


def embed_image(session, rgb: Image.Image, rgba: Image.Image) -> list[float]:
    bounds = detect_content_bounds(rgba)
    if session is not None and _ONNX_OK:
        return _embed_clip(session, rgb)
    return [round(x, 6) for x in _embed_lite(rgb, bounds)]


def is_valid_png(path: Path) -> bool:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
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
