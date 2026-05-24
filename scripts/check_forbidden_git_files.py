from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable


FORBIDDEN_PATH_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"(^|[/\\])\.sdk_extract([/\\]|$)",
        r"赛事SDK包",
        r"(^|[/\\])Insta360 X4 SDK([/\\]|$)",
        r"CameraSDK",
        r"MediaSDK",
        r"DAP-weights",
        r"(^|[/\\])DAP([/\\]|$)",
        r"\.(aar|so|dll|dylib|rar|7z|pth|onnx|ckpt)$",
    ]
]


@dataclass(frozen=True)
class ForbiddenPath:
    path: str
    reason: str


def default_size_lookup(path: str) -> int:
    try:
        return Path(path).stat().st_size
    except OSError:
        return 0


def find_forbidden_paths(
    paths: Iterable[str],
    *,
    max_size_bytes: int,
    size_lookup: Callable[[str], int] = default_size_lookup,
) -> list[ForbiddenPath]:
    violations: list[ForbiddenPath] = []
    for path in paths:
        normalized = path.replace("\\", "/")
        for pattern in FORBIDDEN_PATH_PATTERNS:
            if pattern.search(normalized):
                violations.append(ForbiddenPath(path=path, reason="forbidden SDK/model artifact"))
                break
        else:
            size = size_lookup(path)
            if size > max_size_bytes:
                violations.append(
                    ForbiddenPath(
                        path=path,
                        reason=f"file exceeds {max_size_bytes} bytes",
                    )
                )
    return violations


def git_tracked_files() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return [line for line in result.stdout.splitlines() if line.strip()]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Fail if Git tracks Insta360 SDK, model weights, archives, or large binary artifacts."
    )
    parser.add_argument(
        "--max-size-mb",
        type=int,
        default=100,
        help="Maximum allowed tracked file size in MiB.",
    )
    args = parser.parse_args(argv)

    max_size_bytes = args.max_size_mb * 1024 * 1024
    violations = find_forbidden_paths(
        git_tracked_files(),
        max_size_bytes=max_size_bytes,
        size_lookup=lambda path: os.path.getsize(path) if os.path.exists(path) else 0,
    )

    if not violations:
        print("No forbidden Git-tracked SDK/model/large artifacts found.")
        return 0

    print("Forbidden Git-tracked files found:", file=sys.stderr)
    for violation in violations:
        print(f"- {violation.path}: {violation.reason}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
