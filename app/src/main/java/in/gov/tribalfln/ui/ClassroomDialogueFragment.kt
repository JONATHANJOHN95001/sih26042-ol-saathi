package `in`.gov.tribalfln.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import `in`.gov.tribalfln.R
import `in`.gov.tribalfln.databinding.FragmentClassroomDialogueBinding
import `in`.gov.tribalfln.engine.RealTimeClassroomDialogueEngine
import `in`.gov.tribalfln.engine.TribalPhonemeMatcher
import `in`.gov.tribalfln.ui.viewmodel.ClassroomViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ClassroomDialogueFragment — Real-time bilingual classroom dialogue
 * with push-to-talk (PTT) Hindi speech input, offline tribal language
 * translation, and visual aid flashcards.
 */
class ClassroomDialogueFragment : Fragment() {

    private var _binding: FragmentClassroomDialogueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClassroomViewModel by lazy {
        ViewModelProvider(this)[ClassroomViewModel::class.java]
    }

    private var currentLanguageCode: String = "san"
    private val phonemeMatcher = TribalPhonemeMatcher()
    private var isTwoWayMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClassroomDialogueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPTTButton()
        setupTwoWayToggle()
    }

    private fun setupPTTButton() {
        binding.btnPttSpeak.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    binding.tvSpeakInstruction.text = "Listening..."
                    binding.waveformContainer.visibility = View.VISIBLE
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    binding.tvSpeakInstruction.text = "Hold to speak Hindi"
                    binding.waveformContainer.visibility = View.GONE
                    onPttRelease()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTwoWayToggle() {
        binding.switchTwoWay.setOnCheckedChangeListener { _, isChecked ->
            isTwoWayMode = isChecked
            binding.twoWayControls.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun onPttRelease() {
        // In production, this triggers speech recognition + translation
        // For now, demonstrate with sample text
        val sampleHindi = "नमस्ते"
        val result = phonemeMatcher.translateWithProvenance(sampleHindi, currentLanguageCode)
        binding.tvSourceText.text = sampleHindi
        binding.tvTargetText.text = result.text
        showProvenance(result.provenance)
    }

    /**
     * The output panel always says where its text came from. Script conversion
     * and translation look identical on screen, so the caption is the only
     * thing stopping a teacher from trusting the wrong one.
     */
    private fun showProvenance(provenance: TribalPhonemeMatcher.Provenance) {
        binding.tvOutputProvenance.setText(
            when (provenance) {
                TribalPhonemeMatcher.Provenance.VERIFIED -> R.string.provenance_verified
                TribalPhonemeMatcher.Provenance.TRANSLITERATED -> R.string.provenance_translit
                TribalPhonemeMatcher.Provenance.UNAVAILABLE -> R.string.provenance_unavailable
            }
        )
    }

    fun onLanguageChanged(languageCode: String) {
        currentLanguageCode = languageCode
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): ClassroomDialogueFragment = ClassroomDialogueFragment()
    }
}
