package `in`.gov.tribalfln.ime

import android.inputmethodservice.InputMethodService
import android.util.Log

/**
 * TribalKeyboardService — Ol Chiki tribal script input method editor (IME).
 * Provides a custom keyboard layout for typing in Santhali (Ol Chiki script),
 * Ho (Warang Citi), and Mundari on standard Android devices.
 */
class TribalKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "TribalKeyboardService"
    }

    override fun onCreateInputView(): android.view.View {
        Log.d(TAG, "Ol Chiki keyboard view created")
        return super.onCreateInputView()
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "Input view started")
    }
}
