package com.jimbozhang.folioprism.ui

import com.jimbozhang.folioprism.domain.history.HistoryResult

sealed interface HistoryUiState {
    data object Idle : HistoryUiState
    data object Loading : HistoryUiState
    data class Ready(val result: HistoryResult) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}
