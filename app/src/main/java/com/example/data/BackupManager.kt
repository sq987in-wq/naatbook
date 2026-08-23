package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a backup entry to a destination below [baseDir]. Absolute paths,
 * non-ZIP path separators and canonical paths that escape the base directory
 * are rejected. Keeping this as a pure file-system function makes the Zip Slip
 * boundary directly testable on the JVM.
 */
internal fun safeBackupDestination(baseDir: File, entryName: String): File? {
    val segments = entryName.split('/')
    if (
        entryName.isBlank() || '\\' in entryName || File(entryName).isAbsolute ||
        segments.any { it.isBlank() || it == "." || it == ".." }
    ) return null
    val destination = File(baseDir, entryName)
    val basePath = baseDir.canonicalFile.path + File.separator
    return destination.takeIf { it.canonicalFile.path.startsWith(basePath) }
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NaatRepository
) {
    private companion object {
        // Zip safety limits (guard against zip bombs / malicious archives)
        const val MAX_ENTRIES = 10_000
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB of decompressed data
    }

    suspend fun exportBackup(outputUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val naatsList = repository.allNaats.first()
            val jsonArray = JSONArray()
            for (naat in naatsList) {
                val obj = JSONObject().apply {
                    put("id", naat.id)
                    put("title", naat.title)
                    put("poet", naat.poet ?: "")
                    put("category", naat.category)
                    put("lyrics", naat.lyrics ?: "")
                    put("audioType", naat.audioType)
                    put("audioPath", naat.audioPath ?: "")
                    put("isFavorite", naat.isFavorite)
                    put("createdAt", naat.createdAt)
                }
                jsonArray.put(obj)
            }

            val pfd = context.contentResolver.openFileDescriptor(outputUri, "w") 
                ?: return@withContext Result.failure(Exception("Could not open output stream"))
            val fos = FileOutputStream(pfd.fileDescriptor)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            // 1. Write the database JSON
            zos.putNextEntry(ZipEntry("naatbook_data.json"))
            val writer = BufferedWriter(OutputStreamWriter(zos, "UTF-8"))
            writer.write(jsonArray.toString(4))
            writer.flush()
            zos.closeEntry()

            // 2. Write all custom audio files
            val recordingsDir = File(context.filesDir, "recordings")
            val linkedDir = File(context.filesDir, "linked")

            packDirToZip(recordingsDir, "recordings/", zos)
            packDirToZip(linkedDir, "linked/", zos)

            zos.close()
            pfd.close()

            Result.success("Backup exported successfully")
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
            Result.failure(e)
        }
    }

    private fun packDirToZip(dir: File, prefix: String, zos: ZipOutputStream) {
        if (!dir.exists()) return
        val files = dir.listFiles() ?: return
        val buffer = ByteArray(1024 * 4)
        for (file in files) {
            if (file.isFile) {
                val entry = ZipEntry(prefix + file.name)
                zos.putNextEntry(entry)
                val fis = FileInputStream(file)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                fis.close()
                zos.closeEntry()
            }
        }
    }

    suspend fun importBackup(inputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri) 
                ?: return@withContext Result.failure(Exception("Could not open input stream"))
            val zis = ZipInputStream(BufferedInputStream(inputStream))

            var entry: ZipEntry? = zis.nextEntry
            var jsonContent = ""
            val buffer = ByteArray(1024 * 4)

            // Ensure destination directories exist
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()
            val linkedDir = File(context.filesDir, "linked")
            if (!linkedDir.exists()) linkedDir.mkdirs()

            var entryCount = 0
            var totalExtractedBytes = 0L
            while (entry != null) {
                if (++entryCount > MAX_ENTRIES) {
                    throw IOException("Backup archive has too many entries (>$MAX_ENTRIES)")
                }
                val name = entry.name
                when {
                    name == "naatbook_data.json" -> {
                        val reader = BufferedReader(InputStreamReader(zis, "UTF-8"))
                        jsonContent = reader.readText()
                    }
                    name.startsWith("recordings/") || name.startsWith("linked/") -> {
                        val destFile = safeBackupDestination(context.filesDir, name)
                        if (destFile == null) {
                            Log.w("BackupManager", "Skipped unsafe zip entry: $name")
                        } else {
                            destFile.parentFile?.mkdirs()
                            FileOutputStream(destFile).use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    totalExtractedBytes += len
                                    if (totalExtractedBytes > MAX_TOTAL_BYTES) {
                                        throw IOException("Backup archive exceeds maximum allowed size")
                                    }
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                    else -> Log.w("BackupManager", "Skipping unexpected zip entry: $name")
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            inputStream.close()

            if (jsonContent.isEmpty()) {
                return@withContext Result.failure(Exception("Invalid backup file: naatbook_data.json not found"))
            }

            // Parse json content and restore database records
            val jsonArray = JSONArray(jsonContent)
            var restoreCount = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val naat = NaatEntity(
                    title = obj.getString("title"),
                    poet = if (obj.isNull("poet") || obj.getString("poet").isEmpty()) null else obj.getString("poet"),
                    // Old backups may carry legacy folder names; upgrade them
                    // onto the current taxonomy on the way in.
                    category = NaatCategories.normalize(obj.getString("category")),
                    lyrics = if (obj.isNull("lyrics") || obj.getString("lyrics").isEmpty()) null else obj.getString("lyrics"),
                    audioType = obj.getString("audioType"),
                    audioPath = if (obj.isNull("audioPath") || obj.getString("audioPath").isEmpty()) null else obj.getString("audioPath"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                repository.insert(naat)
                restoreCount++
            }

            Result.success(restoreCount)
        } catch (e: Exception) {
            Log.e("BackupManager", "Import failed", e)
            Result.failure(e)
        }
    }
}
