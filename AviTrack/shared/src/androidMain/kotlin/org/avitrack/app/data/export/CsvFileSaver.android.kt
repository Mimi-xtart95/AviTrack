package org.avitrack.app.data.export

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

actual object CsvFileSaver {
    private data class PendingExport(val name: String, val content: String)

    @Composable
    actual fun rememberSave(onResult: (CsvExportResult) -> Unit): (String, String) -> Unit {
        val resolver = LocalContext.current.contentResolver
        val scope = rememberCoroutineScope()
        val pending = remember { mutableStateOf<PendingExport?>(null) }
        val contract = remember { ActivityResultContracts.CreateDocument("text/csv") }
        val launcher = rememberLauncherForActivityResult(contract) { uri ->
            val request = pending.value
            pending.value = null
            if (uri == null) {
                onResult(CsvExportResult.Cancelled)
            } else if (request == null) {
                onResult(CsvExportResult.Failed)
            } else {
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching {
                            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open CSV output.")
                            stream.use { it.write(request.content.encodeToByteArray()) }
                            val name = runCatching {
                                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                            }.getOrNull() ?: request.name
                            CsvExportResult.Saved(name, CsvExportLocation.SelectedDocument)
                        }.getOrElse { CsvExportResult.Failed }
                    }
                    onResult(saved)
                }
            }
        }
        return { name, content ->
            if (pending.value == null) {
                pending.value = PendingExport(name, content)
                if (runCatching { launcher.launch(name) }.isFailure) {
                    pending.value = null
                    onResult(CsvExportResult.Failed)
                }
            }
        }
    }
}
