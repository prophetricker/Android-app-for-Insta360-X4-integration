import numpy as np

from omnieye_cloud.analysis import (
    FALLBACK_RESULT,
    analyze_depth_map,
    classify_distance,
    scene_text_for_level,
)
from omnieye_cloud.dap_adapter import DapConfig


def test_classify_distance_thresholds():
    assert classify_distance(3.2) == 0
    assert classify_distance(2.0) == 1
    assert classify_distance(1.0) == 2
    assert classify_distance(0.6) == 3
    assert classify_distance(0.3) == 4


def test_scene_text_maps_level_to_chinese_guidance():
    assert scene_text_for_level(0).startswith("前方较远")
    assert "一米" in scene_text_for_level(2)
    assert "避让" in scene_text_for_level(3)
    assert "立即停下" in scene_text_for_level(4)


def test_analyze_depth_map_uses_center_roi_median():
    depth = np.full((10, 10), 4.0, dtype=np.float32)
    depth[4:6, 4:6] = np.array([[0.6, 0.8], [1.0, 1.2]], dtype=np.float32)

    result = analyze_depth_map(depth)

    assert result.distance_m == 0.9
    assert result.level == 2
    assert result.confidence == 0.8
    assert "一米" in result.scene_text


def test_analyze_depth_map_ignores_invalid_values():
    depth = np.full((8, 8), np.nan, dtype=np.float32)
    depth[3:5, 3:5] = np.array([[np.inf, 0.45], [0.55, -1.0]], dtype=np.float32)

    result = analyze_depth_map(depth)

    assert result.distance_m == 0.5
    assert result.level == 3
    assert "避让" in result.scene_text


def test_analyze_depth_map_returns_fallback_when_roi_invalid():
    depth = np.full((8, 8), np.nan, dtype=np.float32)

    result = analyze_depth_map(depth)

    assert result == FALLBACK_RESULT


def test_dap_config_reads_external_paths_from_env(monkeypatch):
    monkeypatch.setenv("DAP_REPO_DIR", "D:/Models/DAP")
    monkeypatch.setenv("DAP_WEIGHTS_PATH", "D:/Models/DAP-weights/model.pth")
    monkeypatch.setenv("DAP_DEVICE", "cuda")
    monkeypatch.setenv("DAP_PYTHON", "D:/Miniconda/envs/dap/python.exe")

    config = DapConfig.from_env()

    assert str(config.repo_dir) == "D:\\Models\\DAP"
    assert str(config.weights_path) == "D:\\Models\\DAP-weights\\model.pth"
    assert config.device == "cuda"
    assert str(config.python_executable) == "D:\\Miniconda\\envs\\dap\\python.exe"
