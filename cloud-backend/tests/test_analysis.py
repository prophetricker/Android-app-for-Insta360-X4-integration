from pathlib import Path

import numpy as np

from omnieye_cloud.analysis import (
    FALLBACK_RESULT,
    analyze_depth_map,
    classify_distance,
    scene_text_for_level,
)
from omnieye_cloud.dap_adapter import DapConfig, DapDepthEstimator
from omnieye_cloud.dap_runner import build_runtime_config, build_subprocess_env
from omnieye_cloud.service import AnalysisService


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


def test_dap_runner_overrides_weight_dir_in_runtime_config(tmp_path):
    source_config = tmp_path / "infer.yaml"
    source_config.write_text(
        "model:\n"
        "  name: dap\n"
        "load_weights_dir: /remote/training/path\n"
        "input:\n"
        "  height: 512\n",
        encoding="utf-8",
    )
    weights = tmp_path / "weights" / "model.pth"
    weights.parent.mkdir()
    weights.write_bytes(b"fake")

    runtime_config = build_runtime_config(source_config, weights, tmp_path)

    assert runtime_config != source_config
    text = runtime_config.read_text(encoding="utf-8")
    assert "load_weights_dir: /remote/training/path" not in text
    assert f'load_weights_dir: "{weights.parent.as_posix()}"' in text
    assert "model:\n  name: dap" in text


def test_dap_runner_forces_utf8_for_windows_subprocess_output(monkeypatch):
    monkeypatch.setenv("DAP_DEVICE", "cuda")

    env = build_subprocess_env()

    assert env["PYTHONIOENCODING"] == "utf-8"
    assert env["PYTHONUTF8"] == "1"
    assert env["CUDA_VISIBLE_DEVICES"] == "0"


def test_dap_config_reads_depth_scale_from_env(monkeypatch):
    monkeypatch.setenv("DAP_DEPTH_SCALE", "100")

    config = DapConfig.from_env()

    assert config.depth_scale == 100.0


def test_dap_depth_estimator_uses_absolute_image_paths_and_utf8(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    image_path = Path("frame.jpg")
    image_path.write_bytes(b"fake")
    repo_dir = tmp_path / "dap"
    repo_dir.mkdir()
    weights_path = tmp_path / "weights" / "model.pth"
    weights_path.parent.mkdir()
    weights_path.write_bytes(b"fake")

    captured = {}

    def fake_run(command, **kwargs):
        captured.update(kwargs)
        image_list = Path(command[command.index("--image-list") + 1])
        captured["image_path"] = image_list.read_text(encoding="utf-8")
        output_dir = Path(command[-1])
        depth_dir = output_dir / "depth_npy"
        depth_dir.mkdir(parents=True)
        np.save(depth_dir / "000001.npy", np.array([[0.5]], dtype=np.float32))

    monkeypatch.setattr("omnieye_cloud.dap_adapter.subprocess.run", fake_run)
    config = DapConfig(
        repo_dir=repo_dir,
        weights_path=weights_path,
        device="cuda",
        python_executable=Path("python"),
        depth_scale=100.0,
    )

    depth = DapDepthEstimator(config=config).infer_depth(image_path)

    assert depth.tolist() == [[50.0]]
    assert Path(captured["image_path"]).is_absolute()
    assert captured["encoding"] == "utf-8"
    assert captured["errors"] == "replace"


def test_analysis_service_can_restart_after_stop(tmp_path):
    service = AnalysisService(upload_dir=tmp_path, interval_s=999)

    service.start()
    service.stop()
    service.start()

    try:
        assert service._thread is not None
        assert service._thread.is_alive()
    finally:
        service.stop()
