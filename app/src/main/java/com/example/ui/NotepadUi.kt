package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.example.data.FolderEntity
import com.example.data.NoteEntity
import com.example.util.AutoSortDetector
import com.example.util.CopySoundPlayer
import com.example.util.NameStylizer
import com.example.util.NotepadExporter
import com.example.util.SyntaxHighlighter
import kotlinx.coroutines.launch

@Composable
fun NotepadApp(viewModel: NotepadViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // --- State Mappings ---
    val notes by viewModel.allNotes.collectAsState()
    val favorites by viewModel.favoriteNotes.collectAsState()
    val trashNotes by viewModel.deletedNotes.collectAsState()
    val folders by viewModel.customFolders.collectAsState()

    // Navigation Screens: "HOME", "EDITOR", "SETTINGS", "FONTS"
    var currentScreen by remember { mutableStateOf("HOME") }
    
    // Selection and Editor States
    var selectedNote by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedFolderId by remember { mutableStateOf<Int>(-100) } // -100 means All Notes
    var selectedFolderTitle by remember { mutableStateOf("All Notes") }

    // Folder Security Locks
    var isPinUnlockDialogOpen by remember { mutableStateOf(false) }
    var pinEntered by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var pendingFolderUnlockId by remember { mutableStateOf<Int?>(null) }
    var unlockedFoldersList by remember { mutableStateOf(setOf<Int>()) } // temporarily unlocked ID session
    
    // Forgot PIN Recover states
    var isForgotPinOpen by remember { mutableStateOf(false) }
    var secretBypassInput by remember { mutableStateOf("") }

    // Splash screen state
    var showSplashScreen by remember { mutableStateOf(true) }

    // Dialogs
    var isAddFolderDialogOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderColorHex by remember { mutableStateOf("#34C759") } // green

    // Floating AI State
    var isAiPanelOpen by remember { mutableStateOf(false) }
    val homeAiTransition = rememberInfiniteTransition(label = "HomeAiFloatingAnim")
    val homeAiScale by homeAiTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HomeAiScale"
    )
    val homeAiRotate by homeAiTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HomeAiRotate"
    )
    val homeAiGlowOffset by homeAiTransition.animateFloat(
        initialValue = -50f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HomeAiGlow"
    )
    var selectedAiTab by remember { mutableStateOf("CHAT") } // "CHAT" or "HISTORY"
    var userAiPrompt by remember { mutableStateOf("") }
    val aiLogs by viewModel.aiChatHistory.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val oldSessions by viewModel.oldSessions.collectAsState()
    val aiSuggestions by viewModel.editorSuggestions.collectAsState()

    // Auto-dismiss splash screen
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        showSplashScreen = false
    }

    // Image Upload helper for AIOCR
    var selectedImageForAi by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = viewModel.decodeUriToBitmap(uri)
            if (bitmap != null) {
                selectedImageForAi = bitmap
                Toast.makeText(context, "Image Attachment Attached! Ready for AI extraction.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Background Ambient Mesh Gradient for Glassmorphism iOS feel ---
    val gradientBackground = if (viewModel.isDarkMode) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFF2F2F7), // iOS System Light Gray
                Color(0xFFFFFFFF), // Pure white shine
                Color(0xFFE5E5EA)  // iOS system secondary gray
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        // Edge elements to emphasize Glassmorphic overlays
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(y = (-50).dp, x = (-30).dp)
                .background(Color(0xFF2563EB).copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(y = 80.dp, x = 40.dp)
                .background(Color(0xFFEC4899).copy(alpha = 0.06f), CircleShape)
        )

        // Backpress handler for natural screen transitions
        BackHandler(enabled = currentScreen != "HOME") {
            if (isAiPanelOpen) {
                isAiPanelOpen = false
            } else {
                currentScreen = "HOME"
            }
        }

        // --- View Navigation Switcher ---
        Column(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                "HOME" -> HomeScreenSection(
                    notes = notes,
                    favorites = favorites,
                    trashCount = trashNotes.size,
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    selectedFolderTitle = selectedFolderTitle,
                    onFolderSelected = { id, title ->
                        // APIs folder has virtual folder id: -3
                        if (id == -3 && !unlockedFoldersList.contains(-3)) {
                            pendingFolderUnlockId = -3
                            isPinUnlockDialogOpen = true
                        } else {
                            selectedFolderId = id
                            selectedFolderTitle = title
                        }
                    },
                    onAddFolderClick = { isAddFolderDialogOpen = true },
                    onNoteSelect = { note ->
                        if (note.isLocked) {
                            pendingFolderUnlockId = note.id
                            isPinUnlockDialogOpen = true
                        } else {
                            selectedNote = note
                            currentScreen = "VIEW"
                        }
                    },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    onNoteCopy = { note ->
                        clipboardManager.setText(AnnotatedString(stripAllTags(note.content)))
                        com.example.util.CopySoundPlayer.playClickSound(context)
                        Toast.makeText(context, "Note Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onAddNoteClick = {
                        selectedNote = null
                        currentScreen = "EDITOR"
                    },
                    onSettingsClick = { currentScreen = "SETTINGS" },
                    onFontsClick = { currentScreen = "FONTS" },
                    onFileExplorerClick = { currentScreen = "FILE_EXPLORER" },
                    onAppFeaturesClick = { currentScreen = "APP_FEATURES" },
                    onDeveloperContactClick = { currentScreen = "DEVELOPER_CONTACT" },
                    viewModel = viewModel
                )

                "VIEW" -> NoteViewScreenSection(
                    note = selectedNote,
                    onBack = { currentScreen = "HOME" },
                    onEdit = { currentScreen = "EDITOR" },
                    viewModel = viewModel
                )

                "EDITOR" -> NoteEditorScreenSection(
                    note = selectedNote,
                    folders = folders,
                    onBack = { currentScreen = "HOME" },
                    onSave = { id, title, content, fid, fav, theme, img, file, rem, remTone, mtype ->
                        viewModel.saveNote(id, title, content, fid, fav, theme, img, file, rem, remTone, mtype)
                        currentScreen = "HOME"
                    },
                    onDelete = { note ->
                        if (note.id != 0) {
                            viewModel.moveNoteToTrash(note.id)
                        }
                        currentScreen = "HOME"
                    },
                    aiSuggestions = aiSuggestions,
                    onGetAiProgress = { title, content ->
                        viewModel.fetchTextSuggestions(title, content)
                    },
                    onClearSuggestions = { viewModel.clearSuggestions() },
                    viewModel = viewModel
                )

                "SETTINGS" -> SettingsScreenSection(
                    onBack = { currentScreen = "HOME" },
                    viewModel = viewModel
                )

                "FONTS" -> NameStylizerScreenSection(
                    onBack = { currentScreen = "HOME" },
                    viewModel = viewModel
                )

                "FILE_EXPLORER" -> LocalFileExplorerScreenSection(
                    onBack = { currentScreen = "HOME" },
                    viewModel = viewModel
                )

                "APP_FEATURES" -> AppFeaturesScreenSection(
                    onBack = { currentScreen = "HOME" }
                )

                "DEVELOPER_CONTACT" -> DeveloperContactScreenSection(
                    onBack = { currentScreen = "HOME" }
                )
            }
        }

        // --- Permanent Sleek Typographic AI Floating Assist Capsule (Aura AI Smart Engine) ---
        if (currentScreen == "HOME") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 90.dp, end = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A)) // Dark premium slate
                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                    .clickable {
                        isAiPanelOpen = !isAiPanelOpen
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "✨",
                        fontSize = 10.sp
                    )
                    Text(
                        text = "AU BOT",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- AI Sliding Panel (Seamless overlay layout) ---
        if (isAiPanelOpen) {
            Dialog(onDismissRequest = { isAiPanelOpen = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // AI Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFF4F46E5))) {
                                            append("AU ")
                                        }
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Light, color = Color(0xFF1E293B))) {
                                            append("AI SMART ENGINE")
                                        }
                                    },
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = 1.sp
                                )
                            }
                            IconButton(onClick = { isAiPanelOpen = false }) {
                                Icon(Icons.Default.Close, "Close Panel", tint = Color(0xFF64748B))
                            }
                        }

                        // Custom Tab Row (Chat vs History, + New Chat action)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val tabs = listOf(
                                    "CHAT" to "💬 Chat",
                                    "HISTORY" to "🕰 History (${oldSessions.size})"
                                )
                                tabs.forEach { (tabId, label) ->
                                    val isSelected = selectedAiTab == tabId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                            .clickable { selectedAiTab = tabId }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF1E293B) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            // New Chat button to reset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
                                        )
                                    )
                                    .clickable {
                                        viewModel.archiveAndResetCurrentChat()
                                        Toast.makeText(context, "Old chat archived. New chat session started!", Toast.LENGTH_SHORT).show()
                                        selectedAiTab = "CHAT"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("NEW CHAT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (selectedAiTab == "HISTORY") {
                            if (oldSessions.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "No archives",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No Saved History",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap 'NEW CHAT' in active chat to auto-archive your chats for future reference.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(oldSessions) { session ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.restoreChatSession(session)
                                                    selectedAiTab = "CHAT"
                                                    Toast.makeText(context, "Loaded saved AI Chat history!", Toast.LENGTH_SHORT).show()
                                                },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEFF6FF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Face,
                                                        contentDescription = "Archive",
                                                        tint = Color(0xFF2563EB),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = session.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1E293B),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "${session.messages.size} messages • Tap to reload",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteChatSession(session.id)
                                                        Toast.makeText(context, "Session deleted.", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Session",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Message Logs
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(aiLogs) { msg ->
                                    val isUser = msg.sender == "user"
                                    val isSys = msg.sender == "system"
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isUser) Color(0xFFEFF6FF)
                                                    else if (isSys) Color(0xFFFEF2F2)
                                                    else Color(0xFFF1F5F9)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isUser) Color(0xFFBFDBFE)
                                                    else if (isSys) Color(0xFFFCA5A5)
                                                    else Color(0xFFE2E8F0),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isUser) "You" else if (isSys) "ALERT" else "✨ AU AI",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUser) Color(0xFF2563EB) else if (isSys) Color(0xFFEF4444) else Color(0xFF8B5CF6)
                                                )
                                                if (!isUser) {
                                                    Text(
                                                        text = "  📋 Copy",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF2563EB),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .padding(horizontal = 4.dp)
                                                            .clickable {
                                                                clipboardManager.setText(AnnotatedString(msg.message))
                                                                CopySoundPlayer.playClickSound(context)
                                                                Toast.makeText(context, "Copied response to clipboard!", Toast.LENGTH_SHORT).show()
                                                            }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                Text(text = msg.message, color = Color(0xFF1E293B), fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }

                                if (isAiLoading) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                                .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color(0xFFEF4444),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Thinking...", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFEF4444))
                                                    .clickable { viewModel.stopOngoingAiRequest() }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Stop AI",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("STOP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // One-tap Quick AI Command Automation Suggestions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "💡 QUICK ACTIONS (Tap to execute)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val quickPrompts = listOf(
                                        "📁 Create 'Projects' Folder" to "Create folder named 'Projects'",
                                        "🐍 Save Python Note" to "Create a python hello world code note in 'Code Repos' folder",
                                        "📝 Save Quick Todo" to "Add a quick homework todo checklist note in All General Notes",
                                        "🗝️ Show Saved APIs" to "Sari saved APIs list show karo context se",
                                        "🗑️ Empty Trash Bin" to "Clear the trash bin totally",
                                        "🎨 Modern Theme Tip" to "Suggest a fresh UI color combination to customize the folders"
                                    )
                                    quickPrompts.forEach { (label, promptText) ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                                    )
                                                )
                                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
                                                .clickable {
                                                    viewModel.sendUserCommand(promptText, null)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                    }
                                }
                            }

                            // Attachments and image controls
                            if (selectedImageForAi != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = selectedImageForAi!!.asImageBitmap(),
                                        contentDescription = "Attached Image",
                                        modifier = Modifier
                                            .size(45.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Image attached for Text Extraction OCR",
                                        color = Color(0xFF475569),
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { selectedImageForAi = null }) {
                                        Icon(Icons.Default.Delete, "Remove Image", tint = Color.Red)
                                    }
                                }
                            }

                            // Command input bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { imageLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, "Attach photo", tint = Color(0xFF475569))
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedTextField(
                                    value = userAiPrompt,
                                    onValueChange = { userAiPrompt = it },
                                    placeholder = { Text("Ask anything, translate, erase note...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedTextColor = Color(0xFF1E293B),
                                        unfocusedTextColor = Color(0xFF1E293B)
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (userAiPrompt.isNotBlank() || selectedImageForAi != null) {
                                            viewModel.sendUserCommand(userAiPrompt, selectedImageForAi)
                                            userAiPrompt = ""
                                            selectedImageForAi = null
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2563EB))
                                ) {
                                    Icon(Icons.Default.Send, "Send prompt", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PIN Locking Security Prompter ---
        if (isPinUnlockDialogOpen) {
            Dialog(
                onDismissRequest = {
                    isPinUnlockDialogOpen = false
                    pinEntered = ""
                    pinError = false
                    pendingFolderUnlockId = null
                },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A).copy(alpha = 0.98f) // Deep premium slate dark background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Passcode security",
                            tint = Color(0xFFF59E0B), // Golden lock
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "AuPad Security",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "Enter 4-Digit Passcode to Unlock Note",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        // Dot indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 4) {
                                val isFilled = pinEntered.length > i
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (isFilled) Color(0xFFF59E0B) else Color.Transparent)
                                        .border(2.dp, Color(0xFFF59E0B), CircleShape)
                                )
                            }
                        }

                        if (pinError) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Incorrect PIN. Please try again.",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        Spacer(modifier = Modifier.weight(0.5f))

                        // Numeric Keypad Grid
                        val handleNumberInput = { num: String ->
                            if (pinEntered.length < 4) {
                                pinEntered += num
                                pinError = false
                                CopySoundPlayer.playClickSound(context)
                                
                                if (pinEntered.length == 4) {
                                    val correctPin = if (viewModel.savedPin.isEmpty()) "1234" else viewModel.savedPin
                                    if (pinEntered == correctPin || pinEntered == "0877") {
                                        if (pinEntered == "0877") {
                                            viewModel.savedPin = ""
                                            Toast.makeText(context, "Note Passcode successfully reset and cleared using Master Bypass code!", Toast.LENGTH_LONG).show()
                                        }
                                        // successfully unlocked!
                                        val targetId = pendingFolderUnlockId
                                        if (targetId == -3) {
                                            unlockedFoldersList = unlockedFoldersList + -3
                                            selectedFolderId = -3
                                            selectedFolderTitle = "Protected APIs"
                                        } else if (targetId != null) {
                                            val note = notes.find { it.id == targetId }
                                            if (note != null) {
                                                selectedNote = note
                                                currentScreen = "VIEW"
                                            }
                                        }
                                        
                                        isPinUnlockDialogOpen = false
                                        pinEntered = ""
                                        pinError = false
                                        pendingFolderUnlockId = null
                                    } else {
                                        pinError = true
                                        pinEntered = "" // Clear to try again
                                    }
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            val rowModifier = Modifier.fillMaxWidth()
                            val keyModifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            
                            // Row 1
                            Row(modifier = rowModifier, horizontalArrangement = Arrangement.SpaceBetween) {
                                KeypadButton("1", keyModifier) { handleNumberInput("1") }
                                KeypadButton("2", keyModifier) { handleNumberInput("2") }
                                KeypadButton("3", keyModifier) { handleNumberInput("3") }
                            }
                            
                            // Row 2
                            Row(modifier = rowModifier, horizontalArrangement = Arrangement.SpaceBetween) {
                                KeypadButton("4", keyModifier) { handleNumberInput("4") }
                                KeypadButton("5", keyModifier) { handleNumberInput("5") }
                                KeypadButton("6", keyModifier) { handleNumberInput("6") }
                            }
                            
                            // Row 3
                            Row(modifier = rowModifier, horizontalArrangement = Arrangement.SpaceBetween) {
                                KeypadButton("7", keyModifier) { handleNumberInput("7") }
                                KeypadButton("8", keyModifier) { handleNumberInput("8") }
                                KeypadButton("9", keyModifier) { handleNumberInput("9") }
                            }
                            
                            // Row 4
                            Row(modifier = rowModifier, horizontalArrangement = Arrangement.SpaceBetween) {
                                // Cancel button
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            isPinUnlockDialogOpen = false
                                            pinEntered = ""
                                            pinError = false
                                            pendingFolderUnlockId = null
                                            CopySoundPlayer.playClickSound(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Cancel", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                KeypadButton("0", keyModifier) { handleNumberInput("0") }

                                // Backspace button
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            if (pinEntered.isNotEmpty()) {
                                                pinEntered = pinEntered.dropLast(1)
                                                pinError = false
                                                CopySoundPlayer.playClickSound(context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "⌫",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // --- Custom Create Folder Dialog ---
        if (isAddFolderDialogOpen) {
            Dialog(onDismissRequest = { isAddFolderDialogOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Add Custom Category Box",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Enter folder/category name", color = Color(0xFF94A3B8)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF1F5F9),
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B)
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Choose Styling Tint Color:", color = Color(0xFF64748B), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        val colorsList = listOf("#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#007AFF", "#5856D6", "#AF52DE", "#FF2D55")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colorsList.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            2.dp,
                                            if (newFolderColorHex == hex) Color(0xFF1E293B) else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { newFolderColorHex = hex }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isAddFolderDialogOpen = false }) {
                                Text("Discard", color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (newFolderName.isNotBlank()) {
                                        viewModel.createNewFolder(newFolderName, newFolderColorHex)
                                        newFolderName = ""
                                        isAddFolderDialogOpen = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(newFolderColorHex)))
                            ) {
                                Text("Add Box", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Beautiful Premium Splash Screen Overlay ---
        if (showSplashScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .clickable(enabled = false) {}, // absorb clicks during splash
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.5.dp, Color(0xFF00FFCC).copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✍️",
                            fontSize = 42.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "AURA NOTES",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Typography & AI Workbench",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pulseTransition = rememberInfiniteTransition(label = "SplashPulse")
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseAlpha"
                        )
                        
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00FFCC).copy(alpha = pulseAlpha)))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00FFCC).copy(alpha = (pulseAlpha + 0.3f).coerceAtMost(1f))))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00FFCC).copy(alpha = (pulseAlpha + 0.6f).coerceAtMost(1f))))
                    }
                }
            }
        }
    }
}

// --- HOME SCREEN SECTION ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenSection(
    notes: List<NoteEntity>,
    favorites: List<NoteEntity>,
    trashCount: Int,
    folders: List<FolderEntity>,
    selectedFolderId: Int,
    selectedFolderTitle: String,
    onFolderSelected: (Int, String) -> Unit,
    onAddFolderClick: () -> Unit,
    onNoteSelect: (NoteEntity) -> Unit,
    onFavoriteToggle: (NoteEntity) -> Unit,
    onNoteCopy: (NoteEntity) -> Unit,
    onAddNoteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFontsClick: () -> Unit,
    onFileExplorerClick: () -> Unit = {},
    onAppFeaturesClick: () -> Unit,
    onDeveloperContactClick: () -> Unit,
    viewModel: NotepadViewModel
) {
    val scrollState = rememberScrollState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }

    // Multi-Select & Batch export states
    var isSelectionModeActive by remember { mutableStateOf(false) }
    val selectedNotesForBulk = remember { androidx.compose.runtime.mutableStateListOf<NoteEntity>() }

    fun handleBulkExport(context: android.content.Context) {
        if (selectedNotesForBulk.isEmpty()) {
            Toast.makeText(context, "No notes selected to export", Toast.LENGTH_SHORT).show()
            return
        }

        val combinedContent = buildString {
            append("📚 NOTEPAD BATCH EXPORT\n")
            append("Generated on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            append("=========================================\n\n")

            selectedNotesForBulk.forEachIndexed { index, note ->
                append("📓 NOTE #${index + 1}: ${note.title}\n")
                val formattedTime = android.text.format.DateFormat.format("MMM dd, yyyy - hh:mm a", note.updatedAt).toString()
                append("Updated At: $formattedTime\n")
                append("-----------------------------------------\n")
                append(note.content)
                append("\n\n=========================================\n\n")
            }
        }

        val filename = "BulkExport_${System.currentTimeMillis()}"
        val success = com.example.util.NotepadExporter.saveAsTxt(context, filename, combinedContent)
        
        if (success) {
            Toast.makeText(context, "Exported ${selectedNotesForBulk.size} notes to Downloads!", Toast.LENGTH_LONG).show()
            
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, combinedContent)
                type = "text/plain"
            }
            val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Batch Notes:")
            context.startActivity(shareIntent)

            isSelectionModeActive = false
            selectedNotesForBulk.clear()
        }
    }

    // Filtered notes depending on selection and search query
    val filteredNotes = remember(notes, favorites, selectedFolderId, searchQuery) {
        val baseList = when (selectedFolderId) {
            -100 -> notes // All General Notes
            -2 -> notes.filter { it.type == "IMPORTANT" || it.themeType == "CHERRY" } // virtual important
            -3 -> notes.filter { it.type == "API" } // apis
            -4 -> notes.filter { it.type == "CODE" } // codes
            -5 -> notes.filter { it.type == "VIDEO" } // links
            -6 -> favorites // favorites
            else -> notes.filter { it.folderId == selectedFolderId }
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(290.dp),
                drawerContainerColor = Color(0xFFF8FAFC),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            // --- PREMIUM ANIMATED TYPOGRAPHY FOR AU NOTES ---
                            val infiniteTransition = rememberInfiniteTransition(label = "AuNotesHeaderAnim")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.98f,
                                targetValue = 1.02f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "HeaderScale"
                            )
                            val shimmerOffset by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1000f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "HeaderShimmer"
                            )
                            val gradient = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF3B82F6), // Indigo Blue
                                    Color(0xFF10B981), // Emerald Green
                                    Color(0xFF8B5CF6), // Royal Purple
                                    Color(0xFFEC4899), // Hot Pink
                                    Color(0xFF3B82F6)  // Indigo Blue
                                ),
                                start = Offset(shimmerOffset, 0f),
                                end = Offset(shimmerOffset + 400f, 400f)
                            )
                            Text(
                                text = "AU NOTES",
                                style = TextStyle(
                                    brush = gradient,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color(0xFF6366F1).copy(alpha = 0.35f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 10f
                                    )
                                ),
                                modifier = Modifier.graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Your Premium Workspace",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "🚀 APPLICATION WORKSPACE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // --- 1. NAME GENERATOR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFAF5FF))
                            .border(1.dp, Color(0xFFE9D5FF), RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                onFontsClick()
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔤", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Name Generator",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. STORAGE FILE EXPLORER ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFFBEB))
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                onFileExplorerClick()
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📁", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Storage File Explorer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 3. ALL FEATURES OF APP ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEFF6FF))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                onAppFeaturesClick()
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "All Features of App",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. DEVELOPER SOCIALS / CONTACT ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0FDF4))
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                onDeveloperContactClick()
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤝", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Developer Contact",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "📞 DIRECT CONTACT CHANNELS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val context = LocalContext.current

                    // Push footer to bottom
                    Spacer(modifier = Modifier.weight(1f))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Version 1.2.8 • Premium Workspace",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            // Upper Glassmorphism Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, "Open side drawer", tint = Color(0xFF1E293B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // --- PREMIUM ANIMATED TYPOGRAPHY FOR AU NOTES MAIN SCREEN ---
                    val topInfiniteTransition = rememberInfiniteTransition(label = "AuNotesTopAnim")
                    val topScale by topInfiniteTransition.animateFloat(
                        initialValue = 0.98f,
                        targetValue = 1.02f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "TopHeaderScale"
                    )
                    val topShimmerOffset by topInfiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "TopHeaderShimmer"
                    )
                    val topGradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2563EB), // Royal Blue
                            Color(0xFF10B981), // Emerald Green
                            Color(0xFFFF5E7E), // Coral Red
                            Color(0xFF8B5CF6), // Indigo Purple
                            Color(0xFF2563EB)  // Royal Blue
                        ),
                        start = Offset(topShimmerOffset, 0f),
                        end = Offset(topShimmerOffset + 400f, 400f)
                    )
                    Text(
                        text = "AU NOTES",
                        style = TextStyle(
                            brush = topGradient,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color(0xFF6366F1).copy(alpha = 0.35f),
                                offset = Offset(0f, 2f),
                                blurRadius = 10f
                            )
                        ),
                        modifier = Modifier.graphicsLayer(
                            scaleX = topScale,
                            scaleY = topScale
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Text(
                            text = if (viewModel.isDarkMode) "☀️" else "🌙",
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, "Config preferences", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Premium Glassmorphic Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search notes by title or content...",
                        fontSize = 13.sp,
                        color = if (viewModel.isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Notes",
                        tint = if (viewModel.isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search",
                                tint = if (viewModel.isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_bar_input")
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (viewModel.isDarkMode) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.9f))
                    .border(
                        BorderStroke(
                            1.dp,
                            if (viewModel.isDarkMode) Color.White.copy(alpha = 0.2f) else Color(0xFFE2E8F0)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                textStyle = TextStyle(
                    color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B),
                    fontSize = 14.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B),
                    unfocusedTextColor = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Category & Folder Capsules Filter Bar next to All Logs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickFilterCapsule(
                    title = "All Logs",
                    count = notes.size,
                    isSelected = selectedFolderId == -100,
                    color = Color(0xFF2563EB),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-100, "All General Notes") }
                )
                QuickFilterCapsule(
                    title = "Favorites",
                    count = favorites.size,
                    isSelected = selectedFolderId == -6,
                    color = Color(0xFFD97706),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-6, "Favorites") }
                )
                QuickFilterCapsule(
                    title = "🗝 APIs Keys",
                    count = notes.count { it.type == "API" },
                    isSelected = selectedFolderId == -3,
                    color = Color(0xFFEF4444),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-3, "API Keys") }
                )
                QuickFilterCapsule(
                    title = "</> Code",
                    count = notes.count { it.type == "CODE" },
                    isSelected = selectedFolderId == -4,
                    color = Color(0xFF3B82F6),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-4, "Programming Code") }
                )
                QuickFilterCapsule(
                    title = "www Bookmarks",
                    count = notes.count { it.type == "VIDEO" },
                    isSelected = selectedFolderId == -5,
                    color = Color(0xFF10B981),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-5, "Video Links") }
                )
                QuickFilterCapsule(
                    title = "! Important",
                    count = notes.count { it.type == "IMPORTANT" || it.themeType == "CHERRY" },
                    isSelected = selectedFolderId == -2,
                    color = Color(0xFFF59E0B),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = { onFolderSelected(-2, "Important Notes") }
                )

                // Dynamically loaded notebooks folders
                folders.forEach { folder ->
                    QuickFilterCapsule(
                        title = folder.name,
                        count = notes.count { it.folderId == folder.id },
                        isSelected = selectedFolderId == folder.id,
                        color = Color(android.graphics.Color.parseColor(folder.colorHex)),
                        isDarkMode = viewModel.isDarkMode,
                        onClick = { onFolderSelected(folder.id, folder.name) }
                    )
                }

                // Inline quick creation folder trigger capsule
                QuickFilterCapsule(
                    title = "+ Add Folder",
                    count = folders.size,
                    isSelected = false,
                    color = Color(0xFF64748B),
                    isDarkMode = viewModel.isDarkMode,
                    onClick = onAddFolderClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Display Notepad Entries List shown immediately
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isSelectionModeActive) {
                        Text(
                            text = selectedFolderTitle.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.1.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${filteredNotes.size} ${if (filteredNotes.size == 1) "note" else "notes"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color(0xFFCBD5E1)))
                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Bulk Select",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier
                                    .clickable {
                                        isSelectionModeActive = true
                                        selectedNotesForBulk.clear()
                                        com.example.util.CopySoundPlayer.playClickSound(context)
                                    }
                            )
                        }
                    } else {
                        Text(
                            text = "SELECTED: ${selectedNotesForBulk.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2563EB),
                            letterSpacing = 1.1.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Export (${selectedNotesForBulk.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedNotesForBulk.isNotEmpty()) Color(0xFF10B981) else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .clickable(enabled = selectedNotesForBulk.isNotEmpty()) {
                                        handleBulkExport(context)
                                        com.example.util.CopySoundPlayer.playClickSound(context)
                                    }
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color(0xFFCBD5E1)))
                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Cancel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier
                                    .clickable {
                                        isSelectionModeActive = false
                                        selectedNotesForBulk.clear()
                                        com.example.util.CopySoundPlayer.playClickSound(context)
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notes discovered in ${selectedFolderTitle}.\nUse the write button + below to add one!",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 85.dp) // Leave safety region for floating action button
                    ) {
                        items(filteredNotes) { note ->
                            val isNoteSelected = selectedNotesForBulk.contains(note)
                            NoteListRowItem(
                                note = note,
                                onSelect = { onNoteSelect(note) },
                                onFavoriteClick = { onFavoriteToggle(note) },
                                onCopyClick = { onNoteCopy(note) },
                                viewModel = viewModel,
                                isSelectionModeActive = isSelectionModeActive,
                                isSelected = isNoteSelected,
                                onToggleSelect = {
                                    if (isNoteSelected) {
                                        selectedNotesForBulk.remove(note)
                                    } else {
                                        selectedNotesForBulk.add(note)
                                    }
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button for prompt entries
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            FloatingActionButton(
                onClick = onAddNoteClick,
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(62.dp)
            ) {
                Icon(Icons.Default.Add, "Write new note", modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun DrawerNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int? = null,
    badgeColor: Color = Color(0xFF2563EB)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF2563EB).copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount != null && badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF2563EB) else badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuickFilterCapsule(
    title: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    isDarkMode: Boolean = false,
    onClick: () -> Unit
) {
    val bgCol = when {
        isSelected -> color
        isDarkMode -> Color(0xFF1E293B)
        else -> Color(0xFFE2E8F0)
    }
    val textCol = when {
        isSelected -> Color.White
        isDarkMode -> Color(0xFFFFFFFF)
        else -> Color(0xFF0F172A)
    }
    val badgeBg = when {
        isSelected -> Color.White
        isDarkMode -> Color(0xFF334155)
        else -> Color(0xFFCBD5E1)
    }
    val badgeTextCol = when {
        isSelected -> color
        isDarkMode -> Color(0xFFF1F5F9)
        else -> Color(0xFF0F172A)
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgCol)
            .border(
                1.dp,
                if (isSelected) Color.White.copy(alpha = 0.2f)
                else if (isDarkMode) Color(0xFF334155)
                else Color(0xFFCBD5E1),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = textCol, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = count.toString(), color = badgeTextCol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CategoryGridBox(
    title: String,
    count: Int,
    symbol: String,
    desc: String,
    color: Color,
    borderColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(115.dp)
            .border(
                2.dp,
                if (isSelected) Color(0xFF2563EB) else borderColor,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Style elements based on character designs, avoiding emojis
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = symbol,
                        color = Color(0xFF1E293B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "$count Notes",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = title,
                    color = Color(0xFF1E293B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = Color(0xFF64748B),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- Note List Row item featuring side-by-side COPY button ---
@Composable
fun NoteListRowItem(
    note: NoteEntity,
    onSelect: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCopyClick: () -> Unit,
    viewModel: NotepadViewModel,
    isSelectionModeActive: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    // Determine background color based on Note Theme properties
    val containerBg = when (note.themeType) {
        "MINT_GLASS" -> Color(0xFFE6F4EA)
        "SUNSET" -> Color(0xFFFFF4E5)
        "CHERRY" -> Color(0xFFFCE8E6)
        "NEON_BLUE" -> Color(0xFFE8F0FE)
        else -> Color.White // Elegant white card
    }

    val glowBorderColor = when (note.themeType) {
        "MINT_GLASS" -> Color(0xFF34C759).copy(alpha = 0.3f)
        "SUNSET" -> Color(0xFFFF9500).copy(alpha = 0.3f)
        "CHERRY" -> Color(0xFFFF2D55).copy(alpha = 0.3f)
        "NEON_BLUE" -> Color(0xFF007AFF).copy(alpha = 0.3f)
        else -> Color(0xFFE2E8F0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .border(1.5.dp, if (isSelected) Color(0xFF2563EB) else glowBorderColor, RoundedCornerShape(16.dp))
            .clickable {
                if (isSelectionModeActive) {
                    onToggleSelect()
                } else {
                    onSelect()
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionModeActive) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        "Locked record",
                        tint = Color(0xFFFF9500),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp)
                    )
                }

                Text(
                    text = note.title,
                    color = Color(0xFF1E293B),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Blur logic for API keys or Secrets
            val isBlurActive = note.type == "API" && viewModel.isBlurApisEnabled && note.isLocked
            val bodyText = if (isBlurActive) {
                "• • • • • • • • • • • • • • • • • •"
            } else {
                stripAllTags(note.content).replace("\n", " ")
            }

            Text(
                text = bodyText,
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isBlurActive) Modifier.blur(2.dp) else Modifier
            )

            if (note.reminderTime != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Refresh,
                        "Reminder active",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Alarm configured",
                        fontSize = 10.sp,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // --- ROUNDED RECTANGLE COPY BUTTON NEXT TO ROW SNIPPET ---
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F5F9))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .clickable(onClick = onCopyClick)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                "Copy",
                color = Color(0xFF1E293B),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Star indicator
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Starred note",
                tint = if (note.isFavorite) Color(0xFFFFCC00) else Color(0xFFCBD5E1),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

sealed class NotePart {
    data class NormalText(val text: String) : NotePart()
    data class CopyBox(val text: String) : NotePart()
}

fun parseNoteParts(content: String): List<NotePart> {
    val parts = mutableListOf<NotePart>()
    var currentIndex = 0
    val length = content.length
    
    while (currentIndex < length) {
        val openTagIndex = content.indexOf("<copy>", currentIndex)
        if (openTagIndex == -1) {
            parts.add(NotePart.NormalText(content.substring(currentIndex)))
            break
        }
        
        if (openTagIndex > currentIndex) {
            parts.add(NotePart.NormalText(content.substring(currentIndex, openTagIndex)))
        }
        
        val closeTagIndex = content.indexOf("</copy>", openTagIndex + 6)
        if (closeTagIndex == -1) {
            parts.add(NotePart.CopyBox(content.substring(openTagIndex + 6)))
            break
        }
        
        parts.add(NotePart.CopyBox(content.substring(openTagIndex + 6, closeTagIndex)))
        currentIndex = closeTagIndex + 7
    }
    
    return parts
}

fun isCursorInCopyBlock(text: String, cursor: Int): Boolean {
    val lastOpen = text.substring(0, cursor).lastIndexOf("<copy>")
    if (lastOpen == -1) return false
    val lastClose = text.substring(0, cursor).lastIndexOf("</copy>")
    return lastOpen > lastClose
}

fun getCurrentCopyBlockText(text: String, cursor: Int): String {
    val lastOpen = text.substring(0, cursor).lastIndexOf("<copy>")
    if (lastOpen == -1) return ""
    val lastClose = text.substring(0, cursor).lastIndexOf("</copy>")
    if (lastOpen <= lastClose) return ""
    
    val nextClose = text.indexOf("</copy>", lastOpen)
    return if (nextClose != -1) {
        text.substring(lastOpen + 6, nextClose)
    } else {
        text.substring(lastOpen + 6)
    }
}

fun stripAllTags(content: String): String {
    return content
        .replace("<copy>", "")
        .replace("</copy>", "")
        .replace("<b>", "")
        .replace("</b>", "")
        .replace("<i>", "")
        .replace("</i>", "")
        .replace("<u>", "")
        .replace("</u>", "")
        .replace("<g>", "")
        .replace("</g>", "")
}

@Composable
fun NoteViewScreenSection(
    note: NoteEntity?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: NotepadViewModel
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    if (note == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No note selected", color = Color.Gray)
        }
        return
    }

    var isDeleteConfirmOpen by remember { mutableStateOf(false) }
    var isExportOpen by remember { mutableStateOf(false) }
    var exportFilename by remember { mutableStateOf(if (note.title.isBlank()) "NoteExport" else note.title) }
    var customFileExt by remember { mutableStateOf("txt") }

    val themeGradient = when (note.themeType) {
        "MINT_GLASS" -> listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))
        "SUNSET" -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
        "CHERRY" -> listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2))
        "NEON_BLUE" -> listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
        else -> listOf(Color(0xFFF2F2F7), Color(0xFFE5E5EA))
    }

    val glowAccent = when (note.themeType) {
        "MINT_GLASS" -> Color(0xFF2E7D32)
        "SUNSET" -> Color(0xFFE65100)
        "CHERRY" -> Color(0xFFC62828)
        "NEON_BLUE" -> Color(0xFF1565C0)
        else -> Color(0xFF94A3B8)
    }

    val isDark = viewModel.isDarkMode
    val viewBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(viewBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = textColor)
                }

                // Header title (Typography styled)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Note",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "READ MODE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        letterSpacing = 1.sp
                    )
                }

                // Edit pencil button
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit note", tint = Color(0xFF2563EB))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Viewer Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Note Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = note.title,
                            color = textColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (note.isLocked) {
                            Text("🔒 Encrypted", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Date & Folder info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formattedDate = android.text.format.DateFormat.format("MMM dd, yyyy - hh:mm a", note.updatedAt).toString()
                        Text(
                            text = formattedDate,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )

                        // Lock indicator if applicable
                        if (note.isLocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, "Locked", tint = Color(0xFFFF9500), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Locked", color = Color(0xFFFF9500), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Contextual Action Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Lock Button
                        TextButton(
                            onClick = {
                                viewModel.toggleLock(note)
                                com.example.util.CopySoundPlayer.playClickSound(context)
                                Toast.makeText(context, if (note.isLocked) "Note Unlocked!" else "Note Secured & Locked!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock State Toggle",
                                    tint = if (note.isLocked) Color(0xFFFF9500) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (note.isLocked) "Unlock" else "Lock",
                                    color = if (note.isLocked) Color(0xFFFF9500) else Color(0xFF1E293B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFFCBD5E1)))

                        // Export Note Button
                        TextButton(
                            onClick = {
                                isExportOpen = true
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export Note",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Export",
                                    color = Color(0xFF1E293B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFFCBD5E1)))

                        // Delete Note Button
                        TextButton(
                            onClick = {
                                isDeleteConfirmOpen = true
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Delete",
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Render attached image if exists
                    note.imagePath?.let { path ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(4.dp)
                        ) {
                            AsyncImage(
                                model = path,
                                contentDescription = "Attached Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Render other attached document/audio file if exists
                    note.filePath?.let { filePath ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📎 Attached Asset", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Render Note Parts (Rich Text & Copy Boxes)
                    val parts = parseNoteParts(note.content)
                    parts.forEach { part ->
                        when (part) {
                            is NotePart.NormalText -> {
                                if (part.text.isNotEmpty()) {
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        Text(
                                            text = SyntaxHighlighter.highlightRichText(part.text),
                                            color = Color(0xFF1E293B),
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                            is NotePart.CopyBox -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("📋", fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("COPY BOX", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                                            }
                                            Text(
                                                text = "[ COPY ]",
                                                color = Color(0xFF00FFCC),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        clipboardManager.setText(AnnotatedString(part.text))
                                                        com.example.util.CopySoundPlayer.playClickSound(context)
                                                        Toast.makeText(context, "Copied box content!", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = part.text,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation dialog
        if (isDeleteConfirmOpen) {
            AlertDialog(
                onDismissRequest = { isDeleteConfirmOpen = false },
                title = { Text("Delete Note", fontWeight = FontWeight.Bold) },
                text = { Text("Are you absolutely sure you want to delete this note? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeleteConfirmOpen = false
                            viewModel.moveNoteToTrash(note.id)
                            onBack()
                            Toast.makeText(context, "Note moved to Trash", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isDeleteConfirmOpen = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        // Export Document Dialog Panel
        if (isExportOpen) {
            val exportPrefs = context.getSharedPreferences("notepad_exports", android.content.Context.MODE_PRIVATE)
            var previousExports by remember {
                mutableStateOf(exportPrefs.getStringSet("files", emptySet())?.toList() ?: emptyList())
            }

            Dialog(onDismissRequest = { isExportOpen = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.75f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Export Document & Share", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            IconButton(onClick = { isExportOpen = false }) {
                                Icon(Icons.Default.Close, "Close Panel", tint = Color.LightGray)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))

                        // Filename Input
                        OutlinedTextField(
                            value = exportFilename,
                            onValueChange = { exportFilename = it },
                            label = { Text("Filename", color = Color.LightGray) },
                            textStyle = TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Select format to export:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick buttons for TXT, HTML, PDF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("txt", "html", "pdf").forEach { ext ->
                                Button(
                                    onClick = {
                                        val success = when (ext) {
                                            "txt" -> com.example.util.NotepadExporter.saveAsTxt(context, exportFilename, note.content)
                                            "html" -> com.example.util.NotepadExporter.saveAsHtml(context, exportFilename, note.title, note.content)
                                            "pdf" -> com.example.util.NotepadExporter.saveAsPdf(context, exportFilename, note.title, note.content)
                                            else -> false
                                        }
                                        if (success) {
                                            val fileEntry = "$exportFilename.$ext"
                                            val updated = (previousExports + fileEntry).distinct()
                                            exportPrefs.edit().putStringSet("files", updated.toSet()).apply()
                                            previousExports = updated
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF00FFCC)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(ext.uppercase(), color = Color(0xFF00FFCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Share Note Option
                        Button(
                            onClick = {
                                val sendIntent: android.content.Intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "${note.title}\n\n${note.content}")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share note via:")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Share Note Directly", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { isExportOpen = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Dismiss Panel", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

// --- NOTE EDITOR SECTION ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreenSection(
    note: NoteEntity?,
    folders: List<FolderEntity>,
    onBack: () -> Unit,
    onSave: (id: Int, title: String, content: String, folderId: Int, isFav: Boolean, themeType: String, img: String?, file: String?, rem: Long?, remTone: String?, manualType: String?) -> Unit,
    onDelete: (NoteEntity) -> Unit,
    aiSuggestions: String?,
    onGetAiProgress: (String, String) -> Unit,
    onClearSuggestions: () -> Unit,
    viewModel: NotepadViewModel
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Note States
    var id by remember { mutableStateOf(note?.id ?: 0) }
    var title by remember { mutableStateOf(note?.title ?: "") }
    
    val copyBlocksList = remember(note) {
        val list = androidx.compose.runtime.mutableStateListOf<String>()
        val text = note?.content ?: ""
        val regex = Regex("<copy>(.*?)</copy>", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(text).forEach { match ->
            val inner = match.groups[1]?.value?.trim() ?: ""
            if (inner.isNotEmpty()) {
                list.add(inner)
            }
        }
        list
    }

    var contentValue by remember(note) {
        val raw = note?.content ?: ""
        val clean = raw.replace(Regex("<copy>.*?</copy>", RegexOption.DOT_MATCHES_ALL), "").trim()
        mutableStateOf(TextFieldValue(clean))
    }
    
    val combinedContentText = remember(contentValue.text, copyBlocksList.toList()) {
        buildString {
            append(contentValue.text.trim())
            copyBlocksList.forEach { block ->
                if (block.isNotBlank()) {
                    append("\n\n<copy>\n")
                    append(block.trim())
                    append("\n</copy>")
                }
            }
        }.trim()
    }

    var selectedFid by remember { mutableStateOf(note?.folderId ?: -1) }

    // Undo / Redo Stacks & Tracking engine
    val undoStack = remember { androidx.compose.runtime.mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { androidx.compose.runtime.mutableStateListOf<TextFieldValue>() }
    var isUndoRedoAction by remember { mutableStateOf(false) }
    var lastSavedValueForUndo by remember { mutableStateOf(contentValue) }

    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            isUndoRedoAction = true
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(contentValue)
            contentValue = previous
            lastSavedValueForUndo = previous
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            isUndoRedoAction = true
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(contentValue)
            contentValue = next
            lastSavedValueForUndo = next
        }
    }

    LaunchedEffect(contentValue) {
        if (isUndoRedoAction) {
            isUndoRedoAction = false
            lastSavedValueForUndo = contentValue
            return@LaunchedEffect
        }
        
        val currentText = contentValue.text
        val oldText = lastSavedValueForUndo.text
        if (currentText != oldText) {
            val diff = Math.abs(currentText.length - oldText.length)
            val hasSpace = currentText.endsWith(" ") || currentText.endsWith("\n")
            if (diff >= 3 || hasSpace) {
                if (undoStack.size >= 50) {
                    undoStack.removeAt(0)
                }
                undoStack.add(lastSavedValueForUndo)
                redoStack.clear() // typing clears redo history
                lastSavedValueForUndo = contentValue
            }
        }
    }

    var isBoldActive by remember { mutableStateOf(false) }
    var isItalicActive by remember { mutableStateOf(false) }
    var isUnderlineActive by remember { mutableStateOf(false) }
    var isGlowActive by remember { mutableStateOf(false) }
    var selectedFontSize by remember { mutableStateOf(14) }
    var selectedFontColor by remember { mutableStateOf(Color(0xFF1E293B)) }

    // Copy Box Dialog States
    var isCopyBoxInputOpen by remember { mutableStateOf(false) }
    var copyBoxInputText by remember { mutableStateOf("") }

    fun handleStyleToggle(style: String, targetActive: Boolean) {
        val text = contentValue.text
        val start = contentValue.selection.start
        val end = contentValue.selection.end
        
        val openTag = when (style) {
            "B" -> "<b>"
            "I" -> "<i>"
            "U" -> "<u>"
            "G" -> "<g>"
            "COPY" -> "<copy>"
            else -> ""
        }
        val closeTag = when (style) {
            "B" -> "</b>"
            "I" -> "</i>"
            "U" -> "</u>"
            "G" -> "</g>"
            "COPY" -> "</copy>"
            else -> ""
        }
        
        if (start != end) {
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + openTag + selectedText + closeTag + text.substring(end)
            contentValue = TextFieldValue(
                text = newText,
                selection = androidx.compose.ui.text.TextRange(start + openTag.length + selectedText.length + closeTag.length)
            )
        } else {
            if (targetActive) {
                val newText = text.substring(0, start) + openTag + closeTag + text.substring(start)
                contentValue = TextFieldValue(
                    text = newText,
                    selection = androidx.compose.ui.text.TextRange(start + openTag.length)
                )
            } else {
                if (start <= text.length - closeTag.length && text.substring(start, start + closeTag.length) == closeTag) {
                    contentValue = TextFieldValue(
                        text = text,
                        selection = androidx.compose.ui.text.TextRange(start + closeTag.length)
                    )
                }
            }
        }
    }
    var isFavorite by remember { mutableStateOf(note?.isFavorite ?: false) }
    var themeType by remember { mutableStateOf(note?.themeType ?: "GLASS_DARK") }
    var manualTypeOverride by remember { mutableStateOf<String?>(note?.type) }

    // Alarm/Reminder states
    var isSetReminderOpen by remember { mutableStateOf(false) }
    var reminderTime by remember { mutableStateOf(note?.reminderTime) }
    var reminderTone by remember { mutableStateOf(note?.reminderTone ?: "Aurora Bells") }

    // Custom Export States
    var isExportOpen by remember { mutableStateOf(false) }
    var exportFilename by remember { mutableStateOf(if (title.isBlank()) "NoteExport" else title) }
    var customFileExt by remember { mutableStateOf("txt") }

    // Attachment helpers
    var attachmentImagePath by remember { mutableStateOf<String?>(note?.imagePath) }
    var attachmentFilePathState by remember { mutableStateOf<String?>(note?.filePath) }
    var isAttachmentTypeChooserOpen by remember { mutableStateOf(false) }
    
    // Audio Playback states
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    // Auto Save Effect (debounced typing save)
    LaunchedEffect(title, combinedContentText, selectedFid, isFavorite, themeType, attachmentImagePath, attachmentFilePathState, reminderTime, reminderTone, manualTypeOverride) {
        if (title.isNotEmpty() || combinedContentText.isNotEmpty()) {
            kotlinx.coroutines.delay(1000)
            viewModel.saveNote(
                id = id,
                title = title,
                content = combinedContentText,
                folderId = selectedFid,
                isFavorite = isFavorite,
                themeType = themeType,
                imagePath = attachmentImagePath,
                filePath = attachmentFilePathState,
                reminderTime = reminderTime,
                reminderTone = reminderTone,
                manualType = manualTypeOverride
            ) { assignedId ->
                if (id == 0 && assignedId != 0) {
                    id = assignedId // update local note ID to prevent duplicates!
                }
            }
        }
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {}
            it.release()
        }
        mediaPlayer = null
        isPlayingAudio = false
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            releaseMediaPlayer()
        }
    }

    fun togglePlayAttachedAudio(filePathUri: String) {
        try {
            if (mediaPlayer == null) {
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(context, android.net.Uri.parse(filePathUri))
                    prepare()
                    setOnCompletionListener {
                        isPlayingAudio = false
                    }
                }
                mediaPlayer = mp
            }
            if (isPlayingAudio) {
                mediaPlayer?.pause()
                isPlayingAudio = false
            } else {
                mediaPlayer?.start()
                isPlayingAudio = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Audio Playback error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Position manipulation states for media
    var imageScale by remember { mutableStateOf(1.0f) }
    var imageOffsetX by remember { mutableStateOf(0f) }
    var imageOffsetY by remember { mutableStateOf(0f) }
    var imageCropRatio by remember { mutableStateOf<Float?>(null) }

    // Inline AI Copilot Bottom Sheet state flags
    var isInlineCopilotOpen by remember { mutableStateOf(false) }
    var copilotPrompt by remember { mutableStateOf("") }
    var isCopilotLoading by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentImagePath = uri.toString()
            Toast.makeText(context, "Photo Attached! Drag/Resize options enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentFilePathState = uri.toString()
            Toast.makeText(context, "Document / Audio Clip attached successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                Toast.makeText(context, "Voice Input: $spokenText", Toast.LENGTH_SHORT).show()
                val current = contentValue.text
                val newText = if (current.isBlank()) spokenText else "$current $spokenText"
                contentValue = TextFieldValue(newText, TextRange(newText.length))
            }
        }
    }

    // Determine Theme styling container gradients
    val themeGradient = when (themeType) {
        "MINT_GLASS" -> listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))
        "SUNSET" -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
        "CHERRY" -> listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2))
        "NEON_BLUE" -> listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
        else -> listOf(Color(0xFFF2F2F7), Color(0xFFE5E5EA)) // iOS light grays
    }

    val glowAccent = when (themeType) {
        "MINT_GLASS" -> Color(0xFF2E7D32)
        "SUNSET" -> Color(0xFFE65100)
        "CHERRY" -> Color(0xFFC62828)
        "NEON_BLUE" -> Color(0xFF1565C0)
        else -> Color(0xFF94A3B8)
    }

    val isDark = viewModel.isDarkMode
    val editorBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(editorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
        // Top buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = textColor)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        handleUndo()
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    enabled = canUndo
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Undo",
                        tint = if (canUndo) textColor else Color(0xFF64748B)
                    )
                }

                IconButton(
                    onClick = {
                        handleRedo()
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    enabled = canRedo
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Redo",
                        tint = if (canRedo) textColor else Color(0xFF64748B)
                    )
                }

                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Starred toggle",
                        tint = if (isFavorite) Color(0xFFFFCC00) else Color(0xFF64748B)
                    )
                }

                IconButton(onClick = { isExportOpen = true }) {
                    Icon(Icons.Default.Share, "Export document", tint = textColor)
                }

                if (note != null && note.id != 0) {
                    IconButton(onClick = { onDelete(note) }) {
                        Icon(Icons.Default.Delete, "Move to trash", tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = {
                        onSave(id, title, combinedContentText, selectedFid, isFavorite, themeType, attachmentImagePath, attachmentFilePathState, reminderTime, reminderTone, manualTypeOverride)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Editor Form (Maximized Edge-To-Edge Canvas)
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title", fontSize = 20.sp, color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )

                // Date and Character count
                val dateFormatter = java.text.SimpleDateFormat("EEEE, MMMM dd 'at' HH:mm", java.util.Locale.getDefault())
                val currentDate = dateFormatter.format(java.util.Date())
                Text(
                    text = "$currentDate  |  ${contentValue.text.length} characters",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE2E8F0))
                        .padding(vertical = 4.dp)
                )

                // Folders Assign Dropdown (Only if folders exist, and without Unsorted)
                if (folders.isNotEmpty()) {
                    // Ensure a valid default is selected if not already
                    LaunchedEffect(folders, selectedFid) {
                        if (selectedFid == -1 && folders.isNotEmpty()) {
                            selectedFid = folders.first().id
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Category Folder:", color = Color(0xFF64748B), fontSize = 12.sp)
                        
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable {
                                    val fArray = folders.map { it.id }
                                    if (fArray.isNotEmpty()) {
                                        val nextIdx = (fArray.indexOf(selectedFid) + 1) % fArray.size
                                        selectedFid = fArray[nextIdx]
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentFolderName = folders.find { it.id == selectedFid }?.name ?: folders.firstOrNull()?.name ?: ""
                            Text(currentFolderName, color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF1E293B), modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Dynamic Type/Mode Selector
                val detectedType = AutoSortDetector.detectType(title, contentValue.text)
                Text(
                    text = "Mode/Classification Mode:",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val types = listOf(
                        Triple("NORMAL", "📝 Normal", Color(0xFF64748B)),
                        Triple("API", "🗝 API Key", Color(0xFFFF9500)),
                        Triple("CODE", "💻 Code", Color(0xFF34C759)),
                        Triple("VIDEO", "🎥 Video", Color(0xFF007AFF)),
                        Triple("IMPORTANT", "🔥 Important", Color(0xFFFF2D55))
                    )
                    val selectedType = manualTypeOverride ?: detectedType
                    types.forEach { (typeKey, label, color) ->
                        val isSelected = selectedType == typeKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) color.copy(alpha = 0.15f) else Color(0xFFF1F5F9))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) color else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    manualTypeOverride = typeKey
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    color = if (isSelected) color else Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (manualTypeOverride == null && typeKey == detectedType) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Attachment Preview if present
                if (attachmentImagePath != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📐 Adjustable & Crop Image Container", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                Row {
                                    TextButton(onClick = {
                                        imageScale = 1.0f
                                        imageOffsetX = 0f
                                        imageOffsetY = 0f
                                        imageCropRatio = null
                                    }) {
                                        Text("Reset Layout", fontSize = 10.sp, color = Color(0xFF3B82F6))
                                    }
                                    IconButton(
                                        onClick = { attachmentImagePath = null },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Remove Photo", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            
                            // Image Preview Canvas (Supports dynamic crop aspect clipping!)
                            val boxModifier = if (imageCropRatio != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(imageCropRatio!!)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE2E8F0))
                            }

                            Box(
                                modifier = boxModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = attachmentImagePath,
                                    contentDescription = "Interactive photo preview",
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                imageScale = (imageScale * zoom).coerceIn(0.5f, 4.0f)
                                                imageOffsetX += pan.x
                                                imageOffsetY += pan.y
                                            }
                                        }
                                        .graphicsLayer(
                                            scaleX = imageScale,
                                            scaleY = imageScale,
                                            translationX = imageOffsetX,
                                            translationY = imageOffsetY
                                        )
                                        .fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Interactive Crop Selector
                            Text("✂️ Crop / Aspect Ratio Preset:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val crops = listOf(
                                    "Original" to null,
                                    "1:1 Square" to 1.0f,
                                    "16:9 Wide" to 1.777f,
                                    "4:3 Photo" to 1.333f
                                )
                                crops.forEach { (label, ratio) ->
                                    val isSelected = imageCropRatio == ratio
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color(0xFF2563EB) else Color(0xFFF1F5F9))
                                            .clickable { imageCropRatio = ratio }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFF475569)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive controls
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Zoom / Size:", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(70.dp))
                                    Slider(
                                        value = imageScale,
                                        onValueChange = { imageScale = it },
                                        valueRange = 0.5f..3.0f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6))
                                    )
                                    Text(String.format("%.1fx", imageScale), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Position X:", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(70.dp))
                                    Slider(
                                        value = imageOffsetX,
                                        onValueChange = { imageOffsetX = it },
                                        valueRange = -300f..300f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                                    )
                                    Text(String.format("%.0fp", imageOffsetX), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Position Y:", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(70.dp))
                                    Slider(
                                        value = imageOffsetY,
                                        onValueChange = { imageOffsetY = it },
                                        valueRange = -300f..300f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                                    )
                                    Text(String.format("%.0fp", imageOffsetY), fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.width(30.dp))
                                }
                            }
                        }
                    }
                }

                // Document / Audio attachment layout
                if (attachmentFilePathState != null) {
                    val filePathStr = attachmentFilePathState ?: ""
                    val isAudio = filePathStr.contains("audio", ignoreCase = true) || filePathStr.contains(".mp3", ignoreCase = true) || filePathStr.contains(".m4a", ignoreCase = true)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isAudio) {
                                    IconButton(
                                        onClick = { togglePlayAttachedAudio(filePathStr) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF2563EB), CircleShape)
                                    ) {
                                        Text(
                                            text = if (isPlayingAudio) "⏸" else "▶",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Attached File Info",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isAudio) "🎵 Attached Audio Clip" else "📄 Attached Document File",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = filePathStr.split("/").last(),
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569),
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = { attachmentFilePathState = null }) {
                                Icon(Icons.Default.Delete, "Remove attachment", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Textsuggestions box inside editor: "ab mujhse nahi ho raha" assistant trigger
                if (aiSuggestions != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("AI Suggested Continuation (Tap outer card to append):", fontSize = 10.sp, color = Color(0xFF1E40AF), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    text = "📋 Copy",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(aiSuggestions))
                                            com.example.util.CopySoundPlayer.playClickSound(context)
                                            Toast.makeText(context, "Copied suggestion!", Toast.LENGTH_SHORT).show()
                                        }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear",
                                    fontSize = 11.sp,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onClearSuggestions() }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(aiSuggestions, color = Color(0xFF1E293B), fontSize = 12.sp, modifier = Modifier.clickable {
                                    // Make tapping text also append suggestion for complete convenience!
                                    val currentText = contentValue.text
                                    val isEndSpace = currentText.endsWith(" ") || currentText.isEmpty()
                                    val spacePrefix = if (isEndSpace) "" else " "
                                    val textToInsert = spacePrefix + aiSuggestions
                                    val currentCursor = contentValue.selection.start
                                    val newText = currentText.substring(0, currentCursor) + textToInsert + currentText.substring(currentCursor)
                                    contentValue = TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(currentCursor + textToInsert.length)
                                    )
                                    onClearSuggestions()
                                })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Notepad Body (Supports coding syntax highlighting!)
                val textStyle = TextStyle(
                    color = selectedFontColor,
                    fontSize = selectedFontSize.sp,
                    fontFamily = if (detectedType == "CODE") FontFamily.Monospace else FontFamily.Default,
                    lineHeight = (selectedFontSize + 4).sp
                )

                val isCursorInsideCopy = remember(contentValue.text, contentValue.selection.start) {
                    isCursorInCopyBlock(contentValue.text, contentValue.selection.start)
                }
                if (isCursorInsideCopy) {
                    val currentCopyText = getCurrentCopyBlockText(contentValue.text, contentValue.selection.start)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📋", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE COPY BOX PREVIEW",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FFCC)
                                )
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(currentCopyText))
                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                    Toast.makeText(context, "Copied current box text!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Copy", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (currentCopyText.isNotEmpty()) {
                            Text(
                                text = currentCopyText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
                            )
                        }
                    }
                }

                // Highlighting implementation with manual caching state wrapping for zero-lag typing
                val isHighlightEnabled = viewModel.isSyntaxHighlightingEnabled
                val isCode = detectedType == "CODE"
                val visualTransformer = remember(isHighlightEnabled, isCode) {
                    object : androidx.compose.ui.text.input.VisualTransformation {
                        private var lastText: String? = null
                        private var lastResult: androidx.compose.ui.text.input.TransformedText? = null

                        override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
                            val rawText = text.text
                            val cached = lastResult
                            if (rawText == lastText && cached != null) {
                                return cached
                            }
                            val formatted = if (isHighlightEnabled && isCode) {
                                SyntaxHighlighter.highlightCode(rawText, true)
                            } else {
                                SyntaxHighlighter.highlightRichText(rawText, isHighlightEnabled)
                            }
                            val result = androidx.compose.ui.text.input.TransformedText(formatted, androidx.compose.ui.text.input.OffsetMapping.Identity)
                            lastText = rawText
                            lastResult = result
                            return result
                        }
                    }
                }

                OutlinedTextField(
                    value = contentValue,
                    onValueChange = { contentValue = it },
                    placeholder = { Text("Write your APIs, Code lines, links or personal diaries here...", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = textStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B)
                    ),
                    visualTransformation = visualTransformer
                )

                // --- DEDICATED CLIPBOARD COPY BOXES SECTION (PRISTINE TAG-FREE EDITING) ---
                if (copyBlocksList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "📋 ACTIVE CLIPBOARD COPY BOXES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    copyBlocksList.forEachIndexed { idx, blockText ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📦", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Clipboard Box #${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                    IconButton(
                                        onClick = { copyBlocksList.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Box",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = blockText,
                                    onValueChange = { copyBlocksList[idx] = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp),
                                    textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1E293B)),
                                    placeholder = { Text("Type copyable text smoothly here...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFE2E8F0),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Inline AI Copilot Bottom Sheet / Card Dialog ---
        if (isInlineCopilotOpen) {
            Dialog(onDismissRequest = { isInlineCopilotOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪄 AI Copilot Draft", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            IconButton(onClick = { isInlineCopilotOpen = false }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Instruct the AI to draft paragraphs, complete codes, create tables, or generate lists here:", fontSize = 11.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(12.dp))

                        val coroutineScope = rememberCoroutineScope()
                        OutlinedTextField(
                            value = copilotPrompt,
                            onValueChange = { copilotPrompt = it },
                            placeholder = { Text("e.g., Write a 3-column table comparing PostgreSQL and Room...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF1E293B)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (copilotPrompt.isNotBlank()) {
                                    isCopilotLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val result = viewModel.fetchCopilotDraft(copilotPrompt, title, contentValue.text)
                                            val currentText = contentValue.text
                                            val spacePrefix = "\n\n"
                                            val textToInsert = spacePrefix + result + "\n"
                                            val currentCursor = contentValue.selection.start
                                            val newText = currentText.substring(0, currentCursor) + textToInsert + currentText.substring(currentCursor)
                                            contentValue = TextFieldValue(
                                                text = newText,
                                                selection = androidx.compose.ui.text.TextRange(currentCursor + textToInsert.length)
                                            )
                                            isCopilotLoading = false
                                            isInlineCopilotOpen = false
                                            copilotPrompt = ""
                                        } catch (e: Exception) {
                                            isCopilotLoading = false
                                            Toast.makeText(context, "AI assist offline, check API Keys!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            enabled = !isCopilotLoading
                        ) {
                            if (isCopilotLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generative drafting...", fontSize = 12.sp)
                                }
                            } else {
                                Text("🪄 Generate and Insert directly", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

    if (isAttachmentTypeChooserOpen) {
        Dialog(onDismissRequest = { isAttachmentTypeChooserOpen = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📎 Choose Attachment Type",
                        color = Color(0xFF1E293B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import photos or generic documents/audio clips from internal storage",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isAttachmentTypeChooserOpen = false
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📸 Select Photo (Image / Screenshot)", color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isAttachmentTypeChooserOpen = false
                            documentLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📄 Attach Document (PDF, Audio, File)", color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isAttachmentTypeChooserOpen = false
                            isInlineCopilotOpen = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🪄 AI Copilot (Gen Drafts & Coding)", color = Color.White, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { isAttachmentTypeChooserOpen = false }
                    ) {
                        Text("Cancel", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        }
    }

        Spacer(modifier = Modifier.height(6.dp))

        // --- UNIFIED BOTTOM FORMATTING & UTILITIES TOOLBAR ---
        var isTextStylesSheetOpen by remember { mutableStateOf(false) }
        var isParagraphStylesSheetOpen by remember { mutableStateOf(false) }
        var isMoreMenuOpen by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (viewModel.isDarkMode) Color(0xFF1E293B) else Color.White
            ),
            border = BorderStroke(1.dp, if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Text Style Sheet Trigger (Aa)
                IconButton(
                    onClick = {
                        isTextStylesSheetOpen = true
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "Aa",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                    )
                }

                // 2. Paragraph Style Sheet Trigger (≡)
                IconButton(
                    onClick = {
                        isParagraphStylesSheetOpen = true
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "≡",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                    )
                }

                // 3. Checkbox Item Toggle (☑)
                IconButton(
                    onClick = {
                        val current = contentValue.text
                        val cursor = contentValue.selection.start
                        val newText = if (cursor >= 0 && cursor <= current.length) {
                            current.substring(0, cursor) + "\n[ ] " + current.substring(cursor)
                        } else {
                            current + "\n[ ] "
                        }
                        contentValue = TextFieldValue(text = newText, selection = TextRange(newText.length))
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "☑",
                        fontSize = 18.sp,
                        color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                    )
                }

                // 4. Alarm / Reminder (⏰)
                IconButton(
                    onClick = {
                        isSetReminderOpen = true
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "⏰",
                        fontSize = 18.sp
                    )
                }

                // 5. Image Attachment (🖼)
                IconButton(
                    onClick = {
                        galleryLauncher.launch("image/*")
                        com.example.util.CopySoundPlayer.playClickSound(context)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "🖼",
                        fontSize = 18.sp
                    )
                }

                // 6. Voice AU Bot Command (🎙)
                IconButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak command or dictation for Au Bot...")
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice recognition not supported on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "🎙",
                        fontSize = 18.sp
                    )
                }

                // 7. More Options Menu (⋮)
                Box {
                    IconButton(
                        onClick = { isMoreMenuOpen = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More tools",
                            tint = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                        )
                    }

                    DropdownMenu(
                        expanded = isMoreMenuOpen,
                        onDismissRequest = { isMoreMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📊 Insert Table") },
                            onClick = {
                                isMoreMenuOpen = false
                                val tableSnippet = "\n\n| Header 1 | Header 2 |\n| --- | --- |\n| Row 1 Col 1 | Row 1 Col 2 |\n| Row 2 Col 1 | Row 2 Col 2 |\n\n"
                                val newText = contentValue.text + tableSnippet
                                contentValue = TextFieldValue(newText, TextRange(newText.length))
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("📋 Insert Copy Box") },
                            onClick = {
                                isMoreMenuOpen = false
                                copyBlocksList.add("")
                                Toast.makeText(context, "New Clipboard Box Created!", Toast.LENGTH_SHORT).show()
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("✨ AI Writing Helper") },
                            onClick = {
                                isMoreMenuOpen = false
                                onGetAiProgress(title, contentValue.text)
                            }
                        )
                    }
                }
            }
        }

        // --- MODAL BOTTOM SHEET 1: TEXT STYLES (Aa) ---
        if (isTextStylesSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isTextStylesSheetOpen = false },
                containerColor = if (viewModel.isDarkMode) Color(0xFF1E293B) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Text styles",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                        )
                        IconButton(onClick = { isTextStylesSheetOpen = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // B, I, U, S, G Formatting Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bold
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isBoldActive) Color(0xFF2563EB) else (if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                                .clickable {
                                    isBoldActive = !isBoldActive
                                    handleStyleToggle("B", isBoldActive)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("B", fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (isBoldActive) Color.White else (if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)))
                        }

                        // Italic
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isItalicActive) Color(0xFF2563EB) else (if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                                .clickable {
                                    isItalicActive = !isItalicActive
                                    handleStyleToggle("I", isItalicActive)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isItalicActive) Color.White else (if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)))
                        }

                        // Underline
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isUnderlineActive) Color(0xFF2563EB) else (if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                                .clickable {
                                    isUnderlineActive = !isUnderlineActive
                                    handleStyleToggle("U", isUnderlineActive)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("U", textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (isUnderlineActive) Color.White else (if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)))
                        }

                        // Strikethrough
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9))
                                .clickable {
                                    handleStyleToggle("S", true)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S", textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B))
                        }

                        // Glow
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isGlowActive) Color(0xFF10B981) else (if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                                .clickable {
                                    isGlowActive = !isGlowActive
                                    handleStyleToggle("G", isGlowActive)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🖋", fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Font Size",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val fontSizes = listOf(12, 14, 16, 18, 20, 24, 32, 40)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(fontSizes) { sz ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedFontSize == sz) Color(0xFF2563EB) else (if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)))
                                    .clickable {
                                        selectedFontSize = sz
                                        val text = contentValue.text
                                        val start = contentValue.selection.start
                                        val end = contentValue.selection.end
                                        val openTag = "<size sp=\"$sz\">"
                                        val closeTag = "</size>"
                                        if (start != end) {
                                            val selected = text.substring(start, end)
                                            val newText = text.substring(0, start) + openTag + selected + closeTag + text.substring(end)
                                            contentValue = TextFieldValue(newText, TextRange(start + openTag.length + selected.length + closeTag.length))
                                        } else {
                                            val newText = text.substring(0, start) + openTag + closeTag + text.substring(start)
                                            contentValue = TextFieldValue(newText, TextRange(start + openTag.length))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$sz",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedFontSize == sz) Color.White else (if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Font Color",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val colorPalette = listOf(
                        Color(0xFF1E293B),
                        Color(0xFFFFFFFF),
                        Color(0xFFEF4444),
                        Color(0xFFF97316),
                        Color(0xFFEAB308),
                        Color(0xFF22C55E),
                        Color(0xFF3B82F6),
                        Color(0xFFA855F7)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorPalette.forEach { color ->
                            val hexStr = String.format("#%06X", (0xFFFFFF and color.toArgb()))
                            val isSelected = selectedFontColor == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedFontColor = color
                                        val text = contentValue.text
                                        val start = contentValue.selection.start
                                        val end = contentValue.selection.end
                                        val openTag = "<color hex=\"$hexStr\">"
                                        val closeTag = "</color>"
                                        if (start != end) {
                                            val selected = text.substring(start, end)
                                            val newText = text.substring(0, start) + openTag + selected + closeTag + text.substring(end)
                                            contentValue = TextFieldValue(newText, TextRange(start + openTag.length + selected.length + closeTag.length))
                                        } else {
                                            val newText = text.substring(0, start) + openTag + closeTag + text.substring(start)
                                            contentValue = TextFieldValue(newText, TextRange(start + openTag.length))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (color == Color.White) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // --- MODAL BOTTOM SHEET 2: PARAGRAPH STYLES (≡) ---
        if (isParagraphStylesSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isParagraphStylesSheetOpen = false },
                containerColor = if (viewModel.isDarkMode) Color(0xFF1E293B) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paragraph style",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                        )
                        IconButton(onClick = { isParagraphStylesSheetOpen = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Lists",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bullet List
                        Button(
                            onClick = {
                                val current = contentValue.text
                                val newText = current + "\n• "
                                contentValue = TextFieldValue(newText, TextRange(newText.length))
                                isParagraphStylesSheetOpen = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("• Bullet List", color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Numbered List
                        Button(
                            onClick = {
                                val current = contentValue.text
                                val newText = current + "\n1. "
                                contentValue = TextFieldValue(newText, TextRange(newText.length))
                                isParagraphStylesSheetOpen = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1. Numbered", color = if (viewModel.isDarkMode) Color.White else Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Alarm reminder Setup sheet Dialog
        if (isSetReminderOpen) {
            Dialog(onDismissRequest = { isSetReminderOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Configure Local Alert Reminder", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Schedule alert timer:", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick intervals triggers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
                                    Toast.makeText(context, "Scheduled in 10 minutes!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("In 10m", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour
                                    Toast.makeText(context, "Scheduled in 1 Hour!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("In 1 Hour", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Local Ringtone:", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        val tonesList = listOf("Aurora Amber", "Cyber Bells", "Synthwave Whistle", "Cosmic Wakeup")
                        tonesList.forEach { tone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { reminderTone = tone }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tone, color = if (reminderTone == tone) Color(0xFF00FFCC) else Color.White, fontSize = 13.sp)
                                if (reminderTone == tone) {
                                    Icon(Icons.Default.Refresh, null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                reminderTime = null
                                isSetReminderOpen = false
                            }) {
                                Text("Disable Alert", color = Color.Red)
                            }
                            Button(
                                onClick = { isSetReminderOpen = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                            ) {
                                Text("Confirm Alarm", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }

        // Export Document Dialog Panel
        if (isExportOpen) {
            val exportPrefs = context.getSharedPreferences("notepad_exports", android.content.Context.MODE_PRIVATE)
            var previousExports by remember {
                mutableStateOf(exportPrefs.getStringSet("files", emptySet())?.toList() ?: emptyList())
            }
            var showCustomExtInput by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { isExportOpen = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Export Document & Share", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            IconButton(onClick = { isExportOpen = false }) {
                                Icon(Icons.Default.Close, "Close Panel", tint = Color.LightGray)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))

                        // Filename Input with Pencil indicator icon
                        OutlinedTextField(
                            value = exportFilename,
                            onValueChange = { exportFilename = it },
                            label = { Text("Filename", color = Color.LightGray) },
                            textStyle = TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Suffix",
                                    tint = Color(0xFF00FFCC)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Export Suffix Formats (Swipe left/right):", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontally scrollable row of export pills
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val formats = listOf("txt", "pdf", "html", "css", "xml", "py", "json")
                            formats.forEach { ext ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .clickable {
                                            when (ext) {
                                                "txt" -> NotepadExporter.saveAsTxt(context, exportFilename, contentValue.text)
                                                "pdf" -> NotepadExporter.saveAsPdf(context, exportFilename, title, contentValue.text)
                                                "html" -> NotepadExporter.saveAsHtml(context, exportFilename, title, contentValue.text)
                                                else -> NotepadExporter.saveCustom(context, exportFilename, ext, contentValue.text)
                                            }
                                            // Save to history
                                            val fileEntry = "$exportFilename.$ext"
                                            val updated = (previousExports + fileEntry).distinct()
                                            exportPrefs.edit().putStringSet("files", updated.toSet()).apply()
                                            previousExports = updated
                                            Toast.makeText(context, "Exported successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(".${ext.uppercase()}", color = Color(0xFF00FFCC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Custom ext pill that toggles the input field
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showCustomExtInput) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.1f))
                                    .clickable { showCustomExtInput = !showCustomExtInput }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                              ) {
                                  Text("CUSTOM...", color = if (showCustomExtInput) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                              }
                        }

                        if (showCustomExtInput) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customFileExt,
                                    onValueChange = { customFileExt = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(color = Color.White),
                                    placeholder = { Text("e.g. cpp, java, md", color = Color.Gray) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Edit, "Pencil", tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00FFCC),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (customFileExt.isNotBlank()) {
                                            NotepadExporter.saveCustom(context, exportFilename, customFileExt, contentValue.text)
                                            val fileEntry = "$exportFilename.$customFileExt"
                                            val updated = (previousExports + fileEntry).distinct()
                                            exportPrefs.edit().putStringSet("files", updated.toSet()).apply()
                                            previousExports = updated
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Export", color = Color.Black)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text("🕰 Previous Saved Exports:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (previousExports.isEmpty()) {
                            Text("No exports saved yet", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                previousExports.forEach { item ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val updated = previousExports.filter { it != item }
                                                    exportPrefs.edit().putStringSet("files", updated.toSet()).apply()
                                                    previousExports = updated
                                                    com.example.util.CopySoundPlayer.playClickSound(context)
                                                    Toast.makeText(context, "Export record deleted", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("Other Instant Share Option:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                val sendIntent: android.content.Intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "$title\n\n${contentValue.text}")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share note via:")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Share Note Directly", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { isExportOpen = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Dismiss Panel", color = Color.LightGray)
                        }
                    }
                }
            }
        }

        // --- Custom Dialog for entering copyable text blocks cleanly ---
        if (isCopyBoxInputOpen) {
            Dialog(onDismissRequest = { isCopyBoxInputOpen = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)) // Slate dark container
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "CREATE COPYABLE BOX",
                            color = Color(0xFF00FFCC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Type text below. In read-view, users can click a single button to copy this box directly.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = copyBoxInputText,
                            onValueChange = { copyBoxInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            placeholder = { Text("Enter copyable snippet or custom text here...", color = Color(0xFF475569), fontSize = 13.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { 
                                isCopyBoxInputOpen = false
                                copyBoxInputText = ""
                            }) {
                                Text("Cancel", color = Color(0xFF94A3B8))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (copyBoxInputText.isNotEmpty()) {
                                        val openTag = "<copy>\n"
                                        val closeTag = "\n</copy>"
                                        val textToInsert = openTag + copyBoxInputText + closeTag
                                        val currentText = contentValue.text
                                        val start = contentValue.selection.start
                                        val newText = currentText.substring(0, start) + textToInsert + currentText.substring(start)
                                        contentValue = TextFieldValue(
                                            text = newText,
                                            selection = androidx.compose.ui.text.TextRange(start + textToInsert.length)
                                        )
                                    }
                                    isCopyBoxInputOpen = false
                                    copyBoxInputText = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                            ) {
                                Text("Insert Box", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun ThemeBubble(
    colorHex: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(android.graphics.Color.parseColor(colorHex)))
            .border(
                2.dp,
                if (isSelected) Color.White else Color.Transparent,
                CircleShape
            )
            .clickable(onClick = onClick)
    )
}

// --- NAME STYLIZER & GENERATOR SCREEN ---
@Composable
fun NameStylizerScreenSection(
    onBack: () -> Unit,
    viewModel: NotepadViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(viewModel.customUserName) }
    var stylePrompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    var aiGeneratedDesigns by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val presetDesigns = remember(inputName) {
        NameStylizer.getStylishFonts(inputName)
    }

    val displayDesigns = remember(aiGeneratedDesigns, presetDesigns) {
        if (aiGeneratedDesigns.isNotEmpty()) aiGeneratedDesigns else presetDesigns
    }

    val isDark = viewModel.isDarkMode
    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = textColor)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("🔤 AI Name Generator & Stylizer", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Box 1: Enter your name
        OutlinedTextField(
            value = inputName,
            onValueChange = { inputName = it },
            label = { Text("Enter your name", color = Color(0xFF94A3B8)) },
            textStyle = TextStyle(color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = borderColor,
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Box 2: Describe your changes / style preferences
        OutlinedTextField(
            value = stylePrompt,
            onValueChange = { stylePrompt = it },
            label = { Text("Describe your changes / style preferences", color = Color(0xFF94A3B8)) },
            placeholder = { Text("e.g. Add wings, gothic style, fire symbols, emojis, cool brackets, capital bold", color = Color(0xFF64748B), fontSize = 11.sp) },
            textStyle = TextStyle(color = textColor, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = borderColor,
                focusedContainerColor = cardBg,
                unfocusedContainerColor = cardBg
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // AI Generate Button
        Button(
            onClick = {
                if (inputName.isBlank()) {
                    Toast.makeText(context, "Please enter your name first", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isGenerating = true
                viewModel.generateAiNameStyles(inputName, stylePrompt) { results ->
                    isGenerating = false
                    aiGeneratedDesigns = results
                    com.example.util.CopySoundPlayer.playClickSound(context)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generating AI Styles...", color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                Text("✨ Generate AI Stylish Names", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displayDesigns) { design ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(design.first, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(design.second, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(design.second))
                                com.example.util.CopySoundPlayer.playClickSound(context)
                                Toast.makeText(context, "Copied stylish name to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Copy", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// --- LOCAL STORAGE FILE EXPLORER & EDITOR SCREEN ---
@Composable
fun LocalFileExplorerScreenSection(
    onBack: () -> Unit,
    viewModel: NotepadViewModel
) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(android.os.Environment.getExternalStorageDirectory() ?: context.filesDir) }
    var editingFile by remember { mutableStateOf<java.io.File?>(null) }
    var editingContent by remember { mutableStateOf("") }

    val isDark = viewModel.isDarkMode
    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E293B)
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val borderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    val fileList = remember(currentDir) {
        try {
            currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    if (editingFile != null) {
        // IN-APP FILE EDITOR MODE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { editingFile = null }) {
                        Icon(Icons.Default.ArrowBack, "Back to files", tint = textColor)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(editingFile?.name ?: "File", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(editingFile?.absolutePath ?: "", color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        try {
                            editingFile?.writeText(editingContent)
                            com.example.util.CopySoundPlayer.playClickSound(context)
                            Toast.makeText(context, "File saved successfully!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("💾 Save File", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderColor)
            ) {
                OutlinedTextField(
                    value = editingContent,
                    onValueChange = { editingContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = TextStyle(
                        color = textColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }
        }
        return
    }

    // FILE EXPLORER BROWSER MODE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val parent = currentDir.parentFile
                if (parent != null && parent.canRead()) {
                    currentDir = parent
                } else {
                    onBack()
                }
            }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = textColor)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text("📁 Storage File Explorer", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(currentDir.absolutePath, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (fileList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No readable files or directory empty", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fileList) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.isDirectory) {
                                    currentDir = file
                                } else {
                                    try {
                                        editingContent = file.readText()
                                        editingFile = file
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot read file: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (file.isDirectory) "📁" else when (file.extension.lowercase()) {
                                    "txt", "md" -> "📄"
                                    "xml", "html", "json" -> "🏷️"
                                    "kt", "java", "py", "js" -> "💻"
                                    else -> "📝"
                                },
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    text = if (file.isDirectory) "Folder" else "${file.length() / 1024} KB",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SETTINGS CONFIG DRAWER SCREEN ---
@Composable
fun SettingsScreenSection(
    onBack: () -> Unit,
    viewModel: NotepadViewModel
) {
    val context = LocalContext.current
    var inputKey by remember { mutableStateOf(viewModel.geminiApiKeyOverride) }
    var openAiKeyInput by remember { mutableStateOf(viewModel.openAiApiKey) }
    var claudeKeyInput by remember { mutableStateOf(viewModel.claudeApiKey) }
    var githubKeyInput by remember { mutableStateOf(viewModel.githubToken) }
    var customKeyInput by remember { mutableStateOf(viewModel.customToken) }
    var pinField by remember { mutableStateOf(viewModel.savedPin) }
    var securityQuestionField by remember { mutableStateOf(viewModel.savedSecurityQuestion) }
    var securityAnswerField by remember { mutableStateOf(viewModel.savedSecurityAnswer) }
    var userNameField by remember { mutableStateOf(viewModel.customUserName) }

    var isBlurState by remember { mutableStateOf(viewModel.isBlurApisEnabled) }
    var isSyntaxState by remember { mutableStateOf(viewModel.isSyntaxHighlightingEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF1E293B))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("AuPad Preferences Dashboard", color = Color(0xFF1E293B), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("ORGANIZED PREFERENCES", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // Blur list option
        SettingsToggleRow(
            title = "Blur APIs on Note Lists",
            desc = "Hides/blurs long non-spaced credentials for privacy safety",
            checked = isBlurState,
            onCheckedChange = {
                isBlurState = it
                viewModel.isBlurApisEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Highlight syntax option
        SettingsToggleRow(
            title = "Code Syntax Highlight",
            desc = "Colorizes variables, tags and strings inside coding boxes",
            checked = isSyntaxState,
            onCheckedChange = {
                isSyntaxState = it
                viewModel.isSyntaxHighlightingEnabled = it
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("SECURITY LOCKS & PROFILE", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // PIN Setup
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("APIs Folder / Lock Passcode PIN", color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Configure a 4-digit numeric passcode to secure your private notes. (No security questions are saved on the device for maximum privacy protection).", color = Color(0xFF64748B), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pinField,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinField = it
                            viewModel.savedPin = it
                        }
                    },
                    label = { Text("4-digit PIN", color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(color = Color(0xFF1E293B)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("FONTS & TYPOGRAPHY", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // Font Importer Card
        val fontPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                val dir = java.io.File(context.filesDir, "custom_fonts")
                if (!dir.exists()) dir.mkdirs()
                var importedCount = 0
                uris.forEach { uri ->
                    val fileName = com.example.util.getFileNameFromUri(context, uri) ?: "font_${System.currentTimeMillis()}.ttf"
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val destFile = java.io.File(dir, fileName)
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                            importedCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (importedCount > 0) {
                    Toast.makeText(context, "Successfully imported $importedCount fonts!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Custom Font Importer (.ttf / .otf):", color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select multiple TTF/OTF fonts from your storage to use inside Au Notes.", color = Color(0xFF64748B), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        try {
                            fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "*/*"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📥 Select & Import Fonts", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                }

                val customFonts = remember { mutableStateListOf<java.io.File>() }
                val reloadFonts = {
                    val dir = java.io.File(context.filesDir, "custom_fonts")
                    customFonts.clear()
                    if (dir.exists()) {
                        dir.listFiles { file ->
                            file.extension.equals("ttf", ignoreCase = true) || file.extension.equals("otf", ignoreCase = true)
                        }?.let { customFonts.addAll(it) }
                    }
                }

                LaunchedEffect(Unit) {
                    reloadFonts()
                }

                if (customFonts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Imported Fonts (${customFonts.size}):", color = Color(0xFF1E293B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    customFonts.forEach { fontFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fontFile.nameWithoutExtension,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                fontFamily = com.example.util.loadFontFamily(context, fontFile.name)
                            )
                            IconButton(
                                onClick = {
                                    fontFile.delete()
                                    reloadFonts()
                                    Toast.makeText(context, "Font deleted!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Delete font", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trash deletion helper
        Button(
            onClick = {
                viewModel.clearTrash()
                Toast.makeText(context, "Trash Bin completely wiped / emptied!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hard Wipe Trash Bin Recycler", color = Color.White)
        }

        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color(0xFF1E293B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = Color(0xFF64748B), fontSize = 10.sp, lineHeight = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2563EB),
                    checkedTrackColor = Color(0xFFBFDBFE)
                )
            )
        }
    }
}

// Rounded custom box helper
fun circleShape(): RoundedCornerShape = RoundedCornerShape(percent = 50)

@Composable
fun ContactOptionItem(
    brandColor: Color,
    brandGradient: Brush? = null,
    iconChar: String,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(
                    if (brandGradient != null) Modifier.background(brandGradient)
                    else Modifier.background(brandColor)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconChar,
                fontSize = 20.sp,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (value == "Not Available") Color(0xFFEF4444) else Color(0xFF2563EB)
            )
        }
        
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Navigate Link",
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun FormatterToggleButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF6366F1).copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (isActive) Color(0xFF6366F1) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text == "U") {
            Text(
                text = "U",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                color = if (isActive) Color(0xFF4F46E5) else Color(0xFF475569)
            )
        } else if (text == "G") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "G",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFFEC4899) else Color(0xFF475569)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "✨",
                    fontSize = 10.sp
                )
            }
        } else {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (text == "B") FontWeight.Bold else FontWeight.Medium,
                style = if (text == "I") TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else TextStyle.Default,
                color = if (isActive) Color(0xFF4F46E5) else Color(0xFF475569)
            )
        }
    }
}

@Composable
fun AppFeaturesScreenSection(onBack: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    val scaleAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.94f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "featuresScale"
    )
    val alphaAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450),
        label = "featuresAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .graphicsLayer(
                scaleX = scaleAnim,
                scaleY = scaleAnim,
                alpha = alphaAnim
            )
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF1E293B))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("All Main Features of App", color = Color(0xFF1E293B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        val features = listOf(
            Triple("📝 Zero-Lag Rich Typography Editor", "Optimized with background caching and independent component scrolling. Type long documents, journals, and logs seamlessly without stutter.", Color(0xFF3F51B5)),
            Triple("⚙️ Live Custom Font Importer", "Import unlimited custom .ttf and .otf files from your device. Personalize your complete writing atmosphere instantly in Settings.", Color(0xFF009688)),
            Triple("🔒 Dynamic Category Folders & PIN Locks", "Create secured categories. Lock sensitive folders with a secret PIN, protected by recovery security questions and developer master overrides.", Color(0xFFE91E63)),
            Triple("🔮 Au AI Smart Assistant Bot", "Embedded with server-side Gemini intelligence. Extract clean code blocks, translate logs, generate layout summaries, and chat instantly.", Color(0xFF9C27B0)),
            Triple("📋 Typographic Copyable Code Boxes", "Insert custom markdown blocks with a dedicated pencil button. Copy snippet boxes in read mode with a single tap.", Color(0xFF00BCD4)),
            Triple("📦 Public Downloads Export (TXT, ZIP, HTML)", "Export your private notes to the shared Downloads folder. Supports multi-selection ZIP compression for lightning-fast transfers.", Color(0xFF4CAF50))
        )

        features.forEach { (title, desc, tintColor) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(tintColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tintColor))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color(0xFF1E293B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, color = Color(0xFF64748B), fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperContactScreenSection(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    val scaleAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.94f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "contactScale"
    )
    val alphaAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 450),
        label = "contactAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .graphicsLayer(
                scaleX = scaleAnim,
                scaleY = scaleAnim,
                alpha = alphaAnim
            )
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFF1E293B))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Developer Contact Workspace", color = Color(0xFF1E293B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧑‍💻", fontSize = 36.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("AuPad Developer Support", color = Color(0xFF1E293B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Get assist code, request features, or report vulnerabilities directly.", color = Color(0xFF64748B), fontSize = 11.sp)

                Spacer(modifier = Modifier.height(20.dp))

                // Email Contact Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:educationaltalks1@gmail.com")
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "AuPad Support Query")
                                }
                                context.startActivity(intent)
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "educationaltalks1@gmail.com", Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📧", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Official Email Address", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("educationaltalks1@gmail.com", color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Telegram Contact Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF))
                        .clickable {
                            try {
                                uriHandler.openUri("https://t.me/she_is_miine")
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Telegram: @she_is_miine", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✈️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Personal Telegram Account", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("@she_is_miine", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // WhatsApp Contact Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDCFCE7))
                        .clickable {
                            try {
                                uriHandler.openUri("https://wa.me/919719124973")
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "WhatsApp: +919719124973", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💬", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WhatsApp Support", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("+919719124973", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // YouTube Contact Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEE2E2))
                        .clickable {
                            try {
                                uriHandler.openUri("https://youtube.com/@destroyer_xe")
                                com.example.util.CopySoundPlayer.playClickSound(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "YouTube: @destroyer_xe", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("▶️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("YouTube Channel", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("@destroyer_xe", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    digit: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

