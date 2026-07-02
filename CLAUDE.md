# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A native Android app (Kotlin + Jetpack Compose) that runs Google's Gemma 4 multimodal LLM on-device via the LiteRT-LM SDK, and exposes it over an embedded Ktor HTTP server on localhost:8080 with OpenAI-compatible routes. Single Gradle module (`:app`), no tests.

## Build commands

```bash
./gradlew assembleDebug     # build debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # build + install on connected device
```

- Requires JDK 17 and Android SDK 35. minSdk 26, targetSdk 35.
- `gradle/wrapper/gradle-wrapper.jar` is NOT committed. CI (`.github/workflows/build.yml`) downloads it from the Gradle v8.9.0 tag before building; locally you need it present (or a system Gradle 8.9) for `./gradlew` to work.
- The app can only be meaningfully exercised on a physical device — inference needs the ~2.6 GB Gemma model downloaded at first launch (or side-loaded via the in-app file picker).

## Architecture

All code lives under `app/src/main/java/com/litert/server/`. The core runtime flow spans several files:

1. **MainActivity** owns all UI state (`AppState` in `data/AppState.kt`, a status enum driving which screen renders). On launch it checks for the model file; if missing, `DownloadScreen` + `download/ModelDownloadManager` fetch it from HuggingFace (resumable via HTTP Range). Once the model exists, MainActivity starts `LLMForegroundService`.

2. **service/LLMForegroundService** (foreground service, START_STICKY) is where the engine actually lives. On start it initializes `engine/LiteRTEngine` and boots `service/HttpApiServer`. It communicates back to MainActivity two ways:
   - **Broadcasts** (`ACTION_ENGINE_READY` / `ACTION_ENGINE_ERROR`, package-scoped) for state transitions.
   - **`LLMForegroundService.engineInstance`** — a `@Volatile` companion-object singleton MainActivity reads so in-app Chat/Vision screens call the engine directly, bypassing HTTP. It's set before the ready broadcast and cleared in `onDestroy`.

3. **engine/LiteRTEngine** wraps the LiteRT-LM SDK (`Engine`/`Conversation`). If GPU backend init fails it recursively retries with CPU. Conversation history is held in a single `Conversation`; `clearHistory()` recreates it. Both the HTTP server and the in-app UI share this one conversation/state.

4. **service/HttpApiServer** (Ktor CIO) tries ports 8080→8082. Routes:
   - OpenAI-compatible: `GET /v1/models`, `POST /v1/chat/completions` (supports SSE streaming via `stream: true`; message history is flattened into a single `role: content` prompt string).
   - Legacy: `POST /chat`, `POST /vision` (takes an on-device `imagePath`), `POST /reset`, `GET /health`.
   - Request/response DTOs are `@Serializable` classes in `data/AppState.kt` (legacy) and `data/OaiModels.kt` (OpenAI-compatible).

Gemma model variants (E2B/E4B download URLs, filenames) are defined in `download/ModelDownloadManager.kt` (`GemmaVariant`).

## Android 15 constraints (API 35)

- Foreground service must use `ServiceCompat.startForeground()` with an explicit type (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`); types are also declared in the manifest.
- `POST_NOTIFICATIONS` is requested at runtime; battery-optimization exemption requested on first launch.
- GPU inference relies on `libOpenCL.so` / `libvndksupport.so` declared as `uses-native-library` in the manifest.
