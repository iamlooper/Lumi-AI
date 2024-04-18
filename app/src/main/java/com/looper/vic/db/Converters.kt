package com.looper.vic.db

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val json = JSONArray(value)
        val out = ArrayList<String>()

        for (i in 0..<json.length()) {
            out.add(json.getString(i))
        }

        return out
    }

    @TypeConverter
    fun fromArrayList(list: List<String>): String {
        return JSONArray(list).toString()
    }
}