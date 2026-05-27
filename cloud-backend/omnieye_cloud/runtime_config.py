from __future__ import annotations

import importlib.util
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


@dataclass(frozen=True)
class RuntimeConfig:
    openai_api_key_set: bool
    openai_installed: bool
    openai_vision_model: str
    openai_base_url_set: bool
    openai_user_agent_set: bool
    dap_repo_dir_set: bool
    dap_weights_path_set: bool

    @classmethod
    def from_env(cls) -> "RuntimeConfig":
        return cls(
            openai_api_key_set=bool(os.getenv("OPENAI_API_KEY")),
            openai_installed=importlib.util.find_spec("openai") is not None,
            openai_vision_model=os.getenv("OPENAI_VISION_MODEL", "gpt-4.1-mini"),
            openai_base_url_set=bool(os.getenv("OPENAI_BASE_URL")),
            openai_user_agent_set=bool(os.getenv("OPENAI_USER_AGENT")),
            dap_repo_dir_set=bool(os.getenv("DAP_REPO_DIR")),
            dap_weights_path_set=bool(os.getenv("DAP_WEIGHTS_PATH")),
        )


def load_env_file(path: Path) -> bool:
    if not path.exists():
        return False
    return load_dotenv(path, override=False)
