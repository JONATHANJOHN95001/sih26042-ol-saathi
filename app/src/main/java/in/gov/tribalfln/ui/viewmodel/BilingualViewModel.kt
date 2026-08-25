package `in`.gov.tribalfln.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BilingualViewModel — Manages the UI state for bilingual material
 * generation, including worksheet templates, flashcard decks, and
 * export/print operations.
 */
class BilingualViewModel(application: Application) : AndroidViewModel(application) {

    data class BilingualUiState(
        val nipunLevel: Int = 1,
        val isWorksheetMode: Boolean = true,
        val currentLanguage: String = "san",
        val isExporting: Boolean = false,
        val worksheetCount: Int = 0,
        val flashcardCount: Int = 0
    )

    private val _uiState = MutableStateFlow(BilingualUiState())
    val uiState: StateFlow<BilingualUiState> = _uiState.asStateFlow()
}
