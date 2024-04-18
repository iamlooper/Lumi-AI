package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.looper.android.support.util.CoroutineUtils
import com.looper.vic.R
import com.looper.vic.model.ChatThread
import com.looper.vic.model.MediaExts
import com.looper.vic.util.ChatUtils
import com.looper.vic.util.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@SuppressLint("NotifyDataSetChanged")
class ChatFilesAdapter(private val context: Context, coroutineUtils: CoroutineUtils, chatId: Int) :
    RecyclerView.Adapter<ViewHolder>() {
    private val fileUriList = ArrayList<Uri>()
    private val fileBufferList = HashMap<String, ByteArray?>()
    private val allowedExts = MediaExts.PHOTO + MediaExts.TEXT + MediaExts.DOCUMENT

    init {
        val fileNameList = ChatUtils.getChatFiles(chatId)
        if (fileNameList.isNotEmpty()) {
            coroutineUtils.io("chatFiles_$chatId") {
                for (name in fileNameList) {
                    fileBufferList[name] = null
                }
            }
        }
    }

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

    fun saveAndConvertFiles(): Pair<List<String>, JSONArray> {
        val folder = getCacheDirectory()
        val outputFileList = ArrayList<String>()
        val outputArray = JSONArray()

        for (item in fileUriList.toList()) { // Avoid concurrent modification exception by making a copy.
            val name = FileUtils.getFileName(context.contentResolver, item)

            if (!folder.exists()) {
                folder.mkdirs()
            }

            if (!fileBufferList.containsKey(name) || fileBufferList[name] == null) {
                val cacheFile = File(folder, name)

                if (!cacheFile.exists()) {
                    readUriContent(item)
                } else {
                    readFileContent(name)
                }
            }

            outputFileList.add(name)
            outputArray.put(fileToJObject(name))
            removeUri(item)
        }

        for (item in fileBufferList.keys.toList()) { // Avoid concurrent modification exception by making a copy.
            if (fileBufferList[item] == null) {
                val cacheFile = File(folder, item)
                if (cacheFile.exists()) {
                    readFileContent(item)
                } else {
                    fileBufferList.remove(item)
                }
            }
        }

        return Pair(outputFileList, outputArray)
    }

    fun getAndConvertFiles(thread: ChatThread): Pair<List<String>, JSONArray> {
        val folder = getCacheDirectory()
        val outputFileList = ArrayList<String>()
        val outputArray = JSONArray()

        // Iterate over files names in the thread.
        for (fileName in thread.filesNames) {
            val file = File(folder, fileName)
            if (file.exists()) {
                // Convert and add file to lists.
                readFileContent(fileName)
                outputFileList.add(fileName)
                outputArray.put(fileToJObject(fileName))
            }
        }

        return Pair(outputFileList, outputArray)
    }

    private fun fileToJObject(name: String): JSONObject {
        return JSONObject().also {
            it.put("filename", name)
            it.put("file_bytes", String(Base64.encode(fileBufferList[name], Base64.DEFAULT)))
        }
    }

    private fun readUriContent(uri: Uri) {
        val name = FileUtils.getFileName(context.contentResolver, uri)
        val file = File(getCacheDirectory(), name)
        val stream = context.contentResolver.openInputStream(uri)
        val output = FileOutputStream(file)
        val exOutput = ByteArrayOutputStream()

        try {
            val buf = ByteArray(4096)
            var count: Int
            while (stream!!.read(buf, 0, buf.size).also { count = it } > 0) {
                output.write(buf, 0, count)
                exOutput.write(buf, 0, count)
            }
        } catch (e: Throwable) {
            // ignored
        } finally {
            stream?.close()
            output.close()
        }

        fileBufferList[name] = exOutput.toByteArray().also { exOutput.close() }
    }

    private fun readFileContent(file: String) {
        val stream = FileInputStream(File(getCacheDirectory(), file))
        val exOutput = ByteArrayOutputStream()

        try {
            val buf = ByteArray(4096)
            var count: Int
            while (stream.read(buf, 0, buf.size).also { count = it } > 0) {
                exOutput.write(buf, 0, count)
            }
        } catch (e: Throwable) {
            // ignored
        } finally {
            stream.close()
            exOutput.close()
        }

        fileBufferList[file] = exOutput.toByteArray().also { exOutput.close() }
    }


    private fun getCacheDirectory() = File(context.cacheDir, "chat_files")
}