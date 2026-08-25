package `in`.gov.tribalfln.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import `in`.gov.tribalfln.databinding.FragmentZeroNetworkMeshSyncBinding
import `in`.gov.tribalfln.mesh.ClassroomMeshSync
import `in`.gov.tribalfln.ui.viewmodel.SyncViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ZeroNetworkMeshSyncFragment — Manages offline P2P mesh network connections,
 * storage metrics, and language pack downloads for zero-internet classroom sync.
 */
class ZeroNetworkMeshSyncFragment : Fragment() {

    private var _binding: FragmentZeroNetworkMeshSyncBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SyncViewModel by lazy {
        ViewModelProvider(this)[SyncViewModel::class.java]
    }

    private var currentLanguageCode: String = "san"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentZeroNetworkMeshSyncBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGoOnlineButton()
        observeSyncState()
    }

    private fun setupGoOnlineButton() {
        binding.btnGoOnline.setOnClickListener {
            Toast.makeText(requireContext(), "Scanning for peers...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeSyncState() {
        // In production, collect ViewModel StateFlow for mesh state updates
        viewModel.uiState.let { /* observe in lifecycle scope */ }
    }

    fun onLanguageChanged(languageCode: String) {
        currentLanguageCode = languageCode
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): ZeroNetworkMeshSyncFragment = ZeroNetworkMeshSyncFragment()
    }
}
