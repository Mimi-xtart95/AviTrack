package org.avitrack.app.data.export

import androidx.compose.runtime.Composable

actual object CsvFileSaver {
    @Composable
    actual fun rememberSave(onResult: (CsvExportResult) -> Unit): (String, String) -> Unit = { _, _ ->
        onResult(CsvExportResult.Failed)
    }
}
