package com.looper.vic.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.looper.vic.R
import com.looper.vic.adapter.ToolsAdapter

/**
 * A simple [Fragment] subclass.
 * Use the [ToolsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ToolsFragment : Fragment() {
    private lateinit var navController: NavController
    private lateinit var toolsTypeChipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewLayoutManager: RecyclerView.LayoutManager
    private lateinit var toolsAdapter: ToolsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize variables and views.
        navController = view.findNavController()

        // Find views by their IDs.
        toolsTypeChipGroup = view.findViewById(R.id.ai_categories_chip_group_tools)
        recyclerView = view.findViewById(R.id.recycler_view_tools)

        // Set up RecyclerView.
        toolsAdapter = ToolsAdapter(requireContext())
        recyclerView.adapter = toolsAdapter
        recyclerViewLayoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = recyclerViewLayoutManager
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        // Set up click listener.
        toolsAdapter.onItemClick = {
            navController.popBackStack(R.id.fragment_tools, true)
            navController.navigate(
                R.id.fragment_chat,
                Bundle().apply { putString("tool", it) },
                NavOptions.Builder().setPopUpTo(R.id.fragment_chat, true).build()
            )
        }

        // Set up ChipGroup.
        val categories = requireContext().resources.getStringArray(R.array.ai_categories)
        for (i in categories.indices) {
            val chip = layoutInflater.inflate(
                R.layout.item_category_chip,
                toolsTypeChipGroup,
                false
            ) as Chip
            chip.text = categories[i]
            chip.setOnClickListener {
                toolsAdapter.changeCategory(i)
            }

            if (i == 0) {
                chip.isChecked = true
            }
            toolsTypeChipGroup.addView(chip)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = ToolsFragment()
    }
}