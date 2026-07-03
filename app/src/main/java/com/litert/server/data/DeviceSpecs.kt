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
    private val NPU_SOC = Regex("""\.(mediatek|qualcomm)\.([a-z0-9_-]+)\.""", RegexOption.IGNORE_CASE)

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
     * SoC tag of a per-chipset NPU build filename
     * (e.g. "gemma3-1b-it-int4.mediatek.mt6991.litertlm" -> "mt6991"),
     * or null when the file is a generic GPU/CPU build.
     */
    fun npuSoc(filename: String): String? =
        NPU_SOC.find(filename)?.groupValues?.get(2)?.lowercase()

    /** True when an NPU build's SoC tag matches this device's SoC. */
    fun npuMatchesDevice(filename: String, specs: DeviceSpecs): Boolean {
        val soc = npuSoc(filename) ?: return false
        return specs.socModel.isNotBlank() && soc.equals(specs.socModel, ignoreCase = true)
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
