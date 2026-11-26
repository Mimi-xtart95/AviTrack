package org.avitrack.app.data.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

actual object CsvFileSaver {
    @Composable
    actual fun rememberSave(onResult: (CsvExportResult) -> Unit): (String, String) -> Unit {
        val scope = rememberCoroutineScope()
        return { name, content ->
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val path = writeCsv(name, content)
                        CsvExportResult.Saved(path.fileName.toString(), CsvExportLocation.FilePath(path.toString()))
                    }.getOrElse { CsvExportResult.Failed }
                }
                onResult(saved)
            }
        }
    }

    private fun writeCsv(name: String, content: String): Path {
        val home = Path.of(System.getProperty("user.home"))
        val downloads = listOf(home.resolve("Downloads"), home.resolve("Descargas"))
            .firstOrNull { Files.isDirectory(it) } ?: home
        val baseName = name.removeSuffix(".csv")
        var copy = 0
        while (true) {
            val target = downloads.resolve(if (copy == 0) name else "$baseName (${copy + 1}).csv")
            val stream = try {
                Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            } catch (_: FileAlreadyExistsException) {
                copy++
                continue
            }
            stream.use { it.write(content.encodeToByteArray()) }
            return target.toAbsolutePath()
        }
    }
}
