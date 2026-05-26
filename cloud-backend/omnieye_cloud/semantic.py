from __future__ import annotations

import base64
import json
import os
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from time import perf_counter
from typing import Any


class SemanticMode(StrEnum):
    PRODUCT = "product"
    TRAFFIC_LIGHT = "traffic_light"


@dataclass(frozen=True)
class SemanticResult:
    mode: str
    summary: str
    objects: list[str]
    traffic_light: str | None
    target_found: bool
    product_name: str | None
    confidence: float
    latency_ms: int


class SemanticAnalyzer:
    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
    ) -> None:
        self.api_key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY")
        self.model = model or os.getenv("OPENAI_VISION_MODEL", "gpt-4.1-mini")

    def analyze(self, image_path: Path, mode: SemanticMode, query: str | None = None) -> SemanticResult:
        start = perf_counter()
        if not self.api_key:
            return _fallback_result(mode, query, _elapsed_ms(start))

        try:
            payload = self._call_openai(image_path=image_path, mode=mode, query=query)
            return _result_from_payload(payload, mode=mode, query=query, latency_ms=_elapsed_ms(start))
        except Exception:
            return _fallback_result(mode, query, _elapsed_ms(start))

    def _call_openai(self, image_path: Path, mode: SemanticMode, query: str | None) -> dict[str, Any]:
        from openai import OpenAI

        client = OpenAI(api_key=self.api_key)
        image_data = base64.b64encode(image_path.read_bytes()).decode("ascii")
        prompt = _prompt_for_mode(mode, query)
        response = client.responses.create(
            model=self.model,
            input=[
                {
                    "role": "user",
                    "content": [
                        {"type": "input_text", "text": prompt},
                        {
                            "type": "input_image",
                            "image_url": f"data:image/jpeg;base64,{image_data}",
                            "detail": "high",
                        },
                    ],
                }
            ],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "semantic_result",
                    "schema": _json_schema(),
                    "strict": True,
                }
            },
        )
        text = getattr(response, "output_text", "")
        if not text:
            raise ValueError("OpenAI response did not contain output_text")
        return json.loads(text)


def _prompt_for_mode(mode: SemanticMode, query: str | None) -> str:
    if mode is SemanticMode.PRODUCT:
        target = query or "用户想找的商品"
        return (
            "你是为视障人士服务的商品识别助手。请分析图片中的货架、商品包装、品牌文字和价格标签。"
            f"目标商品是：{target}。"
            "只输出 JSON。summary 必须是适合直接中文语音播报的一句话，包含大致方位或货架位置；"
            "如果不能确定，明确说疑似或未找到。"
        )
    return (
        "你是为视障人士服务的路口安全辅助助手。请识别图片中的人行红绿灯、机动车信号灯、斑马线、车辆和障碍物。"
        "traffic_light 只能是 red、green、yellow、unknown。"
        "summary 必须是适合直接中文语音播报的一句话。"
        "不要承诺绝对安全，绿灯时也要提醒确认周围安全。只输出 JSON。"
    )


def _json_schema() -> dict[str, Any]:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "summary": {"type": "string"},
            "objects": {"type": "array", "items": {"type": "string"}},
            "traffic_light": {"type": ["string", "null"], "enum": ["red", "green", "yellow", "unknown", None]},
            "target_found": {"type": "boolean"},
            "product_name": {"type": ["string", "null"]},
            "confidence": {"type": "number"},
        },
        "required": [
            "summary",
            "objects",
            "traffic_light",
            "target_found",
            "product_name",
            "confidence",
        ],
    }


def _result_from_payload(
    payload: dict[str, Any],
    mode: SemanticMode,
    query: str | None,
    latency_ms: int,
) -> SemanticResult:
    fallback = _fallback_result(mode, query, latency_ms)
    return SemanticResult(
        mode=mode.value,
        summary=str(payload.get("summary") or fallback.summary),
        objects=[str(item) for item in payload.get("objects", fallback.objects)],
        traffic_light=_normalize_traffic_light(payload.get("traffic_light")),
        target_found=bool(payload.get("target_found", fallback.target_found)),
        product_name=_optional_str(payload.get("product_name")) or fallback.product_name,
        confidence=_clamp_confidence(payload.get("confidence"), fallback.confidence),
        latency_ms=latency_ms,
    )


def _fallback_result(mode: SemanticMode, query: str | None, latency_ms: int) -> SemanticResult:
    if mode is SemanticMode.PRODUCT:
        product = query or "目标商品"
        return SemanticResult(
            mode=mode.value,
            summary=f"演示模式：画面中疑似有{product}货架，请靠近后再次扫描确认。",
            objects=["shelf", "product package", "price tag"],
            traffic_light=None,
            target_found=True,
            product_name=product,
            confidence=0.55,
            latency_ms=latency_ms,
        )
    return SemanticResult(
        mode=mode.value,
        summary="演示模式：前方疑似为绿灯，请确认周围车辆和人行横道后再通行。",
        objects=["traffic light", "crosswalk", "road"],
        traffic_light="green",
        target_found=True,
        product_name=None,
        confidence=0.55,
        latency_ms=latency_ms,
    )


def _normalize_traffic_light(value: Any) -> str | None:
    if value in {"red", "green", "yellow", "unknown"}:
        return str(value)
    return None


def _optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _clamp_confidence(value: Any, fallback: float) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return fallback
    return max(0.0, min(1.0, number))


def _elapsed_ms(start: float) -> int:
    return int((perf_counter() - start) * 1000)
