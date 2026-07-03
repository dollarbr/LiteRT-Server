package com.litert.server.data

import kotlinx.serialization.Serializable

enum class AppStatus {
    MODEL_SELECTION,
    SETTINGS,
    BROWSING,
    DOWNLOADING,
    DOWNLOAD_ERROR,
    INITIALIZING,
    READY,
    ERROR
}

data class AppState(
    val status: AppStatus = AppStatus.MODEL_SELECTION,
    val downloadProgress: Float = 0f,
    val downloadedMb: Float = 0f,
    val totalMb: Float = 0f,
    val downloadSpeedMbps: Float = 0f,
    val etaSeconds: Int = 0,
    val errorMessage: String? = null,
    val isServerRunning: Boolean = false,
    val serverPort: Int = 8080,
    val activeBackend: String = "",
    val engineReady: Boolean = false,
    val selectedModelPath: String = "",
    val availableModels: List<com.litert.server.download.LocalModel> = emptyList(),
    val requestLog: List<RequestLogEntry> = emptyList()
)

data class RequestLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val endpoint: String,
    val responseTimeMs: Long,
    val statusCode: Int
)

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val role: MessageRole,
    val content: String,
    val tokenCount: Int = 0,
    val generationTimeMs: Long = 0,
    val isStreaming: Boolean = false
)

enum class MessageRole { USER, ASSISTANT }

@Serializable
data class ChatRequest(
    val message: String,
    val stream: Boolean = false
)

@Serializable
data class ChatResponse(
    val response: String,
    val tokens: Int,
    val ms: Long
)

@Serializable
data class VisionRequest(
    val imagePath: String,
    val prompt: String = "Describe what you see in detail",
    val stream: Boolean = false
)

@Serializable
data class HealthResponse(
    val status: String,
    val model: String,
    val backend: String,
    val ready: Boolean
)

@Serializable
data class ErrorResponse(
    val error: String,
    val code: Int
)
