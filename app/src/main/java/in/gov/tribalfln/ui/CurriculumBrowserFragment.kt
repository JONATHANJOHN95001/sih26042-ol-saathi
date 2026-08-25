package `in`.gov.tribalfln.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import `in`.gov.tribalfln.databinding.FragmentCurriculumBrowserBinding
import `in`.gov.tribalfln.ui.viewmodel.DashboardViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * CurriculumBrowserFragment — Browse and search the NIPUN Bharat curriculum
 * database with full-text search across Hindi and tribal language content.
 * Displays competency-mapped lessons organized by grade level and subject.
 */
class CurriculumBrowserFragment : Fragment() {

    private var _binding: FragmentCurriculumBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by lazy {
        ViewModelProvider(this)[DashboardViewModel::class.java]
    }

    private var currentLanguageCode: String = "san"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurriculumBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCurriculumList()
    }

    private fun setupCurriculumList() {
        // Curriculum list populated from Room database via ViewModel
    }

    fun onLanguageChanged(languageCode: String) {
        currentLanguageCode = languageCode
        // Refresh curriculum list for the selected language
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): CurriculumBrowserFragment = CurriculumBrowserFragment()
    }
}
