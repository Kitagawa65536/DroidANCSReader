package com.bridgeip.ancsreader.util

import com.bridgeip.ancsreader.data.model.DebugLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugLogStore(
    private val maxEntries: Int = 300,
) {
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    fun append(message: String) {
        val entry = DebugLogEntry(
            timestampMillis = System.currentTimeMillis(),
            message = message,
        )
        _entries.value = (_entries.value + entry).takeLast(maxEntries)
    }
}

