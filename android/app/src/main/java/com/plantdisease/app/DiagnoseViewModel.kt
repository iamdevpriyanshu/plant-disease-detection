package com.plantdisease.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantdisease.app.image.compressPlantPhotoToJpeg
import com.plantdisease.app.network.DiagnoseApi
import com.plantdisease.app.network.DiagnoseResponseDto
import com.plantdisease.app.network.RetrofitFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.ContentResolver
import android.net.Uri

sealed interface DiagnoseUiState {
    data object Idle : DiagnoseUiState
    data object Compressing : DiagnoseUiState
    data object Uploading : DiagnoseUiState
    data class Success(val body: DiagnoseResponseDto, val jpegSizeBytes: Int) : DiagnoseUiState
    data class Error(val message: String) : DiagnoseUiState
}

class DiagnoseViewModel(
    private val api: DiagnoseApi = RetrofitFactory.create(),
) : ViewModel() {

    private val _state = MutableStateFlow<DiagnoseUiState>(DiagnoseUiState.Idle)
    val state: StateFlow<DiagnoseUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = DiagnoseUiState.Idle
    }

    fun diagnose(resolver: ContentResolver, imageUri: Uri) {
        viewModelScope.launch {
            _state.value = DiagnoseUiState.Compressing
            val jpeg = try {
                withContext(Dispatchers.Default) {
                    compressPlantPhotoToJpeg(resolver, imageUri)
                }
            } catch (e: Exception) {
                _state.value = DiagnoseUiState.Error(e.message ?: "Compression failed")
                return@launch
            }
            _state.value = DiagnoseUiState.Uploading
            try {
                val body = jpeg.toRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("image", "leaf.jpg", body)
                val response = api.diagnose(part)
                _state.value = DiagnoseUiState.Success(response, jpeg.size)
            } catch (e: Exception) {
                _state.value = DiagnoseUiState.Error(e.message ?: "Network error")
            }
        }
    }
}
