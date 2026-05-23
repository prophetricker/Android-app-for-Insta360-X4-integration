from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


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

    env = os.environ.copy()
    if os.getenv("DAP_DEVICE", "cuda").startswith("cuda"):
        env.setdefault("CUDA_VISIBLE_DEVICES", "0")

    command = [
        sys.executable,
        str(repo / "test" / "infer.py"),
        "--config",
        str(config_path),
        "--txt",
        str(Path(args.image_list).resolve()),
        "--output",
        str(Path(args.output).resolve()),
    ]
    subprocess.run(command, cwd=repo, env=env, check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
