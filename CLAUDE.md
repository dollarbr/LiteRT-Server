# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A native Android app (Kotlin + Jetpack Compose) that runs multimodal LLMs (Gemma and other LiteRT-LM-compatible models, downloaded dynamically from HuggingFace) on-device via the LiteRT-LM SDK, with NPU/GPU/CPU backend selection, and exposes it over an embedded Ktor HTTP server (configurable port, default 8080) with OpenAI-compatible routes. Single Gradle module (`:app`); unit tests live under `app/src/test/`.

## Build commands

```bash
./gradlew assembleDebug     # build debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # build + install on connected device
./gradlew testDebugUnitTest # run JVM unit tests (BackendTypeTest, HfJsonTest — 5 tests)
```

- Requires JDK 17 and Android SDK 36. minSdk 26, compileSdk/targetSdk 36.
- LiteRT-LM SDK: `com.google.ai.edge.litertlm:litertlm-android:0.13.1`.
- `gradle/wrapper/gradle-wrapper.jar` is NOT committed. CI (`.github/workflows/build.yml`) downloads it from the Gradle v8.13.0 tag before building; locally you need it present (or a system Gradle 8.13) for `./gradlew` to work.
- The app can only be meaningfully exercised on a physical device — inference needs a `.litertlm` model downloaded via the in-app model browser (or side-loaded via the file picker). Target device for this branch's work: Moto Edge 60 / Dimensity 7300 (MT6878) / Mali-G615 / Android 16.

## Architecture

All code lives under `app/src/main/java/com/litert/server/`. The core runtime flow spans several files:

1. **MainActivity** owns all UI state (`AppState` in `data/AppState.kt`, a status enum driving which screen renders). On launch it shows `ui/ModelLibraryScreen` listing models found in `[ExternalFilesDir]/models/` (legacy pre-fork models are migrated in automatically); the last-used model is highlighted but not auto-loaded. The user picks a model (or opens `ui/ModelBrowserScreen` to search/download from HuggingFace, or imports a file). Selecting a model starts `LLMForegroundService`, passing only the model path as an intent extra — the service reads everything else (port, backend preference, sampler settings, HF token) itself from `data/SettingsStore.kt`.

2. **data/SettingsStore.kt** wraps a Jetpack DataStore (`Preferences`) holding `serverPort`, `backendPreference`, `hfToken`, `lastModelPath`, and sampler config (temperature/topK/topP/maxTokens). `ui/SettingsScreen.kt` writes to it; `LLMForegroundService` reads it directly on start rather than receiving values via intent/broadcast.

3. **service/LLMForegroundService** (foreground service, START_STICKY) is where the engine actually lives. On start it reads `SettingsStore`, initializes `engine/LiteRTEngine` with the configured backend preference and sampler settings, and boots `service/HttpApiServer`. It communicates back to MainActivity two ways:
   - **Broadcasts** (`ACTION_ENGINE_READY` / `ACTION_ENGINE_ERROR`, package-scoped); `ACTION_ENGINE_READY` carries `EXTRA_SERVER_PORT` and `EXTRA_BACKEND` (the backend the engine actually initialized with, as a string).
   - **`LLMForegroundService.engineInstance`** — a `@Volatile` companion-object singleton MainActivity reads so in-app Chat/Vision screens call the engine directly, bypassing HTTP. It's set before the ready broadcast and cleared in `onDestroy`.

4. **engine/BackendType.kt** defines `AUTO`/`NPU`/`GPU`/`CPU` and each type's `fallbackChain()`: `AUTO` expands to `[NPU, GPU, CPU]`; any concrete backend expands to just itself (forced backends never fall back).

5. **engine/LiteRTEngine** wraps the LiteRT-LM SDK (`Engine`/`Conversation`). `initialize()` walks `preference.fallbackChain()`, trying each backend in turn and only falling through on failure — so a forced backend either succeeds or reports an error, never silently degrading. Conversation history is held in a single `Conversation`; `clearHistory()` recreates it. Both the HTTP server and the in-app UI share this one conversation/state. `getBackend()` exposes the effective backend name.

6. **hf/HuggingFaceApi.kt** is a minimal HuggingFace Hub client: searches `https://huggingface.co/api/models?library=litert-lm`, fetches per-model file listings, and filters to `.litertlm` files. Sends `Authorization: Bearer <token>` when an HF token is configured (required for gated repos like official `google/gemma-*`).

7. **service/HttpApiServer** (Ktor CIO), constructed as `HttpApiServer(engine, modelName, onRequest)`, tries `basePort..basePort+2` (default base 8080, configurable via Settings). Routes:
   - OpenAI-compatible: `GET /v1/models` (returns the loaded model's name), `POST /v1/chat/completions` (supports SSE streaming via `stream: true`; message history is flattened into a single `role: content` prompt string).
   - Legacy: `POST /chat`, `POST /vision` (takes an on-device `imagePath`), `POST /reset`, `GET /health` (returns `backend` as a string instead of the old boolean `gpu` field).
   - Request/response DTOs are `@Serializable` classes in `data/AppState.kt` (legacy) and `data/OaiModels.kt` (OpenAI-compatible).

Model downloading/storage (`download/ModelDownloadManager.kt`) is generic — there is no fixed `GemmaVariant` list; models are discovered dynamically via `hf/HuggingFaceApi.kt` and stored under `[ExternalFilesDir]/models/`.

## Android 16 constraints (API 36)

- Foreground service must use `ServiceCompat.startForeground()` with an explicit type (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`); types are also declared in the manifest.
- `POST_NOTIFICATIONS` is requested at runtime; battery-optimization exemption requested on first launch.
- GPU inference relies on `libOpenCL.so` / `libvndksupport.so` declared as `uses-native-library` in the manifest; NPU inference uses `Backend.NPU(nativeLibraryDir = ...)` and depends on a prebuilt NPU-compatible model being available for the target chipset (uncertain for MT6878 across all models — see README's Backend Selection section).
