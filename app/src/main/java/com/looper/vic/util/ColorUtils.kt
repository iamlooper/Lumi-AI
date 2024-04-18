package com.looper.vic.util

import android.content.Context
import android.util.TypedValue

object ColorUtils {
    fun getColorControlNormal(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(
            androidx.appcompat.R.attr.colorControlNormal,
            typedValue,
            true
        )
        return context.getColor(typedValue.resourceId)
    }
}