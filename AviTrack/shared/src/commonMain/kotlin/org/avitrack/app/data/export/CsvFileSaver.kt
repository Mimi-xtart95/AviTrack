package org.avitrack.app.data.export

import androidx.compose.runtime.Composable

sealed interface CsvExportResult {
    data class Saved(val fileName: String, val location: CsvExportLocation) : CsvExportResult
    data object Failed : CsvExportResult
    data object Cancelled : CsvExportResult
}

sealed interface CsvExportLocation {
    data class FilePath(val path: String) : CsvExportLocation
    data object BrowserDownloads : CsvExportLocation
    data object SelectedDocument : CsvExportLocation
}

expect object CsvFileSaver {
    @Composable
    fun rememberSave(onResult: (CsvExportResult) -> Unit): (fileName: String, content: String) -> Unit
}
