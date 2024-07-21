package com.looper.vic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.looper.android.support.util.AppUtils
import com.looper.android.support.util.IntentUtils
import com.looper.vic.R

class AboutFragment : Fragment() {
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize variables.
        navController = view.findNavController()

        // Set up version number.
        val version = "v${AppUtils.getVersionName(requireContext())}"
        view.findViewById<MaterialTextView>(R.id.text_version_number).text = version

        // Set up click listener.
        view.findViewById<MaterialCardView>(R.id.card_more_apps_on_play_store).setOnClickListener {
            IntentUtils.openURL(
                requireContext(),
                "https://play.google.com/store/apps/dev?id=6115418187591502860"
            )
        }

        view.findViewById<MaterialCardView>(R.id.card_release_channel).setOnClickListener {
            IntentUtils.openURL(requireContext(), "https://telegram.me/loopprojects")
        }

        view.findViewById<MaterialCardView>(R.id.card_credits).setOnClickListener {
            IntentUtils.openURL(
                requireContext(),
                "https://github.com/iamlooper/Lumi-AI/tree/main#credits-"
            )
        }

        view.findViewById<MaterialCardView>(R.id.card_license).setOnClickListener {
            IntentUtils.openURL(
                requireContext(),
                "https://github.com/iamlooper/Lumi-AI/tree/main#licenses-"
            )
        }
    }
}