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

    private const val MAX_STORED_RESULTS = 100

    /** True if OutLoopAccessibilityService is currently connected by Android. */
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    /**
     * True if the user has explicitly authorized and started a diagnostic session.
     * MUST be false by default across all app launches and restarts.
     */
    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    /** Live list of sanitized diagnostic classification results (most recent first). */
    private val _diagnosticResults = MutableStateFlow<List<ClassificationResult>>(emptyList())
    val diagnosticResults: StateFlow<List<ClassificationResult>> = _diagnosticResults.asStateFlow()

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

    /** Clears all stored in-memory diagnostic results. */
    fun clearResults() {
        _diagnosticResults.value = emptyList()
    }

    /**
     * Records a new classification result if and only if a diagnostic session is currently active.
     */
    fun recordResult(result: ClassificationResult) {
        if (!_isSessionActive.value) {
            return
        }
        _diagnosticResults.update { currentList ->
            listOf(result) + currentList.take(MAX_STORED_RESULTS - 1)
        }
    }
}
