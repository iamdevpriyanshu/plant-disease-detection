from __future__ import annotations

import logging
from typing import Annotated, Optional

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.classifier import ClassificationResult, PlantClassifier, StubPlantClassifier
from app.config import settings
from app.llm_client import DEFAULT_DISCLAIMER, fetch_llm_details
from app.schemas import DiagnoseResponse, ErrorResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Plant Disease Diagnosis API",
    version="1.0.0",
    description="Single-round-trip diagnosis: image → classifier → optional LLM causes/treatment text.",
)

_origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
if _origins == ["*"]:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )
else:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )


def get_classifier() -> PlantClassifier:
    return StubPlantClassifier()


@app.get("/health")
async def health():
    return {"status": "ok", "model_version": settings.classifier_version}


@app.post(
    "/v1/diagnose",
    response_model=DiagnoseResponse,
    responses={413: {"model": ErrorResponse}, 400: {"model": ErrorResponse}},
)
async def diagnose(
    image: Annotated[UploadFile, File(description="JPEG/PNG plant leaf image")],
    locale: Annotated[Optional[str], Form()] = None,
    classifier: PlantClassifier = Depends(get_classifier),
):
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Expected an image file (image/*).")

    raw = await image.read()
    if len(raw) > settings.max_upload_bytes:
        raise HTTPException(
            status_code=413,
            detail=f"Image too large; max {settings.max_upload_bytes} bytes.",
        )
    if len(raw) == 0:
        raise HTTPException(status_code=400, detail="Empty file.")

    result: ClassificationResult = classifier.classify(raw)
    llm = await fetch_llm_details(result.disease_name, locale)

    if llm:
        return DiagnoseResponse(
            disease_name=result.disease_name,
            causes=llm.causes,
            medicines_or_treatment=llm.medicines_or_treatment,
            confidence=result.confidence,
            disclaimer=DEFAULT_DISCLAIMER,
            model_version=settings.classifier_version,
        )

    return DiagnoseResponse(
        disease_name=result.disease_name,
        causes="Details unavailable (LLM not configured or request failed).",
        medicines_or_treatment="Details unavailable (LLM not configured or request failed).",
        confidence=result.confidence,
        disclaimer=DEFAULT_DISCLAIMER,
        model_version=settings.classifier_version,
    )
