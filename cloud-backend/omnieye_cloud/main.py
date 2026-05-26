from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from time import perf_counter

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel

from .service import AnalysisService
from .semantic import SemanticAnalyzer, SemanticMode


class AnalyzeResponse(BaseModel):
    distance_m: float
    level: int
    confidence: float
    scene_text: str
    latency_ms: int


class SemanticAnalyzeResponse(BaseModel):
    mode: str
    summary: str
    objects: list[str]
    traffic_light: str | None
    target_found: bool
    product_name: str | None
    confidence: float
    latency_ms: int


service = AnalysisService(upload_dir=Path("cloud-backend/uploads"))
semantic_analyzer = SemanticAnalyzer()


@asynccontextmanager
async def lifespan(_: FastAPI):
    service.start()
    try:
        yield
    finally:
        service.stop()


app = FastAPI(title="OmniEye Cloud Backend", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, bool]:
    return {"ok": True}


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(frame: UploadFile = File(...)) -> AnalyzeResponse:
    start = perf_counter()
    content = await frame.read()
    if not _is_image(content):
        raise HTTPException(status_code=400, detail="Uploaded frame must be an image")

    suffix = Path(frame.filename or "frame.jpg").suffix or ".jpg"
    result = service.save_latest_frame(content, suffix=suffix)
    latency_ms = int((perf_counter() - start) * 1000)
    return AnalyzeResponse(
        distance_m=result.distance_m,
        level=result.level,
        confidence=result.confidence,
        scene_text=result.scene_text,
        latency_ms=latency_ms,
    )


@app.post("/semantic-analyze", response_model=SemanticAnalyzeResponse)
async def semantic_analyze(
    frame: UploadFile = File(...),
    mode: SemanticMode = Form(...),
    query: str | None = Form(default=None),
) -> SemanticAnalyzeResponse:
    content = await frame.read()
    if not _is_image(content):
        raise HTTPException(status_code=400, detail="Uploaded frame must be an image")

    suffix = Path(frame.filename or "frame.jpg").suffix or ".jpg"
    frame_path = service.upload_dir / f"semantic_latest{suffix}"
    frame_path.write_bytes(content)
    result = semantic_analyzer.analyze(frame_path, mode=mode, query=query)
    return SemanticAnalyzeResponse(
        mode=result.mode,
        summary=result.summary,
        objects=result.objects,
        traffic_light=result.traffic_light,
        target_found=result.target_found,
        product_name=result.product_name,
        confidence=result.confidence,
        latency_ms=result.latency_ms,
    )


def _is_image(content: bytes) -> bool:
    try:
        with Image.open(__import__("io").BytesIO(content)) as image:
            image.verify()
        return True
    except (UnidentifiedImageError, OSError):
        return False
