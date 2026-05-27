from pydantic import BaseModel
from typing import Optional, List


class AnalyzeResponse(BaseModel):
    distance_m: float
    level: str
    confidence: float
    scene_text: str
    latency_ms: float


class SemanticAnalyzeResponse(BaseModel):
    mode: str
    summary: str
    objects: List[str]
    traffic_light: str
    target_found: bool
    product_name: str
    confidence: float
    latency_ms: float
    fallback_reason: Optional[str]


class ImageInfo(BaseModel):
    width: int
    height: int
    size_bytes: int
    format: str
