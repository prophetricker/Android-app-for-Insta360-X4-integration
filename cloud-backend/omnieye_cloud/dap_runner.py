from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


def build_runtime_config(source_config: Path, weights: Path, work_dir: Path) -> Path:
    text = source_config.read_text(encoding="utf-8")
    weights_dir = weights.parent.as_posix()
    replacement = f'load_weights_dir: "{weights_dir}"'

    if re.search(r"(?m)^load_weights_dir:\s*.*$", text):
        text = re.sub(r"(?m)^load_weights_dir:\s*.*$", replacement, text)
    else:
        text = text.rstrip() + "\n" + replacement + "\n"

    work_dir.mkdir(parents=True, exist_ok=True)
    runtime_config = work_dir / "infer.runtime.yaml"
    runtime_config.write_text(text, encoding="utf-8")
    return runtime_config


def build_subprocess_env() -> dict[str, str]:
    env = os.environ.copy()
    env["PYTHONIOENCODING"] = "utf-8"
    env["PYTHONUTF8"] = "1"
    if os.getenv("DAP_DEVICE", "cuda").startswith("cuda"):
        env.setdefault("CUDA_VISIBLE_DEVICES", "0")
    return env


def main() -> int:
    parser = argparse.ArgumentParser(description="Run external DAP inference.")
    parser.add_argument("--image-list", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    repo_dir = os.getenv("DAP_REPO_DIR")
    weights_path = os.getenv("DAP_WEIGHTS_PATH")
    if not repo_dir or not weights_path:
        return 2

    repo = Path(repo_dir)
    weights = Path(weights_path)
    weights_dir = weights.parent
    if not (repo / "test" / "infer.py").exists() or not weights.exists():
        return 2

    config_path = repo / "config" / "infer.yaml"
    if not config_path.exists():
        return 2

    expected_model = weights_dir / "model.pth"
    if expected_model != weights:
        expected_model.parent.mkdir(parents=True, exist_ok=True)
        if not expected_model.exists():
            shutil.copy2(weights, expected_model)

    runtime_config_path = build_runtime_config(
        config_path,
        weights,
        Path(args.output).resolve().parent,
    )

    command = [
        sys.executable,
        str(repo / "test" / "infer.py"),
        "--config",
        str(runtime_config_path),
        "--txt",
        str(Path(args.image_list).resolve()),
        "--output",
        str(Path(args.output).resolve()),
    ]
    subprocess.run(command, cwd=repo, env=build_subprocess_env(), check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
