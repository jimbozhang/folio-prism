package com.jimbozhang.folioprism.ui

import com.jimbozhang.folioprism.domain.history.HistoryResult

sealed interface HistoryUiState {
    data object Idle : HistoryUiState
    /**
     * A history load is in flight. [message] describes what is actually
     * happening right now (e.g. waiting on the network) so the user is never
     * told we are "fetching" when we are only computing from cache. It is
     * null when no wording is needed (fast in-memory compute) — the UI then
     * shows only the spinner.
     */
    data class Loading(val message: String? = null) : HistoryUiState
    data class Ready(val result: HistoryResult) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}
