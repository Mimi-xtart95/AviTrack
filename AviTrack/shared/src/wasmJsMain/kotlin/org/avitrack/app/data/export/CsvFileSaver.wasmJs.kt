package org.avitrack.app.data.export

import androidx.compose.runtime.Composable

actual object CsvFileSaver {
    @Composable
    actual fun rememberSave(onResult: (CsvExportResult) -> Unit): (String, String) -> Unit = { fileName, content ->
        val result = runCatching {
            downloadCsv(fileName, content)
            CsvExportResult.Saved(fileName, CsvExportLocation.BrowserDownloads)
        }.getOrElse { CsvExportResult.Failed }
        onResult(result)
    }
}

private fun downloadCsv(fileName: String, content: String): Unit = js("""{
    const url = URL.createObjectURL(new Blob([content], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    try {
      link.click();
    } finally {
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
}""")
