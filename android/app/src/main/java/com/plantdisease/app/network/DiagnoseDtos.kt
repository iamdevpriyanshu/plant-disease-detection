package com.plantdisease.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiagnoseResponseDto(
    @SerialName("disease_name") val diseaseName: String,
    @SerialName("causes") val causes: String,
    @SerialName("medicines_or_treatment") val medicinesOrTreatment: String,
    @SerialName("confidence") val confidence: Double,
    @SerialName("disclaimer") val disclaimer: String,
    @SerialName("model_version") val modelVersion: String,
)
