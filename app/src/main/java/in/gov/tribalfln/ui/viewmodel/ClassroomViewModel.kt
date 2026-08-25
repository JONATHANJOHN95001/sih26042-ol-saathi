package `in`.gov.tribalfln.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ClassroomViewModel — Manages the UI state for real-time classroom
 * dialogue, including speech recognition, translation results, and
 * visual aid flashcard data.
 */
class ClassroomViewModel(application: Application) : AndroidViewModel(application) {

    data class DialogueUiState(
        val sourceText: String = "",
        val translatedText: String = "",
        val currentLanguage: String = "san",
        val isListening: Boolean = false,
        val isTwoWayMode: Boolean = false,
        val latencyMs: Long = 0L
    )

    private val _uiState = MutableStateFlow(DialogueUiState())
    val uiState: StateFlow<DialogueUiState> = _uiState.asStateFlow()
}
