from __future__ import annotations

from contextlib import asynccontextmanager
from io import BytesIO
import logging
from pathlib import Path
from time import perf_counter

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from .service import AnalysisService
from .semantic import SemanticAnalyzer, SemanticMode
from .obstacle_vision import VisionObstacleAnalyzer
from .runtime_config import RuntimeConfig, load_env_file


load_env_file(Path("cloud-backend/.env"))
logger = logging.getLogger("omnieye_cloud")


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
    fallback_reason: str | None = None


class ConfigStatusResponse(BaseModel):
    openai_api_key_set: bool
    openai_installed: bool
    openai_vision_model: str
    openai_base_url_set: bool
    openai_user_agent_set: bool
    dap_repo_dir_set: bool
    dap_weights_path_set: bool


service = AnalysisService(upload_dir=Path("cloud-backend/uploads"))


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


@app.get("/config/status", response_model=ConfigStatusResponse)
def config_status() -> ConfigStatusResponse:
    return ConfigStatusResponse(**RuntimeConfig.from_env().__dict__)


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(frame: UploadFile = File(...)) -> AnalyzeResponse:
    start = perf_counter()
    content = await frame.read()
    image_size = _image_size(content)
    if image_size is None:
        raise HTTPException(status_code=400, detail="Uploaded frame must be an image")
    logger.info(
        "analyze_received filename=%s bytes=%s image=%sx%s",
        frame.filename,
        len(content),
        image_size[0],
        image_size[1],
    )

    suffix = Path(frame.filename or "frame.jpg").suffix or ".jpg"
    save_start = perf_counter()
    result = service.save_latest_frame(content, suffix=suffix)
    save_ms = int((perf_counter() - save_start) * 1000)
    model_ms = 0
    used_visual_fallback = False
    if result.confidence == 0.0:
        frame_path = service.latest_frame_path()
        if frame_path is not None:
            model_start = perf_counter()
            vision_result = await run_in_threadpool(VisionObstacleAnalyzer().analyze, frame_path)
            model_ms = int((perf_counter() - model_start) * 1000)
            used_visual_fallback = True
            result = vision_result.result
    latency_ms = int((perf_counter() - start) * 1000)
    logger.info(
        "analyze_timing bytes=%s image=%sx%s save_ms=%s model_ms=%s visual_fallback=%s total_ms=%s level=%s confidence=%.2f",
        len(content),
        image_size[0],
        image_size[1],
        save_ms,
        model_ms,
        used_visual_fallback,
        latency_ms,
        result.level,
        result.confidence,
    )
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
    start = perf_counter()
    content = await frame.read()
    image_size = _image_size(content)
    if image_size is None:
        raise HTTPException(status_code=400, detail="Uploaded frame must be an image")
    logger.info(
        "semantic_received mode=%s filename=%s bytes=%s image=%sx%s",
        mode.value,
        frame.filename,
        len(content),
        image_size[0],
        image_size[1],
    )

    suffix = Path(frame.filename or "frame.jpg").suffix or ".jpg"
    frame_path = service.upload_dir / f"semantic_latest{suffix}"
    save_start = perf_counter()
    frame_path.write_bytes(content)
    save_ms = int((perf_counter() - save_start) * 1000)
    model_start = perf_counter()
    result = await run_in_threadpool(SemanticAnalyzer().analyze, frame_path, mode=mode, query=query)
    model_ms = int((perf_counter() - model_start) * 1000)
    total_ms = int((perf_counter() - start) * 1000)
    logger.info(
        "semantic_timing mode=%s bytes=%s image=%sx%s save_ms=%s model_ms=%s total_ms=%s fallback=%s confidence=%.2f",
        mode.value,
        len(content),
        image_size[0],
        image_size[1],
        save_ms,
        model_ms,
        total_ms,
        result.fallback_reason,
        result.confidence,
    )
    return SemanticAnalyzeResponse(
        mode=result.mode,
        summary=result.summary,
        objects=result.objects,
        traffic_light=result.traffic_light,
        target_found=result.target_found,
        product_name=result.product_name,
        confidence=result.confidence,
        latency_ms=result.latency_ms,
        fallback_reason=result.fallback_reason,
    )


def _image_size(content: bytes) -> tuple[int, int] | None:
    try:
        with Image.open(BytesIO(content)) as image:
            image.verify()
            return image.size
    except (UnidentifiedImageError, OSError):
        return None


def _is_image(content: bytes) -> bool:
    return _image_size(content) is not None
