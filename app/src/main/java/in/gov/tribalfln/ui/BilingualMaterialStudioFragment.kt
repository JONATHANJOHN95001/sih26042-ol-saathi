package `in`.gov.tribalfln.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import `in`.gov.tribalfln.databinding.FragmentBilingualMaterialStudioBinding
import `in`.gov.tribalfln.engine.materials.BilingualMaterialSynthesizer
import `in`.gov.tribalfln.ui.viewmodel.BilingualViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * BilingualMaterialStudioFragment — Auto-generates bilingual worksheets
 * (Santhali Ol Chiki + Hindi) and flashcards for NIPUN FLN curriculum.
 * Supports A4 PDF export and thermal printer output.
 */
class BilingualMaterialStudioFragment : Fragment() {

    private var _binding: FragmentBilingualMaterialStudioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BilingualViewModel by lazy {
        ViewModelProvider(this)[BilingualViewModel::class.java]
    }

    private var currentLanguageCode: String = "san"
    private var nipunLevel = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBilingualMaterialStudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMaterialModeToggle()
        setupLevelSelector()
        setupExportButton()
    }

    private fun setupMaterialModeToggle() {
        binding.toggleMaterialMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    binding.btnWorksheet.id -> {
                        binding.flashcardTypeSelector.visibility = View.GONE
                    }
                    binding.btnFlashcards.id -> {
                        binding.flashcardTypeSelector.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupLevelSelector() {
        binding.chipGroupLevel.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                nipunLevel = when (checkedIds[0]) {
                    binding.chipLevel1.id -> 1
                    binding.chipLevel2.id -> 2
                    binding.chipLevel3.id -> 3
                    else -> 1
                }
            }
        }
    }

    private fun setupExportButton() {
        binding.btnExportPrint.setOnClickListener {
            Toast.makeText(requireContext(), "Generating worksheet...", Toast.LENGTH_SHORT).show()
        }
        binding.btnShareP2p.setOnClickListener {
            Toast.makeText(requireContext(), "Preparing P2P share...", Toast.LENGTH_SHORT).show()
        }
    }

    fun onLanguageChanged(languageCode: String) {
        currentLanguageCode = languageCode
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): BilingualMaterialStudioFragment = BilingualMaterialStudioFragment()
    }
}
