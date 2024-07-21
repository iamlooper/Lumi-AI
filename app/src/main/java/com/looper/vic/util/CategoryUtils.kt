package com.looper.vic.util

import com.looper.vic.MyApp
import com.looper.vic.R

object CategoryUtils {
    const val CATEGORY_ALL = 0
    const val CATEGORY_WRITING = 1
    const val CATEGORY_CODING = 2
    const val CATEGORY_OTHERS = 3

    fun getCategoryItems(categoryTypeIndex: Int): List<Pair<String, String>> {
        val pairs = when (categoryTypeIndex) {
            CATEGORY_ALL -> {
                val writingType = getCategoryItemsForType(CATEGORY_WRITING)
                val codingType = getCategoryItemsForType(CATEGORY_CODING)
                val othersType = getCategoryItemsForType(CATEGORY_OTHERS)

                writingType.addAll(codingType)
                writingType.addAll(othersType)

                return writingType.sortedBy { it.second }
            }

            else -> getCategoryItemsForType(categoryTypeIndex).sortedBy { it.second }
        }

        return pairs
    }

    private fun getCategoryItemsForType(type: Int): ArrayList<Pair<String, String>> {
        val pairs: List<Array<String>> = when (type) {
            CATEGORY_WRITING -> listOf(
                MyApp.getAppContext()!!.resources.getStringArray(R.array.ai_category_writing_value),
                MyApp.getAppContext()!!.resources.getStringArray(R.array.ai_category_writing),
            )

            CATEGORY_CODING -> listOf(
                MyApp.getAppContext()!!.resources.getStringArray(R.array.ai_category_coding_value),
                MyApp.getAppContext()!!.resources.getStringArray(R.array.ai_category_coding),
            )

            CATEGORY_OTHERS -> listOf(
                MyApp.getAppContext()!!.resources.getStringArray(R.array.ai_category_others_value),
                MyApp.getAppContext()!!.resources.getStringArray(R.array.ai_category_others),
            )

            else -> listOf(arrayOf())
        }

        val newMap = ArrayList<Pair<String, String>>()
        for (index in 0..<pairs.first().size) {
            val key = pairs[0][index]
            val title = pairs[1][index]
            newMap.add(Pair(key, title))
        }

        return newMap
    }
}