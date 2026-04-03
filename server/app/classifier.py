"""
Pluggable plant-disease classifier.

Replace `StubPlantClassifier` with a PyTorch/ONNX model loader:
  - Subclass `PlantClassifier` and implement `classify(image_bytes) -> ClassificationResult`.
  - Wire it in `app/main.py` (dependency or settings flag).

The stub uses image byte length to pick a deterministic demo label so QA is repeatable.
"""

from __future__ import annotations

import hashlib
from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class ClassificationResult:
    disease_name: str
    confidence: float


class PlantClassifier(ABC):
    @abstractmethod
    def classify(self, image_bytes: bytes) -> ClassificationResult:
        pass


# Demo labels resembling PlantVillage-style names (not real predictions from the stub).
_STUB_LABELS: list[tuple[str, float]] = [
    ("Tomato — Early blight (demo)", 0.78),
    ("Potato — Late blight (demo)", 0.74),
    ("Corn — Common rust (demo)", 0.71),
    ("Grape — Black rot (demo)", 0.69),
    ("Apple — Apple scab (demo)", 0.81),
    ("Healthy leaf (demo)", 0.62),
]


class StubPlantClassifier(PlantClassifier):
    """Deterministic fake classifier for development and CI without GPU weights."""

    def classify(self, image_bytes: bytes) -> ClassificationResult:
        h = int(hashlib.sha256(image_bytes).hexdigest()[:8], 16)
        name, base_conf = _STUB_LABELS[h % len(_STUB_LABELS)]
        # Jitter confidence slightly but keep in (0.55, 0.92)
        noise = (h % 13) / 100.0
        conf = min(0.92, max(0.55, base_conf + noise - 0.06))
        return ClassificationResult(disease_name=name, confidence=round(conf, 3))
