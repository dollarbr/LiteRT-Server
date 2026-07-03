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
    fun `extracts NPU SoC tag from every filename convention on the Hub`() {
        assertEquals("mt6991", ModelFit.npuSoc("gemma3-1b-it-int4.mediatek.mt6991.litertlm"))
        assertEquals("mt6989", ModelFit.npuSoc("Gemma3-1B-IT_q4_ekv1280_mt6989.litertlm"))
        assertEquals("sm8750", ModelFit.npuSoc("gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertEquals("qcs8275", ModelFit.npuSoc("gemma-4-E2B-it_qualcomm_qcs8275.litertlm"))
        assertEquals("google_tensor_g5", ModelFit.npuSoc("gemma-4-E2B-it_Google_Tensor_G5.litertlm"))
        assertEquals("intel", ModelFit.npuSoc("gemma-4-E2B-it_intel_LNL.litertlm"))
        assertNull(ModelFit.npuSoc("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"))
        assertNull(ModelFit.npuSoc("gemma3-1b-it-int4.litertlm"))
    }

    @Test
    fun `NPU build only matches the device SoC`() {
        assertTrue(
            ModelFit.npuMatchesDevice("gemma.mediatek.mt6878.litertlm", eightGbDevice)
        )
        assertTrue(
            ModelFit.npuMatchesDevice("Gemma3-1B-IT_q4_ekv1280_mt6878.litertlm", eightGbDevice)
        )
        assertFalse(
            ModelFit.npuMatchesDevice("gemma.mediatek.mt6991.litertlm", eightGbDevice)
        )
        assertFalse(
            ModelFit.npuMatchesDevice("generic-gpu-build.litertlm", eightGbDevice)
        )
        val tensorDevice = DeviceSpecs(socModel = "Tensor G5", totalRamBytes = 8L * 1_073_741_824)
        assertTrue(
            ModelFit.npuMatchesDevice("gemma-4-E2B-it_Google_Tensor_G5.litertlm", tensorDevice)
        )
    }

    @Test
    fun `small models fit and huge models do not`() {
        assertEquals(true, ModelFit.fitsDevice("litert-community/Gemma3-1B-IT", eightGbDevice))
        assertEquals(false, ModelFit.fitsDevice("someorg/Llama-70B", eightGbDevice))
        assertNull(ModelFit.fitsDevice("someorg/mystery-model", eightGbDevice))
    }
}
