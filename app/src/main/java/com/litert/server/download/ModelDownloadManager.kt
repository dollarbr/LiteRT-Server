package com.litert.server.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val progressPercent: Float,
    val downloadedMb: Float,
    val totalMb: Float,
    val speedMbps: Float,
    val etaSeconds: Int,
    val isDone: Boolean = false,
    val error: String? = null
)

data class LocalModel(
    val name: String,
    val path: String,
    val sizeGb: Float
)

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val MIN_VALID_BYTES = 100_000_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val modelsDir: File = File(context.getExternalFilesDir(null), "models")

    /** Moves pre-fork models from the external-files root into models/. */
    fun migrateLegacyModels() {
        modelsDir.mkdirs()
        context.getExternalFilesDir(null)?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".litertlm") }
            ?.forEach { legacy ->
                val dest = File(modelsDir, legacy.name)
                if (!dest.exists()) legacy.renameTo(dest) else legacy.delete()
            }
    }

    fun listLocalModels(): List<LocalModel> {
        modelsDir.mkdirs()
        return modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".litertlm") && it.length() >= MIN_VALID_BYTES }
            ?.sortedBy { it.name }
            ?.map { LocalModel(name = it.nameWithoutExtension, path = it.absolutePath, sizeGb = it.length() / 1024f / 1024f / 1024f) }
            ?: emptyList()
    }

    fun modelFile(filename: String): File = File(modelsDir, filename)

    fun deleteModel(path: String) {
        File(path).delete()
    }

    fun importModel(input: InputStream, filename: String): String {
        modelsDir.mkdirs()
        val dest = File(modelsDir, filename)
        input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
        return dest.absolutePath
    }

    fun downloadModel(
        url: String,
        filename: String,
        token: String,
        expectedTotalBytes: Long? = null
    ): Flow<DownloadProgress> = flow {
        modelsDir.mkdirs()
        val destFile = File(modelsDir, filename)
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "LiteRT-Server-Android/1.1")
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 401 || response.code == 403) {
            throw Exception("Access denied (HTTP ${response.code}). Gated model — connect your HuggingFace account in Settings and accept the model license on huggingface.co.")
        }
        if (!response.isSuccessful && response.code != 206) {
            throw Exception("Download failed: HTTP ${response.code} — ${response.message}")
        }

        val totalBytes = when {
            response.code == 206 -> {
                val contentRange = response.header("Content-Range") ?: ""
                contentRange.substringAfterLast('/').toLongOrNull() ?: expectedTotalBytes ?: 0L
            }
            else -> response.body?.contentLength()?.takeIf { it > 0 } ?: expectedTotalBytes ?: 0L
        }

        val body = response.body ?: throw Exception("Empty response body")
        val buffer = ByteArray(8192)
        var downloadedBytes = existingBytes
        var lastSpeedTime = System.currentTimeMillis()
        var lastSpeedBytes = downloadedBytes

        val outputStream = FileOutputStream(destFile, existingBytes > 0)

        body.byteStream().use { inputStream ->
            outputStream.use { outStream ->
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    outStream.write(buffer, 0, read)
                    downloadedBytes += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastSpeedTime
                    if (elapsed >= 1000) {
                        val bytesSinceLastCheck = downloadedBytes - lastSpeedBytes
                        val speedMbps = (bytesSinceLastCheck / 1024f / 1024f) / (elapsed / 1000f)
                        val remaining = totalBytes - downloadedBytes
                        val etaSec = if (speedMbps > 0) (remaining / 1024 / 1024 / speedMbps).toInt() else 0
                        emit(
                            DownloadProgress(
                                progressPercent = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f,
                                downloadedMb = downloadedBytes / 1024f / 1024f,
                                totalMb = totalBytes / 1024f / 1024f,
                                speedMbps = speedMbps,
                                etaSeconds = etaSec
                            )
                        )
                        lastSpeedTime = now
                        lastSpeedBytes = downloadedBytes
                    }
                }
            }
        }

        if (destFile.length() < MIN_VALID_BYTES) {
            destFile.delete()
            throw Exception("Downloaded file too small — may be corrupted. Please retry.")
        }

        emit(
            DownloadProgress(
                progressPercent = 1f,
                downloadedMb = destFile.length() / 1024f / 1024f,
                totalMb = totalBytes / 1024f / 1024f,
                speedMbps = 0f,
                etaSeconds = 0,
                isDone = true
            )
        )
    }.flowOn(Dispatchers.IO)
}
