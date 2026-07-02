# NPU Offload + Android 16 + Model Library — Design

**Date:** 2026-07-02
**Target device:** Motorola Moto Edge 60 — MediaTek Dimensity 7300 (MT6878), Mali-G615 GPU, Android 16
**Branch:** `feature/npu-android16`

## Goal

Evolve LiteRT-Server to run on Android 16 with NPU acceleration on the Dimensity 7300, let the user pick which locally available model to load at startup, configure the HTTP server port, and connect a HuggingFace account to search and download compatible models.

## Feasibility notes (researched 2026-07-02)

- `litertlm-android` **0.13.1** (current on Maven; project uses 0.10.0) exposes `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)` in the Kotlin API.
- The LiteRT NeuroPilot stack supports MediaTek SoCs **MT6878–MT6991**; the Dimensity 7300 is MT6878, so it is in range.
- **Risk:** prebuilt NPU `.litertlm` model variants on HuggingFace are documented for MT6989/MT6991 (flagship, 4-bit). A build targeting MT6878 may not exist for a given model. Mitigation: automatic backend fallback (below) keeps the app functional on GPU/CPU.
- GPU backend is the SDK's GPU delegate (OpenCL-based on Mali/Adreno). The SDK does not expose a Vulkan option; "GPU" is whatever `Backend.GPU()` provides.
- HuggingFace Hub REST API supports model search (`GET /api/models?library=litert-lm&search=...`) and per-model file listing (`GET /api/models/{id}?blobs=true`). Gemma models are gated, so an account token is required for download anyway.

## Design

### 1. Platform upgrade

- `compileSdk = 36`, `targetSdk = 36` (Android 16); `minSdk` stays 26.
- AGP / Kotlin / Compose BOM bumped as needed to build against SDK 36.
- `litertlm-android` 0.10.0 → 0.13.1; adapt `LiteRTEngine` to any API changes.
- Review manifest for API 36 foreground-service restrictions (`dataSync` type expected to remain valid).

### 2. Backend selection with fallback (engine)

- New enum `BackendType { NPU, GPU, CPU, AUTO }` (AUTO = default).
- `LiteRTEngine.initialize(modelPath, backendPreference, ...)`:
  - AUTO tries **NPU → GPU → CPU** in order, catching init failure at each step (extends the existing GPU→CPU recursion).
  - A forced backend (from Settings) tries only that backend and reports failure without fallback.
- NPU constructed as `Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)`; same backend used for `visionBackend` when supported, else GPU/CPU for vision.
- The engine records the **effective backend**; `LLMForegroundService` broadcasts it (extend `EXTRA_IS_GPU` → `EXTRA_BACKEND` string) and Server/Settings screens display it.

### 3. Model library — selection at startup

- Models live in `[ExternalFilesDir]/models/` (existing single hardcoded `gemma4.litertlm` path is migrated into this dir on first run).
- On launch, a new **ModelLibraryScreen** replaces the current auto-check:
  - Lists local `.litertlm` files (name, size).
  - Tapping a model starts `LLMForegroundService` with that path (engine loads on selection).
  - Switching models later (Settings → "Change model") stops the service and returns to this screen.
  - Buttons: "Search HuggingFace" (→ Model Browser, §5) and "Import file" (existing SAF picker, now copying into `models/`).
- `AppState`: `MODEL_NOT_FOUND` becomes `MODEL_SELECTION`; add `selectedModelPath` and `availableModels`. Last used model is remembered (DataStore) and pre-highlighted, but not auto-loaded — the user confirms selection at startup as requested.

### 4. Configurable server port

- Settings gains a port field (1024–65535, default 8080).
- Persisted in DataStore; passed to `HttpApiServer.start(port)` via service intent extra. The current 8080→8082 retry becomes: try configured port, then +1, +2.
- Changing the port while running restarts the HTTP server (engine stays loaded).

### 5. HuggingFace integration

- **Auth:** Settings field to paste a HF token (fine-grained, read scope). Stored in Preferences DataStore. No OAuth — token paste is simpler and sufficient for a personal device.
- **Model Browser screen:** searches `GET https://huggingface.co/api/models?library=litert-lm&search=<query>` (also a curated default listing of `litert-community`), shows model id, downloads count, and file sizes from `GET /api/models/{id}?blobs=true` (picking `.litertlm` files).
- **Download:** existing `ModelDownloadManager` generalized — takes repo id + filename instead of hardcoded `GemmaVariant`; adds `Authorization: Bearer <token>` header (required for gated Gemma repos); keeps resume-via-Range and progress UI. Files land in `models/`.
- Errors (no network, 401 on gated repo without token) surface in the browser UI; the local model library remains usable offline.

### 6. Persistence

Single Preferences DataStore replacing ad-hoc state: `serverPort`, `backendPreference`, `hfToken`, `lastModelPath`, and the sampler settings currently held only in memory (`temperature`, `topK`, `topP`, `maxTokens`) so they survive restarts.

## Components summary

| Unit | Responsibility | Depends on |
|---|---|---|
| `LiteRTEngine` | init with backend preference + fallback chain; expose effective backend | litertlm-android 0.13.1 |
| `LLMForegroundService` | receives model path + backend + port; broadcasts effective backend | engine, HttpApiServer |
| `HttpApiServer` | unchanged routes; configurable port | engine |
| `data/SettingsStore` (new) | DataStore persistence | — |
| `hf/HuggingFaceApi` (new) | model search + file listing (Ktor client or OkHttp + kotlinx.serialization) | hfToken |
| `download/ModelDownloadManager` | generalized authenticated resumable download into `models/` | HuggingFaceApi |
| `ui/ModelLibraryScreen` (new) | startup model selection | SettingsStore |
| `ui/ModelBrowserScreen` (new) | HF search + download | HuggingFaceApi, ModelDownloadManager |
| `ui/SettingsScreen` | + port, backend selector, HF token, change model | SettingsStore |

## Error handling

- NPU init failure (missing MT6878 build, driver error) → automatic GPU then CPU fallback in AUTO; UI shows effective backend so degradation is visible.
- Forced backend failure → error state with message, user can switch backend or model.
- HF API failure → browser shows error; local library unaffected.
- Download interruption → resume via HTTP Range (existing behavior, kept).

## Verification

- `./gradlew assembleDebug` locally and in existing CI.
- On-device (Moto Edge 60): install, download a model via HF browser, load with AUTO, confirm which backend initialized (expect NPU if an MT6878-compatible model build exists; otherwise GPU), compare tokens/s across forced NPU/GPU/CPU, exercise `/health`, `/v1/chat/completions` (stream + non-stream) on a custom port.

## Out of scope (YAGNI)

- Hot-swapping models without service restart.
- OAuth flow for HuggingFace.
- Running multiple models simultaneously.
- On-device AOT compilation of models for the NPU.
