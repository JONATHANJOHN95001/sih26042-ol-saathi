package `in`.gov.tribalfln.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import `in`.gov.tribalfln.databinding.FragmentHomeDashboardBinding
import `in`.gov.tribalfln.ui.viewmodel.DashboardViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * HomeDashboardFragment — Main dashboard overview showing student count,
 * class mastery percentage, and quick navigation to other screens.
 */
class HomeDashboardFragment : Fragment() {

    private var _binding: FragmentHomeDashboardBinding? = null
    private val binding get() = _binding!!

    interface DashboardNavigationListener {
        fun navigateToScreen(screenId: Int)
    }

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(this)[DashboardViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDashboard()
    }

    private fun setupDashboard() {
        // Dashboard content is rendered via layout XML
        // ViewModel provides data binding in production
    }

    fun onLanguageChanged(languageCode: String) {
        // Refresh dashboard content for the selected language
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): HomeDashboardFragment = HomeDashboardFragment()
    }
}
