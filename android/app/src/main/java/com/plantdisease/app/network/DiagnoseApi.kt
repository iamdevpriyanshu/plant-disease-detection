package com.plantdisease.app.network

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface DiagnoseApi {
    @Multipart
    @POST("v1/diagnose")
    suspend fun diagnose(
        @Part image: MultipartBody.Part,
    ): DiagnoseResponseDto
}
