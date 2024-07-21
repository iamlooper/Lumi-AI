package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.looper.vic.R
import com.looper.vic.model.ChatThread
import com.looper.vic.util.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@SuppressLint("NotifyDataSetChanged")
class ChatFilesAdapter(private val context: Context) :
    RecyclerView.Adapter<ViewHolder>() {
    private val fileUriList = ArrayList<Uri>()
    private val allowedExts =
        FileUtils.TEXT_EXTS + FileUtils.PHOTO_EXTS + FileUtils.DOCUMENT_EXTS + FileUtils.AUDIO_EXTS + FileUtils.VIDEO_EXTS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_file, parent, false)
        return ViewHolder(layout)
    }

    override fun getItemCount(): Int = fileUriList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = fileUriList[position]

        (holder.itemView as Chip).also {
            it.text = FileUtils.getFileName(holder.itemView.context.contentResolver, item)
            it.setOnCloseIconClickListener { removeUri(item) }
        }
    }

    fun addFiles(uris: List<Uri>) {
        val validUris = uris.filter {
            FileUtils.validateFile(context, it, allowedExts)
        }.filterNot { uri ->
            fileUriList.any { existingUri ->
                FileUtils.getFileName(
                    context.contentResolver,
                    uri
                ) == FileUtils.getFileName(context.contentResolver, existingUri)
            }
        }

        if (validUris.isNotEmpty()) {
            val startIdx = fileUriList.size
            fileUriList.addAll(validUris)
            notifyItemRangeInserted(startIdx, validUris.size)
        }
    }

    private fun removeUri(uri: Uri) {
        val idx = fileUriList.indexOf(uri)
        if (idx >= 0) {
            fileUriList.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun clearUriList() {
        fileUriList.clear()
        notifyDataSetChanged()
    }

    fun saveFiles(): List<File> {
        val folder = getCacheDirectory()
        val outputFileList = ArrayList<File>()

        for (item in fileUriList.toList()) { // Avoid concurrent modification exception by making a copy.
            val name = FileUtils.getFileName(context.contentResolver, item)
            val cacheFile = File(folder, name)

            if (!folder.exists()) {
                folder.mkdirs()
            }

            saveUriContentToFile(item, cacheFile)
            outputFileList.add(cacheFile)
            removeUri(item)
        }

        return outputFileList
    }

    fun getFiles(thread: ChatThread): List<File> {
        val folder = getCacheDirectory()
        val outputFileList = ArrayList<File>()

        for (fileName in thread.localFiles) {
            val cacheFile = File(folder, fileName)
            if (cacheFile.exists()) {
                outputFileList.add(cacheFile)
            }
        }

        return outputFileList
    }

    private fun saveUriContentToFile(uri: Uri, file: File) {
        val stream: InputStream? = context.contentResolver.openInputStream(uri)
        val output = FileOutputStream(file)

        stream.use { input ->
            output.use {
                input?.copyTo(output)
            }
        }
    }

    private fun getCacheDirectory() = File(context.cacheDir, "chat_files")
}