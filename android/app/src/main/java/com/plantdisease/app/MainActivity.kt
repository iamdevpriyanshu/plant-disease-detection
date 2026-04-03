package com.plantdisease.app

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plantdisease.app.ui.theme.PlantDiseaseTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantDiseaseTheme {
                val vm: DiagnoseViewModel = viewModel()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    DiagnoseScreen(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun DiagnoseScreen(vm: DiagnoseViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        pendingUri = uri
        vm.reset()
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (!success) pendingUri = null
        else vm.reset()
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = context.createCaptureUri()
            pendingUri = uri
            takePicture.launch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.compression_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.pick_image))
            }
            OutlinedButton(
                onClick = {
                    when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                        android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                            val uri = context.createCaptureUri()
                            pendingUri = uri
                            takePicture.launch(uri)
                        }
                        else -> requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.take_photo))
            }
        }

        Button(
            onClick = {
                val u = pendingUri ?: return@Button
                vm.diagnose(context.contentResolver, u)
            },
            enabled = pendingUri != null && state !is DiagnoseUiState.Compressing && state !is DiagnoseUiState.Uploading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.analyze))
        }

        when (val s = state) {
            DiagnoseUiState.Idle -> {
                if (pendingUri != null) {
                    Text(
                        text = "Photo ready. Tap ${stringResource(R.string.analyze)}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            DiagnoseUiState.Compressing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp), strokeWidth = 3.dp)
                    Text("Compressing image…")
                }
            }
            DiagnoseUiState.Uploading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.uploading))
                }
            }
            is DiagnoseUiState.Success -> ResultTable(s)
            is DiagnoseUiState.Error -> Text(
                text = s.message.ifBlank { stringResource(R.string.error_generic) },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ResultTable(success: DiagnoseUiState.Success) {
    val body = success.body
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = stringResource(R.string.confidence, body.confidence * 100.0),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "JPEG sent: ${success.jpegSizeBytes / 1024} KB (${success.jpegSizeBytes} bytes)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TableRow(stringResource(R.string.section_disease), body.diseaseName)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TableRow(stringResource(R.string.section_causes), body.causes)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TableRow(stringResource(R.string.section_medicines), body.medicinesOrTreatment)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = body.disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = "Model: ${body.modelVersion}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TableRow(header: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun android.content.Context.createCaptureUri(): Uri {
    val file = File.createTempFile("leaf_", ".jpg", cacheDir).apply { deleteOnExit() }
    val authority = "${packageName}.fileprovider"
    return FileProvider.getUriForFile(this, authority, file)
}
