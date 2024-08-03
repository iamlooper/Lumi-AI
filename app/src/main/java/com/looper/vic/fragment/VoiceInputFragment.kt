package com.looper.vic.fragment

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.looper.android.support.util.SpeechUtils
import com.looper.vic.R

class VoiceInputFragment :
    BottomSheetDialogFragment(), RecognitionListener {
    private lateinit var speechUtils: SpeechUtils
    private lateinit var navController: NavController

    override fun onDestroyView() {
        speechUtils.destroy()

        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_voice_input_bottom_sheet, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SpeechUtils.
        speechUtils = SpeechUtils(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find navigation controller.
        val navHostFragment = activity
            ?.supportFragmentManager
            ?.findFragmentById(com.looper.android.support.R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Initiate speech recognition.
        speechUtils.initSpeechToText(this)

        // Start listening.
        speechUtils.startListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {}

    override fun onResults(results: Bundle?) {
        val recognition = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        navController.currentBackStackEntry?.savedStateHandle?.set(
            "voice_text",
            recognition?.first()
        )
        dismiss()
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        const val TAG = "VoiceInputBottomSheetFragment"
    }
}