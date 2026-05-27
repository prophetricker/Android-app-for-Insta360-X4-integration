from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

from omnieye_cloud.main import app
from omnieye_cloud.runtime_config import RuntimeConfig, load_env_file
from omnieye_cloud.semantic import SemanticAnalyzer, SemanticMode, _prompt_for_mode


client = TestClient(app)


def make_jpeg() -> bytes:
    image = Image.new("RGB", (16, 8), color=(90, 120, 80))
    buffer = BytesIO()
    image.save(buffer, format="JPEG")
    return buffer.getvalue()


def test_semantic_analyze_product_reports_model_unavailable_without_fake_detection(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    analyzer = SemanticAnalyzer(api_key=None)

    result = analyzer.analyze(make_temp_image_path(), SemanticMode.PRODUCT, query="milk")

    assert result.mode == "product"
    assert result.target_found is False
    assert result.product_name
    assert "milk" in result.summary
    assert "视觉模型" in result.summary
    assert "演示模式" not in result.summary
    assert result.traffic_light is None
    assert isinstance(result.objects, list)
    assert isinstance(result.latency_ms, int)
    assert result.confidence == 0.0
    assert result.fallback_reason == "openai_api_key_missing"


def make_temp_image_path():
    from pathlib import Path
    import tempfile

    path = Path(tempfile.gettempdir()) / "omnieye-test-semantic.jpg"
    path.write_bytes(make_jpeg())
    return path


def test_semantic_analyze_traffic_light_returns_demo_safe_fallback(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    analyzer = SemanticAnalyzer(api_key=None)

    result = analyzer.analyze(make_temp_image_path(), SemanticMode.TRAFFIC_LIGHT)

    assert result.mode == "traffic_light"
    assert result.traffic_light in {"red", "green", "yellow", "unknown"}
    assert result.product_name is None
    assert result.fallback_reason == "openai_api_key_missing"


def test_semantic_analyze_surroundings_returns_environment_summary(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    analyzer = SemanticAnalyzer(api_key=None)

    result = analyzer.analyze(make_temp_image_path(), SemanticMode.SURROUNDINGS)

    assert result.mode == "surroundings"
    assert result.product_name is None
    assert result.traffic_light in {"red", "green", "yellow", "unknown", None}
    assert result.objects
    assert "环境" in result.summary or "周围" in result.summary
    assert result.fallback_reason == "openai_api_key_missing"


def test_surroundings_prompt_requires_full_panorama_directions():
    prompt = _prompt_for_mode(SemanticMode.SURROUNDINGS, query=None)

    for text in ["前方", "后方", "左侧", "右侧", "头顶", "脚下"]:
        assert text in prompt
    assert "不要只描述正前方" in prompt


def test_semantic_analyze_surroundings_endpoint_contract(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    response = client.post(
        "/semantic-analyze",
        data={"mode": "surroundings"},
        files={"frame": ("frame.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["mode"] == "surroundings"
    assert isinstance(payload["summary"], str)
    assert isinstance(payload["objects"], list)
    assert payload["product_name"] is None


def test_semantic_analyze_surroundings_uses_model_payload(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")

    def return_surroundings_payload(self, image_path, mode, query):
        return {
            "summary": "周围是超市场景，左侧有饮品货架，前方通道暂时通畅。",
            "objects": ["shelf", "aisle", "drink"],
            "traffic_light": None,
            "target_found": True,
            "product_name": None,
            "confidence": 0.76,
        }

    monkeypatch.setattr(SemanticAnalyzer, "_call_openai", return_surroundings_payload)

    result = SemanticAnalyzer().analyze(make_temp_image_path(), SemanticMode.SURROUNDINGS)

    assert result.summary == "周围是超市场景，左侧有饮品货架，前方通道暂时通畅。"
    assert result.objects == ["shelf", "aisle", "drink"]
    assert result.confidence == 0.76
    assert result.fallback_reason is None


def test_semantic_analyze_uses_chat_completions_after_responses_permission_error(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")

    def raise_permission_error(self, image_path, mode, query):
        raise PermissionError("responses API not allowed")

    def return_chat_payload(self, image_path, mode, query):
        return {
            "summary": "你在室内桌边，前方有窗户，身边有椅子和桌面物品，请先确认桌沿位置再移动。",
            "objects": ["table edge", "chairs", "window"],
            "traffic_light": None,
            "target_found": True,
            "product_name": None,
            "confidence": 0.82,
        }

    monkeypatch.setattr(SemanticAnalyzer, "_call_openai", raise_permission_error)
    monkeypatch.setattr(SemanticAnalyzer, "_call_openai_chat_completions", return_chat_payload)

    result = SemanticAnalyzer().analyze(make_temp_image_path(), SemanticMode.SURROUNDINGS)

    assert "桌边" in result.summary
    assert result.objects == ["table edge", "chairs", "window"]
    assert result.confidence == 0.82
    assert result.fallback_reason is None


def test_semantic_analyze_rejects_unknown_mode():
    response = client.post(
        "/semantic-analyze",
        data={"mode": "face"},
        files={"frame": ("frame.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 422


def test_semantic_analyze_reports_openai_error_reason(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")

    def raise_auth_error(self, image_path, mode, query):
        raise RuntimeError("Error code: 401 - invalid_api_key")

    def raise_chat_auth_error(self, image_path, mode, query):
        raise PermissionError("chat completions also rejected")

    monkeypatch.setattr(SemanticAnalyzer, "_call_openai", raise_auth_error)
    monkeypatch.setattr(SemanticAnalyzer, "_call_openai_chat_completions", raise_chat_auth_error)

    response = client.post(
        "/semantic-analyze",
        data={"mode": "product", "query": "milk"},
        files={"frame": ("shelf.jpg", make_jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["fallback_reason"] == "openai_error: RuntimeError; chat_error: PermissionError"
    assert "演示模式" not in payload["summary"]
    assert "视觉模型" in payload["summary"]


def test_config_status_reports_openai_dependency_without_secret():
    response = client.get("/config/status")

    assert response.status_code == 200
    payload = response.json()
    assert "openai_api_key_set" in payload
    assert "openai_installed" in payload
    assert "openai_base_url_set" in payload
    assert "openai_api_key" not in payload


def test_load_env_file_sets_missing_values(tmp_path, monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_VISION_MODEL", raising=False)
    monkeypatch.delenv("OPENAI_BASE_URL", raising=False)
    monkeypatch.delenv("OPENAI_USER_AGENT", raising=False)
    env_file = tmp_path / ".env"
    env_file.write_text(
        "OPENAI_API_KEY=sk-test\n"
        "OPENAI_VISION_MODEL=gpt-test\n"
        "OPENAI_BASE_URL=https://xlabapi.com/v1\n"
        "OPENAI_USER_AGENT=claude-cli/2.0.76 (external, cli)\n",
        encoding="utf-8",
    )

    load_env_file(env_file)

    assert RuntimeConfig.from_env().openai_api_key_set is True
    assert RuntimeConfig.from_env().openai_vision_model == "gpt-test"
    assert RuntimeConfig.from_env().openai_base_url_set is True
    assert RuntimeConfig.from_env().openai_user_agent_set is True


def test_load_env_file_replaces_empty_process_values(tmp_path, monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "")
    monkeypatch.setenv("OPENAI_VISION_MODEL", "")
    monkeypatch.setenv("OPENAI_BASE_URL", "")
    env_file = tmp_path / ".env"
    env_file.write_text(
        "OPENAI_API_KEY=sk-test\n"
        "OPENAI_VISION_MODEL=Qwen3.6-27B\n"
        "OPENAI_BASE_URL=https://spiritx-api-hh.xzinfra.com:1443/spiritx-api/v1\n",
        encoding="utf-8",
    )

    load_env_file(env_file)

    assert RuntimeConfig.from_env().openai_api_key_set is True
    assert RuntimeConfig.from_env().openai_vision_model == "Qwen3.6-27B"
    assert RuntimeConfig.from_env().openai_base_url_set is True


def test_semantic_analyzer_reads_openai_base_url(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("OPENAI_BASE_URL", "https://xlabapi.com/v1")
    monkeypatch.setenv("OPENAI_USER_AGENT", "claude-cli/2.0.76 (external, cli)")

    analyzer = SemanticAnalyzer()

    assert analyzer.base_url == "https://xlabapi.com/v1"
    assert analyzer.default_headers == {"User-Agent": "claude-cli/2.0.76 (external, cli)"}
