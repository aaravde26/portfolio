package wpaest.pad.padremake2

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wpaest.pad.padremake2.ui.theme.PADRemake2Theme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Helper class to manage mixed media types
sealed class PermutableItem {
    data class Media(val uri: Uri, val name: String, val mimeType: String?) : PermutableItem()
    data class Text(val content: String) : PermutableItem()
}

// Recursive mathematical function to generate all possible permutations
fun <T> getPermutations(list: List<T>): List<List<T>> {
    if (list.isEmpty()) return listOf(emptyList())
    val result = mutableListOf<List<T>>()
    for (i in list.indices) {
        val element = list[i]
        val rest = list.toMutableList().apply { removeAt(i) }
        for (perm in getPermutations(rest)) {
            result.add(listOf(element) + perm)
        }
    }
    return result
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PADRemake2Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF111827) // Dark theme background
                ) { innerPadding ->
                    PermutationApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PermutationApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState() 
    // --- State Variables ---
    var itemsList by remember { mutableStateOf<List<PermutableItem>>(emptyList()) }
    var playlist by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentTrackIndex by remember { mutableIntStateOf(0) }
    var playbackState by remember { mutableStateOf("stopped") }
    var isCalculating by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var delayInputText by remember { mutableStateOf("3") }

    // Onboarding and Info State
    var showOnboarding by remember { mutableStateOf(true) }
    var onboardingStep by remember { mutableIntStateOf(1) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // --- Native File Picker ---
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val newMedia = uris.map { uri ->
            val mimeType = context.contentResolver.getType(uri)
            var name = "Unknown File"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) name = cursor.getString(nameIndex)
            }
            PermutableItem.Media(uri, name, mimeType)
        }
        itemsList = itemsList + newMedia
    }

    LaunchedEffect(currentTrackIndex) {
        if (playlist.isNotEmpty()) {
            // This scrolls the list so the active item from the playlist is visible
            // We use the index from the playlist to highlight the specific item
            listState.animateScrollToItem(currentTrackIndex)
        }
    }
    // --- Media3 ExoPlayer Setup ---
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            // Listener specifically for when an audio/video file finishes naturally
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        if (currentTrackIndex < playlist.size - 1) currentTrackIndex++
                        else playbackState = "stopped"
                    }
                }
            })
        }
    }

    // --- Playback Loop Engine ---
    val currentItem = if (playlist.isNotEmpty() && currentTrackIndex < playlist.size) {
        itemsList[playlist[currentTrackIndex]]
    } else null

    LaunchedEffect(currentItem, playbackState) {
        if (playbackState == "playing" && currentItem != null) {

            val delaySeconds = delayInputText.toFloatOrNull() ?: 3.0f
            val delayMilliseconds = (delaySeconds * 1000).toLong()

            when (currentItem) {
                is PermutableItem.Media -> {
                    val isVideoOrAudio = currentItem.mimeType?.startsWith("video") == true ||
                            currentItem.mimeType?.startsWith("audio") == true

                    if (isVideoOrAudio) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(currentItem.uri))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else if (currentItem.mimeType?.startsWith("image") == true) {
                        exoPlayer.pause()
                        // Pause the player and show the image for the specified delay
                        delay(delayMilliseconds)
                        if (currentTrackIndex < playlist.size - 1) currentTrackIndex++
                        else playbackState = "stopped"
                    }
                }
                is PermutableItem.Text -> {
                    exoPlayer.pause()
                    // Pause the player and show the text for the specified delay
                    delay(delayMilliseconds)
                    if (currentTrackIndex < playlist.size - 1) currentTrackIndex++
                    else playbackState = "stopped"
                }
            }
        } else {
            exoPlayer.pause()
        }
    }

    // Cleanup resources to prevent memory leaks
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // --- UI Layout ---
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // NEW: Header Box with Centered Title and Info Button on the Right
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "PAD Ultimate Permutation Media Tool",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Info, contentDescription = "App Info", tint = Color.Gray)
            }
        }

        // NEW: The Popup Dialog Logic
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("About This Design", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This app is engineered to calculate up to 362,880 permutations (9!). Because generating a playlist of this massive size is highly resource-intensive, complex design updates and additional features have been intentionally omitted.\n\nThis minimalist interface ensures optimal performance and provides a distraction-free environment meant purely for studying and memorization."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Understood", color = Color.Cyan)
                    }
                },
                containerColor = Color(0xFF1F2937),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }

        // Onboarding Sequence
        if (showOnboarding) {
            AlertDialog(
                onDismissRequest = { showOnboarding = false },
                title = {
                    Text(
                        when (onboardingStep) {
                            1 -> "Welcome to PAD"
                            2 -> "The Power of Permutations"
                            else -> "How to Use"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        when (onboardingStep) {
                            1 -> "This tool is designed to take a list of items and reorder them into every possible sequence (permutation). It automates the process of exploring every combination of your content."
                            2 -> "The purpose is to expose you to specific orderings and transitions you would otherwise miss. This helps in deep memorization and uncovering hidden patterns across your media."
                            else -> "• Add Text or Select Media (up to 9 items).\n• Set a Delay for how long text/images appear.\n• Press Play to calculate the full sequence and start playback."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (onboardingStep < 3) onboardingStep++
                        else showOnboarding = false
                    }) {
                        Text(if (onboardingStep < 3) "Next" else "Get Started", color = Color.Cyan)
                    }
                },
                dismissButton = {
                    if (onboardingStep > 1) {
                        TextButton(onClick = { onboardingStep-- }) {
                            Text("Back", color = Color.Gray)
                        }
                    } else {
                        TextButton(onClick = { showOnboarding = false }) {
                            Text("Skip", color = Color.Gray)
                        }
                    }
                },
                containerColor = Color(0xFF1F2937),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Media Viewer
                Box(
                    modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.Black, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (currentItem) {
                        is PermutableItem.Media -> {
                            if (currentItem.mimeType?.startsWith("image") == true) {
                                Image(
                                    painter = rememberAsyncImagePainter(currentItem.uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                AndroidView(
                                    factory = { PlayerView(it).apply { player = exoPlayer } },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        is PermutableItem.Text -> {
                            Text(
                                text = currentItem.content,
                                color = Color.White,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                        null -> Text("Awaiting Input...", color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Text Input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Enter text string...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFF374151),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    IconButton(onClick = {
                        if (textInput.isNotBlank() && itemsList.size < 9) {
                            itemsList = itemsList + PermutableItem.Text(textInput)
                            textInput = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Text", tint = Color.Cyan)
                    }
                }

                // File Input
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*", "video/*", "image/*")) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                    enabled = itemsList.size < 9
                ) {
                    Text("Select Media (.mp3, .mp4, images)")
                }

                // Delay Configuration
                OutlinedTextField(
                    value = delayInputText,
                    onValueChange = { newValue ->
                        // Allow digits and a single decimal point, max 5 characters, max 60 seconds
                        if (newValue.length <= 5 && newValue.count { it == '.' } <= 1 && newValue.all { it.isDigit() || it == '.' }) {
                            val numericValue = newValue.toFloatOrNull()
                            if (numericValue == null || numericValue <= 60.0f) {
                                delayInputText = newValue
                            }
                        }
                    },
                    label = { Text("Delay (Max 60s, e.g., 0.5)", color = Color.LightGray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Cyan,
                        unfocusedIndicatorColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Controls
                // Hardcap the tool at 9 items to prevent memory crashing (9! = 362,880)
                val isSafeSize = itemsList.isNotEmpty() && itemsList.size <= 9

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            if (playbackState == "playing") {
                                playbackState = "paused"
                            } else {
                                coroutineScope.launch(Dispatchers.Default) {
                                    isCalculating = true
                                    val indices = itemsList.indices.toList()
                                    val allPerms = getPermutations(indices)
                                    val flatPlaylist = allPerms.flatten()

                                    withContext(Dispatchers.Main) {
                                        playlist = flatPlaylist
                                        currentTrackIndex = 0
                                        playbackState = "playing"
                                        isCalculating = false
                                    }
                                }
                            }
                        },
                        enabled = isSafeSize && !isCalculating
                    ) {
                        if (isCalculating) {
                            Text("Calculating ${itemsList.size}! ...")
                        } else {
                            Text(if (playbackState == "playing") "Pause" else "Play ${itemsList.size}! Permutations")
                        }
                    }
                    Button(onClick = {
                        playbackState = "stopped"
                        itemsList = emptyList()
                        playlist = emptyList()
                    }) {
                        Text("Clear All")
                    }
                }
            }
        }

        // Current Inventory List
        // --- Inventory Highlight List (Max 9 items) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
        ) {
            itemsList.forEachIndexed { index, item ->
                // Identify if this specific object is currently active in the permutation loop
                val isCurrentlyPlaying = if (playlist.isNotEmpty() && currentTrackIndex < playlist.size) {
                    playlist[currentTrackIndex] == index
                } else false

                val label = when (item) {
                    is PermutableItem.Media -> "Object ${index + 1}: ${item.name}"
                    is PermutableItem.Text -> "Object ${index + 1}: ${item.content}"
                }

                Text(
                    text = label,
                    // Highlighting colors
                    color = if (isCurrentlyPlaying) Color.Cyan else Color.LightGray,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            // Darker background for the active object
                            if (isCurrentlyPlaying) Color(0xFF374151) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        // "Zoom In" pop effect for the active object
                        .graphicsLayer(
                            scaleX = if (isCurrentlyPlaying) 1.05f else 1.0f,
                            scaleY = if (isCurrentlyPlaying) 1.05f else 1.0f
                        )
                )
            }
        }
    }
}