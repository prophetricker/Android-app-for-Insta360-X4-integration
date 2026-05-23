from fastapi.testclient import TestClient

from app import app, classify_distance


client = TestClient(app)


def test_health():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"ok": True}


def test_analyze_returns_mvp_payload():
    response = client.post(
        "/analyze",
        files={"frame": ("frame.jpg", b"fake-jpeg", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["distance_m"] == 1.2
    assert payload["level"] == 2
    assert payload["confidence"] == 0.5
    assert payload["scene_text"]
    assert "latency_ms" in payload


def test_classify_distance_thresholds():
    assert classify_distance(3.2) == 0
    assert classify_distance(2.0) == 1
    assert classify_distance(1.0) == 2
    assert classify_distance(0.6) == 3
    assert classify_distance(0.3) == 4
