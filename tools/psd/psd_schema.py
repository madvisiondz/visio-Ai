"""JSON-serializable schema for PSD → Visio Ai template cloning."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass
class Bounds:
    left: int
    top: int
    right: int
    bottom: int
    width: int
    height: int


@dataclass
class TextInfo:
    value: str
    font_name: str | None = None
    font_size: float | None = None
    color: str | None = None
    alignment: str | None = None


@dataclass
class LayerSpec:
    index: int
    name: str
    path: str
    depth: int
    kind: str
    visible: bool
    opacity: float
    blend_mode: str
    bounds: Bounds | None
    text: TextInfo | None = None
    role_hint: str | None = None
    children_count: int = 0


@dataclass
class DocumentSpec:
    width: int
    height: int
    channels: int | None = None
    color_mode: str | None = None
    dpi_x: float | None = None
    dpi_y: float | None = None


@dataclass
class PsdTemplateSpec:
    source_file: str
    engine: str
    document: DocumentSpec
    layers: list[LayerSpec] = field(default_factory=list)
    role_summary: dict[str, list[str]] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
