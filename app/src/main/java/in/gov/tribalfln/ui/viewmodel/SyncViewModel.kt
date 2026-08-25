package `in`.gov.tribalfln.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import `in`.gov.tribalfln.mesh.ClassroomMeshSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SyncViewModel — Manages the UI state for the mesh sync screen,
 * including peer discovery, file transfers, and storage metrics.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {

    data class SyncUiState(
        val meshState: ClassroomMeshSync.State = ClassroomMeshSync.State.IDLE,
        val isOnline: Boolean = false,
        val peerCount: Int = 0,
        val isTransferring: Boolean = false
    )

    data class StorageMetrics(
        val usedGB: Double = 0.0,
        val totalGB: Double = 0.0,
        val vectorDbMB: Double = 0.0,
        val audioAssetsMB: Double = 0.0,
        val pdfCacheMB: Double = 0.0,
        val coreAppMB: Double = 0.0
    )

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _storageMetrics = MutableStateFlow(StorageMetrics())
    val storageMetrics: StateFlow<StorageMetrics> = _storageMetrics.asStateFlow()
}
