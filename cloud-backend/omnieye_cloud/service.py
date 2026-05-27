from __future__ import annotations

import threading
import time
from pathlib import Path

from .analysis import AnalyzeResult, FALLBACK_RESULT, analyze_depth_map
from .dap_adapter import DapDepthEstimator


class AnalysisService:
    def __init__(
        self,
        upload_dir: Path,
        estimator: DapDepthEstimator | None = None,
        interval_s: float = 1.5,
    ):
        self.upload_dir = upload_dir
        self.estimator = estimator or DapDepthEstimator()
        self.interval_s = interval_s
        self._latest_frame: Path | None = None
        self._latest_result: AnalyzeResult = FALLBACK_RESULT
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.upload_dir.mkdir(parents=True, exist_ok=True)

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2)
            self._thread = None

    def save_latest_frame(self, content: bytes, suffix: str = ".jpg") -> AnalyzeResult:
        frame_path = self.upload_dir / f"latest{suffix}"
        frame_path.write_bytes(content)
        with self._lock:
            self._latest_frame = frame_path
            return self._latest_result

    def latest_result(self) -> AnalyzeResult:
        with self._lock:
            return self._latest_result

    def latest_frame_path(self) -> Path | None:
        with self._lock:
            return self._latest_frame

    def _run(self) -> None:
        while not self._stop.is_set():
            with self._lock:
                frame = self._latest_frame
            if frame is not None:
                depth = self.estimator.infer_depth(frame)
                if depth is not None:
                    result = analyze_depth_map(depth)
                    with self._lock:
                        self._latest_result = result
            self._stop.wait(self.interval_s)

    def process_once_for_tests(self) -> AnalyzeResult:
        with self._lock:
            frame = self._latest_frame
        if frame is None:
            return self.latest_result()
        depth = self.estimator.infer_depth(frame)
        if depth is None:
            return self.latest_result()
        result = analyze_depth_map(depth)
        with self._lock:
            self._latest_result = result
        time.sleep(0)
        return result
