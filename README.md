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
├── hf/HuggingFaceApi.kt         — HuggingFace Hub search/detail client (litert-lm filter, author/sort params, Bearer token)
├── service/
│   ├── LLMForegroundService.kt  — Android foreground service (START_STICKY), reads settings itself
│   └── HttpApiServer.kt         — Ktor CIO embedded HTTP server, configurable port
└── ui/
    ├── ChatScreen.kt            — Text chat with streaming tokens
    ├── VisionScreen.kt          — Image + text analysis
    ├── ServerScreen.kt          — Server control panel + request log + curl examples
    ├── DownloadScreen.kt        — Model download UI with progress
    ├── ModelLibraryScreen.kt    — Startup screen: pick from installed models, last-used highlighted (not auto-loaded)
    ├── ModelBrowserScreen.kt    — HuggingFace search + download UI (sort + author filter chips)
    └── SettingsScreen.kt        — Server port, backend preference, HF token, full sampler config, model management
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

## Sampler Settings

Settings exposes the full sampler configuration — **temperature** (0–2), **top K** (≥1), **top P** (0–1) and **max tokens**. Each has a slider, and tapping the green value opens a dialog to type an exact value. Max tokens accepts any positive integer, but values above **8192** show a warning: the KV cache growth can freeze or crash the app due to memory pressure on-device. Sampler changes apply on the next model load.

**MT6878 (Dimensity 7300) caveat**: NPU acceleration depends on a prebuilt NPU-compatible model being available for the selected model/variant. Not every `.litertlm` model on HuggingFace ships an NPU build for this chipset — if none is available, AUTO falls through to GPU (Mali-G615) automatically, and a forced NPU selection will fail loudly rather than fall back.

## Model Library

- Models live in `[ExternalFilesDir]/models/`. Files previously downloaded to the external-files root (pre-fork layout) are migrated into this directory automatically on first launch.
- The **model browser** (`ui/ModelBrowserScreen.kt`) searches HuggingFace's Hub for repos tagged `litert-lm` (via the Hub API's `filter` param) and exposes the full search API: sort by downloads / likes / last update / creation date / trending (ascending or descending), free-text author filter with quick chips (`litert-community`, `google`), and result limit (25/50/100). Two extras are computed client-side (the Hub API doesn't support them): a **"File name contains"** filter matching against each repo's `.litertlm` filenames (e.g. `mt6878`, `q4`), and **sort by size**. Sizes aren't returned by the listing endpoint, so after each search the app fetches per-repo file details in the background (6 at a time) and shows each model's file count and size range as they arrive. A **"Fits device"** chip hides models estimated too large for the device's RAM (parameter count parsed from the model name — `data/DeviceSpecs.kt`/`ModelFit`), and a stricter **"⚡ Best for device"** chip keeps only models with an NPU build matching the device SoC (listed first, with an ⚡ NPU badge) or a confirmed comfortable RAM fit; each result shows its estimated size and fit. Downloads resume via HTTP Range headers; URLs are resolved dynamically at runtime — there is no fixed list of models baked into the app.
- When a repo ships **multiple `.litertlm` variants**, a picker dialog lists every file with its size and badges, and pre-highlights the recommended one for this device: an NPU build matching the device SoC (`Build.SOC_MODEL`, e.g. `mt6878`) wins; otherwise the largest generic build that still fits in RAM. NPU builds for other SoCs are flagged incompatible; oversized files get a RAM warning.
- Gated repositories (e.g. official `google/gemma-*` models) show a 🔒 badge and require a HuggingFace access token (sent as a `Bearer` token on Hub API and download requests). Tapping download without access (no token, or HTTP 401/403) opens the model's HuggingFace page directly so you can request access / accept the license.
- The **model library** (`ui/ModelLibraryScreen.kt`) lists installed models on startup; the last-used model is highlighted but not auto-loaded — you pick a model explicitly each launch.
- **Unloading:** Settings has an **Unload** button that stops the engine and HTTP server and frees the model's memory. Switching models always unloads the previous one first — the foreground service shuts down the old engine and releases the port before initializing the new model.

## Android 16 Notes

- Foreground service type: `specialUse|dataSync`
- `POST_NOTIFICATIONS` requested at runtime
- Battery optimization exemption requested on first launch
- `ServiceCompat.startForeground()` used with correct type flags
- Edge-to-edge: the Compose root applies `safeDrawingPadding()`, so content never draws under the notch/status bar and resizes when the keyboard opens

## Download

Prebuilt debug APKs are published on the [Releases page](https://github.com/dollarbr/LiteRT-Server/releases). Every push to `main` also uploads an APK artifact to the [Build APK workflow](https://github.com/dollarbr/LiteRT-Server/actions) (7-day retention).

All APKs are signed with the shared keystore committed at `signing/shared.keystore`, so newer releases install directly over older ones as updates. (This key is intentionally public, like a debug key — never reuse it for Play Store distribution.) Releases up to v0.1.4 were signed with throwaway CI keys; updating from one of those requires uninstalling once before installing v0.1.5+.
