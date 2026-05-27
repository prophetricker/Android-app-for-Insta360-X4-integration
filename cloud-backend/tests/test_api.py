from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

from omnieye_cloud.main import app


client = TestClient(app)


def make_jpeg() -> bytes:
    image = Image.new("RGB", (8, 8), color=(80, 120, 160))
    buffer = BytesIO()
    image.save(buffer, format="JPEG")
    return buffer.getvalue()


def test_health_returns_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"ok": True}


def test_analyze_returns_android_compatible_fallback_without_dap():
    response = client.post(
        "/analyze",
        files={"frame": ("frame.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload == {
        "distance_m": 3.5,
        "level": 0,
        "confidence": 0.0,
        "scene_text": "正在分析前方环境",
        "latency_ms": payload["latency_ms"],
    }
    assert isinstance(payload["latency_ms"], int)


def test_analyze_rejects_non_image_uploads():
    response = client.post(
        "/analyze",
        files={"frame": ("frame.txt", b"not an image", "text/plain")},
    )

    assert response.status_code == 400
    assert "image" in response.json()["detail"].lower()
