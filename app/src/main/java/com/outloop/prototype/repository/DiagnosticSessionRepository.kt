package com.outloop.prototype.repository

import com.outloop.classifier.ClassificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory repository managing diagnostic session state and classification records.
 *
 * PRIVACY & LIFECYCLE GUARANTEES:
 * - Diagnostics are OFF by default on application launch and restart.
 * - All results are stored strictly in-memory (zero database, zero disk persistence, zero cloud transmission).
 * - When the session is stopped, processing halts immediately and new entries are dropped.
 * - Clearing results removes all retained in-memory diagnostic entries.
 */
object DiagnosticSessionRepository {

    /** Maximum in-memory retention capacity for diagnostic events. Increased to 1000 for full session coverage. */
    const val MAX_STORED_RESULTS = 1000

    /** True if OutLoopAccessibilityService is currently connected by Android. */
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    /**
     * True if the user has explicitly authorized and started a diagnostic session.
     * MUST be false by default across all app launches and restarts.
     */
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    /** Total cumulative events recorded in this session (including any evicted due to capacity). */
    private val _totalRecordedCount = MutableStateFlow(0)
    val totalRecordedCount: StateFlow<Int> = _totalRecordedCount.asStateFlow()

    /** Live list of sanitized diagnostic classification results (most recent first). */
    private val _diagnosticResults = MutableStateFlow<List<ClassificationResult>>(emptyList())
    val diagnosticResults: StateFlow<List<ClassificationResult>> = _diagnosticResults.asStateFlow()

    /** Whether privacy-safe structural tree dump collection is enabled during sessions. */
    private val _isDetailedTreeDumpEnabled = MutableStateFlow(true)
    val isDetailedTreeDumpEnabled: StateFlow<Boolean> = _isDetailedTreeDumpEnabled.asStateFlow()

    fun setDetailedTreeDumpEnabled(enabled: Boolean) {
        _isDetailedTreeDumpEnabled.value = enabled
    }

    /** Called by OutLoopAccessibilityService on connection/disconnection. */
    fun setServiceConnected(connected: Boolean) {
        _isServiceConnected.value = connected
        if (!connected) {
            // Disconnection automatically terminates any ongoing diagnostic session
            _isSessionActive.value = false
        }
    }

    /** Explicitly starts a diagnostic session authorizing local structural inspection. */
    fun startSession() {
        _isSessionActive.value = true
    }

    /**
     * Explicitly stops diagnostic processing.
     * Immediately prevents any further events or hierarchy nodes from being inspected or recorded.
     */
    fun stopSession() {
        _isSessionActive.value = false
    }

    /** Clears all stored in-memory diagnostic results and resets total session counter. */
    fun clearResults() {
        _diagnosticResults.value = emptyList()
        _totalRecordedCount.value = 0
    }

    /**
     * Records a new classification result if and only if a diagnostic session is currently active.
     */
    fun recordResult(result: ClassificationResult) {
        if (!_isSessionActive.value) {
            return
        }
        _totalRecordedCount.update { it + 1 }
        _diagnosticResults.update { currentList ->
            listOf(result) + currentList.take(MAX_STORED_RESULTS - 1)
        }
    }

    /**
     * Returns an immutable snapshot of the current session state and results.
     */
    fun getSnapshot(): DiagnosticSessionSnapshot {
        val currentResults = _diagnosticResults.value
        val total = _totalRecordedCount.value
        val active = _isSessionActive.value
        return DiagnosticSessionSnapshot(
            results = currentResults,
            totalRecordedCount = total,
            retainedCount = currentResults.size,
            droppedCount = maxOf(0, total - currentResults.size),
            maxCapacity = MAX_STORED_RESULTS,
            isSessionActive = active
        )
    }
}

/**
 * Immutable snapshot of the diagnostic repository state at a given instant.
 */
data class DiagnosticSessionSnapshot(
    val results: List<ClassificationResult>,
    val totalRecordedCount: Int,
    val retainedCount: Int,
    val droppedCount: Int,
    val maxCapacity: Int,
    val isSessionActive: Boolean
)
