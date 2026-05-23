from __future__ import annotations

import os
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np


@dataclass(frozen=True)
class DapConfig:
    repo_dir: Path | None
    weights_path: Path | None
    device: str
    python_executable: Path

    @classmethod
    def from_env(cls) -> "DapConfig":
        repo_dir = os.getenv("DAP_REPO_DIR")
        weights_path = os.getenv("DAP_WEIGHTS_PATH")
        python_executable = os.getenv("DAP_PYTHON") or sys.executable
        return cls(
            repo_dir=Path(repo_dir) if repo_dir else None,
            weights_path=Path(weights_path) if weights_path else None,
            device=os.getenv("DAP_DEVICE", "cuda"),
            python_executable=Path(python_executable),
        )

    @property
    def ready(self) -> bool:
        return bool(
            self.repo_dir
            and self.repo_dir.exists()
            and self.weights_path
            and self.weights_path.exists()
        )


class DapDepthEstimator:
    def __init__(self, config: DapConfig | None = None, timeout_s: int = 120):
        self.config = config or DapConfig.from_env()
        self.timeout_s = timeout_s

    def infer_depth(self, image_path: Path) -> np.ndarray | None:
        if not self.config.ready:
            return None

        assert self.config.repo_dir is not None
        assert self.config.weights_path is not None

        with tempfile.TemporaryDirectory(prefix="omnieye-dap-") as tmp:
            tmp_path = Path(tmp)
            list_path = tmp_path / "images.txt"
            output_dir = tmp_path / "output"
            list_path.write_text(str(image_path), encoding="utf-8")

            env = os.environ.copy()
            env["DAP_REPO_DIR"] = str(self.config.repo_dir)
            env["DAP_WEIGHTS_PATH"] = str(self.config.weights_path)
            env["DAP_DEVICE"] = self.config.device

            command = [
                str(self.config.python_executable),
                "-m",
                "omnieye_cloud.dap_runner",
                "--image-list",
                str(list_path),
                "--output",
                str(output_dir),
            ]
            subprocess.run(
                command,
                check=True,
                timeout=self.timeout_s,
                cwd=Path(__file__).resolve().parents[1],
                env=env,
                capture_output=True,
                text=True,
            )

            depth_path = output_dir / "depth_npy" / "000001.npy"
            if not depth_path.exists():
                return None
            return np.load(depth_path)
