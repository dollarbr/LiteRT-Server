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
    val likes: Long = 0
)

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
 * Minimal HuggingFace Hub client. Token is optional for search but required
 * to download gated repos (e.g. google/gemma models).
 */
class HuggingFaceApi(private val tokenProvider: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * @param author restrict results to a HuggingFace user/org (blank = any)
     * @param sort HF Hub sort field: "downloads", "likes" or "lastModified"
     */
    suspend fun searchModels(
        query: String,
        author: String = "",
        sort: String = "downloads"
    ): List<HfModel> = withContext(Dispatchers.IO) {
        val url = buildString {
            // "filter" matches the litert-lm library tag; the "library" param is ignored by the Hub API.
            append("https://huggingface.co/api/models?filter=litert-lm&limit=50")
            append("&sort=").append(URLEncoder.encode(sort, "UTF-8")).append("&direction=-1")
            if (author.isNotBlank()) {
                append("&author=").append(URLEncoder.encode(author, "UTF-8"))
            }
            if (query.isNotBlank()) {
                append("&search=").append(URLEncoder.encode(query, "UTF-8"))
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
                throw Exception("HuggingFace API error: HTTP ${resp.code}")
            }
            return resp.body?.string() ?: throw Exception("Empty response from HuggingFace")
        }
    }
}
