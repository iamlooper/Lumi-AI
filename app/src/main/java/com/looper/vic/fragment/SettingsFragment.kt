package com.looper.vic.fragment

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.looper.android.support.preference.PreferenceFragment
import com.looper.vic.R

class SettingsFragment : PreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings, null)
    }

    override fun customizePreferenceDialog(
        preference: Preference,
        dialogBuilder: MaterialAlertDialogBuilder,
        dialogView: View?
    ) {
        when (preference.key) {
            "pref_custom_instructions" -> {
                dialogView?.let {
                    val input: TextInputEditText =
                        it.findViewById(com.looper.android.support.R.id.input)

                    // Set the hint.
                    input.hint =
                        requireContext().getString(R.string.pref_custom_instructions_input_hint)

                    // Set max length to 6000 characters.
                    input.filters = arrayOf(InputFilter.LengthFilter(6000))
                }
            }
        }
    }
}