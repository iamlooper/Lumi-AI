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
import java.io.IOException
import java.io.InputStream
import java.text.DecimalFormat

object FileUtils {
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

        if (!isFileSizeValid(context.contentResolver, uri)) {
            Toast.makeText(
                context,
                context.getString(R.string.file_exceeds_size_limit, name, formatFileSize(FILE_SIZE.toLong())),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (isTextFile(context.contentResolver, uri)) {
            return true
        }

        if (!allowedExts.contains(ext)) {
            Toast.makeText(
                context,
                context.getString(R.string.file_not_supported, name),
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

    private fun isTextFile(contentResolver: ContentResolver, uri: Uri): Boolean {
        return try {
            // Define the range of printable ASCII characters.
            val printableChars = 32..126
            val newLineChars = listOf(10, 13) // \n and \r

            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Read the file byte by byte.
                var byte = inputStream.read()
                while (byte != -1) {
                    if (byte !in printableChars && byte !in newLineChars) {
                        return false // Found a non-printable character, it's a binary file.
                    }
                    byte = inputStream.read()
                }
            }
            true // All characters are printable, it's a text file.
        } catch (e: IOException) {
            e.printStackTrace()
            false // In case of an error, assume it's not a text file.
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