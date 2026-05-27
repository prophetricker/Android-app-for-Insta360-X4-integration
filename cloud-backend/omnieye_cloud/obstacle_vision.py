from __future__ import annotations

import os
import base64
from io import BytesIO
from dataclasses import dataclass
from pathlib import Path
from time import perf_counter
from typing import Any

from PIL import Image

from .analysis import FALLBACK_RESULT, AnalyzeResult, classify_distance, scene_text_for_level
from .semantic import (
    _chat_message_content_to_text,
    _clamp_confidence,
    _json_payload_from_text,
)


@dataclass(frozen=True)
class VisionObstacleResult:
    result: AnalyzeResult
    latency_ms: int
    fallback_reason: str | None = None


class VisionObstacleAnalyzer:
    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
        base_url: str | None = None,
        user_agent: str | None = None,
    ) -> None:
        self.api_key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY")
        self.model = model or os.getenv("OPENAI_VISION_MODEL", "gpt-4.1-mini")
        self.base_url = base_url if base_url is not None else os.getenv("OPENAI_BASE_URL")
        resolved_user_agent = user_agent if user_agent is not None else os.getenv("OPENAI_USER_AGENT")
        self.default_headers = {"User-Agent": resolved_user_agent} if resolved_user_agent else None

    def analyze(self, image_path: Path) -> VisionObstacleResult:
        start = perf_counter()
        if not self.api_key:
            return VisionObstacleResult(
                result=FALLBACK_RESULT,
                latency_ms=_elapsed_ms(start),
                fallback_reason="openai_api_key_missing",
            )

        try:
            payload = self._call_vision_model(image_path)
            return VisionObstacleResult(
                result=_result_from_payload(payload),
                latency_ms=_elapsed_ms(start),
                fallback_reason=None,
            )
        except Exception as exc:
            return VisionObstacleResult(
                result=FALLBACK_RESULT,
                latency_ms=_elapsed_ms(start),
                fallback_reason=f"vision_error: {type(exc).__name__}",
            )

    def _create_openai_client(self) -> Any:
        from openai import OpenAI

        client_kwargs: dict[str, Any] = {"api_key": self.api_key}
        if self.base_url:
            client_kwargs["base_url"] = self.base_url
        if self.default_headers:
            client_kwargs["default_headers"] = self.default_headers
        return OpenAI(**client_kwargs)

    def _call_vision_model(self, image_path: Path) -> dict[str, Any]:
        client = self._create_openai_client()
        response = client.chat.completions.create(
            model=self.model,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": _obstacle_prompt()},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": image_data_url_for_obstacle(image_path),
                                "detail": "low",
                            },
                        },
                    ],
                }
            ],
        )
        choices = getattr(response, "choices", [])
        if not choices:
            raise ValueError("Vision obstacle response did not contain choices")
        message = getattr(choices[0], "message", None)
        text = _chat_message_content_to_text(getattr(message, "content", None))
        if not text:
            raise ValueError("Vision obstacle response did not contain message content")
        return _json_payload_from_text(text)


def _obstacle_prompt() -> str:
    return (
        "你是为视障人士服务的全景避障助手。请只分析用户正前方中心区域和近距离通行风险，"
        "识别桌沿、椅子、墙、门、行人、车辆、树木、台阶、栏杆等可能阻挡行走的物体。"
        "你可以做视觉估计，但不要夸大精度。只输出 JSON，字段必须为："
        "distance_m 数字，表示最近主要障碍的大致距离；"
        "level 整数 0 到 4，规则是小于0.4米为4，小于0.8米为3，小于1.5米为2，小于3米为1，否则0；"
        "confidence 数字 0 到 1；"
        "scene_text 一句适合直接中文语音播报的话，说明前方风险和行动建议。"
        "如果前方通道基本通畅，distance_m 填 3.5，level 填 0。"
    )


def image_data_url_for_obstacle(image_path: Path, max_width: int = 640, quality: int = 68) -> str:
    with Image.open(image_path) as image:
        rgb_image = image.convert("RGB")
        rgb_image = _front_center_crop(rgb_image)
        if rgb_image.width > max_width:
            ratio = max_width / rgb_image.width
            new_size = (max_width, max(1, int(rgb_image.height * ratio)))
            rgb_image = rgb_image.resize(new_size, Image.Resampling.LANCZOS)

        buffer = BytesIO()
        rgb_image.save(buffer, format="JPEG", quality=quality, optimize=True)
    image_data = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/jpeg;base64,{image_data}"


def _front_center_crop(image: Image.Image) -> Image.Image:
    width, height = image.size
    left = int(width * 0.25)
    right = int(width * 0.75)
    top = int(height * 0.2)
    bottom = int(height * 0.8)
    return image.crop((left, top, right, bottom))


def _result_from_payload(payload: dict[str, Any]) -> AnalyzeResult:
    distance_m = _coerce_distance(payload.get("distance_m"))
    level = _coerce_level(payload.get("level"), distance_m)
    confidence = _clamp_confidence(payload.get("confidence"), 0.45)
    scene_text = str(payload.get("scene_text") or "").strip()
    if not scene_text:
        scene_text = scene_text_for_level(level)

    return AnalyzeResult(
        distance_m=distance_m,
        level=level,
        confidence=confidence,
        scene_text=scene_text,
    )


def _coerce_distance(value: Any) -> float:
    try:
        distance_m = float(value)
    except (TypeError, ValueError):
        return FALLBACK_RESULT.distance_m
    if distance_m <= 0:
        return FALLBACK_RESULT.distance_m
    return round(min(distance_m, 20.0), 2)


def _coerce_level(value: Any, distance_m: float) -> int:
    try:
        level = int(value)
    except (TypeError, ValueError):
        return classify_distance(distance_m)
    return max(0, min(level, 4))


def _elapsed_ms(start: float) -> int:
    return int((perf_counter() - start) * 1000)
