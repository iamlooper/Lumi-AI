package com.looper.vic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.textview.MaterialTextView
import com.looper.vic.R

class SelectTextFragment :
    Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_select_text, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val responseText = arguments?.getString("text") ?: ""
        val selectText: MaterialTextView = view.findViewById(R.id.select_text)
        selectText.text = responseText
    }
}