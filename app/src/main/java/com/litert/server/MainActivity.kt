package com.litert.server

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.litert.server.data.*
import com.litert.server.download.ModelDownloadManager
import com.litert.server.service.LLMForegroundService
import com.litert.server.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var downloadManager: ModelDownloadManager
    private lateinit var settingsStore: SettingsStore
    private var appState by mutableStateOf(AppState())
    private var currentSettings by mutableStateOf(AppSettings())
    private var chatMessages = mutableStateListOf<ChatMessage>()
    private var isGenerating by mutableStateOf(false)
    private var visionResult by mutableStateOf("")
    private var isAnalyzing by mutableStateOf(false)
    private var selectedTab by mutableIntStateOf(0)
    private var pendingDownload: Triple<String, String, Long?>? = null // url, filename, expectedBytes
    private var hfResults by mutableStateOf(listOf<com.litert.server.hf.HfModel>())
    private var hfLoading by mutableStateOf(false)
    private var hfError by mutableStateOf<String?>(null)
    private var hfHasToken by mutableStateOf(false)
    private var hfVariantPick by mutableStateOf<Pair<String, List<com.litert.server.hf.HfSibling>>?>(null)
    // modelId -> .litertlm files with sizes, filled in the background after each
    // search (the Hub listing endpoint returns filenames but never sizes).
    private val hfFileSizes = mutableStateMapOf<String, List<com.litert.server.hf.HfSibling>>()
    private val deviceSpecs by lazy { DeviceSpecs.from(this) }

    // Holds reference to the engine once the service boots it.
    // We bind to the service via a shared singleton so the UI can call it directly.
    private var liteRTEngine: com.litert.server.engine.LiteRTEngine? = null

    // ── File picker ───────────────────────────────────────────────────────
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                } ?: "imported-${System.currentTimeMillis()}.litertlm"
                val input = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
                downloadManager.importModel(input, name)
                withContext(Dispatchers.Main) { refreshModelLibrary() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appState = appState.copy(status = AppStatus.DOWNLOAD_ERROR, errorMessage = "Failed to import file: ${e.message}")
                }
            }
        }
    }

    private val engineReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LLMForegroundService.ACTION_ENGINE_READY -> {
                    val port = intent.getIntExtra(LLMForegroundService.EXTRA_SERVER_PORT, 8080)
                    val backend = intent.getStringExtra(LLMForegroundService.EXTRA_BACKEND) ?: "NONE"
                    // Grab the engine reference from the service singleton
                    liteRTEngine = LLMForegroundService.engineInstance
                    appState = appState.copy(
                        status = AppStatus.READY,
                        isServerRunning = true,
                        serverPort = port,
                        activeBackend = backend,
                        engineReady = true
                    )
                }
                LLMForegroundService.ACTION_ENGINE_ERROR -> {
                    val msg = intent.getStringExtra(LLMForegroundService.EXTRA_ERROR_MESSAGE)
                    appState = appState.copy(status = AppStatus.ERROR, errorMessage = msg)
                }
            }
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handle result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        downloadManager = ModelDownloadManager(this)
        settingsStore = SettingsStore(this)

        lifecycleScope.launch {
            settingsStore.settings.collect { currentSettings = it }
        }

        val filter = IntentFilter().apply {
            addAction(LLMForegroundService.ACTION_ENGINE_READY)
            addAction(LLMForegroundService.ACTION_ENGINE_ERROR)
        }
        registerReceiver(engineReceiver, filter, RECEIVER_NOT_EXPORTED)

        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        downloadManager.migrateLegacyModels()
        refreshModelLibrary()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                // Dark fill behind the status/nav bars; content stays clear of the
                // notch and shrinks when the keyboard opens (safeDrawing includes IME).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground)
                        .safeDrawingPadding()
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    fun AppContent() {
        when (appState.status) {
            AppStatus.MODEL_SELECTION -> ModelLibraryScreen(
                models = appState.availableModels,
                lastModelPath = appState.selectedModelPath,
                onSelect = ::selectAndLoadModel,
                onBrowseHuggingFace = ::openModelBrowser,
                onImportFile = { pickFileLauncher.launch(arrayOf("*/*")) },
                onDelete = { model ->
                    downloadManager.deleteModel(model.path)
                    refreshModelLibrary()
                },
                onOpenSettings = { appState = appState.copy(status = AppStatus.SETTINGS) }
            )
            AppStatus.SETTINGS -> SettingsScreen(
                settings = currentSettings,
                modelPath = "",
                activeBackend = "",
                onBackendSelected = { pref ->
                    lifecycleScope.launch { settingsStore.setBackendPreference(pref) }
                },
                onPortChanged = { port ->
                    lifecycleScope.launch { settingsStore.setServerPort(port) }
                },
                onHfTokenChanged = { token ->
                    lifecycleScope.launch { settingsStore.setHfToken(token) }
                },
                onSamplerChanged = { temp, topK, topP, maxTok ->
                    lifecycleScope.launch {
                        settingsStore.setSampler(temp, topK, topP, maxTok)
                    }
                },
                onChangeModel = {},
                onUnloadModel = {},
                onDeleteModel = {},
                isModelLoaded = false,
                onBack = ::refreshModelLibrary
            )
            AppStatus.BROWSING -> {
                ModelBrowserScreen(
                    isLoading = hfLoading,
                    results = hfResults,
                    errorMessage = hfError,
                    hasToken = hfHasToken,
                    deviceSpecs = deviceSpecs,
                    fileSizes = hfFileSizes,
                    onSearch = ::searchHfModels,
                    onDownload = ::downloadHfModel,
                    onBack = ::refreshModelLibrary
                )
                hfVariantPick?.let { (modelId, files) ->
                    VariantPickerDialog(
                        modelId = modelId,
                        files = files,
                        deviceSpecs = deviceSpecs,
                        onPick = { file ->
                            hfVariantPick = null
                            startVariantDownload(modelId, file)
                        },
                        onDismiss = { hfVariantPick = null }
                    )
                }
            }
            AppStatus.DOWNLOADING, AppStatus.DOWNLOAD_ERROR, AppStatus.INITIALIZING -> DownloadScreen(
                status = appState.status,
                progressPercent = appState.downloadProgress,
                downloadedMb = appState.downloadedMb,
                totalMb = appState.totalMb,
                speedMbps = appState.downloadSpeedMbps,
                etaSeconds = appState.etaSeconds,
                errorMessage = appState.errorMessage,
                onRetry = ::retryDownload,
                onBack = ::refreshModelLibrary,
                modelName = appState.selectedModelPath.substringAfterLast('/')
            )
            AppStatus.READY -> MainTabLayout()
            AppStatus.ERROR -> {
                Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Error: ${appState.errorMessage}", color = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = ::refreshModelLibrary) { Text("Back to models") }
                    }
                }
            }
        }
    }

    @Composable
    fun MainTabLayout() {
        val tabs = listOf("Chat", "Vision", "Server", "Settings")
        val icons = listOf(
            Icons.Default.Chat,
            Icons.Default.Image,
            Icons.Default.Api,
            Icons.Default.Settings
        )
        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                NavigationBar(containerColor = SurfaceColor) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], contentDescription = tab) },
                            label = { Text(tab, color = if (selectedTab == index) GreenPrimary else Color.Gray) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                unselectedIconColor = Color.Gray,
                                indicatorColor = Color(0xFF1A3A1A)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> ChatScreen(
                        messages = chatMessages,
                        isGenerating = isGenerating,
                        onSend = ::sendMessage,
                        onClear = { chatMessages.clear() }
                    )
                    1 -> VisionScreen(
                        isAnalyzing = isAnalyzing,
                        analysisResult = visionResult,
                        onAnalyze = ::analyzeImage,
                        onShare = {
                            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("result", it))
                            Toast.makeText(this@MainActivity, "Copied!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> ServerScreen(
                        isRunning = appState.isServerRunning,
                        port = appState.serverPort,
                        requestLog = appState.requestLog,
                        onToggle = ::toggleServer
                    )
                    3 -> SettingsScreen(
                        settings = currentSettings,
                        modelPath = appState.selectedModelPath,
                        activeBackend = appState.activeBackend,
                        onBackendSelected = { pref ->
                            lifecycleScope.launch { settingsStore.setBackendPreference(pref) }
                        },
                        onPortChanged = { port ->
                            lifecycleScope.launch {
                                settingsStore.setServerPort(port)
                                restartService()
                            }
                        },
                        onHfTokenChanged = { token ->
                            lifecycleScope.launch { settingsStore.setHfToken(token) }
                        },
                        onSamplerChanged = { temp, topK, topP, maxTok ->
                            lifecycleScope.launch {
                                settingsStore.setSampler(temp, topK, topP, maxTok)
                            }
                        },
                        onChangeModel = ::unloadModel,
                        onUnloadModel = ::unloadModel,
                        onDeleteModel = {
                            stopService(Intent(this@MainActivity, LLMForegroundService::class.java))
                            liteRTEngine = null
                            downloadManager.deleteModel(appState.selectedModelPath)
                            lifecycleScope.launch {
                                settingsStore.setLastModelPath("")
                                refreshModelLibrary()
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────────
    private fun sendMessage(text: String) {
        val engine = liteRTEngine
        if (engine == null || !engine.isReady) {
            Toast.makeText(this, "Engine not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val userMsg = ChatMessage(role = MessageRole.USER, content = text)
        chatMessages.add(userMsg)

        // Placeholder assistant bubble that streams tokens in
        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        chatMessages.add(assistantMsg)
        val assistantIndex = chatMessages.lastIndex
        isGenerating = true

        lifecycleScope.launch {
            try {
                engine.generateText(text)
                    .onCompletion { err ->
                        isGenerating = false
                        chatMessages[assistantIndex] =
                            chatMessages[assistantIndex].copy(isStreaming = false)
                        if (err != null) {
                            chatMessages[assistantIndex] =
                                chatMessages[assistantIndex].copy(content = "Error: ${err.message}")
                        }
                    }
                    .collect { token ->
                        chatMessages[assistantIndex] = chatMessages[assistantIndex].copy(
                            content = chatMessages[assistantIndex].content + token
                        )
                    }
            } catch (e: Exception) {
                isGenerating = false
                chatMessages[assistantIndex] =
                    chatMessages[assistantIndex].copy(
                        content = "Error: ${e.message}",
                        isStreaming = false
                    )
            }
        }
    }

    // ── Vision ───────────────────────────────────────────────────────────
    private fun analyzeImage(uri: Uri, prompt: String) {
        val engine = liteRTEngine
        if (engine == null || !engine.isReady) {
            Toast.makeText(this, "Engine not ready", Toast.LENGTH_SHORT).show()
            return
        }

        isAnalyzing = true
        visionResult = ""

        lifecycleScope.launch {
            try {
                // Copy URI to a temp file so LiteRT can read it as a file path
                val tmpFile = File(cacheDir, "vision_input_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        tmpFile.outputStream().use { out -> ins.copyTo(out) }
                    }
                }

                engine.analyzeImage(tmpFile.absolutePath, prompt)
                    .onCompletion {
                        isAnalyzing = false
                        tmpFile.delete()
                    }
                    .collect { token ->
                        visionResult += token
                    }
            } catch (e: Exception) {
                isAnalyzing = false
                visionResult = "Error: ${e.message}"
            }
        }
    }

    // ── Lifecycle helpers ───────────────────────────────────────────────
    private fun refreshModelLibrary() {
        lifecycleScope.launch {
            val settings = settingsStore.current()
            appState = appState.copy(
                status = AppStatus.MODEL_SELECTION,
                availableModels = downloadManager.listLocalModels(),
                selectedModelPath = settings.lastModelPath
            )
        }
    }

    private fun openModelBrowser() {
        appState = appState.copy(status = AppStatus.BROWSING)
        searchHfModels(com.litert.server.hf.HfSearchParams())
    }

    private fun searchHfModels(params: com.litert.server.hf.HfSearchParams) {
        hfLoading = true
        hfError = null
        lifecycleScope.launch {
            try {
                val token = settingsStore.current().hfToken
                hfHasToken = token.isNotBlank()
                val api = com.litert.server.hf.HuggingFaceApi { token }
                hfResults = api.searchModels(params)
                prefetchFileSizes(hfResults, api)
            } catch (e: Exception) {
                hfError = e.message
            } finally {
                hfLoading = false
            }
        }
    }

    /** Fills [hfFileSizes] in the background (a few requests at a time) so the
     * browser can show sizes and sort by them; failures just leave the size unknown. */
    private fun prefetchFileSizes(
        models: List<com.litert.server.hf.HfModel>,
        api: com.litert.server.hf.HuggingFaceApi
    ) {
        val semaphore = kotlinx.coroutines.sync.Semaphore(6)
        models.filter { it.id !in hfFileSizes && it.litertlmFilenames.isNotEmpty() }.forEach { model ->
            lifecycleScope.launch(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    val files = com.litert.server.hf.HfJson.litertlmFiles(api.modelDetail(model.id))
                    withContext(Dispatchers.Main) { hfFileSizes[model.id] = files }
                } catch (_: Exception) {
                    // Size stays unknown (e.g. gated repo without access).
                } finally {
                    semaphore.release()
                }
            }
        }
    }

    private fun downloadHfModel(model: com.litert.server.hf.HfModel) {
        // Gated repo we can't access (no token yet): send the user straight to
        // the model page to request access / accept the license.
        if (model.isGated && !hfHasToken) {
            openModelPage(model.id)
            return
        }
        hfLoading = true
        lifecycleScope.launch {
            try {
                val token = settingsStore.current().hfToken
                val api = com.litert.server.hf.HuggingFaceApi { token }
                val files = hfFileSizes[model.id]
                    ?: com.litert.server.hf.HfJson.litertlmFiles(api.modelDetail(model.id))
                when {
                    files.isEmpty() -> throw Exception("No .litertlm file in ${model.id}")
                    files.size == 1 -> startVariantDownload(model.id, files.first())
                    else -> {
                        // Multiple variants — let the user pick (dialog suggests
                        // the best one for this device's SoC/RAM).
                        hfVariantPick = model.id to files
                        hfLoading = false
                    }
                }
            } catch (e: com.litert.server.hf.HfHttpException) {
                hfLoading = false
                if (e.code == 401 || e.code == 403) {
                    // Token lacks access to this gated repo — open its page.
                    openModelPage(model.id)
                } else {
                    hfError = e.message
                }
            } catch (e: Exception) {
                hfError = e.message
                hfLoading = false
            }
        }
    }

    private fun openModelPage(modelId: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/$modelId")))
        Toast.makeText(
            this,
            "Gated model — accept the license on the HuggingFace page, then try again",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun startVariantDownload(modelId: String, file: com.litert.server.hf.HfSibling) {
        val url = "https://huggingface.co/$modelId/resolve/main/${file.rfilename}"
        startDownload(url, file.rfilename, file.size)
    }

    private fun selectAndLoadModel(model: com.litert.server.download.LocalModel) {
        // Unload whatever is currently loaded before bringing up the new model
        // (the service also guards this, but dropping our reference first keeps
        // the UI from talking to a dying engine).
        liteRTEngine = null
        chatMessages.clear()
        lifecycleScope.launch {
            settingsStore.setLastModelPath(model.path)
            appState = appState.copy(selectedModelPath = model.path)
            startEngineService(model.path)
        }
    }

    private fun unloadModel() {
        stopService(Intent(this, LLMForegroundService::class.java))
        liteRTEngine = null
        chatMessages.clear()
        refreshModelLibrary()
    }

    private fun startDownload(url: String, filename: String, expectedBytes: Long?) {
        pendingDownload = Triple(url, filename, expectedBytes)
        appState = appState.copy(status = AppStatus.DOWNLOADING, errorMessage = null, downloadProgress = 0f)
        lifecycleScope.launch(Dispatchers.IO) {
            val token = settingsStore.current().hfToken
            downloadManager.downloadModel(url, filename, token, expectedBytes)
                .catch { e ->
                    appState = appState.copy(status = AppStatus.DOWNLOAD_ERROR, errorMessage = e.message)
                }
                .collect { progress ->
                    appState = appState.copy(
                        downloadProgress = progress.progressPercent,
                        downloadedMb = progress.downloadedMb,
                        totalMb = progress.totalMb,
                        downloadSpeedMbps = progress.speedMbps,
                        etaSeconds = progress.etaSeconds
                    )
                    if (progress.isDone) {
                        withContext(Dispatchers.Main) { refreshModelLibrary() }
                    }
                }
        }
    }

    private fun retryDownload() {
        pendingDownload?.let { (url, filename, bytes) -> startDownload(url, filename, bytes) }
            ?: refreshModelLibrary()
    }

    private fun startEngineService(modelPath: String) {
        appState = appState.copy(status = AppStatus.INITIALIZING)
        val intent = Intent(this, LLMForegroundService::class.java).apply {
            putExtra(LLMForegroundService.EXTRA_MODEL_PATH, modelPath)
        }
        startForegroundService(intent)
    }

    private fun toggleServer() {
        if (appState.isServerRunning) {
            stopService(Intent(this, LLMForegroundService::class.java))
            liteRTEngine = null
            appState = appState.copy(isServerRunning = false, engineReady = false)
        } else {
            startEngineService(appState.selectedModelPath)
        }
    }

    private fun restartService() {
        if (appState.isServerRunning) {
            stopService(Intent(this, LLMForegroundService::class.java))
            liteRTEngine = null
            startEngineService(appState.selectedModelPath)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(engineReceiver)
        super.onDestroy()
    }
}
