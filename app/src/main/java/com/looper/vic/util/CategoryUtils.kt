package com.looper.vic.util

import com.looper.vic.MyApp.Companion.context
import com.looper.vic.R

object CategoryUtils {
    const val CATEGORY_ALL = 0
    const val CATEGORY_WRITING = 1
    const val CATEGORY_CODING = 2
    const val CATEGORY_OTHERS = 3

    fun getCategoryItems(categoryTypeIndex: Int): List<Triple<String, String, String>> {
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

    private fun getCategoryItemsForType(type: Int): ArrayList<Triple<String, String, String>> {
        val pairs: List<Array<String>> = when (type) {
            CATEGORY_WRITING -> listOf(
                context.resources.getStringArray(R.array.ai_category_writing_value),
                context.resources.getStringArray(R.array.ai_category_writing),
                context.resources.getStringArray(R.array.ai_category_writing_descriptions),
            )

            CATEGORY_CODING -> listOf(
                context.resources.getStringArray(R.array.ai_category_coding_value),
                context.resources.getStringArray(R.array.ai_category_coding),
                context.resources.getStringArray(R.array.ai_category_coding_descriptions),
            )

            CATEGORY_OTHERS -> listOf(
                context.resources.getStringArray(R.array.ai_category_others_value),
                context.resources.getStringArray(R.array.ai_category_others),
                context.resources.getStringArray(R.array.ai_category_others_descriptions),
            )

            else -> listOf(arrayOf())
        }

        val newMap = ArrayList<Triple<String, String, String>>()
        for (index in 0..<pairs.first().size) {
            val key = pairs[0][index]
            val title = pairs[1][index]
            val desc = pairs[2][index]
            newMap.add(Triple(key, title, desc))
        }

        return newMap
    }
}