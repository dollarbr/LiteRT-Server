package com.litert.server.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LiteRTEngine(private val context: Context) {

    companion object {
        private const val TAG = "LiteRTEngine"
    }

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var currentSamplerConfig: SamplerConfig = SamplerConfig(
        topK = 40,
        topP = 0.9,
        temperature = 0.7
    )

    var effectiveBackend: BackendType? = null
        private set

    var isReady = false
        private set

    suspend fun initialize(
        modelPath: String,
        preference: BackendType = BackendType.AUTO,
        temperature: Double = 0.7,
        maxTokens: Int = 1024,
        topK: Int = 40,
        topP: Double = 0.9
    ): Boolean = withContext(Dispatchers.IO) {
        currentSamplerConfig = SamplerConfig(topK = topK, topP = topP, temperature = temperature)
        for (candidate in preference.fallbackChain()) {
            var attemptEngine: Engine? = null
            try {
                val backend = toSdkBackend(candidate)
                // Vision encoder NPU support is uncertain on MT6878; pair NPU main with GPU vision.
                val visionBackend = if (candidate == BackendType.NPU) Backend.GPU() else backend
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend,
                    maxNumTokens = maxTokens,
                    cacheDir = context.cacheDir.absolutePath
                )
                attemptEngine = Engine(config)
                attemptEngine.initialize()

                conversation = createNewConversation(attemptEngine, currentSamplerConfig)
                engine = attemptEngine
                effectiveBackend = candidate
                isReady = true
                Log.i(TAG, "Engine initialized with $candidate backend")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Backend $candidate failed to initialize, trying next in chain", e)
                try {
                    attemptEngine?.close()
                } catch (closeError: Exception) {
                    Log.w(TAG, "Failed to close partially-initialized engine", closeError)
                }
            }
        }
        isReady = false
        false
    }

    private fun toSdkBackend(type: BackendType): Backend = when (type) {
        BackendType.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        BackendType.GPU -> Backend.GPU()
        BackendType.CPU -> Backend.CPU()
        BackendType.AUTO -> error("AUTO is not a concrete backend")
    }

    private fun createNewConversation(
        eng: Engine,
        samplerConfig: SamplerConfig
    ): com.google.ai.edge.litertlm.Conversation {
        return eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    Content.Text(
                        "You are a helpful AI assistant running locally on an Android device " +
                        "powered by Google's Gemma multimodal LLM via LiteRT."
                    )
                ),
                samplerConfig = samplerConfig
            )
        )
    }

    suspend fun generateText(prompt: String): Flow<String> {
        val conv = conversation ?: throw IllegalStateException("Engine not initialized")
        return conv.sendMessageAsync(prompt).map { it.toString() }
    }

    /**
     * Passes the image file + prompt as proper multimodal content.
     * imagePath must be an absolute file path readable by the engine.
     */
    suspend fun analyzeImage(imagePath: String, prompt: String): Flow<String> {
        val conv = conversation ?: throw IllegalStateException("Engine not initialized")
        val contents = Contents.of(
            Content.ImageFile(imagePath),
            Content.Text(prompt)
        )
        return conv.sendMessageAsync(contents).map { it.toString() }
    }

    fun clearHistory() {
        val eng = engine ?: return
        conversation?.close()
        conversation = createNewConversation(eng, currentSamplerConfig)
        Log.i(TAG, "Conversation history cleared")
    }

    fun getBackend(): String = effectiveBackend?.name ?: "NONE"

    fun shutdown() {
        isReady = false
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }
}
