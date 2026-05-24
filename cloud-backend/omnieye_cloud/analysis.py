from __future__ import annotations

from dataclasses import dataclass
from math import ceil

import numpy as np


@dataclass(frozen=True)
class AnalyzeResult:
    distance_m: float
    level: int
    confidence: float
    scene_text: str


FALLBACK_RESULT = AnalyzeResult(
    distance_m=3.5,
    level=0,
    confidence=0.0,
    scene_text="正在分析前方环境",
)


def classify_distance(distance_m: float) -> int:
    if distance_m < 0.4:
        return 4
    if distance_m < 0.8:
        return 3
    if distance_m < 1.5:
        return 2
    if distance_m < 3.0:
        return 1
    return 0


def scene_text_for_level(level: int) -> str:
    level = max(0, min(level, 4))
    if level >= 4:
        return "前方极近有障碍物，请立即停下。"
    if level == 3:
        return "前方近距离有障碍物，请避让。"
    if level == 2:
        return "前方约一米有障碍物，请减速。"
    if level == 1:
        return "前方较远有障碍物，注意通行。"
    return "前方较远，注意通行。"


def analyze_depth_map(depth_map: np.ndarray) -> AnalyzeResult:
    if depth_map.ndim != 2:
        return FALLBACK_RESULT

    height, width = depth_map.shape
    row_start = max(0, int(height * 0.4))
    row_end = min(height, ceil(height * 0.6))
    col_start = max(0, int(width * 0.4))
    col_end = min(width, ceil(width * 0.6))

    roi = depth_map[row_start:row_end, col_start:col_end]
    valid = roi[np.isfinite(roi) & (roi > 0)]
    if valid.size == 0:
        return FALLBACK_RESULT

    distance_m = round(float(np.median(valid)), 2)
    level = classify_distance(distance_m)
    return AnalyzeResult(
        distance_m=distance_m,
        level=level,
        confidence=0.8,
        scene_text=scene_text_for_level(level),
    )
