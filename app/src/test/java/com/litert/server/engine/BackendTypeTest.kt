package com.litert.server.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendTypeTest {

    @Test
    fun `AUTO falls back NPU then GPU then CPU`() {
        assertEquals(
            listOf(BackendType.NPU, BackendType.GPU, BackendType.CPU),
            BackendType.AUTO.fallbackChain()
        )
    }

    @Test
    fun `forced backend has single-element chain`() {
        assertEquals(listOf(BackendType.NPU), BackendType.NPU.fallbackChain())
        assertEquals(listOf(BackendType.GPU), BackendType.GPU.fallbackChain())
        assertEquals(listOf(BackendType.CPU), BackendType.CPU.fallbackChain())
    }

    @Test
    fun `fromString is case-insensitive and defaults to AUTO`() {
        assertEquals(BackendType.NPU, BackendType.fromString("npu"))
        assertEquals(BackendType.GPU, BackendType.fromString("Gpu"))
        assertEquals(BackendType.AUTO, BackendType.fromString(null))
        assertEquals(BackendType.AUTO, BackendType.fromString("quantum"))
    }
}
