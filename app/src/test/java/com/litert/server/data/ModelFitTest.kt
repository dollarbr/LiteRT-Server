package com.litert.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFitTest {

    private val eightGbDevice = DeviceSpecs(socModel = "MT6878", totalRamBytes = 8L * 1_073_741_824)

    @Test
    fun `parses parameter count in billions`() {
        assertEquals(1.0, ModelFit.paramsBillion("litert-community/Gemma3-1B-IT")!!, 0.001)
        assertEquals(0.6, ModelFit.paramsBillion("litert-community/Qwen3-0.6B")!!, 0.001)
        assertEquals(2.0, ModelFit.paramsBillion("litert-community/gemma-4-E2B-it-litert-lm")!!, 0.001)
    }

    @Test
    fun `parses parameter count in millions`() {
        assertEquals(0.135, ModelFit.paramsBillion("HuggingFaceTB/SmolLM-135M")!!, 0.001)
        assertEquals(0.27, ModelFit.paramsBillion("google/gemma-3-270m-it")!!, 0.001)
    }

    @Test
    fun `returns null when name has no size`() {
        assertNull(ModelFit.paramsBillion("someorg/mystery-model"))
    }

    @Test
    fun `extracts NPU SoC tag from per-chipset filenames`() {
        assertEquals("mt6991", ModelFit.npuSoc("gemma3-1b-it-int4.mediatek.mt6991.litertlm"))
        assertNull(ModelFit.npuSoc("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"))
    }

    @Test
    fun `NPU build only matches the device SoC`() {
        assertTrue(
            ModelFit.npuMatchesDevice("gemma.mediatek.mt6878.litertlm", eightGbDevice)
        )
        assertFalse(
            ModelFit.npuMatchesDevice("gemma.mediatek.mt6991.litertlm", eightGbDevice)
        )
        assertFalse(
            ModelFit.npuMatchesDevice("generic-gpu-build.litertlm", eightGbDevice)
        )
    }

    @Test
    fun `small models fit and huge models do not`() {
        assertEquals(true, ModelFit.fitsDevice("litert-community/Gemma3-1B-IT", eightGbDevice))
        assertEquals(false, ModelFit.fitsDevice("someorg/Llama-70B", eightGbDevice))
        assertNull(ModelFit.fitsDevice("someorg/mystery-model", eightGbDevice))
    }
}
