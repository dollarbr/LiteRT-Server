# LiteRT Server — Android Studio Project

A complete native Android application in Kotlin that runs multimodal LLMs (Gemma and other
LiteRT-LM-compatible models, downloaded dynamically from HuggingFace) locally on-device via
Google's LiteRT-LM SDK, with NPU/GPU/CPU backend selection.

## Requirements

- Android Studio (current stable) or newer
- Android SDK 36 (Android 16)
- JDK 17
- Target device: Android 8.0+ (API 26+) — developed against Moto Edge 60 / MediaTek Dimensity 7300 (MT6878) / Mali-G615 / Android 16

## Project Structure

```
app/src/main/java/com/litert/server/
├── MainActivity.kt              — Entry point, navigation, state management
├── data/
│   ├── AppState.kt              — All data classes and app state enum
│   └── SettingsStore.kt         — DataStore-backed settings (port, backend, HF token, last model, sampler)
├── download/ModelDownloadManager.kt  — HuggingFace model download with resume + progress, models dir + legacy migration
├── engine/
│   ├── LiteRTEngine.kt          — LiteRT-LM SDK wrapper (NPU/GPU/CPU backend)
│   └── BackendType.kt           — AUTO/NPU/GPU/CPU enum with fallback chain
├── hf/HuggingFaceApi.kt         — HuggingFace Hub search/detail client (litert-lm library filter, Bearer token)
├── service/
│   ├── LLMForegroundService.kt  — Android foreground service (START_STICKY), reads settings itself
│   └── HttpApiServer.kt         — Ktor CIO embedded HTTP server, configurable port
└── ui/
    ├── ChatScreen.kt            — Text chat with streaming tokens
    ├── VisionScreen.kt          — Image + text analysis
    ├── ServerScreen.kt          — Server control panel + request log + curl examples
    ├── DownloadScreen.kt        — Model download UI with progress
    ├── ModelLibraryScreen.kt    — Startup screen: pick from installed models, last-used highlighted (not auto-loaded)
    ├── ModelBrowserScreen.kt    — HuggingFace search + download UI
    └── SettingsScreen.kt        — Server port, backend preference, HF token, sampler, model management
```

## Setup

1. Clone / open this folder in Android Studio
2. Let Gradle sync (it will download ~200MB of dependencies)
3. Build and install on your device: `./gradlew installDebug`
4. On first launch the app opens the **model library** (empty on a fresh install). Use the model browser to search HuggingFace for a `.litertlm` model and download it, or import a file directly.

## HTTP API (Ktor, configurable port)

The server listens on the port configured in Settings (default `8080`, valid range `1024`–`65535`; if that port is taken it tries the next two ports).

**Security note:** the server binds to all network interfaces (`0.0.0.0`), not just `localhost` — it is reachable from any other device on the same local network, and there is no authentication. This is by design, to let other devices on your LAN talk to the model server. Be aware that the `/vision` endpoint accepts a device file path and reads that file directly, so anyone who can reach the port can ask the device to read arbitrary files it has access to. Only run this on networks you trust.

Once a model is loaded and the server is running:

```bash
# Health check — "backend" reports which backend the engine actually initialized with (NPU/GPU/CPU)
curl http://localhost:8080/health

# OpenAI-compatible routes
curl http://localhost:8080/v1/models
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"x","messages":[{"role":"user","content":"Hello!"}],"stream":true}'

# Legacy routes (kept for backward compat)
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello!"}'

curl -X POST http://localhost:8080/vision \
  -H "Content-Type: application/json" \
  -d '{"imagePath":"/sdcard/DCIM/photo.jpg","prompt":"Describe this image"}'

curl -X POST http://localhost:8080/reset
```

`/health` returns a `backend` field (string, e.g. `"NPU"`, `"GPU"`, `"CPU"`) instead of the old boolean `gpu` field. `/v1/models` returns the currently loaded model's name (derived from its filename), not a fixed model id.

## Backend Selection (NPU / GPU / CPU)

Backend preference is set in Settings and stored via `data/SettingsStore.kt`. `engine/BackendType.kt` defines the fallback behavior:

- **AUTO** (default): tries **NPU → GPU → CPU**, in order, until one initializes successfully.
- **Forced backend** (NPU, GPU, or CPU explicitly selected): only that backend is attempted — it never silently falls back. If it fails to initialize, the engine reports an error instead of degrading.

`LiteRTEngine` logs which backend actually initialized; `/health` and the in-app UI surface the same value.

**MT6878 (Dimensity 7300) caveat**: NPU acceleration depends on a prebuilt NPU-compatible model being available for the selected model/variant. Not every `.litertlm` model on HuggingFace ships an NPU build for this chipset — if none is available, AUTO falls through to GPU (Mali-G615) automatically, and a forced NPU selection will fail loudly rather than fall back.

## Model Library

- Models live in `[ExternalFilesDir]/models/`. Files previously downloaded to the external-files root (pre-fork layout) are migrated into this directory automatically on first launch.
- The **model browser** (`ui/ModelBrowserScreen.kt`) searches HuggingFace's Hub for repos tagged `library=litert-lm` and lists their `.litertlm` files for download, with resumable progress via HTTP Range headers. Search results and download URLs are resolved dynamically at runtime — there is no fixed list of models baked into the app.
- Gated repositories (e.g. official `google/gemma-*` models) require a HuggingFace access token. Paste one in Settings; it's sent as a `Bearer` token on Hub API requests and download requests.
- The **model library** (`ui/ModelLibraryScreen.kt`) lists installed models on startup; the last-used model is highlighted but not auto-loaded — you pick a model explicitly each launch.

## Android 16 Notes

- Foreground service type: `specialUse|dataSync`
- `POST_NOTIFICATIONS` requested at runtime
- Battery optimization exemption requested on first launch
- `ServiceCompat.startForeground()` used with correct type flags

## APK Link

https://drive.google.com/file/d/147EVwUyKYFmUYRys2-xXf1qiRUDXqL50/view?usp=sharing

**Note:** this is an older pre-fork build and does not reflect the current state of this repository (NPU/Android 16 support, OpenAI-compatible routes, etc.). Build from source (`./gradlew installDebug`) to get the latest version.
