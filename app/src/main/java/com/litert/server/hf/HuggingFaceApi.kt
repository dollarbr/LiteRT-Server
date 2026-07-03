package com.litert.server.hf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class HfModel(
    val id: String,
    val downloads: Long = 0,
    val likes: Long = 0,
    // The Hub sends false for open repos and "auto"/"manual" for gated ones.
    val gated: kotlinx.serialization.json.JsonElement? = null,
    // Populated by full=true listings: filenames only, sizes are null.
    val siblings: List<HfSibling> = emptyList()
) {
    val isGated: Boolean
        get() = gated != null &&
            gated !is kotlinx.serialization.json.JsonNull &&
            gated.toString() != "false"

    val litertlmFilenames: List<String>
        get() = siblings.map { it.rfilename }.filter { it.endsWith(".litertlm") }
}

/** HTTP failure from the Hub API, keeping the status code for gated-repo handling. */
class HfHttpException(val code: Int) : Exception("HuggingFace API error: HTTP $code")

@Serializable
data class HfSibling(
    val rfilename: String,
    val size: Long? = null
)

@Serializable
data class HfModelDetail(
    val id: String,
    val siblings: List<HfSibling> = emptyList()
)

object HfJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseModels(body: String): List<HfModel> = json.decodeFromString(body)

    fun parseModelDetail(body: String): HfModelDetail = json.decodeFromString(body)

    fun litertlmFiles(detail: HfModelDetail): List<HfSibling> =
        detail.siblings.filter { it.rfilename.endsWith(".litertlm") }
}

/**
 * Everything the Hub `/api/models` endpoint accepts for listing models.
 * `sort` is one of "downloads", "likes", "lastModified", "createdAt",
 * "trendingScore"; `descending` maps to direction=-1/1.
 */
data class HfSearchParams(
    val query: String = "",
    val author: String = "",
    val sort: String = "downloads",
    val descending: Boolean = true,
    val limit: Int = 50
)

/**
 * Minimal HuggingFace Hub client. Token is optional for search but required
 * to download gated repos (e.g. google/gemma models).
 */
class HuggingFaceApi(private val tokenProvider: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun searchModels(params: HfSearchParams = HfSearchParams()): List<HfModel> =
        withContext(Dispatchers.IO) {
            val url = buildString {
                // "filter" matches the litert-lm library tag; the "library" param is ignored by the Hub API.
                // full=true adds sibling filenames (no sizes) for filename filtering.
                append("https://huggingface.co/api/models?filter=litert-lm&full=true")
                append("&limit=").append(params.limit.coerceIn(1, 100))
                append("&sort=").append(URLEncoder.encode(params.sort, "UTF-8"))
                append("&direction=").append(if (params.descending) "-1" else "1")
                if (params.author.isNotBlank()) {
                    append("&author=").append(URLEncoder.encode(params.author.trim(), "UTF-8"))
                }
                if (params.query.isNotBlank()) {
                    append("&search=").append(URLEncoder.encode(params.query, "UTF-8"))
                }
            }
            HfJson.parseModels(get(url))
        }

    suspend fun modelDetail(modelId: String): HfModelDetail = withContext(Dispatchers.IO) {
        HfJson.parseModelDetail(get("https://huggingface.co/api/models/$modelId?blobs=true"))
    }

    private fun get(url: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "LiteRT-Server-Android/" + com.litert.server.BuildConfig.VERSION_NAME)
        val token = tokenProvider()
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw HfHttpException(resp.code)
            }
            return resp.body?.string() ?: throw Exception("Empty response from HuggingFace")
        }
    }
}
