from __future__ import annotations

import json
import logging
from dataclasses import dataclass

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

DEFAULT_DISCLAIMER = (
    "Informational only; not professional agronomic or medical advice. "
    "Confirm with a qualified agricultural extension officer or plant pathologist before acting."
)


@dataclass(frozen=True)
class LlmDetailResult:
    causes: str
    medicines_or_treatment: str


# Bounded cache: repeat lookups for same disease label avoid extra LLM calls (helps on slow 3G).
_LLM_CACHE: dict[tuple[str, str], LlmDetailResult] = {}
_LLM_CACHE_MAX = 256

SYSTEM_PROMPT = """You are an agricultural information assistant. The user will give a plant disease label from an automated classifier (may be wrong).

Respond with a single JSON object only, no markdown, with exactly these keys:
- "causes": 2-4 short sentences on typical causes and conditions (fungal/bacterial/environmental where relevant). Use cautious wording.
- "medicines_or_treatment": 2-5 bullet-style sentences as plain text (use semicolons between ideas). Suggest general classes of remedies (e.g. fungicide, sanitation, resistant varieties) without specific dosages, brand names, or guarantees. Emphasize local expert verification.

Do not claim certainty. If the label seems non-specific, say what additional checks a farmer could do."""


async def fetch_llm_details(disease_name: str, locale: str | None) -> LlmDetailResult | None:
    cache_key = (disease_name, locale or "")
    if cache_key in _LLM_CACHE:
        return _LLM_CACHE[cache_key]

    key = settings.openai_api_key
    if not key or not key.strip():
        logger.warning("OPENAI_API_KEY not set; skipping LLM enrichment")
        return None

    user = f'Disease label (from vision model): "{disease_name}"\nLocale hint: {locale or "en"}'
    payload = {
        "model": settings.openai_model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user},
        ],
        "response_format": {"type": "json_object"},
        "temperature": 0.4,
    }
    url = f"{settings.openai_base_url.rstrip('/')}/chat/completions"
    headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}

    try:
        async with httpx.AsyncClient(timeout=settings.llm_timeout_seconds) as client:
            r = await client.post(url, headers=headers, json=payload)
            r.raise_for_status()
            data = r.json()
        content = data["choices"][0]["message"]["content"]
        obj = json.loads(content)
        causes = str(obj.get("causes", "")).strip()
        meds = str(obj.get("medicines_or_treatment", "")).strip()
        if not causes and not meds:
            return None
        out = LlmDetailResult(
            causes=causes or "Details unavailable.",
            medicines_or_treatment=meds or "Details unavailable.",
        )
        if len(_LLM_CACHE) >= _LLM_CACHE_MAX:
            _LLM_CACHE.clear()
        _LLM_CACHE[cache_key] = out
        return out
    except Exception as e:
        logger.exception("LLM request failed: %s", e)
        return None
