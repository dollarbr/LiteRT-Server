package com.litert.server.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Hardware facts used to recommend models: SoC identity (for matching
 * per-chipset NPU builds like "*.mediatek.mt6991.litertlm") and total RAM.
 */
data class DeviceSpecs(
    val socModel: String,   // e.g. "MT6878"; empty when unknown (< API 31)
    val totalRamBytes: Long
) {
    val totalRamGb: Float get() = totalRamBytes / 1_073_741_824f

    companion object {
        fun from(context: Context): DeviceSpecs {
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL ?: "" else ""
            return DeviceSpecs(socModel = soc, totalRamBytes = mem.totalMem)
        }
    }
}

/**
 * Heuristics for judging whether a HuggingFace model fits this device.
 * Pure functions so they are unit-testable on the JVM.
 */
object ModelFit {

    private val PARAMS_B = Regex("""(\d+(?:[._]\d+)?)\s*[bB](?![a-zA-Z0-9])""")
    private val PARAMS_M = Regex("""(\d+)\s*[mM](?![a-zA-Z0-9])""")

    // Per-chipset builds on the Hub use several naming conventions:
    //   "Qwen3-0.6B.mediatek.mt6993.litertlm", "Gemma3-1B-IT_q4_ekv1280_mt6989.litertlm",
    //   "gemma-4-E2B-it_qualcomm_sm8750.litertlm", "..._Google_Tensor_G5.litertlm", "..._intel_LNL.litertlm"
    private val SOC_TOKEN = Regex("""(?:^|[._\-])((?:mt|sm|qcs)\d{3,5})(?=[._\-])""", RegexOption.IGNORE_CASE)
    private val VENDOR = Regex(
        """(?:^|[._\-])(mediatek|qualcomm|samsung|intel|(?:google[._\-])?tensor[._\-]g\d)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parameter count in billions parsed from a model id,
     * e.g. "litert-community/Gemma3-1B-IT" -> 1.0, "Qwen3-0.6B" -> 0.6,
     * "SmolLM-135M" -> 0.135. Null when the name carries no size.
     */
    fun paramsBillion(modelId: String): Double? {
        val name = modelId.substringAfterLast('/')
        PARAMS_B.find(name)?.let { return it.groupValues[1].replace('_', '.').toDouble() }
        PARAMS_M.find(name)?.let { return it.groupValues[1].toDouble() / 1000.0 }
        return null
    }

    /**
     * SoC/vendor tag of a per-chipset NPU build filename
     * (e.g. "gemma3-1b-it-int4.mediatek.mt6991.litertlm" -> "mt6991",
     * "..._Google_Tensor_G5.litertlm" -> "google_tensor_g5"),
     * or null when the file is a generic GPU/CPU build.
     */
    fun npuSoc(filename: String): String? {
        SOC_TOKEN.find(filename)?.let { return it.groupValues[1].lowercase() }
        VENDOR.find(filename)?.let { return it.groupValues[1].lowercase().replace(Regex("[._\\-]"), "_") }
        return null
    }

    /** True when an NPU build's SoC tag matches this device's SoC. */
    fun npuMatchesDevice(filename: String, specs: DeviceSpecs): Boolean {
        if (specs.socModel.isBlank() || npuSoc(filename) == null) return false
        // Compare with separators stripped so "Tensor G5" matches "_Google_Tensor_G5"
        // and "MT6878" matches "_ekv1280_mt6878".
        val file = filename.lowercase().replace(Regex("[^a-z0-9]"), "")
        val soc = specs.socModel.lowercase().replace(Regex("[^a-z0-9]"), "")
        return file.contains(soc)
    }

    /** Rough RAM needed to run a model: int4/int8 weights plus KV-cache/runtime overhead. */
    fun estimatedRamGb(paramsB: Double): Double = paramsB * 0.75 + 1.0

    /**
     * Whether a model likely runs on this device. Null when the id carries
     * no parameter count (unknown — callers should not filter those out).
     */
    fun fitsDevice(modelId: String, specs: DeviceSpecs): Boolean? {
        val paramsB = paramsBillion(modelId) ?: return null
        return estimatedRamGb(paramsB) <= specs.totalRamGb * 0.6
    }
}
