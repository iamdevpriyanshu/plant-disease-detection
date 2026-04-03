from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    max_upload_bytes: int = 153_600  # ~150KB; client targets ≤100KB JPEG
    # Env: CLASSIFIER_VERSION (avoid pydantic "model_" protected namespace)
    classifier_version: str = "stub-1.0"
    openai_api_key: Optional[str] = None
    openai_base_url: str = "https://api.openai.com/v1"
    openai_model: str = "gpt-4o-mini"
    llm_timeout_seconds: float = 60.0
    cors_origins: str = "*"


settings = Settings()
