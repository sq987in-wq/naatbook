package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val repository: NaatRepository
) {
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

            while (entry != null) {
                val name = entry.name
                if (name == "naatbook_data.json") {
                    val reader = BufferedReader(InputStreamReader(zis, "UTF-8"))
                    jsonContent = reader.readText()
                } else if (name.startsWith("recordings/")) {
                    val destFile = File(context.filesDir, name)
                    destFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(destFile)
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                    fos.close()
                } else if (name.startsWith("linked/")) {
                    val destFile = File(context.filesDir, name)
                    destFile.parentFile?.mkdirs()
                    val fos = FileOutputStream(destFile)
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                    fos.close()
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
                    category = obj.getString("category"),
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
