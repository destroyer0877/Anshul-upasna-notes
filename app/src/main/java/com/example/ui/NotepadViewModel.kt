package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.network.*
import com.example.util.AutoSortDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class NotepadViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = DatabaseProvider.getDatabase(context)
    private val repo = NoteRepository(db.noteDao())

    // --- State Streams ---
    val allNotes = repo.allNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteNotes = repo.favoriteNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedNotes = repo.deletedNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customFolders = repo.allFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Key SharedPreferences (Settings & Flags) ---
    private val sharedPrefs = context.getSharedPreferences("notepad_prefs", Context.MODE_PRIVATE)

    var geminiApiKeyOverride: String
        get() = sharedPrefs.getString("api_key_override", "") ?: ""
        set(value) = sharedPrefs.edit().putString("api_key_override", value).apply()

    var openAiApiKey: String
        get() = sharedPrefs.getString("openai_api_key_override", "") ?: ""
        set(value) = sharedPrefs.edit().putString("openai_api_key_override", value).apply()

    var claudeApiKey: String
        get() = sharedPrefs.getString("claude_api_key_override", "") ?: ""
        set(value) = sharedPrefs.edit().putString("claude_api_key_override", value).apply()

    var githubToken: String
        get() = sharedPrefs.getString("github_token_override", "") ?: ""
        set(value) = sharedPrefs.edit().putString("github_token_override", value).apply()

    var customToken: String
        get() = sharedPrefs.getString("custom_token_override", "") ?: ""
        set(value) = sharedPrefs.edit().putString("custom_token_override", value).apply()

    fun getActiveApiKey(): String {
        val ov = geminiApiKeyOverride
        if (ov.isNotEmpty()) return ov
        // fallback to build config if set
        val conf = BuildConfig.GEMINI_API_KEY
        if (conf.isNotEmpty() && conf != "MY_GEMINI_API_KEY") return conf
        return "AIzaSyA1HhLDLQp9haHUHj4dlrD21yPIqYscSWo"
    }

    var savedPin: String
        get() = sharedPrefs.getString("saved_pin", "") ?: ""
        set(value) = sharedPrefs.edit().putString("saved_pin", value).apply()

    var savedSecurityQuestion: String
        get() = sharedPrefs.getString("security_question", "Who is your favorite person?") ?: "Who is your favorite person?"
        set(value) = sharedPrefs.edit().putString("security_question", value).apply()

    var savedSecurityAnswer: String
        get() = sharedPrefs.getString("security_answer", "she_is_miine") ?: "she_is_miine"
        set(value) = sharedPrefs.edit().putString("security_answer", value).apply()

    var isBlurApisEnabled: Boolean
        get() = sharedPrefs.getBoolean("blur_apis", true)
        set(value) = sharedPrefs.edit().putBoolean("blur_apis", value).apply()

    var isSyntaxHighlightingEnabled: Boolean
        get() = sharedPrefs.getBoolean("syntax_highlighting", true)
        set(value) = sharedPrefs.edit().putBoolean("syntax_highlighting", value).apply()

    var customUserName: String
        get() = sharedPrefs.getString("user_name", "Alex") ?: "Alex"
        set(value) = sharedPrefs.edit().putString("user_name", value).apply()

    // --- AI Assistant Floating Chat State ---
    private val _aiChatHistory = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("system", "Hello! I am your AU Bot assistant. I can write notes, organize files, manage folders, structure text, and auto-format your workbench just like Microsoft Copilot. Give me commands like 'create folder Learnings' or 'summarize my note'!")
    ))
    val aiChatHistory: StateFlow<List<ChatMessage>> = _aiChatHistory.asStateFlow()

    private var activeAiJob: kotlinx.coroutines.Job? = null

    private val _oldSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val oldSessions: StateFlow<List<ChatSession>> = _oldSessions.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _editorSuggestions = MutableStateFlow<String?>(null)
    val editorSuggestions: StateFlow<String?> = _editorSuggestions.asStateFlow()

    init {
        loadChatSessions()
        // Hydrate demo folders if empty
        viewModelScope.launch {
            customFolders.take(1).collect { folders ->
                if (folders.isEmpty()) {
                    repo.saveFolder(FolderEntity(name = "Personal Notes", colorHex = "#FF5E7E", iconType = "CUSTOM"))
                }
            }
            // 15 days in milliseconds
            val threshold = System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000)
            repo.deleteOldTrashNotes(threshold)
        }
    }

    // --- Note CRUD with Autodetect Logic ---
    fun saveNote(
        id: Int = 0,
        title: String,
        content: String,
        folderId: Int = -1,
        isFavorite: Boolean = false,
        themeType: String = "GLASS_DARK",
        imagePath: String? = null,
        filePath: String? = null,
        reminderTime: Long? = null,
        reminderTone: String? = null,
        manualType: String? = null,
        onSaved: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            // Auto sort type checking unless user manually specified type override
            val calculatedType = manualType ?: AutoSortDetector.detectType(title, content)
            val updatedFolderId = if (folderId == -1) {
                // Auto-route to virtual categories in UI - kept unsorted (-1) in Db but labeled
                -1
            } else {
                folderId
            }

            val existingNote = if (id != 0) repo.getNoteById(id) else null
            
            val note = NoteEntity(
                id = id,
                title = title.ifBlank { "Untitled Note" },
                content = content,
                folderId = updatedFolderId,
                isFavorite = isFavorite,
                isLocked = existingNote?.isLocked ?: (calculatedType == "API"), // auto_lock APIs by default!
                type = calculatedType,
                themeType = themeType,
                imagePath = imagePath ?: existingNote?.imagePath,
                filePath = filePath ?: existingNote?.filePath,
                reminderTime = reminderTime,
                reminderTone = reminderTone,
                createdAt = existingNote?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val assignedId = repo.saveNote(note).toInt()
            onSaved(assignedId)
        }
    }

    fun toggleFavorite(note: NoteEntity) {
        viewModelScope.launch {
            repo.updateNote(note.copy(isFavorite = !note.isFavorite, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleLock(note: NoteEntity) {
        viewModelScope.launch {
            repo.updateNote(note.copy(isLocked = !note.isLocked, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateTheme(note: NoteEntity, newThemeType: String) {
        viewModelScope.launch {
            repo.updateNote(note.copy(themeType = newThemeType, updatedAt = System.currentTimeMillis()))
        }
    }

    fun moveNoteToTrash(id: Int) {
        viewModelScope.launch {
            repo.moveToTrash(id)
        }
    }

    fun restoreNote(id: Int) {
        viewModelScope.launch {
            repo.restoreFromTrash(id)
        }
    }

    suspend fun hardDeleteNote(note: NoteEntity) {
        repo.deleteNote(note)
    }

    fun clearTrash() {
        viewModelScope.launch {
            repo.emptyTrash()
        }
    }

    fun createNewFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            repo.saveFolder(FolderEntity(name = name, colorHex = colorHex, iconType = "CUSTOM"))
        }
    }

    fun deleteFolder(folderId: Int) {
        viewModelScope.launch {
            repo.deleteFolder(folderId)
        }
    }

    // --- AI Command Parser via Gemini API (Direct Retrofit Option B) ---
    fun sendUserCommand(commandText: String, attachedImageBitmap: Bitmap? = null) {
        if (commandText.isBlank() && attachedImageBitmap == null) return

        activeAiJob?.cancel()

        val originalHistory = _aiChatHistory.value.toMutableList()
        originalHistory.add(ChatMessage("user", commandText + (if (attachedImageBitmap != null) " [Sent Attachment Image]" else "")))
        _aiChatHistory.value = originalHistory
        _isAiLoading.value = true

        activeAiJob = viewModelScope.launch(Dispatchers.IO) {
            val key = getActiveApiKey()
            if (key.isBlank()) {
                withContext(Dispatchers.Main) {
                    originalHistory.add(ChatMessage("system", "Error: Gemini API Key is missing. Please add/configure your key in settings or `.env`."))
                    _aiChatHistory.value = originalHistory
                    _isAiLoading.value = false
                    activeAiJob = null
                }
                return@launch
            }

            try {
                var finalResponseText = ""
                
                if (attachedImageBitmap != null) {
                    // Vision Flow: Base64 wrap
                    val userPrompt = commandText.ifBlank { "Extract any useful text, structure, format properly, and return a clean title and paragraphs that we can save to notes." }
                    val base64Img = bitmapToBase64(attachedImageBitmap)
                    val req = GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(
                                    GeminiPart(text = userPrompt),
                                    GeminiPart(inlineData = InlinePartData(mimeType = "image/jpeg", data = base64Img))
                                )
                            )
                        ),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a professional optical character recognition and analysis engine for AuPad.")))
                    )
                    val apiResponse = RetrofitClient.service.generateContent(key, req)
                    finalResponseText = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Failed to read image"
                } else {
                    // Regular Text & Action commands
                    val textCommand = commandText.trim()
                    
                    // Generate rich context string of folders and notes
                    val currentFoldersContext = customFolders.value.joinToString("\n") { "  * Folder ID ${it.id}: Name '${it.name}' (ColorHex: ${it.colorHex})" }
                    val currentNotesContext = allNotes.value.joinToString("\n") { "  * Note ID ${it.id}: Title '${it.title}' in Folder ID ${it.folderId} (Type: ${it.type})" }

                    // Forward to general intelligence with dynamic list context & action capability
                    val instructions = """
                        You are the central manager and automation AI agent of 'AU NOTES', an elegant iOS metallic glassmorphism Notepad app using a Room database.
                        Your job is to assist the user by executing actions (such as creating/deleting folders, writing/updating/deleting notes) and replying naturally in Hindi, Hinglish, or English.

                        IMPORTANT REQUIRED OUTPUT FORMAT:
                        You MUST ALWAYS respond with a SINGLE valid JSON object containing exactly two keys: "textResponse" and "actions".
                        NO other text, NO backticks, NO "```json" wrappers. Just pure parseable JSON.

                        Example format:
                        {
                          "textResponse": "Maine 'Python Learnings' folder mein hello_world.py save kar diya hai! 🚀",
                          "actions": [
                            {
                              "type": "CREATE_NOTE",
                              "title": "hello_world.py",
                              "content": "print('Hello World')",
                              "folderName": "Python Learnings",
                              "noteType": "CODE"
                            }
                          ]
                        }

                        AVAILABLE DATABASE ACTION COMMANDS:
                        1. { "type": "CREATE_FOLDER", "name": "Folder Name", "colorHex": "#34C759" } -> Use vibrant pastel/modern colors like #FF5E7E, #32D74B, #FF9500, #5AC8FA, #AF52DE.
                        2. { "type": "DELETE_FOLDER", "folderId": ID }
                        3. { "type": "CREATE_NOTE", "title": "Note Title", "content": "Note Content", "folderId": ID, "folderName": "Name", "themeType": "GLASS_DARK", "noteType": "NORMAL" } -> noteType can be: "NORMAL", "API", "CODE", "VIDEO", "IMPORTANT". If folderName is supplied, the app will auto-link to it (or create it if needed).
                        4. { "type": "UPDATE_NOTE", "noteId": ID, "title": "New Title", "content": "New Content", "isFavorite": Boolean, "isLocked": Boolean }
                        5. { "type": "MOVE_TO_TRASH", "noteId": ID } -> Equivalent to soft delete.
                        6. { "type": "RESTORE_NOTE", "noteId": ID }
                        7. { "type": "CLEAR_TRASH" }

                        CURRENT DATABASE CONTEXT (Use these actual IDs when requested!):
                        -- Active Folders (Workspaces):
                        $currentFoldersContext
                        
                        -- Active Notes:
                        $currentNotesContext

                        Rule for Actions:
                        - If the user asks for a simple query or generic question (e.g., "how are you" or "explain list comprehension"), set "actions" to [].
                        - If the user asks to "delete the note titled Hello", find the ID of that note from Context, and output "MOVE_TO_TRASH" with that id.
                        - If the user asks to "add folder Python", output "CREATE_FOLDER" with name="Python".
                        - Keep the "textResponse" friendly, concise, natural, and helpful. Do not mention JSON or coding terms in textResponse.
                    """.trimIndent()

                    val req = GeminiRequest(
                        contents = listOf(
                            GeminiContent(parts = listOf(GeminiPart(text = textCommand)))
                        ),
                        systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = instructions)))
                    )
                    
                    val apiResponse = RetrofitClient.service.generateContent(key, req)
                    val rawResult = apiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    
                    var jsonString = rawResult.trim()
                    if (jsonString.startsWith("```json")) {
                        jsonString = jsonString.removePrefix("```json")
                    }
                    if (jsonString.endsWith("```")) {
                        jsonString = jsonString.removeSuffix("```")
                    }
                    jsonString = jsonString.trim()

                    if (jsonString.startsWith("{") && jsonString.endsWith("}")) {
                        try {
                            val jsonObject = org.json.JSONObject(jsonString)
                            finalResponseText = jsonObject.optString("textResponse", rawResult)
                            
                            val actionsArray = jsonObject.optJSONArray("actions")
                            if (actionsArray != null) {
                                for (i in 0 until actionsArray.length()) {
                                    val action = actionsArray.getJSONObject(i)
                                    val type = action.optString("type")
                                    when (type) {
                                        "CREATE_FOLDER" -> {
                                            val name = action.getString("name")
                                            val colorHex = action.optString("colorHex", "#FF5E7E")
                                            repo.saveFolder(FolderEntity(name = name, colorHex = colorHex, iconType = "CUSTOM"))
                                        }
                                        "DELETE_FOLDER" -> {
                                            val folderId = action.getInt("folderId")
                                            repo.deleteFolder(folderId)
                                        }
                                        "CREATE_NOTE" -> {
                                            val title = action.getString("title")
                                            val content = action.getString("content")
                                            val themeType = action.optString("themeType", "GLASS_DARK")
                                            val noteType = action.optString("noteType", "NORMAL")
                                            var folderId = action.optInt("folderId", -1)
                                            val folderName = action.optString("folderName")

                                            if (folderId == -1 && !folderName.isNullOrBlank()) {
                                                val existingFolder = customFolders.value.find { it.name.equals(folderName, ignoreCase = true) }
                                                if (existingFolder != null) {
                                                    folderId = existingFolder.id
                                                } else {
                                                    val newFolderId = repo.saveFolder(FolderEntity(name = folderName, colorHex = "#AF52DE", iconType = "CUSTOM"))
                                                    folderId = newFolderId.toInt()
                                                }
                                            }

                                            repo.saveNote(
                                                NoteEntity(
                                                    title = title,
                                                    content = content,
                                                    folderId = folderId,
                                                    themeType = themeType,
                                                    type = noteType,
                                                    isLocked = (noteType == "API")
                                                )
                                            )
                                        }
                                        "UPDATE_NOTE" -> {
                                            val noteId = action.getInt("noteId")
                                            val existingNote = repo.getNoteById(noteId)
                                            if (existingNote != null) {
                                                val title = action.optString("title", existingNote.title)
                                                val content = action.optString("content", existingNote.content)
                                                val folderId = action.optInt("folderId", existingNote.folderId)
                                                val isFavorite = action.optBoolean("isFavorite", existingNote.isFavorite)
                                                val isLocked = action.optBoolean("isLocked", existingNote.isLocked)
                                                repo.saveNote(
                                                    existingNote.copy(
                                                        title = title,
                                                        content = content,
                                                        folderId = folderId,
                                                        isFavorite = isFavorite,
                                                        isLocked = isLocked,
                                                        updatedAt = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                        "MOVE_TO_TRASH" -> {
                                            val noteId = action.getInt("noteId")
                                            repo.moveToTrash(noteId)
                                        }
                                        "RESTORE_NOTE" -> {
                                            val noteId = action.getInt("noteId")
                                            repo.restoreFromTrash(noteId)
                                        }
                                        "CLEAR_TRASH" -> {
                                            repo.emptyTrash()
                                        }
                                    }
                                }
                            }
                        } catch (parsingException: Exception) {
                            finalResponseText = rawResult
                        }
                    } else {
                        finalResponseText = rawResult
                    }
                }

                withContext(Dispatchers.Main) {
                    originalHistory.add(ChatMessage("assistant", finalResponseText))
                    _aiChatHistory.value = originalHistory
                    _isAiLoading.value = false
                    activeAiJob = null
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    originalHistory.add(ChatMessage("system", "Network Error: Unable to compute command. Please check details or verify API Key.\nReason: ${e.localizedMessage}"))
                    _aiChatHistory.value = originalHistory
                    _isAiLoading.value = false
                    activeAiJob = null
                }
            }
        }
    }

    // --- AI Stop & Reset & Session Persistence Helpers ---
    fun stopOngoingAiRequest() {
        activeAiJob?.cancel()
        activeAiJob = null
        _isAiLoading.value = false
        val currentHistory = _aiChatHistory.value.toMutableList()
        currentHistory.add(ChatMessage("system", "⚠️ AI response generation was stopped by the user."))
        _aiChatHistory.value = currentHistory
    }

    private fun persistSessions(list: List<ChatSession>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { session ->
                val obj = org.json.JSONObject()
                obj.put("id", session.id)
                obj.put("title", session.title)
                val msgsArr = org.json.JSONArray()
                session.messages.forEach { msg ->
                    val mObj = org.json.JSONObject()
                    mObj.put("sender", msg.sender)
                    mObj.put("message", msg.message)
                    msgsArr.put(mObj)
                }
                obj.put("messages", msgsArr)
                arr.put(obj)
            }
            sharedPrefs.edit().putString("ai_chat_sessions_v1", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadChatSessions() {
        val jsonStr = sharedPrefs.getString("ai_chat_sessions_v1", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ChatSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getLong("id")
                val title = obj.getString("title")
                val msgsArr = obj.getJSONArray("messages")
                val msgsList = mutableListOf<ChatMessage>()
                for (j in 0 until msgsArr.length()) {
                    val mObj = msgsArr.getJSONObject(j)
                    msgsList.add(ChatMessage(mObj.getString("sender"), mObj.getString("message")))
                }
                list.add(ChatSession(id, title, msgsList))
            }
            _oldSessions.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun archiveAndResetCurrentChat() {
        val currentMsgs = _aiChatHistory.value
        if (currentMsgs.size > 1) {
            val firstUserPrompt = currentMsgs.firstOrNull { it.sender == "user" }?.message?.take(30) ?: "Quick AI Chat Session"
            val title = "$firstUserPrompt..."
            val newSession = ChatSession(
                id = System.currentTimeMillis(),
                title = title,
                messages = currentMsgs
            )
            val updatedList = _oldSessions.value.toMutableList()
            updatedList.add(0, newSession)
            _oldSessions.value = updatedList
            persistSessions(updatedList)
        }
        
        _aiChatHistory.value = listOf(
            ChatMessage("system", "Hello! I am your AU Bot assistant. I can write notes, organize files, manage folders, structure text, and auto-format your workbench just like Microsoft Copilot. Give me commands like 'create folder Learnings' or 'summarize my note'!")
        )
    }

    fun restoreChatSession(session: ChatSession) {
        _aiChatHistory.value = session.messages
    }

    fun deleteChatSession(sessionId: Long) {
        val updatedList = _oldSessions.value.filter { it.id != sessionId }
        _oldSessions.value = updatedList
        persistSessions(updatedList)
    }

    // --- On-Demand Suggestions while typing ("ab mujhse nahi ho raha") ---
    fun fetchTextSuggestions(currentTitle: String, currentText: String) {
        if (currentText.isBlank()) return
        val key = getActiveApiKey()
        if (key.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = "The user is currently writing a notepad entry with title '$currentTitle'. The body content written so far is:\n\"\"\"\n$currentText\n\"\"\"\nThey said they are stuck, tired or need assistance ('ab mujhse nahi ho raha'). Complete the current sentence/thought beautifully with creative 2-3 structured sentences that blend in. Return ONLY the continuation text, don't say 'sure' or introduce anything else."
                val req = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                )
                val response = RetrofitClient.service.generateContent(key, req)
                val suggestion = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                _editorSuggestions.value = suggestion?.trim()
            } catch (e: Exception) {
                // Fail silently for inline typing helpers
            }
        }
    }

    // On-demand customized Copilot Drafting inside rich text area
    suspend fun fetchCopilotDraft(instruction: String, currentTitle: String, currentText: String): String = withContext(Dispatchers.IO) {
        val key = getActiveApiKey()
        if (key.isBlank()) return@withContext "Error: Gemini API key is not configured. Please add it in settings."
        try {
            val prompt = """
                You are the AU Inline Copilot writing assistant.
                The user is writing a document titled: '$currentTitle'.
                The current content is:
                \"\"\"
                $currentText
                \"\"\"
                
                The user has requested the following guidance or block generation:
                "Create content following these instructions: $instruction"
                
                Generate high-quality, professional formatted rich text (Markdown / Tables supported) to satisfy the user's instructions.
                Keep it concise and ready to insert into the document directly.
                Do not include generic intro/outro phrases. Just return the requested content block directly.
            """.trimIndent()
            val req = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
            )
            val response = RetrofitClient.service.generateContent(key, req)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Error: Did not receive output from AI."
        } catch (e: Exception) {
            "Error generating draft: ${e.message}"
        }
    }

    // --- AI Name Generator Stylizer ---
    fun generateAiNameStyles(name: String, stylePrompt: String, onResult: (List<Pair<String, String>>) -> Unit) {
        if (name.isBlank()) {
            onResult(emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val key = getActiveApiKey()
            val presets = com.example.util.NameStylizer.getStylishFonts(name)
            if (key.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(presets)
                }
                return@launch
            }
            try {
                val promptDetails = if (stylePrompt.isBlank()) "Generate a wild, diverse mix of styles (e.g. gothic, cute, hacker, minimalist, emojis, cursive, small caps, bubble)." else stylePrompt
                val sysInstruction = "You are an expert typography and stylish name generator. Generate 15 unique, highly creative font and symbol variations of the name '$name'. If the user provides a preference, you MUST strictly follow it (e.g. if they say 'small caps', output in small caps). Preference: '$promptDetails'. Output ONLY a JSON array of objects with keys 'style' (short description) and 'result' (the stylized name). Do not include markdown or backticks. Ensure output is just raw JSON."
                val req = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Name: $name\nPreferences: $promptDetails")))),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = sysInstruction)))
                )
                val response = RetrofitClient.service.generateContent(key, req)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                var cleanJson = rawText.replace("```json", "").replace("```", "").trim()
                val aiList = mutableListOf<Pair<String, String>>()
                if (cleanJson.startsWith("[")) {
                    val jsonArr = org.json.JSONArray(cleanJson)
                    for (i in 0 until jsonArr.length()) {
                        val obj = jsonArr.getJSONObject(i)
                        val st = obj.optString("style", "AI Style ${i + 1}")
                        val res = obj.optString("result", name)
                        aiList.add(st to res)
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(aiList + presets)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(presets)
                }
            }
        }
    }


    // --- Dynamic Dark Mode State ---
    var isDarkMode by mutableStateOf(sharedPrefs.getBoolean("dark_mode_enabled", true))
        private set

    fun toggleDarkMode() {
        val newVal = !isDarkMode
        isDarkMode = newVal
        sharedPrefs.edit().putBoolean("dark_mode_enabled", newVal).apply()
    }

    fun clearSuggestions() {
        _editorSuggestions.value = null
    }

    // Convert Image Uri to base64 for Gemini multipart support
    private suspend fun bitmapToBase64(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        // Compress standard to avoid too large images over slow networks
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun decodeUriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun restoreFromTrash(id: Int) {
        viewModelScope.launch {
            repo.restoreFromTrash(id)
        }
    }
    
    fun deleteNotePermanently(note: com.example.data.NoteEntity) {
        viewModelScope.launch {
            repo.deleteNote(note)
        }
    }

}
data class ChatMessage(val sender: String, val message: String)
data class ChatSession(val id: Long = System.currentTimeMillis(), val title: String, val messages: List<ChatMessage>)
