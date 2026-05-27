import io
import pytest
from fastapi.testclient import TestClient
from PIL import Image

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from main import app

client = TestClient(app)


def create_test_image(width: int = 640, height: int = 480) -> io.BytesIO:
    """Create a test JPEG image."""
    img = Image.new("RGB", (width, height), color=(73, 109, 137))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    buf.seek(0)
    return buf


class TestHealthEndpoint:
    def test_health_check(self):
        """Test health check endpoint."""
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert "service" in data


class TestAnalyzeEndpoint:
    def test_analyze_returns_correct_fields(self):
        """Test /analyze returns all required fields."""
        img_buf = create_test_image()
        
        response = client.post(
            "/analyze",
            files={"image": ("test.jpg", img_buf, "image/jpeg")}
        )
        
        assert response.status_code == 200
        data = response.json()
        
        # Verify all required fields are present
        assert "distance_m" in data
        assert "level" in data
        assert "confidence" in data
        assert "scene_text" in data
        assert "latency_ms" in data
        
        # Verify types
        assert isinstance(data["distance_m"], (int, float))
        assert isinstance(data["level"], str)
        assert isinstance(data["confidence"], (int, float))
        assert isinstance(data["scene_text"], str)
        assert isinstance(data["latency_ms"], (int, float))
        
        # Verify ranges
        assert data["distance_m"] >= 0
        assert 0 <= data["confidence"] <= 1
        assert data["latency_ms"] >= 0

    def test_analyze_with_quality_header(self):
        """Test /analyze with X-Upload-Quality header."""
        img_buf = create_test_image()
        
        response = client.post(
            "/analyze",
            files={"image": ("test.jpg", img_buf, "image/jpeg")},
            headers={"X-Upload-Quality": "FAST_ANALYZE"}
        )
        
        assert response.status_code == 200
        data = response.json()
        assert "latency_ms" in data

    def test_analyze_accepts_different_sizes(self):
        """Test /analyze accepts images of different sizes."""
        for size in [(320, 240), (640, 480), (1920, 1080)]:
            img_buf = create_test_image(*size)
            response = client.post(
                "/analyze",
                files={"image": ("test.jpg", img_buf, "image/jpeg")}
            )
            assert response.status_code == 200


class TestSemanticAnalyzeEndpoint:
    def test_semantic_analyze_returns_correct_fields(self):
        """Test /semantic-analyze returns all required fields."""
        img_buf = create_test_image()
        
        response = client.post(
            "/semantic-analyze",
            files={"image": ("test.jpg", img_buf, "image/jpeg")},
            data={"mode": "surroundings"}
        )
        
        assert response.status_code == 200
        data = response.json()
        
        # Verify all required fields are present
        assert "mode" in data
        assert "summary" in data
        assert "objects" in data
        assert "traffic_light" in data
        assert "target_found" in data
        assert "product_name" in data
        assert "confidence" in data
        assert "latency_ms" in data
        assert "fallback_reason" in data
        
        # Verify types
        assert isinstance(data["mode"], str)
        assert isinstance(data["summary"], str)
        assert isinstance(data["objects"], list)
        assert isinstance(data["traffic_light"], str)
        assert isinstance(data["target_found"], bool)
        assert isinstance(data["product_name"], str)
        assert isinstance(data["confidence"], (int, float))
        assert isinstance(data["latency_ms"], (int, float))
        assert data["fallback_reason"] is None or isinstance(data["fallback_reason"], str)

    def test_semantic_analyze_with_different_modes(self):
        """Test /semantic-analyze with different modes."""
        img_buf = create_test_image()
        
        modes = ["surroundings", "traffic", "objects", "product"]
        for mode in modes:
            response = client.post(
                "/semantic-analyze",
                files={"image": ("test.jpg", img_buf, "image/jpeg")},
                data={"mode": mode}
            )
            assert response.status_code == 200
            data = response.json()
            assert data["mode"] == mode

    def test_semantic_analyze_with_quality_header(self):
        """Test /semantic-analyze with X-Upload-Quality header."""
        img_buf = create_test_image()
        
        response = client.post(
            "/semantic-analyze",
            files={"image": ("test.jpg", img_buf, "image/jpeg")},
            data={"mode": "surroundings"},
            headers={"X-Upload-Quality": "HIGH_QUALITY"}
        )
        
        assert response.status_code == 200
        data = response.json()
        assert "latency_ms" in data


class TestLatencyLogging:
    """Test that latency measurement works correctly."""
    
    def test_analyze_latency_is_non_negative(self):
        """Test analyze endpoint returns non-negative latency."""
        img_buf = create_test_image()
        
        response = client.post(
            "/analyze",
            files={"image": ("test.jpg", img_buf, "image/jpeg")}
        )
        
        assert response.status_code == 200
        data = response.json()
        assert data["latency_ms"] >= 0
    
    def test_semantic_analyze_latency_is_non_negative(self):
        """Test semantic-analyze endpoint returns non-negative latency."""
        img_buf = create_test_image()
        
        response = client.post(
            "/semantic-analyze",
            files={"image": ("test.jpg", img_buf, "image/jpeg")},
            data={"mode": "surroundings"}
        )
        
        assert response.status_code == 200
        data = response.json()
        assert data["latency_ms"] >= 0
