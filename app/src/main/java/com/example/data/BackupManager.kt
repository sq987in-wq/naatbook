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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
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

/** User-controlled ZIP import/export for the offline notebook. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NaatRepository
) {
    private companion object {
        const val FORMAT_VERSION = 2
        const val DATA_ENTRY = "naatbook_data.json"
        const val V2_AUDIO_PREFIX = "audio/"
        const val LEGACY_RECORDINGS_PREFIX = "recordings/"
        const val LEGACY_LINKED_PREFIX = "linked/"

        // Zip safety limits (guard against zip bombs / malicious archives).
        const val MAX_ENTRIES = 10_000
        const val MAX_JSON_BYTES = 16L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
        const val BUFFER_SIZE = 32 * 1024
    }

    private data class PackedAudio(val relativePath: String, val source: File)

    private data class ExportAudioIndex(
        val relativePathByNaatId: Map<Int, String>,
        val uniqueFiles: Collection<PackedAudio>
    )

    private class ExtractionBudget {
        var totalBytes: Long = 0L

        fun add(byteCount: Int) {
            totalBytes += byteCount
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw IOException("Backup archive exceeds maximum allowed size")
            }
        }
    }

    suspend fun exportBackup(outputUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val naats = repository.allNaats.first()
            val audioIndex = buildAudioIndex(naats)
            val entries = JSONArray()
            naats.forEach { naat ->
                entries.put(
                    JSONObject().apply {
                        put("id", naat.id)
                        put("title", naat.title)
                        put("poet", naat.poet ?: JSONObject.NULL)
                        put("category", naat.category)
                        put("lyrics", naat.lyrics ?: JSONObject.NULL)
                        put("audioType", naat.audioType)
                        put(
                            "audioPath",
                            audioIndex.relativePathByNaatId[naat.id] ?: JSONObject.NULL
                        )
                        put("isFavorite", naat.isFavorite)
                        put("createdAt", naat.createdAt)
                    }
                )
            }
            val root = JSONObject().apply {
                put("formatVersion", FORMAT_VERSION)
                put("entries", entries)
            }

            val descriptor = context.contentResolver.openFileDescriptor(outputUri, "w")
                ?: throw IOException("Could not open output stream")
            descriptor.use { pfd ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(pfd.fileDescriptor))).use { zip ->
                    zip.putNextEntry(ZipEntry(DATA_ENTRY))
                    zip.write(root.toString(4).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    // Content-addressed names make every unique payload appear once,
                    // even when several entries reference the same or duplicate files.
                    audioIndex.uniqueFiles.forEach { packed ->
                        zip.putNextEntry(ZipEntry(packed.relativePath))
                        FileInputStream(packed.source).use { input -> input.copyTo(zip, BUFFER_SIZE) }
                        zip.closeEntry()
                    }
                }
            }
            "Backup exported successfully"
        }.onFailure { Log.e("BackupManager", "Export failed", it) }
    }

    /**
     * Builds content-addressed archive paths. Runtime absolute paths never enter
     * the JSON document, and files with identical SHA-256 content share one ZIP
     * member and one restored path.
     */
    private fun buildAudioIndex(naats: List<NaatEntity>): ExportAudioIndex {
        val relativeByNaatId = mutableMapOf<Int, String>()
        val relativeByCanonicalSource = mutableMapOf<String, String>()
        val packedByDigest = linkedMapOf<String, PackedAudio>()

        naats.forEach { naat ->
            val source = naat.audioPath?.let(::safeAudioSource) ?: return@forEach
            val canonicalPath = source.canonicalPath
            val relativePath = relativeByCanonicalSource[canonicalPath] ?: run {
                val digest = source.sha256()
                val existing = packedByDigest[digest]
                if (existing != null) {
                    existing.relativePath
                } else {
                    val extension = source.extension
                        .lowercase()
                        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                        ?: "bin"
                    val path = "$V2_AUDIO_PREFIX$digest.$extension"
                    packedByDigest[digest] = PackedAudio(path, source)
                    path
                }.also { relativeByCanonicalSource[canonicalPath] = it }
            }
            relativeByNaatId[naat.id] = relativePath
        }

        return ExportAudioIndex(relativeByNaatId, packedByDigest.values)
    }

    /** Only app-owned audio directories are eligible for export. */
    private fun safeAudioSource(path: String): File? {
        val source = File(path)
        if (!source.isFile) return null
        val filesDir = context.filesDir.canonicalFile
        val canonical = source.canonicalFile
        val basePath = filesDir.path + File.separator
        if (!canonical.path.startsWith(basePath)) return null
        val relative = canonical.path.removePrefix(basePath).replace(File.separatorChar, '/')
        return canonical.takeIf {
            relative.startsWith(LEGACY_RECORDINGS_PREFIX) ||
                relative.startsWith(LEGACY_LINKED_PREFIX) ||
                relative.startsWith(V2_AUDIO_PREFIX)
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    suspend fun importBackup(inputUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(inputUri)
                ?: throw IOException("Could not open input stream")
            val budget = ExtractionBudget()
            val extractedPaths = mutableSetOf<String>()
            var jsonContent: String? = null
            var entryCount = 0

            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entryCount > MAX_ENTRIES) {
                        throw IOException("Backup archive has too many entries (>$MAX_ENTRIES)")
                    }
                    val name = entry.name
                    when {
                        entry.isDirectory -> copyEntry(zip, null, budget)
                        name == DATA_ENTRY -> {
                            if (jsonContent != null) {
                                throw IOException("Backup archive contains duplicate $DATA_ENTRY entries")
                            }
                            jsonContent = readJsonEntry(zip, budget)
                        }
                        isSupportedAudioEntry(name) -> {
                            val destination = safeBackupDestination(context.filesDir, name)
                            if (destination == null) {
                                Log.w("BackupManager", "Skipped unsafe zip entry: $name")
                                copyEntry(zip, null, budget)
                            } else if (!extractedPaths.add(name)) {
                                // Duplicate ZIP members are never written twice.
                                copyEntry(zip, null, budget)
                            } else {
                                destination.parentFile?.mkdirs()
                                try {
                                    FileOutputStream(destination).use { output ->
                                        copyEntry(zip, output, budget)
                                    }
                                } catch (error: Exception) {
                                    destination.delete()
                                    throw error
                                }
                            }
                        }
                        else -> {
                            Log.w("BackupManager", "Skipping unexpected zip entry: $name")
                            copyEntry(zip, null, budget)
                        }
                    }
                    zip.closeEntry()
                }
            }

            val document = jsonContent
                ?: throw IOException("Invalid backup file: $DATA_ENTRY not found")
            val (formatVersion, entries) = parseBackupDocument(document)
            restoreEntries(formatVersion, entries, extractedPaths)
        }.onFailure { Log.e("BackupManager", "Import failed", it) }
    }

    private fun isSupportedAudioEntry(name: String): Boolean =
        name.startsWith(V2_AUDIO_PREFIX) ||
            name.startsWith(LEGACY_RECORDINGS_PREFIX) ||
            name.startsWith(LEGACY_LINKED_PREFIX)

    private fun readJsonEntry(zip: ZipInputStream, budget: ExtractionBudget): String {
        val output = ByteArrayOutputStream()
        copyEntry(zip, output, budget) { bytesRead ->
            if (bytesRead > MAX_JSON_BYTES) {
                throw IOException("Backup metadata exceeds maximum allowed size")
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun copyEntry(
        zip: ZipInputStream,
        output: OutputStream?,
        budget: ExtractionBudget,
        onEntryBytes: (Long) -> Unit = {}
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            budget.add(count)
            entryBytes += count
            onEntryBytes(entryBytes)
            output?.write(buffer, 0, count)
        }
    }

    /** A top-level array is the v1 format; v2 is a versioned object. */
    private fun parseBackupDocument(json: String): Pair<Int, JSONArray> {
        val trimmed = json.trimStart()
        if (trimmed.startsWith("[")) return 1 to JSONArray(trimmed)
        val root = JSONObject(trimmed)
        val version = root.optInt("formatVersion", -1)
        if (version != FORMAT_VERSION) {
            throw IOException("Unsupported backup format version: $version")
        }
        return version to root.getJSONArray("entries")
    }

    private suspend fun restoreEntries(
        formatVersion: Int,
        entries: JSONArray,
        extractedPaths: Set<String>
    ): Int {
        var restoreCount = 0
        for (index in 0 until entries.length()) {
            val obj = entries.getJSONObject(index)
            val rawAudioPath = obj.nullableString("audioPath")
            val audioPath = when (formatVersion) {
                FORMAT_VERSION -> resolveV2AudioPath(rawAudioPath, extractedPaths)
                1 -> resolveLegacyAudioPath(rawAudioPath, extractedPaths)
                else -> null
            }
            val requestedAudioType = obj.optString("audioType", "none")
            val naat = NaatEntity(
                title = obj.getString("title"),
                poet = obj.nullableString("poet"),
                category = NaatCategories.normalize(obj.nullableString("category")),
                lyrics = obj.nullableString("lyrics"),
                audioType = if (audioPath == null) "none" else requestedAudioType,
                audioPath = audioPath,
                isFavorite = obj.optBoolean("isFavorite", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
            repository.insert(naat)
            restoreCount++
        }
        return restoreCount
    }

    private fun resolveV2AudioPath(rawPath: String?, extractedPaths: Set<String>): String? {
        if (rawPath == null || !rawPath.startsWith(V2_AUDIO_PREFIX) || rawPath !in extractedPaths) {
            return null
        }
        return safeBackupDestination(context.filesDir, rawPath)?.absolutePath
    }

    /**
     * v1 stored source-device absolute paths while packing files under
     * recordings/ or linked/. Match only the relative member that was actually
     * extracted; never trust or recreate the absolute path from the JSON.
     */
    private fun resolveLegacyAudioPath(rawPath: String?, extractedPaths: Set<String>): String? {
        if (rawPath == null) return null
        val normalized = rawPath.replace('\\', '/')
        val relative = when {
            normalized.startsWith(LEGACY_RECORDINGS_PREFIX) -> normalized
            normalized.startsWith(LEGACY_LINKED_PREFIX) -> normalized
            "/$LEGACY_RECORDINGS_PREFIX" in normalized ->
                LEGACY_RECORDINGS_PREFIX + normalized.substringAfterLast("/$LEGACY_RECORDINGS_PREFIX")
            "/$LEGACY_LINKED_PREFIX" in normalized ->
                LEGACY_LINKED_PREFIX + normalized.substringAfterLast("/$LEGACY_LINKED_PREFIX")
            else -> null
        } ?: return null
        if (relative !in extractedPaths) return null
        return safeBackupDestination(context.filesDir, relative)?.absolutePath
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
