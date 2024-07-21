package com.looper.vic.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.looper.vic.R
import java.io.File
import java.io.InputStream
import java.text.DecimalFormat

object FileUtils {
    val TEXT_EXTS = arrayOf(
        "c", "cpp", "cs", "java", "py", "js", "ts", "rb", "go", "php", "pl", "swift", "kt",
        "rs", "hs", "jl", "lua", "sh", "bat", "r", "m", "sql", "xml", "html", "css", "scss",
        "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "md", "rst", "txt", "adoc", "tex",
        "rtf", "csv", "tsv", "log", "properties", "env", "config", "prefs", "ps1", "vbs", "wsh",
        "ahk", "bib", "srt", "sub", "vtt", "po", "pot", "h", "hpp", "jsx", "tsx", "erl", "ex",
        "exs", "dart", "groovy", "scala", "sc", "clj", "cljs", "edn", "coffee", "litcoffee",
        "elm", "fs", "fsi", "fsx", "fsscript", "ml", "mli", "nim", "cr", "v", "sv", "svh"
    )
    val PHOTO_EXTS = arrayOf("png", "jpg", "jpeg")
    val AUDIO_EXTS = arrayOf(
        "wav",
        "mp3",
        "aiff",
        "aac",
        "ogg",
        "flac"
    )
    val VIDEO_EXTS = arrayOf(
        "mp4",
        "mpeg",
        "mov",
        "avi",
        "x-flv",
        "mpg",
        "webm",
        "wmv",
        "3gpp"
    )
    val DOCUMENT_EXTS = arrayOf("pdf", "docx", "xlsx")
    private const val FILE_SIZE = 5 * 1024 * 1024

    fun validateFile(context: Context, uri: Uri, allowedExts: Array<String>): Boolean {
        val name = getFileName(context.contentResolver, uri)
        val ext = name.substringAfterLast('.', "")

        if (isFileEmpty(context.contentResolver, uri)) {
            Toast.makeText(
                context,
                context.getString(R.string.file_empty, name),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (!allowedExts.contains(ext)) {
            Toast.makeText(
                context,
                context.getString(R.string.file_not_supported, name),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (!isFileSizeValid(context.contentResolver, uri)) {
            Toast.makeText(
                context,
                context.getString(
                    R.string.file_exceeds_size_limit,
                    name,
                    formatFileSize(FILE_SIZE.toLong())
                ),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }

    private fun isFileSizeValid(contentResolver: ContentResolver, uri: Uri): Boolean =
        getFileSize(contentResolver, uri) <= FILE_SIZE

    fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { return it.length }
        return 0L
    }

    fun formatFileSize(sizeInBytes: Long): String {
        val kiloBytes = sizeInBytes / 1024.0
        val megaBytes = kiloBytes / 1024.0
        val formatter = DecimalFormat("#.##")

        return when {
            megaBytes >= 1.0 -> "${formatter.format(megaBytes)} MB"
            kiloBytes >= 1.0 -> "${formatter.format(kiloBytes)} KB"
            else -> "$sizeInBytes bytes"
        }
    }

    @SuppressLint("Range")
    fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(
                uri, null, null, null, null
            )
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result =
                        cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor!!.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    fun getFileUriFromCache(context: Context, fileName: String): Uri? {
        val cacheDirectory = File(context.cacheDir, "chat_files")
        val file = File(cacheDirectory, fileName)

        return if (file.exists()) {
            Uri.fromFile(file)
        } else {
            null
        }
    }

    private fun isFileEmpty(contentResolver: ContentResolver, fileUri: Uri): Boolean {
        var inputStream: InputStream? = null
        return try {
            inputStream = contentResolver.openInputStream(fileUri)
            val fileSize = inputStream?.available() ?: 0
            fileSize == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            inputStream?.close()
        }
    }
}