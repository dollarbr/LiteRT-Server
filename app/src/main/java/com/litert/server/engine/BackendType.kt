package com.litert.server.engine

enum class BackendType {
    AUTO, NPU, GPU, CPU;

    /** Concrete backends to attempt, in order. AUTO tries NPU → GPU → CPU; forced backends never fall back. */
    fun fallbackChain(): List<BackendType> = when (this) {
        AUTO -> listOf(NPU, GPU, CPU)
        else -> listOf(this)
    }

    companion object {
        fun fromString(value: String?): BackendType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}
