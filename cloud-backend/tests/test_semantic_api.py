from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

from omnieye_cloud.main import app


client = TestClient(app)


def make_jpeg() -> bytes:
    image = Image.new("RGB", (16, 8), color=(90, 120, 80))
    buffer = BytesIO()
    image.save(buffer, format="JPEG")
    return buffer.getvalue()


def test_semantic_analyze_product_returns_demo_safe_fallback(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    response = client.post(
        "/semantic-analyze",
        data={"mode": "product", "query": "牛奶"},
        files={"frame": ("shelf.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["mode"] == "product"
    assert payload["target_found"] is True
    assert payload["product_name"]
    assert "牛奶" in payload["summary"]
    assert payload["traffic_light"] is None
    assert isinstance(payload["objects"], list)
    assert isinstance(payload["latency_ms"], int)


def test_semantic_analyze_traffic_light_returns_demo_safe_fallback(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    response = client.post(
        "/semantic-analyze",
        data={"mode": "traffic_light"},
        files={"frame": ("crossing.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["mode"] == "traffic_light"
    assert payload["traffic_light"] in {"red", "green", "yellow", "unknown"}
    assert "通行" in payload["summary"] or "等待" in payload["summary"]
    assert payload["product_name"] is None


def test_semantic_analyze_rejects_unknown_mode():
    response = client.post(
        "/semantic-analyze",
        data={"mode": "face"},
        files={"frame": ("frame.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 422
