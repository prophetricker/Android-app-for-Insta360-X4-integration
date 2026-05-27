from io import BytesIO
import base64

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


def test_analyze_returns_android_compatible_fallback_without_dap(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
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


def test_analyze_uses_vision_fallback_when_dap_is_unavailable(monkeypatch):
    from omnieye_cloud.obstacle_vision import VisionObstacleAnalyzer

    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")

    def return_obstacle_payload(self, image_path):
        return {
            "distance_m": 0.9,
            "level": 2,
            "confidence": 0.74,
            "scene_text": "前方约一米有桌沿和椅子，请减速并从左侧绕行。",
        }

    monkeypatch.setattr(VisionObstacleAnalyzer, "_call_vision_model", return_obstacle_payload)

    response = client.post(
        "/analyze",
        files={"frame": ("frame.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["distance_m"] == 0.9
    assert payload["level"] == 2
    assert payload["confidence"] == 0.74
    assert payload["scene_text"] == "前方约一米有桌沿和椅子，请减速并从左侧绕行。"
    assert isinstance(payload["latency_ms"], int)


def test_obstacle_vision_data_url_downscales_large_panorama(tmp_path):
    from omnieye_cloud.obstacle_vision import image_data_url_for_obstacle

    image_path = tmp_path / "panorama.jpg"
    image = Image.new("RGB", (3840, 1920), color=(80, 120, 160))
    image.save(image_path, format="JPEG", quality=95)

    data_url = image_data_url_for_obstacle(image_path)
    _, encoded = data_url.split(",", 1)
    decoded = base64.b64decode(encoded)
    result = Image.open(BytesIO(decoded))

    assert result.width == 640
    assert result.height == 384
    assert len(decoded) < image_path.stat().st_size


def test_obstacle_vision_data_url_uses_front_center_crop(tmp_path):
    from omnieye_cloud.obstacle_vision import image_data_url_for_obstacle

    image_path = tmp_path / "panorama.jpg"
    image = Image.new("RGB", (1000, 500), color=(0, 0, 255))
    for x in range(250, 750):
        for y in range(100, 400):
            image.putpixel((x, y), (255, 0, 0))
    image.save(image_path, format="JPEG", quality=95)

    data_url = image_data_url_for_obstacle(image_path)
    _, encoded = data_url.split(",", 1)
    decoded = base64.b64decode(encoded)
    result = Image.open(BytesIO(decoded))

    corner = result.getpixel((4, 4))
    assert corner[0] > 200
    assert corner[2] < 80


def test_analyze_rejects_non_image_uploads():
    response = client.post(
        "/analyze",
        files={"frame": ("frame.txt", b"not an image", "text/plain")},
    )

    assert response.status_code == 400
    assert "image" in response.json()["detail"].lower()
