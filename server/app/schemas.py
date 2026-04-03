from pydantic import BaseModel, Field


class DiagnoseResponse(BaseModel):
    disease_name: str
    causes: str
    medicines_or_treatment: str
    confidence: float = Field(ge=0.0, le=1.0)
    disclaimer: str
    model_version: str


class ErrorResponse(BaseModel):
    detail: str
