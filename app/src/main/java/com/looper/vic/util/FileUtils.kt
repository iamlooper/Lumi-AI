package com.looper.vic.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.looper.android.support.util.UIUtils
import java.io.File
import java.text.DecimalFormat

object FileUtils {
    private const val FILE_SIZE = 2 * 1024 * 1024

    fun validateFile(context: Context, uri: Uri, allowedExts: Array<String>): Boolean {
        val name = getFileName(context.contentResolver, uri)
        val ext = name.substringAfterLast('.', "")

        if (!allowedExts.contains(".$ext")) {
            UIUtils.showToast(
                context,
                "File ($name) is not supported",
                UIUtils.TOAST_LENGTH_SHORT
            )
            return false
        }

        if (!isFileSizeValid(context.contentResolver, uri)) {
            UIUtils.showToast(
                context,
                "File ($name) exceeds the size limit of 2MB",
                UIUtils.TOAST_LENGTH_SHORT
            )
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

    fun isFileValid(
        contentResolver: ContentResolver,
        uri: Uri,
        allowedExts: Array<String>
    ): Boolean {
        if (!isFileSizeValid(contentResolver, uri)) {
            return false
        }

        val name = getFileName(contentResolver, uri)
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex < 0) {
            return false
        }

        val ext = name.substring(dotIndex)
        return allowedExts.contains(ext)
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
}