package com.looper.vic.fragment

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.eyalbira.loadingdots.LoadingDots
import com.looper.vic.R
import java.util.Locale

class VoiceAssistantFragment : Fragment(), RecognitionListener, MenuProvider {
    private val speechRecognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private var lastRecognition: ArrayList<String>? = null
    private var isRecording = false
    private lateinit var navController: NavController
    private lateinit var startSpeakButton: MaterialButton
    private lateinit var endSpeakButton: MaterialButton
    private lateinit var sendSpeakButton: MaterialButton
    private lateinit var speakOutputTextView: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var waveformSeekBar: LoadingDots

    init {
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }

    override fun onDestroyView() {
        speechRecognizer.destroy()

        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_voice_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup MenuProvider.
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Initialize variables.
        navController = view.findNavController()
        startSpeakButton = view.findViewById(R.id.button_start_speak)
        endSpeakButton = view.findViewById(R.id.button_end_speak)
        sendSpeakButton = view.findViewById(R.id.button_send_speak_result)
        speakOutputTextView = view.findViewById(R.id.text_speak_result)
        waveformSeekBar = view.findViewById(R.id.waveform_seek_bar)

        // Set speech recognizer.
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer.setRecognitionListener(this)

        // Set onClickListener for buttons.
        startSpeakButton.setOnClickListener {
            startRecording()
        }

        endSpeakButton.setOnClickListener {
            endRecording()
        }

        sendSpeakButton.setOnClickListener {
            sendResult()
        }
    }

    override fun onReadyForSpeech(bundle: Bundle) {}

    override fun onBeginningOfSpeech() {
        waveformSeekBar.startAnimation()
        isRecording = true
        setSpeakButtonStyle()
    }

    override fun onRmsChanged(v: Float) {}

    override fun onBufferReceived(bytes: ByteArray) {}

    override fun onEndOfSpeech() {}

    override fun onError(i: Int) {}

    override fun onResults(bundle: Bundle) {
        waveformSeekBar.stopAnimation()
        lastRecognition = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        speakOutputTextView.text = lastRecognition?.first() ?: ""
        isRecording = false
        setSpeakButtonStyle()
    }

    override fun onPartialResults(bundle: Bundle) {}

    override fun onEvent(i: Int, bundle: Bundle) {}

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_voice_assistant_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.chat_close -> {
                navController.popBackStack()
                true
            }

            else -> false
        }
    }

    private fun setSpeakButtonStyle() {
        if (isRecording) {
            startSpeakButton.visibility = View.INVISIBLE
            endSpeakButton.visibility = View.VISIBLE
        } else {
            startSpeakButton.visibility = View.VISIBLE
            endSpeakButton.visibility = View.INVISIBLE
        }

        val isRecorded = lastRecognition?.first().isNullOrEmpty()
        if (isRecorded) {
            sendSpeakButton.visibility = View.VISIBLE
        }
    }

    private fun startRecording() {
        speechRecognizer.startListening(speechRecognizerIntent)
    }

    private fun endRecording() {
        speechRecognizer.stopListening()
    }

    private fun sendResult() {
        navController.previousBackStackEntry?.savedStateHandle?.set(
            "voice_text",
            lastRecognition?.first()
        )
        navController.popBackStack()
    }
}