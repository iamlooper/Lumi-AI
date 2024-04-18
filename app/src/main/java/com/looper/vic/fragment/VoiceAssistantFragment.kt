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
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.eyalbira.loadingdots.LoadingDots
import com.looper.vic.R
import java.util.Locale


/**
 * A simple [Fragment] subclass.
 * Use the [VoiceAssistantFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class VoiceAssistantFragment : Fragment() {
    private lateinit var navController: NavController
    private lateinit var startSpeakButton: Button
    private lateinit var endSpeakButton: Button
    private lateinit var sendSpeakButton: Button
    private lateinit var speakOutputTextView: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var waveformSeekBar: LoadingDots
    private val speechRecognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private var lastRecognition: ArrayList<String>? = null

    init {
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }

    private var isRecording = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_voice_assistant, container, false)
    }

    override fun onDestroyView() {
        speechRecognizer.destroy()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        // Initialize variables and views.
        navController = view.findNavController()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
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
        })

        // Find views by their IDs.
        startSpeakButton = view.findViewById(R.id.button_start_speak)
        endSpeakButton = view.findViewById(R.id.button_end_speak)
        sendSpeakButton = view.findViewById(R.id.button_send_speak_result)
        speakOutputTextView = view.findViewById(R.id.text_speak_result)
        waveformSeekBar = view.findViewById(R.id.waveform_seek_bar)

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
            "data",
            lastRecognition?.first()
        )
        navController.popBackStack()
    }

    companion object {
        @JvmStatic
        fun newInstance() = VoiceAssistantFragment()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.fragment_voice_assistant_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle menu item selection.
        return when (item.itemId) {
            R.id.chat_close -> {
                navController.popBackStack()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}