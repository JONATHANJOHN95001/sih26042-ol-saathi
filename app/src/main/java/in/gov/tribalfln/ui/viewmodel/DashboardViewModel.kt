package `in`.gov.tribalfln.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * DashboardViewModel — Provides dashboard UI state with student count
 * and class-wide mastery percentage from the Room database.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    data class DashboardUiState(
        val studentCount: Int = 0,
        val classMastery: Float = 0f,
        val activeLanguage: String = "san",
        val isInitialized: Boolean = false
    )

    private val _uiState = DashboardUiState()
    val uiState: DashboardUiState get() = _uiState
}
