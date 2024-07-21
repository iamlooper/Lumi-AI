package com.looper.vic.fragment

import android.Manifest
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.looper.android.support.util.DialogUtils
import com.looper.android.support.util.PermissionUtils
import com.looper.android.support.util.SharedPreferencesUtils
import com.looper.android.support.util.SpeechUtils
import com.looper.android.support.util.SystemServiceUtils
import com.looper.vic.BuildConfig
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.adapter.ChatAdapter
import com.looper.vic.adapter.ChatFilesAdapter
import com.looper.vic.model.Chat
import com.looper.vic.model.ChatRequest
import com.looper.vic.model.ChatResponse
import com.looper.vic.model.ChatThread
import com.looper.vic.model.ChatTitleRequest
import com.looper.vic.util.AESUtils
import com.looper.vic.util.ChatUtils
import com.looper.vic.util.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ChatFragment : Fragment(), NavController.OnDestinationChangedListener, MenuProvider {

    // Declare and initialize variables.
    private val fileSelector = registerForActivityResult(OpenMultipleDocuments()) {
        chatFilesAdapter.addFiles(it)
    }
    private val okHttpClientBuilder: OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
    private val encryptKey: ByteArray = AESUtils.keyFromString(BuildConfig.encryptBase64Key)
    private val userCancelledResponse: String =
        MyApp.getAppContext()!!.getString(R.string.user_cancelled_response)
    private val networkErrorResponse: String =
        MyApp.getAppContext()!!.getString(R.string.network_error_response)
    private val unexpectedErrorResponse: String =
        MyApp.getAppContext()!!.getString(R.string.unexpected_error_response)
    private var chatId: Int = -1
    private var toolType: String? = null
    private var fragmentMenu: Menu? = null
    private var apiRequestCall1: EventSource? = null
    private var apiRequestCall2: Call? = null
    private lateinit var navController: NavController
    private lateinit var sharedPreferencesUtils: SharedPreferencesUtils
    private lateinit var speechUtils: SpeechUtils
    private lateinit var scrollViewNewChat: NestedScrollView
    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewLayoutManager: RecyclerView.LayoutManager
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var queryInputBox: TextInputEditText
    private lateinit var querySendButton: MaterialButton
    private lateinit var queryStopButton: MaterialButton
    private lateinit var querySpeakButton: MaterialButton
    private lateinit var queryAddFilesButton: MaterialButton
    private lateinit var fabArrowDown: FloatingActionButton
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatFilesAdapter: ChatFilesAdapter
    private lateinit var chatThread: ChatThread
    private lateinit var requestAudioPermission: () -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onDestroyView() {
        navController.removeOnDestinationChangedListener(this)

        super.onDestroyView()
    }

    override fun onDestroy() {
        cancelRequestCalls()
        speechUtils.destroy()

        if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
            val thread = chatAdapter.getThreadsOfChat().last()
            cancelQuery(thread, userCancelledResponse)
        }

        super.onDestroy()
    }

    override fun onPause() {
        speechUtils.stopTextToSpeech()

        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize variables.
        requestAudioPermission = PermissionUtils.requestPermission(
            activity = (requireActivity() as AppCompatActivity),
            context = requireContext(),
            permission = Manifest.permission.RECORD_AUDIO,
            onPermissionGranted = { getVoiceInput() }
        )
        speechUtils = SpeechUtils(requireContext())

        // Determine chat id.
        val cid = arguments?.getInt("chatId", -1) ?: -1
        chatId = if (cid != -1) {
            cid
        } else {
            ChatUtils.generateNewChatId().also {
                // Put the generated chat id in the arguments to prevent chat resets from device changes.
                if (arguments == null) {
                    arguments = Bundle()
                }
                arguments?.putInt("chatId", it)
            }
        }

        // Determine tool type.
        toolType = arguments?.getString("tool")
        if (toolType == null) {
            toolType = MyApp.chatDao().getToolType(chatId)
        } else {
            val chat = MyApp.chatDao().getChat(chatId)
            if (chat != null) {
                chat.tool = toolType
                MyApp.chatDao().updateChat(chat)
            } else {
                val newChat = Chat(
                    chatId = chatId,
                    chatTitle = "",
                    tool = toolType,
                )
                MyApp.chatDao().insertChat(newChat)
            }
        }

        // Initialize ChatAdapter.
        chatAdapter = ChatAdapter(
            activity as AppCompatActivity,
            chatId,
            speechUtils
        )
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val position = item.intent!!.getIntExtra("position", -1)
        val viewId = item.intent!!.getIntExtra("viewId", -1)
        chatThread = chatAdapter.getCurrentThread(position)

        return when (item.itemId) {
            R.id.copy_clipboard -> {
                when (viewId) {
                    R.id.layout_user -> {
                        SystemServiceUtils.copyToClipboard(
                            requireContext(),
                            chatThread.userContent
                        )
                    }

                    R.id.layout_ai -> {
                        SystemServiceUtils.copyToClipboard(
                            requireContext(),
                            chatThread.aiContent
                        )
                    }
                }
                true
            }

            R.id.select_text -> {
                when (viewId) {
                    R.id.layout_user -> {
                        navController.navigate(
                            R.id.fragment_select_text,
                            Bundle().apply { putString("text", chatThread.userContent) })
                    }

                    R.id.layout_ai -> {
                        navController.navigate(
                            R.id.fragment_select_text,
                            Bundle().apply { putString("text", chatThread.aiContent) })
                    }
                }
                true
            }

            R.id.regenerate_response -> {
                cancelRequestCalls()

                // Make the thread pending and clear previous AI response.
                chatThread.isPending = true
                chatThread.isCancelled = false
                chatThread.aiContent = ""
                MyApp.chatDao().updateThread(chatThread)
                chatAdapter.notifyItemChanged(chatAdapter.getThreadIndexById(chatThread.id))

                // Show recycler view and scroll to the thread.
                scrollViewNewChat.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerViewLayoutManager.scrollToPosition(chatAdapter.getThreadIndexById(chatThread.id))

                // Show stop button.
                querySpeakButton.visibility = View.GONE
                querySendButton.visibility = View.INVISIBLE
                queryStopButton.visibility = View.VISIBLE

                // Process AI response.
                processAIResponse(chatThread)

                true
            }

            R.id.speak -> {
                speechUtils.speak(chatThread.aiContent)
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup MenuProvider.
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Set the chat title.
        ChatUtils.setChatTitle(activity as AppCompatActivity?, chatId, toolType, null)

        // Initialize variables.
        navController = view.findNavController()
        navController.addOnDestinationChangedListener(this)
        sharedPreferencesUtils = SharedPreferencesUtils(requireContext())
        chatFilesAdapter = ChatFilesAdapter(requireContext())
        scrollViewNewChat = view.findViewById(R.id.scroll_view_new_chat)
        recyclerView = view.findViewById(R.id.recycler_view_chat)
        recyclerViewFiles = view.findViewById(R.id.recycler_view_chat_files)
        queryInputBox = view.findViewById(R.id.edit_text_input_box)
        querySendButton = view.findViewById(R.id.button_send_query)
        queryStopButton = view.findViewById(R.id.button_stop_query)
        querySpeakButton = view.findViewById(R.id.button_speak)
        queryAddFilesButton = view.findViewById(R.id.button_add_files)
        fabArrowDown = view.findViewById(R.id.fab_arrow_down)

        // Set up RecyclerView.
        recyclerView.adapter = chatAdapter
        recyclerViewLayoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = recyclerViewLayoutManager
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )
        recyclerViewFiles.adapter = chatFilesAdapter

        // Add scroll listener to show/hide arrow down FAB.
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!recyclerView.canScrollVertically(1)) { // 1 for down direction.
                    fabArrowDown.hide()
                } else {
                    fabArrowDown.show()
                }
            }
        })

        // Show stop button if there is a pending query.
        if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
            if (chatAdapter.getThreadsOfChat().last().isPending) {
                querySpeakButton.visibility = View.GONE
                querySendButton.visibility = View.INVISIBLE
                queryStopButton.visibility = View.VISIBLE
            }
        }

        // Open the keyboard when the fragment starts.
        SystemServiceUtils.showKeyboard(queryInputBox, requireActivity())

        // Set a text listener to hide/show speak button.
        queryInputBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                querySpeakButton.visibility = if (s?.trim()?.isNotEmpty() == true) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        querySendButton.setOnClickListener { processUserQuery(false) }

        queryStopButton.setOnClickListener { cancelQuery(chatThread, userCancelledResponse) }

        querySpeakButton.setOnClickListener { requestAudioPermission() }

        queryAddFilesButton.setOnClickListener { fileSelector.launch(arrayOf("*/*")) }

        fabArrowDown.setOnClickListener {
            val lastPosition = chatAdapter.itemCount - 1
            if (lastPosition >= 0) {
                recyclerView.smoothScrollToPosition(lastPosition)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (chatAdapter.hasConversation()) {
            // Show menu items.
            fragmentMenu?.findItem(R.id.new_chat)?.isVisible = true
            fragmentMenu?.findItem(R.id.edit_chat_title)?.isVisible = true
            fragmentMenu?.findItem(R.id.delete_chat)?.isVisible = true

            // Show recycler view.
            scrollViewNewChat.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Set the chat title as it resets.
            ChatUtils.setChatTitle(activity as AppCompatActivity?, chatId, toolType, null)
        }

        if (toolType != null) {
            // Show menu items if there is a tool is selected.
            fragmentMenu?.findItem(R.id.new_chat)?.isVisible = true
            fragmentMenu?.findItem(R.id.edit_chat_title)?.isVisible = true
            fragmentMenu?.findItem(R.id.delete_chat)?.isVisible = true
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val handle = controller.currentBackStackEntry?.savedStateHandle
        val data = handle?.get<String?>("voice_text") ?: return
        handle.remove<String?>("voice_text")
        queryInputBox.setText(data)
        processUserQuery(true)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_chat_menu, menu)

        // Show menu items if there is a conversation or a tool is selected.
        if (chatAdapter.hasConversation() || toolType != null) {
            menu.findItem(R.id.new_chat)?.isVisible = true
            menu.findItem(R.id.edit_chat_title)?.isVisible = true
            menu.findItem(R.id.delete_chat)?.isVisible = true
        }

        // Store a reference for future use.
        fragmentMenu = menu
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.delete_chat -> {
                deleteConversation()
                true
            }

            R.id.edit_chat_title -> {
                displayEditChatTitleDialog()
                true
            }

            R.id.new_chat -> {
                newConversation()
                true
            }

            else -> false
        }
    }

    private fun processUserQuery(voice: Boolean) {
        // Get user query.
        val userQuery = queryInputBox.text.toString().trim()

        if (userQuery.isNotBlank()) {
            // Clear the input box.
            queryInputBox.text?.clear()

            // Hide keyboard.
            SystemServiceUtils.hideKeyboard(requireActivity())

            // Show recycler view.
            scrollViewNewChat.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Show stop button.
            querySpeakButton.visibility = View.GONE
            querySendButton.visibility = View.INVISIBLE
            queryStopButton.visibility = View.VISIBLE

            // Create a new ChatThread, add it to the ChatAdapter, and notify change.
            val thread = ChatThread(
                chatId = chatId,
                userContent = userQuery,
                aiContent = "",
                isPending = true,
                isVoiceInput = voice,
            )
            chatAdapter.addThread(thread)
            chatThread = chatAdapter.getThreadsOfChat().last()

            // Scroll to the new thread.
            recyclerViewLayoutManager.scrollToPosition(chatAdapter.itemCount - 1)

            // Show menu items if there is a conversation.
            if (chatAdapter.hasConversation()) {
                fragmentMenu?.findItem(R.id.new_chat)?.isVisible = true
                fragmentMenu?.findItem(R.id.edit_chat_title)?.isVisible = true
                fragmentMenu?.findItem(R.id.delete_chat)?.isVisible = true
            }

            // Process AI response.
            processAIResponse(chatThread)
        }
    }

    private fun processAIResponse(thread: ChatThread) {
        // Initialize variables.
        val userQuery = thread.userContent
        val timestamp = System.currentTimeMillis() / 1000
        val signatureHash = HashUtils.generateSignHash(
            userQuery.take(10),
            timestamp,
            BuildConfig.apiKey
        )

        // Prepare local files.
        val fileNames: MutableList<String> = mutableListOf()
        val filesList: MutableList<Map<String, String>> = mutableListOf()
        val files: List<File>
        if (chatFilesAdapter.itemCount > 0) {
            files = chatFilesAdapter.saveFiles()
            for (file in files) {
                fileNames.add(file.name)

                val fileBytes = file.readBytes()
                val base64EncodedFileBytes = Base64.encodeToString(fileBytes, Base64.DEFAULT)
                filesList.add(
                    mapOf(
                        "filename" to file.name,
                        "file_bytes" to base64EncodedFileBytes
                    )
                )
            }
            chatFilesAdapter.clearUriList()
        } else {
            files = chatFilesAdapter.getFiles(thread)
            for (file in files) {
                fileNames.add(file.name)

                val fileBytes = file.readBytes()
                val base64EncodedFileBytes = Base64.encodeToString(fileBytes, Base64.DEFAULT)
                filesList.add(
                    mapOf(
                        "filename" to file.name,
                        "file_bytes" to base64EncodedFileBytes
                    )
                )
            }
        }

        // Update the thread with names of local files.
        if (fileNames.isNotEmpty()) {
            chatAdapter.updateThreadWithLocalFiles(thread, fileNames)
        }

        // Prepare request data.
        val jsonRequestData = ChatRequest(
            time = timestamp.toInt(),
            sign = signatureHash,
            query = userQuery,
            history = chatAdapter.getChatHistory(thread),
            persona = sharedPreferencesUtils.get("ai_persona", "assistant"),
            style = sharedPreferencesUtils.get("ai_style", "balanced"),
            web_search = sharedPreferencesUtils.get(
                "ai_web_search",
                false
            ),
            tool = toolType,
            custom_instructions = sharedPreferencesUtils.get(
                "pref_custom_instructions",
                ""
            ),
            stream = true,
            files = filesList
        )

        // Prepare json and encrypt it.
        val json = Gson().toJson(jsonRequestData)
        val encryptedJson = AESUtils.encrypt(json, encryptKey)

        // Get response from API.
        getAIResponse(encryptedJson, thread)
    }

    private fun getAIResponse(
        jsonString: String,
        thread: ChatThread
    ) {
        // Build okhttp client.
        val client = okHttpClientBuilder.build()

        // Build request body.
        val requestBody = jsonString.toRequestBody("text/plain".toMediaTypeOrNull())

        // Build request.
        val request = Request.Builder()
            .url(BuildConfig.apiBaseUrl + "v2/chat")
            .post(requestBody)
            .build()

        // Create EventSource listener.
        val eventSourceListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // No operation.
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                // Remove the b' and the trailing ' to get the actual byte representation in string form.
                val trimmedBase64String = data.removePrefix("b'").removeSuffix("'")

                // Decode the base64 string to bytes.
                val decodedBytes = Base64.decode(trimmedBase64String, Base64.DEFAULT)

                // Convert bytes to string using UTF-8 encoding.
                val decodedString = String(decodedBytes, Charsets.UTF_8)

                // Process the JSON data.
                val fixedLine = decodedString.replace("}{", "}\n{").split("\n")
                fixedLine.forEach { part ->
                    try {
                        val jsonData = Gson().fromJson(part, ChatResponse::class.java)

                        activity?.runOnUiThread {
                            // Update the thread with files.
                            jsonData.files?.let { files ->
                                chatAdapter.updateThreadWithAIFiles(thread, files)
                            }

                            // Update the thread with response part.
                            jsonData.chunk?.let { chunk ->
                                chatAdapter.updateThreadWithResponsePart(thread, chunk)
                            }
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            cancelQuery(thread, unexpectedErrorResponse)
                        }
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // Process chat title if it is a new chat.
                if (chatAdapter.itemCount == 1) {
                    processChatTitle(thread)
                }

                // Speak if it is a voice input.
                if (thread.isVoiceInput && !thread.isCancelled) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        speechUtils.speak(thread.aiContent)
                    }
                }

                // Hide stop button and show send button.
                activity?.runOnUiThread {
                    querySpeakButton.visibility = View.VISIBLE
                    querySendButton.visibility = View.VISIBLE
                    queryStopButton.visibility = View.INVISIBLE
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                activity?.runOnUiThread {
                    when (t) {
                        is SocketTimeoutException -> {
                            cancelQuery(thread, networkErrorResponse)
                        }

                        else -> {
                            cancelQuery(thread, unexpectedErrorResponse)
                        }
                    }
                }
            }
        }

        // Create EventSource.
        apiRequestCall1 =
            EventSources.createFactory(client).newEventSource(request, eventSourceListener)
    }

    private fun processChatTitle(thread: ChatThread) {
        // Initialize variables.
        val userQuery = thread.userContent
        val timestamp = System.currentTimeMillis() / 1000
        val signatureHash = HashUtils.generateSignHash(
            userQuery.take(10),
            timestamp,
            BuildConfig.apiKey
        )

        // Prepare request data.
        val jsonRequestData = ChatTitleRequest(
            query = userQuery,
            time = timestamp.toInt(),
            sign = signatureHash
        )
        val json = Gson().toJson(jsonRequestData)
        val encryptedJson = AESUtils.encrypt(json, encryptKey)

        // Build okhttp client.
        val client = okHttpClientBuilder.build()

        // Build request body.
        val requestBody = encryptedJson.toRequestBody("text/plain".toMediaTypeOrNull())

        // Build request.
        val request = Request.Builder()
            .url(BuildConfig.apiBaseUrl + "v1/title")
            .post(requestBody)
            .build()

        // Send API request.
        apiRequestCall2 = client.newCall(request)

        // Enqueue the API call.
        apiRequestCall2?.enqueue(object : Callback {
            override fun onResponse(
                call: Call,
                response: Response
            ) {
                val chatTitle: String = if (response.isSuccessful) {
                    response.body?.string() ?: userQuery
                } else {
                    userQuery
                }

                activity?.runOnUiThread {
                    // Set the chat title.
                    ChatUtils.setChatTitle(
                        activity as AppCompatActivity?,
                        chatId,
                        toolType,
                        chatTitle
                    )
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                // No operation.
            }
        })
    }

    private fun cancelQuery(thread: ChatThread, cancelResponse: String) {
        // Set pending to true if response is streaming.
        if (apiRequestCall1 != null) {
            thread.isPending = true
        }

        // Don't cancel when there's no pending query.
        if (!thread.isPending) {
            return
        }

        // Hide stop button and show send button.
        (querySpeakButton as MaterialButton?)?.visibility = View.VISIBLE
        (querySendButton as MaterialButton?)?.visibility = View.VISIBLE
        (queryStopButton as MaterialButton?)?.visibility = View.INVISIBLE

        // Cancel all calls.
        cancelRequestCalls()

        // Update conversation status.
        (chatAdapter as ChatAdapter?)?.cancelPendingQuery(thread, cancelResponse)

        // Set the chat title.
        ChatUtils.setChatTitle(activity as AppCompatActivity?, chatId, toolType, null)
    }

    private fun newConversation() {
        // Cancel pending query.
        if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
            val thread = chatAdapter.getThreadsOfChat().last()
            chatAdapter.cancelPendingQuery(thread, userCancelledResponse)
        }

        // Change visibility of views accordingly.
        scrollViewNewChat.visibility = View.VISIBLE
        fabArrowDown.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // Show a toast message.
        Toast.makeText(
            context,
            getString(R.string.chat_toast_new_conversation),
            Toast.LENGTH_SHORT
        ).show()

        // Navigate back to chat fragment.
        navController.navigate(
            R.id.fragment_chat, null, NavOptions.Builder()
                .setPopUpTo(R.id.fragment_chat, true)
                .build()
        )
    }

    private fun deleteConversation() {
        // Don't delete conversation if there is no conversation already.
        if (chatAdapter.hasConversation()) {
            DialogUtils.displayActionConfirmDialog(
                context = requireContext(),
                title = getString(R.string.delete_chat),
                onPositiveAction = {
                    // Cancel pending query.
                    if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
                        val thread = chatAdapter.getThreadsOfChat().last()
                        chatAdapter.cancelPendingQuery(thread, userCancelledResponse)
                    }

                    // Delete conversation.
                    chatAdapter.deleteConversation()

                    // Change visibility of views accordingly.
                    scrollViewNewChat.visibility = View.VISIBLE
                    fabArrowDown.visibility = View.GONE
                    recyclerView.visibility = View.GONE

                    // Show a toast message.
                    Toast.makeText(
                        context,
                        getString(R.string.chat_toast_delete_conversation),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate back to chat fragment.
                    navController.navigate(
                        R.id.fragment_chat, null, NavOptions.Builder()
                            .setPopUpTo(R.id.fragment_chat, true)
                            .build()
                    )
                }
            )
        } else {
            // Show a toast message.
            Toast.makeText(
                context,
                getString(R.string.chat_toast_delete_conversation_not_found),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun displayEditChatTitleDialog() {
        DialogUtils.displayEditTextDialog(
            context = requireContext(),
            title = getString(R.string.edit_chat_title),
            initialInput = ChatUtils.getChatTitle(chatId, false),
            onPositiveAction = { input ->
                // Set the new chat title.
                val newChatTitle = input.text.toString().trim()
                if (newChatTitle.isNotEmpty()) {
                    ChatUtils.setChatTitle(
                        activity as AppCompatActivity?,
                        chatId,
                        toolType,
                        newChatTitle
                    )
                }
            }
        )
    }

    private fun cancelRequestCalls() {
        apiRequestCall1?.cancel()
        apiRequestCall2?.cancel()
        apiRequestCall1 = null
        apiRequestCall2 = null
    }

    private fun getVoiceInput() {
        // Create a VoiceInputFragment instance and show it.
        val voiceInputFragment = VoiceInputFragment()
        voiceInputFragment.show(parentFragmentManager, VoiceInputFragment.TAG)

        // Observe the lifecycle of the VoiceInputFragment.
        voiceInputFragment.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                onDestinationChanged(navController, navController.currentDestination!!, null)
            }
        })
    }
}
