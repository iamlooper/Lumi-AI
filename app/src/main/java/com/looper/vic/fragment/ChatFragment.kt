package com.looper.vic.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.looper.android.support.util.AppUtils
import com.looper.android.support.util.CoroutineUtils
import com.looper.android.support.util.OkHttpUtils
import com.looper.android.support.util.SharedPreferenceUtils
import com.looper.android.support.util.SpeechUtils
import com.looper.android.support.util.SystemServiceUtils
import com.looper.vic.BuildConfig
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.activity.MainActivity
import com.looper.vic.adapter.ChatAdapter
import com.looper.vic.adapter.ChatFilesAdapter
import com.looper.vic.model.Chat
import com.looper.vic.model.ChatThread
import com.looper.vic.util.ChatUtils
import com.looper.vic.util.SignUtils
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import com.google.android.material.button.MaterialButton as Button


open class ChatFragment : Fragment(), NavController.OnDestinationChangedListener {

    // Declare variables.
    private lateinit var navController: NavController
    private lateinit var sharedPreferenceUtils: SharedPreferenceUtils
    private lateinit var okHttpUtils: OkHttpUtils
    private lateinit var speechUtils: SpeechUtils
    private lateinit var scrollViewNewChat: NestedScrollView
    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewLayoutManager: RecyclerView.LayoutManager
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var queryInputBox: TextInputEditText
    private lateinit var querySendButton: Button
    private lateinit var queryStopButton: Button
    private lateinit var querySpeakButton: Button
    private lateinit var queryAddFilesButton: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatFilesAdapter: ChatFilesAdapter
    private lateinit var coroutineUtils: CoroutineUtils
    private lateinit var layoutFooter: LinearLayout
    private lateinit var paddingConsumer: LinearLayout
    private lateinit var userCancelledResponse: String
    private lateinit var networkErrorResponse: String
    private lateinit var unexpectedErrorResponse: String
    private lateinit var chatThread: ChatThread
    private var toolType: String? = null
    private var menu: Menu? = null
    private var chatId: Int = -1
    private val fileSelector = registerForActivityResult(OpenMultipleDocuments()) {
        chatFilesAdapter.addFiles(it)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        // Enable options menu in the fragment.
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        return view
    }

    override fun onDestroyView() {
        navController.removeOnDestinationChangedListener(this)
        super.onDestroyView()
    }

    override fun onDestroy() {
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

        speechUtils = SpeechUtils(requireContext())

        // Determine chat id.
        val cid = arguments?.getInt("chatId", -1) ?: -1
        chatId = if (cid != -1) {
            cid
        } else {
            ChatUtils.generateNewChatId().also {
                // Put the generated chat id in the arguments
                // to prevent chat resets when the theme changed.
                if (arguments == null) {
                    arguments = Bundle()
                }

                requireArguments().putInt("chatId", it)
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
        chatAdapter = ChatAdapter(chatId, speechUtils) { thread ->
            chatThread = thread

            // Make the thread pending and clear previous AI response.
            thread.isPending = true
            thread.isCancelled = false
            MyApp.chatDao().updateThread(thread)
            chatAdapter.notifyItemChanged(chatAdapter.getThreadIndexById(thread.id))

            // Show recycler view.
            scrollViewNewChat.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Show stop button.
            querySpeakButton.visibility = View.GONE
            querySendButton.visibility = View.INVISIBLE
            queryStopButton.visibility = View.VISIBLE

            // Scroll to the thread and show recyclerView.
            recyclerViewLayoutManager.scrollToPosition(chatAdapter.getThreadIndexById(thread.id))
            recyclerView.visibility = View.VISIBLE

            // Process AI response.
            processAIResponse(thread)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set chat title.
        val title = ChatUtils.getChatTitle(chatId)
        setChatTitle(title)

        // Initialize variables and views.
        navController = view.findNavController()
        navController.addOnDestinationChangedListener(this)
        sharedPreferenceUtils = SharedPreferenceUtils.getInstance(requireContext())
        okHttpUtils = OkHttpUtils()
        coroutineUtils = CoroutineUtils()
        chatFilesAdapter = ChatFilesAdapter(requireContext(), coroutineUtils, chatId)
        userCancelledResponse = requireContext().getString(R.string.user_cancelled_response)
        networkErrorResponse = requireContext().getString(R.string.network_error_response)
        unexpectedErrorResponse = requireContext().getString(R.string.unexpected_error_response)

        // Find views by their IDs.
        scrollViewNewChat = view.findViewById(R.id.scroll_view_new_chat)
        recyclerView = view.findViewById(R.id.recycler_view_chat)
        recyclerViewFiles = view.findViewById(R.id.recycler_view_chat_files)
        queryInputBox = view.findViewById(R.id.edit_text_input_box)
        querySendButton = view.findViewById(R.id.button_send_query)
        queryStopButton = view.findViewById(R.id.button_stop_query)
        querySpeakButton = view.findViewById(R.id.button_speak)
        queryAddFilesButton = view.findViewById(R.id.button_add_files)
        layoutFooter = view.findViewById(R.id.layout_footer)
        paddingConsumer = view.findViewById(R.id.layout_padding_consumer)

        // Hide files button if a tool is triggered.
        queryAddFilesButton.isEnabled = toolType == null

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

        // Show stop button if there is a pending query.
        if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
            if (chatAdapter.hasPendingQuery(chatAdapter.getThreadsOfChat().last())) {
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

        // Set onClickListener for buttons.
        querySendButton.setOnClickListener { processUserQuery(false) }

        queryStopButton.setOnClickListener { cancelQuery(chatThread, userCancelledResponse) }

        querySpeakButton.setOnClickListener { getVoiceInput() }

        queryAddFilesButton.setOnClickListener { fileSelector.launch(arrayOf("*/*")) }

        // Workaround to remove extra top padding of attribute android:fitsSystemWindows="true"
        paddingConsumer.viewTreeObserver.addOnGlobalLayoutListener {
            if (context != null) {
                layoutFooter.setPadding(
                    layoutFooter.paddingLeft,
                    requireContext().resources.getDimensionPixelSize(com.looper.android.support.R.dimen.dp_medium),
                    layoutFooter.paddingRight,
                    paddingConsumer.paddingBottom
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.fragment_chat_menu, menu)

        // Show menu items if there is a conversation or a tool is selected.
        if (chatAdapter.hasConversation() || toolType != null) {
            menu.findItem(R.id.new_chat)?.isVisible = true
            menu.findItem(R.id.delete_chat)?.isVisible = true
        }

        // Store a reference for future use.
        this.menu = menu

        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle menu item selection.
        return when (item.itemId) {
            R.id.delete_chat -> {
                deleteConversation()
                true
            }

            R.id.new_chat -> {
                newConversation()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()

        // If there is a conversation to display...
        if (chatAdapter.hasConversation()) {
            // Show menu items.
            menu?.findItem(R.id.new_chat)?.isVisible = true
            menu?.findItem(R.id.delete_chat)?.isVisible = true

            // Show recycler view.
            scrollViewNewChat.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Set the chat title as it resets.
            val title = ChatUtils.getChatTitle(chatId)
            setChatTitle(title)
        } else if (toolType != null) {
            // Show menu items if there is a tool is selected.
            menu?.findItem(R.id.new_chat)?.isVisible = true
            menu?.findItem(R.id.delete_chat)?.isVisible = true
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

            // Scroll to the new thread and show recyclerView.
            recyclerViewLayoutManager.scrollToPosition(chatAdapter.getThreadIndexById(thread.id))
            recyclerView.visibility = View.VISIBLE

            // Show menu items if there is a conversation.
            if (chatAdapter.hasConversation()) {
                menu?.findItem(R.id.new_chat)?.isVisible = true
                menu?.findItem(R.id.delete_chat)?.isVisible = true
            }

            // Process AI response.
            processAIResponse(chatThread)
        }
    }

    private fun processAIResponse(thread: ChatThread) {
        // Recreate coroutine scope.
        coroutineUtils.recreateScope()

        // Prepare chat history and options.
        val historyArray = chatAdapter.getChatHistory(thread)
        val webSearch = sharedPreferenceUtils
            .getBoolean("ai_web_search", false) && toolType == null
        val customInstructions = if (toolType == null) {
            sharedPreferenceUtils.getString("pref_custom_instructions", "")
        } else ""
        val fileNames = ArrayList<String>()

        // Prepare hash.
        val userQuery = thread.userContent
        val timestamp = System.currentTimeMillis() / 1000
        val hash = SignUtils.createSignature(
            userQuery.take(10),
            timestamp,
            AppUtils.getSignatureHash(requireContext()) + BuildConfig.apiSecretKey
        )

        // Prepare JSON body for API request.
        val jsonBody = JSONObject().apply {
            put("timestamp", timestamp)
            put("sign", hash)
            if (chatFilesAdapter.itemCount > 0) {
                val filesArray = chatFilesAdapter.saveAndConvertFiles()
                fileNames.addAll(filesArray.first)
                chatFilesAdapter.clearUriList()

                if (BuildConfig.DEBUG) {
                    Log.d(ChatFragment::class.simpleName, "Files: ${fileNames.size}")
                }

                put("files", filesArray.second)
            } else if (thread.filesNames.isNotEmpty()) {
                val filesArray = chatFilesAdapter.getAndConvertFiles(thread)

                put("files", filesArray.second)
            }
            put("requires_title", true)
            put("query", userQuery)
            put("history", historyArray)
            put("persona", sharedPreferenceUtils.getString("ai_persona", "assistant"))
            put("style", sharedPreferenceUtils.getString("ai_style", "balanced"))
            put("web_search", webSearch)
            if (toolType != null) {
                put("tool", toolType)
            }
            put("custom_instructions", customInstructions)
        }

        // Update the thread with files.
        if (fileNames.isNotEmpty()) {
            chatAdapter.updateThreadWithFilesNames(thread, fileNames)
        }

        // Send API request.
        val apiCall = okHttpUtils.sendPostRequest(BuildConfig.apiUrl, jsonBody)

        coroutineUtils.io("apiRequest") {
            // Execute API call and handle exceptions.
            val response: Response?
            try {
                response = apiCall.execute()
            } catch (e: IOException) {
                coroutineUtils.main("cancelQuery") {
                    cancelQuery(thread, networkErrorResponse)
                }
                return@io
            } catch (e: Exception) {
                coroutineUtils.main("cancelQuery") {
                    cancelQuery(thread, unexpectedErrorResponse)
                }
                return@io
            }

            var aiResponse = ""
            var responseJson = JSONObject()

            // Check if response is successful and perform respective operations.
            if (response.isSuccessful) {
                responseJson = JSONObject(response.body!!.string())
                aiResponse = responseJson.getString("response")
            } else {
                coroutineUtils.main("cancelQuery") {
                    cancelQuery(thread, unexpectedErrorResponse)
                }
            }

            coroutineUtils.main("aiResponseUpdate") {
                // Update the thread with AI response.
                chatAdapter.updateThreadWithAIResponse(
                    chatId,
                    toolType,
                    thread,
                    aiResponse,
                    responseJson
                )

                // Set the chat title.
                val title = ChatUtils.getChatTitle(chatId)
                setChatTitle(title)

                // Scroll to the thread.
                recyclerViewLayoutManager.scrollToPosition(chatAdapter.getThreadIndexById(thread.id))

                // Speak if it is a voice input.
                if (thread.isVoiceInput && !thread.isCancelled) {
                    speechUtils.speak(thread.aiContent)
                }

                // Hide stop button and show send button.
                querySpeakButton.visibility = View.VISIBLE
                querySendButton.visibility = View.VISIBLE
                queryStopButton.visibility = View.INVISIBLE
            }
        }
    }

    private fun cancelQuery(thread: ChatThread, cancelResponse: String) {
        // Don't cancel when there's no pending query
        if (!chatAdapter.hasPendingQuery(thread)) {
            return
        }

        // Hide stop button and show send button.
        if (::querySpeakButton.isInitialized) {
            querySpeakButton.visibility = View.VISIBLE
        }
        if (::querySendButton.isInitialized) {
            querySendButton.visibility = View.VISIBLE
        }
        if (::queryStopButton.isInitialized) {
            queryStopButton.visibility = View.INVISIBLE
        }

        // Cancel all jobs.
        if (::coroutineUtils.isInitialized) {
            coroutineUtils.cancelAllJobs()
        }

        // Update conversation status.
        if (::chatAdapter.isInitialized) {
            chatAdapter.cancelPendingQuery(thread, cancelResponse)
        }

        // Set the query as the chat title.
        val title = ChatUtils.getChatTitle(chatId)
        setChatTitle(title)
    }

    private fun newConversation() {
        // Cancel active jobs.
        coroutineUtils.cancelAllJobs()

        // Cancel pending query.
        if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
            val thread = chatAdapter.getThreadsOfChat().last()
            chatAdapter.cancelPendingQuery(thread, userCancelledResponse)
        }

        // Change visibility of views accordingly.
        scrollViewNewChat.visibility = View.VISIBLE
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
            // Cancel active jobs.
            coroutineUtils.cancelAllJobs()

            // Cancel pending query.
            if (chatAdapter.getThreadsOfChat().isNotEmpty()) {
                val thread = chatAdapter.getThreadsOfChat().last()
                chatAdapter.cancelPendingQuery(thread, userCancelledResponse)
            }

            // Delete conversation.
            chatAdapter.deleteConversation()

            // Change visibility of views accordingly.
            scrollViewNewChat.visibility = View.VISIBLE
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
        } else {
            // Show a toast message.
            Toast.makeText(
                context,
                getString(R.string.chat_toast_delete_conversation_not_found),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getVoiceInput() {
        // Create a VoiceInputFragment instance and show it.
        val voiceInputFragment = VoiceInputFragment(navController)
        voiceInputFragment.show(parentFragmentManager, VoiceInputFragment.TAG)

        // Observe the lifecycle of the VoiceInputFragment.
        voiceInputFragment.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                onDestinationChanged(navController, navController.currentDestination!!, null)
            }
        })
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val handle = controller.currentBackStackEntry?.savedStateHandle
        val data = handle?.get<String?>("data") ?: return
        handle.remove<String?>("data")
        queryInputBox.setText(data)
        processUserQuery(true)
    }

    private fun setChatTitle(title: String) {
        if (activity != null) {
            val navHostFragment = requireActivity()
                .supportFragmentManager
                .findFragmentById(com.looper.android.support.R.id.fragment_container_view_content_main) as NavHostFragment

            // Set title on the navigation drawer.
            (activity as MainActivity).setDrawerFragmentTitle(
                R.id.fragment_chat,
                title
            )

            // Set title to action bar if the current fragment is being shown.
            if (navHostFragment.childFragmentManager.primaryNavigationFragment == this) {
                (activity as AppCompatActivity).supportActionBar?.title = title
            }
        }
    }
}
