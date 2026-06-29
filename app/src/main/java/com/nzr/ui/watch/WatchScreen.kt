package com.nzr.ui.watch

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.nzr.ui.home.toUserFriendlyMessage
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.media3.session.MediaSession
import androidx.navigation.NavController
import coil.compose.AsyncImage
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nzr.api.ApiClient
import com.nzr.api.models.StreamResponse
import com.nzr.api.models.WatchResponse
import com.nzr.data.AppDatabase
import com.nzr.data.WatchProgress
import com.nzr.data.WatchProgressRepository
import com.nzr.ui.shared.tvFocusGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchViewModel(
    private val id: String,
    private val season: Int,
    private val episode: Int,
    private val repository: WatchProgressRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        fetchWatchData()
    }

    private fun fetchWatchData() {
        viewModelScope.launch {
            try {
                _uiState.value = WatchUiState.Loading
                val response = ApiClient.service.getTestLiveItem(id)
                val watchDataUnmapped = response.subject
                
                if (watchDataUnmapped == null) {
                    _uiState.value = WatchUiState.Error("Failed to fetch details")
                    return@launch
                }
                
                val watchData = watchDataUnmapped.copy(
                    resource_detectors = listOf(com.nzr.api.models.ResourceDetector(resolution_list = response.streams))
                )
                
                val related = try { ApiClient.service.getTrending(1).data?.items?.flatMap { it.allSubjects }?.shuffled()?.take(12) ?: emptyList() } catch(e: Exception) { emptyList() }
                val trendingMovies = try { ApiClient.service.getTrendingMovies(2).data?.items?.flatMap { it.allSubjects }?.shuffled()?.take(12) ?: emptyList() } catch(e: Exception) { emptyList() }
                val trendingSeries = try { ApiClient.service.getTrendingSeries(2).data?.items?.flatMap { it.allSubjects }?.shuffled()?.take(12) ?: emptyList() } catch(e: Exception) { emptyList() }
                
                // Get the existing progress for this movie/episode
                val progressData = repository.getProgress(id, season, episode)
                
                val resList = response.streams
                
                val targetEps = resList.filter { it.se == season || (watchData.subject_type != 2) }
                val matchingEps = targetEps.filter { it.ep == episode || (watchData.subject_type != 2) }
                val bestLink = matchingEps.sortedByDescending { it.resolution }.firstOrNull { it.codec_name == "h264" }?.resource_link
                    ?: matchingEps.sortedByDescending { it.resolution }.firstOrNull()?.resource_link
                    
                val encodedLink = bestLink?.let { java.net.URLEncoder.encode(it, "UTF-8") }
                val proxiedLink = encodedLink?.let { "https://movie-box-api-v1.onrender.com/api/proxy/stream?url=$it" }
                
                val mappedStreams = matchingEps.mapNotNull { epItem ->
                    epItem.resource_link?.let { link ->
                        val enc = java.net.URLEncoder.encode(link, "UTF-8")
                        com.nzr.api.models.StreamSource(
                            url = "https://movie-box-api-v1.onrender.com/api/proxy/stream?url=$enc",
                            quality = epItem.resolution ?: 0,
                            quality_label = "${epItem.resolution ?: 0}p",
                            codec = epItem.codec_name ?: ""
                        )
                    }
                }.sortedByDescending { it.quality }.distinctBy { it.quality_label }
               
                val episodes = targetEps.distinctBy { it.ep }.map {
                    com.nzr.api.models.Episode(
                        episode = it.ep.takeIf { ep -> ep > 0 } ?: 1,
                        title = it.title,
                        thumbnail = watchData.coverUrlResolved
                    )
                }.sortedBy { it.episode }
                
                val type = if (watchData.subject_type == 2) "series" else "movie"
                
                // find seasons
                val seasonsMap = resList.groupBy { it.se }
                val allSeasonsMapped = seasonsMap.keys.sorted().map { s ->
                    com.nzr.api.models.SeasonInfo(season = s, episode_count = seasonsMap[s]?.map { it.ep }?.distinct()?.size ?: 0)
                }.filter { (it.seasonNumber ?: 0) > 0 }
                
                val mappedWatch = com.nzr.api.models.WatchResponse(
                    stream = com.nzr.api.models.StreamResponse(best_url = proxiedLink, streams = mappedStreams),
                    metadata = watchData,
                    title = watchData.title ?: "Unknown",
                    type = type,
                    season = season,
                    episode = episode,
                    episodes = episodes,
                    all_seasons = allSeasonsMapped
                )
                
                _uiState.value = WatchUiState.Success(mappedWatch, related, trendingMovies, trendingSeries, progressData?.progressMs ?: 0L)
            } catch (e: Exception) {
                _uiState.value = WatchUiState.Error(e.toUserFriendlyMessage())
            }
        }
    }

    fun saveProgress(data: WatchResponse, currentPosition: Long, duration: Long) {
        if (currentPosition < 10000L) return // Don't save if watched less than 10 seconds
        val validDuration = if (duration < 0) 0L else duration
        
        // Reset progress if reached within 10 seconds of the end
        val finalProgress = if (validDuration > 0 && currentPosition >= validDuration - 10000L) 0L else currentPosition
        
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            val progressItem = WatchProgress(
                subject_id = id,
                title = data.title,
                cover = data.metadata?.coverUrlResolved,
                timestamp = System.currentTimeMillis(),
                season = season,
                episode = episode,
                progressMs = finalProgress,
                durationMs = validDuration
            )
            repository.insert(progressItem)
        }
    }
}

class WatchViewModelFactory(
    private val id: String,
    private val season: Int,
    private val episode: Int,
    private val repository: WatchProgressRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WatchViewModel(id, season, episode, repository) as T
    }
}

sealed class WatchUiState {
    object Loading : WatchUiState()
    data class Success(
        val watchData: WatchResponse,
        val related: List<com.nzr.api.models.ContentItem>,
        val trendingMovies: List<com.nzr.api.models.ContentItem>,
        val trendingSeries: List<com.nzr.api.models.ContentItem>,
        val initialProgress: Long
    ) : WatchUiState()
    data class Error(val message: String) : WatchUiState()
}

@Composable
fun WatchScreen(navController: NavController, id: String, season: Int, episode: Int) {
    val context = LocalContext.current
    val exoContext = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.createAttributionContext("playback")
        } else {
            context
        }
    }
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { WatchProgressRepository(database.watchProgressDao()) }
    val viewModel: WatchViewModel = viewModel(factory = WatchViewModelFactory(id, season, episode, repository))
    val uiState by viewModel.uiState.collectAsState()

    val audioManager = remember { exoContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    val isTv = remember { 
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }

    var exoPlayer by remember { mutableStateOf<Player?>(null) }
    var isFullscreen by remember { mutableStateOf(isTv) }
    var screenLocked by remember { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var interactionIndicator by remember { mutableIntStateOf(0) }

    
    val initialBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    
    var brightnessOverlay by remember { mutableStateOf(false) }
    var volumeOverlay by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf("") }

    val currentUiState by rememberUpdatedState(uiState)

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val player = exoPlayer
                val state = currentUiState
                if (state is WatchUiState.Success && player != null) {
                    viewModel.saveProgress(state.watchData, player.currentPosition, player.duration)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        val compActivity = activity as? androidx.activity.ComponentActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            compActivity?.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                compActivity?.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }

    DisposableEffect(Unit) {
        val sessionToken = androidx.media3.session.SessionToken(
            exoContext,
            android.content.ComponentName(exoContext, com.nzr.service.PlaybackService::class.java)
        )
        val controllerFuture = androidx.media3.session.MediaController.Builder(exoContext, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            exoPlayer = controllerFuture.get()
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
        
        onDispose {
            val player = exoPlayer
            val state = currentUiState
            if (state is WatchUiState.Success && player != null) {
                viewModel.saveProgress(state.watchData, player.currentPosition, player.duration)
            }
            if (player != null) {
                player.stop()
                player.clearMediaItems()
            }
            androidx.media3.session.MediaController.releaseFuture(controllerFuture)
        }
    }

    // Save progress periodically using a player listener and Firestore
    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        
        var updateJob: kotlinx.coroutines.Job? = null
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateJob?.cancel()
                if (isPlaying) {
                    updateJob = scope.launch {
                        while (true) {
                            delay(10000) // every 10 seconds
                            val state = currentUiState
                            if (state is WatchUiState.Success) {
                                viewModel.saveProgress(state.watchData, player.currentPosition, player.duration)
                            }
                        }
                    }
                }
            }
        }
        
        player.addListener(listener)
        
        onDispose {
            player.removeListener(listener)
            updateJob?.cancel()
        }
    }

    var selectedQualityUrl by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeSettingsMenu by remember { mutableStateOf("Main") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    LaunchedEffect(uiState, selectedQualityUrl, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (uiState is WatchUiState.Success) {
            val state = uiState as WatchUiState.Success
            val stream = state.watchData.stream
            if (stream != null) {
                val urlToPlay = selectedQualityUrl ?: selectBestStreamUrl(stream)
                val currentMediaItem = player.currentMediaItem
                
                if (currentMediaItem?.localConfiguration?.uri?.toString() != urlToPlay && urlToPlay.isNotEmpty()) {
                    val currentPos = player.currentPosition
                    val isQualityChange = selectedQualityUrl != null
                    val wasPlaying = player.isPlaying || currentPos == 0L
                    
                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                        .setUri(urlToPlay)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(state.watchData.title ?: "Unknown Title")
                                .setDisplayTitle(state.watchData.title ?: "Unknown Title")
                                .setArtworkUri(android.net.Uri.parse(state.watchData.metadata?.coverUrlResolved ?: ""))
                                .setArtist(context.getString(com.nzr.R.string.app_name))
                                .build()
                        )
                        .build()
                    
                    if (isQualityChange) {
                        player.setMediaItem(mediaItem, currentPos)
                    } else if (state.initialProgress > 0) {
                        player.setMediaItem(mediaItem, state.initialProgress)
                    } else {
                        player.setMediaItem(mediaItem)
                    }
                    
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        val window = activity?.window
        val windowInsetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        if (isFullscreen) {
            try {
                if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } catch (e: Exception) {}
            
            try {
                window?.attributes?.let { params -> 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    window.attributes = params
                }
            } catch (e: Exception) {}
            
            windowInsetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            try {
                if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (e: Exception) {}
            
            windowInsetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            try {
                window?.attributes?.let { params -> 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                    window.attributes = params
                }
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val windowInsetsController = activity?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (e: Exception) {}
            windowInsetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Restore initial brightness
            try {
                activity?.window?.let { window ->
                    val params = window.attributes
                    params.screenBrightness = initialBrightness
                    window.attributes = params
                }
            } catch (e: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f/9f))
            .background(Color.Black)
        ) {
            if (uiState is WatchUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState is WatchUiState.Error) {
                Text(
                    text = (uiState as WatchUiState.Error).message,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                var playerView by remember { mutableStateOf<PlayerView?>(null) }
                val focusRequester = remember { FocusRequester() }
                
                LaunchedEffect(Unit) {
                    try {
                        focusRequester.requestFocus()
                    } catch (e: Exception) {}
                }

                LaunchedEffect(isControlsVisible, interactionIndicator) {
                    if (isControlsVisible) {
                        delay(5000)
                        isControlsVisible = false
                        playerView?.hideController()
                        try {
                            focusRequester.requestFocus()
                        } catch (e: Exception) {}
                    }
                }

                AndroidView(
                    factory = {
                        PlayerView(exoContext).apply {
                            playerView = this
                            player = exoPlayer
                            keepScreenOn = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = !screenLocked && !isInPipMode
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowSubtitleButton(true)
                            setShowFastForwardButton(true)
                            setShowRewindButton(true)
                            isFocusable = true
                            
                            val exoSettingsBtn = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)
                            exoSettingsBtn?.visibility = android.view.View.GONE
                            
                            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                                val shouldBeVisible = visibility == android.view.View.VISIBLE
                                if (shouldBeVisible != isControlsVisible) {
                                    isControlsVisible = shouldBeVisible
                                    if (shouldBeVisible) interactionIndicator++
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                interactionIndicator++
                                val k = event.key
                                if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter ||
                                    k == Key.DirectionLeft || k == Key.DirectionRight || 
                                    k == Key.DirectionUp || k == Key.DirectionDown) {
                                    
                                    playerView?.let { pv ->
                                        if (!pv.isControllerFullyVisible) {
                                            pv.showController()
                                            return@onKeyEvent true
                                        }
                                    }
                                }
                            }
                            false
                        }
                )
                
                if (!screenLocked && !isInPipMode) {
                    if (!isTv && isFullscreen) {
                        // Brightness swipe zone (left half)
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.3f)
                        .align(Alignment.CenterStart)
                        .pointerInput(Unit) {
                            var startBrightness = -1f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    val window = activity?.window
                                    val params = window?.attributes
                                    startBrightness = params?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                    if (startBrightness < 0) startBrightness = 0.5f // Default
                                    brightnessOverlay = true
                                },
                                onDragEnd = { brightnessOverlay = false },
                                onDragCancel = { brightnessOverlay = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val window = activity?.window
                                val params = window?.attributes
                                if (params != null) {
                                    val delta = -dragAmount / size.height
                                    var newBrightness = startBrightness + delta * 2 // Multiply for sensitivity
                                    newBrightness = newBrightness.coerceIn(0f, 1f)
                                    params.screenBrightness = newBrightness
                                    window.attributes = params
                                    startBrightness = newBrightness // Update start to accumulate properly
                                    overlayText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                }
                            }
                        }
                    )

                    // Volume swipe zone (right half)
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.3f)
                        .align(Alignment.CenterEnd)
                        .pointerInput(Unit) {
                            var startVolume = -1
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            detectVerticalDragGestures(
                                onDragStart = {
                                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    volumeOverlay = true
                                },
                                onDragEnd = { volumeOverlay = false },
                                onDragCancel = { volumeOverlay = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height
                                var newVolume = startVolume + (delta * maxVolume * 2).toInt() // Sensitivity factor
                                newVolume = newVolume.coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                startVolume = newVolume
                                overlayText = "Volume: ${(newVolume.toFloat() / maxVolume * 100).toInt()}%"
                            }
                        }
                    ) // end volume point input
                    } // end if !isTv
                }
                
                if ((brightnessOverlay || volumeOverlay) && !isInPipMode) {
                    Box(modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium).padding(16.dp)) {
                        Text(text = overlayText, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            if (isControlsVisible && !screenLocked && !isInPipMode) {
                // Back Button
                val backInteractionSource = remember { MutableInteractionSource() }
                val isBackFocused by backInteractionSource.collectIsFocusedAsState()
                IconButton(
                    onClick = { navController.popBackStack() },
                    interactionSource = backInteractionSource,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).focusable(interactionSource = backInteractionSource).tvFocusGlow(isTv, isBackFocused, MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState is WatchUiState.Success) {
                        val stateStream = (uiState as WatchUiState.Success).watchData.stream
                        if (stateStream != null && stateStream.streams.isNotEmpty()) {
                            val settingsInteractionSource = remember { MutableInteractionSource() }
                            val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()
                            IconButton(onClick = { 
                                activeSettingsMenu = "Main"
                                showSettingsDialog = true 
                            }, interactionSource = settingsInteractionSource, modifier = Modifier.focusable(interactionSource = settingsInteractionSource).tvFocusGlow(isTv, isSettingsFocused, MaterialTheme.shapes.small)) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Quality", tint = Color.White)
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isTv && activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true) {
                        val pipInteractionSource = remember { MutableInteractionSource() }
                        val isPipFocused by pipInteractionSource.collectIsFocusedAsState()
                        IconButton(onClick = {
                            try {
                                val aspectRatio = Rational(16, 9)
                                val params = PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build()
                                activity?.enterPictureInPictureMode(params)
                            } catch (e: Exception) {}
                        }, interactionSource = pipInteractionSource, modifier = Modifier.focusable(interactionSource = pipInteractionSource).tvFocusGlow(isTv, isPipFocused, MaterialTheme.shapes.small)) {
                            Icon(Icons.Rounded.PictureInPicture, contentDescription = "PiP", tint = Color.White)
                        }
                    }
                    if (!isTv) {
                        val fullscreenInteractionSource = remember { MutableInteractionSource() }
                        val isFullscreenFocused by fullscreenInteractionSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            interactionSource = fullscreenInteractionSource,
                            modifier = Modifier.focusable(interactionSource = fullscreenInteractionSource).tvFocusGlow(isTv, isFullscreenFocused, MaterialTheme.shapes.small)
                        ) {
                            Icon(if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                        }
                    }
                }
                
                if (isFullscreen && uiState is WatchUiState.Success) {
                    val data = (uiState as WatchUiState.Success).watchData
                    if (data.type == "series" || data.type == "anime") {
                        val nextEp = data.episodes.find { it.episode == data.episode + 1 }
                        if (nextEp != null) {
                            val nextInteractionSource = remember { MutableInteractionSource() }
                            val isNextFocused by nextInteractionSource.collectIsFocusedAsState()
                            Button(
                                onClick = {
                                    navController.navigate("watch/${id}?s=${data.season}&e=${data.episode + 1}") {
                                        popUpTo("detail/${id}") { inclusive = false }
                                    }
                                },
                                interactionSource = nextInteractionSource,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).focusable(interactionSource = nextInteractionSource).tvFocusGlow(isTv, isNextFocused, MaterialTheme.shapes.extraLarge)
                            ) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Next Episode", color = Color.White)
                            }
                        } else {
                            val nextSeason = data.all_seasons?.find { it.seasonNumber > data.season }
                            if (nextSeason != null) {
                                val nextSnInteractionSource = remember { MutableInteractionSource() }
                                val isNextSnFocused by nextSnInteractionSource.collectIsFocusedAsState()
                                Button(
                                    onClick = {
                                        navController.navigate("watch/${id}?s=${nextSeason.seasonNumber}&e=1") {
                                            popUpTo("detail/${id}") { inclusive = false }
                                        }
                                    },
                                    interactionSource = nextSnInteractionSource,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).focusable(interactionSource = nextSnInteractionSource).tvFocusGlow(isTv, isNextSnFocused, MaterialTheme.shapes.extraLarge)
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Next Season (${nextSeason.seasonNumber})", color = Color.White)
                                }
                            }
                        }
                    }
                }
            } // end isControlsVisible && !screenLocked && !isInPipMode

            // Overlay to capture clicks when locked
            if (screenLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(screenLocked) {
                            detectTapGestures(
                                onTap = {
                                    isControlsVisible = !isControlsVisible
                                    interactionIndicator++
                                }
                            )
                        }
                )
            }

            // Lock toggle
            if (isControlsVisible && !isInPipMode && !isTv) {
                IconButton(
                    onClick = { 
                        screenLocked = !screenLocked
                        if (!screenLocked) {
                            isControlsVisible = true
                            interactionIndicator++
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                ) {
                    Icon(if (screenLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        if (showSettingsDialog && uiState is WatchUiState.Success) {
            val streamData = (uiState as WatchUiState.Success).watchData.stream
            val streams = streamData?.streams ?: emptyList()
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text(if (activeSettingsMenu == "Main") "Settings" else if (activeSettingsMenu == "Quality") "Select Quality" else "Playback Speed") },
                text = {
                    LazyColumn {
                        if (activeSettingsMenu == "Main") {
                            item {
                                val intSrc = remember { MutableInteractionSource() }
                                val isFoc by intSrc.collectIsFocusedAsState()
                                TextButton(onClick = { activeSettingsMenu = "Quality" }, interactionSource = intSrc, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusable(interactionSource = intSrc).tvFocusGlow(isTv, isFoc, MaterialTheme.shapes.small)) {
                                    Text("Quality", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            item {
                                val intSrc2 = remember { MutableInteractionSource() }
                                val isFoc2 by intSrc2.collectIsFocusedAsState()
                                TextButton(onClick = { activeSettingsMenu = "Speed" }, interactionSource = intSrc2, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusable(interactionSource = intSrc2).tvFocusGlow(isTv, isFoc2, MaterialTheme.shapes.small)) {
                                    Text("Playback Speed", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        } else if (activeSettingsMenu == "Quality") {
                            items(streams) { stream ->
                                val isSelected = selectedQualityUrl == stream.url || (selectedQualityUrl == null && stream.url == selectBestStreamUrl(streamData!!))
                                val interactionSource = remember { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()

                                Text(
                                    text = stream.quality_label + if (stream.codec.isNotEmpty()) " (${stream.codec})" else "",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                        .clickable {
                                            selectedQualityUrl = stream.url
                                            showSettingsDialog = false
                                        }
                                        .focusable(interactionSource = interactionSource)
                                        .tvFocusGlow(isTv, isFocused, MaterialTheme.shapes.small)
                                        .padding(16.dp)
                                )
                            }
                        } else if (activeSettingsMenu == "Speed") {
                            items(playbackSpeeds) { speed ->
                                val isSelected = playbackSpeed == speed
                                val interactionSource = remember { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()

                                Text(
                                    text = "${speed}x",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                        .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                        .clickable {
                                            playbackSpeed = speed
                                            exoPlayer?.setPlaybackSpeed(speed)
                                            showSettingsDialog = false
                                        }
                                        .focusable(interactionSource = interactionSource)
                                        .tvFocusGlow(isTv, isFocused, MaterialTheme.shapes.small)
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    val confInt = remember { MutableInteractionSource() }
                    val isConfFoc by confInt.collectIsFocusedAsState()
                    TextButton(onClick = {
                        if (activeSettingsMenu != "Main") {
                            activeSettingsMenu = "Main"
                        } else {
                            showSettingsDialog = false 
                        }
                    }, interactionSource = confInt, modifier = Modifier.focusable(interactionSource = confInt).tvFocusGlow(isTv, isConfFoc, MaterialTheme.shapes.small)) {
                        Text(if (activeSettingsMenu != "Main") "Back" else "Close")
                    }
                }
            )
        }

        if (!isFullscreen && !isInPipMode && uiState is WatchUiState.Success) {
            val data = (uiState as WatchUiState.Success).watchData
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = data.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (data.type == "series" || data.type == "anime") {
                            Text(text = "Season ${data.season} · Episode ${data.episode}", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                            
                            val nextEp = data.episodes.find { it.episode == data.episode + 1 }
                            if (nextEp != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        navController.navigate("watch/${id}?s=${data.season}&e=${data.episode + 1}") {
                                            popUpTo("detail/${id}") { inclusive = false }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Next Episode: E${nextEp.episode} ${nextEp.title ?: ""}")
                                }
                            } else {
                                val nextSeason = data.all_seasons.find { it.seasonNumber > data.season }
                                if (nextSeason != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            navController.navigate("watch/${id}?s=${nextSeason.seasonNumber}&e=1") {
                                                popUpTo("detail/${id}") { inclusive = false }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Next Season (${nextSeason.seasonNumber})")
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (data.episodes.isNotEmpty() && (data.type == "series" || data.type == "anime")) {
                    item {
                        Text(text = "Episodes", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(16.dp))
                    }
                    items(data.episodes) { ep ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Card(
                            onClick = { 
                                navController.navigate("watch/${id}?s=${data.season}&e=${ep.episode}") {
                                    popUpTo("detail/${id}") { inclusive = false }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            interactionSource = interactionSource,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFocused) Color.DarkGray.copy(alpha = 0.8f) else if (ep.episode == data.episode) Color.DarkGray.copy(alpha = 0.3f) else Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ep.thumbnail ?: data.metadata?.cover,
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(120.dp).height(68.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(text = "E${ep.episode} ${ep.title ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        }
                    }
                }
                
                val successState = uiState as? WatchUiState.Success
                val related = successState?.related ?: emptyList()
                val trendingMovies = successState?.trendingMovies ?: emptyList()
                val trendingSeries = successState?.trendingSeries ?: emptyList()
                
                if (related.isNotEmpty()) {
                    item {
                        Text(
                            text = "Explore More",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(related) { relatedItem ->
                                com.nzr.ui.shared.StandardContentCard(
                                    item = relatedItem,
                                    onClick = { navController.navigate("detail/${android.net.Uri.encode(relatedItem.subject_id ?: "")}") }
                                )
                            }
                        }
                    }
                }
                
                if (trendingMovies.isNotEmpty()) {
                    item {
                        Text(
                            text = "Trending Movies",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(trendingMovies) { relatedItem ->
                                com.nzr.ui.shared.StandardContentCard(
                                    item = relatedItem,
                                    onClick = { navController.navigate("detail/${android.net.Uri.encode(relatedItem.subject_id ?: "")}") }
                                )
                            }
                        }
                    }
                }
                
                if (trendingSeries.isNotEmpty()) {
                    item {
                        Text(
                            text = "Trending TV Shows",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 32.dp)
                        ) {
                            items(trendingSeries) { relatedItem ->
                                com.nzr.ui.shared.StandardContentCard(
                                    item = relatedItem,
                                    onClick = { navController.navigate("detail/${android.net.Uri.encode(relatedItem.subject_id ?: "")}") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun selectBestStreamUrl(stream: StreamResponse): String {
    if (stream.hls.isNotEmpty()) return stream.hls.first().url
    val h264 = stream.streams.firstOrNull { it.codec.contains("h264") }
    if (h264 != null) return h264.url
    if (stream.streams.isNotEmpty()) return stream.streams.first().url
    if (stream.dash.isNotEmpty()) return stream.dash.first().url
    return stream.best_url ?: ""
}
