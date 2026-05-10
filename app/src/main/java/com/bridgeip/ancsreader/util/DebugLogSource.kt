package com.bridgeip.ancsreader.util

import com.bridgeip.ancsreader.data.model.DebugLogEntry
import kotlinx.coroutines.flow.StateFlow

interface DebugLogSource {
    val entries: StateFlow<List<DebugLogEntry>>
}
