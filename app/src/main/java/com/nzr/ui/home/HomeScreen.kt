package com.nzr.ui.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.nzr.ui.shared.tvFocusGlow
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nzr.api.ApiClient
import com.nzr.api.models.ContentItem
import com.nzr.api.models.HomeResponse
import com.nzr.ui.shared.StandardContentCard
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import androidx.compose.animation.animateColorAsState

fun Exception.toUserFriendlyMessage(): String {
    return when (this) {
        is retrofit2.HttpException -> {
            when (this.code()) {
                502 -> "Our streaming servers are temporarily down or experiencing high traffic. Please try again soon."
                404 -> "The requested content could not be found."
                else -> "An unexpected connection error occurred (${this.code()}). Please check your internet and try again."
            }
        }
        is java.net.UnknownHostException -> "No internet connection. Please check your network connection."
        is java.net.SocketTimeoutException -> "The connection timed out. Please try again."
        else -> "We're having trouble loading content right now. Please try again later."
    }
}

@Composable
fun HomeHeroSection(item: ContentItem, navController: NavController, onColorsLoaded: (Color, Color) -> Unit = { _, _ -> }) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 300.dp else 550.dp)
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(item.coverUrlResolved ?: "")
                .allowHardware(false)
                .build(),
            contentDescription = item.title ?: "Unknown",
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                val drawable = state.result.drawable
                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                        val dominant = palette?.dominantSwatch?.rgb ?: palette?.mutedSwatch?.rgb
                        val vibrant = palette?.vibrantSwatch?.rgb ?: palette?.darkVibrantSwatch?.rgb
                        if (dominant != null && vibrant != null) {
                            onColorsLoaded(Color(dominant).copy(alpha = 0.6f), Color(vibrant).copy(alpha = 0.6f))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 50f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = if (isTv) 16.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.title ?: "Unknown",
                style = if (isTv) MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Subtitle with year and rating
            val subtitleInfo = mutableListOf<String>()
            item.year?.takeIf { it.isNotBlank() }?.let { subtitleInfo.add(it) }
            item.ratingValue?.let { subtitleInfo.add("⭐ $it") }
            item.content_rating?.takeIf { it.isNotBlank() }?.let { subtitleInfo.add(it) }
            
            if (subtitleInfo.isNotEmpty()) {
                Text(
                    text = subtitleInfo.joinToString("  •  "),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(if (isTv) 16.dp else 24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { navController.navigate("watch/${android.net.Uri.encode(item.subject_id ?: "")}?s=0&e=1") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(if (isTv) 44.dp else 56.dp)
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(if (isTv) 20.dp else 28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play", color = Color.Black, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                }
                
                Button(
                    onClick = { navController.navigate("detail/${android.net.Uri.encode(item.subject_id ?: "")}") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x66444444)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(if (isTv) 44.dp else 56.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(if (isTv) 20.dp else 24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("More Info", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                }
            }
        }
    }
}

class HomeViewModel(private val watchProgressRepository: com.nzr.data.WatchProgressRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Observe progress
    val continueWatching = watchProgressRepository.allProgress.map { list -> list.filter { it.progressMs > 0L } }

    private val _isFetchingEndless = MutableStateFlow(false)
    val isFetchingEndless = _isFetchingEndless.asStateFlow()

    private var endlessPage = 1
    private var hasMoreEndless = true
    private val _endlessMovies = MutableStateFlow<List<com.nzr.api.models.ContentItem>>(emptyList())
    val endlessMovies = _endlessMovies.asStateFlow()

    init {
        viewModelScope.launch {
            val threeDaysAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
            watchProgressRepository.clearOldProgress(threeDaysAgo)
        }
        fetchHome()
        fetchEndlessMovies()
    }

    fun deleteProgress(id: String) {
        viewModelScope.launch {
            watchProgressRepository.deleteById(id)
        }
    }

    private fun fetchHome() {
        viewModelScope.launch {
            try {
                _uiState.value = HomeUiState.Loading
                val trending = ApiClient.service.getTrending(page = 1)
                
                val sections = listOf(
                    com.nzr.api.models.HomeSection(
                        id = "trending",
                        title = "Trending Now",
                        items = (trending.data?.items?.flatMap { it.allSubjects } ?: emptyList()).shuffled()
                    ),
                    com.nzr.api.models.HomeSection(
                        id = "movies",
                        title = "Movies To Watch",
                        items = ApiClient.service.getTrendingMovies(page = 1).data?.items?.flatMap { it.allSubjects } ?: emptyList()
                    ),
                    com.nzr.api.models.HomeSection(
                        id = "series",
                        title = "Top TV Series",
                        items = ApiClient.service.getTrendingSeries(page = 1).data?.items?.flatMap { it.allSubjects } ?: emptyList()
                    )
                )
                _uiState.value = HomeUiState.Success(com.nzr.api.models.HomeResponse(sections = sections))
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.toUserFriendlyMessage())
            }
        }
    }

    fun fetchEndlessMovies() {
        if (_isFetchingEndless.value || !hasMoreEndless) return
        _isFetchingEndless.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.service.getTrending(page = endlessPage + 1)
                val results = response.data?.items?.flatMap { it.allSubjects } ?: emptyList()
                if (results.isEmpty()) {
                    hasMoreEndless = false
                } else {
                    _endlessMovies.value = _endlessMovies.value + results
                    endlessPage++
                }
            } catch (e: Exception) {
                // Ignore endless error
            } finally {
                _isFetchingEndless.value = false
            }
        }
    }
}

class HomeViewModelFactory(private val watchProgressRepository: com.nzr.data.WatchProgressRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(watchProgressRepository) as T
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val data: HomeResponse) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@Composable
fun AnimatedMeshBackground(modifier: Modifier = Modifier, color1: Color = Color(0x776200EE), color2: Color = Color(0xaa3700B3)) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase2"
    )

    androidx.compose.foundation.Canvas(modifier = modifier.blur(32.dp)) {
        val width = size.width
        val height = size.height

        val gradient1 = Brush.radialGradient(
            colors = listOf(
                color1,
                Color.Transparent
            ),
            center = androidx.compose.ui.geometry.Offset(
                x = width / 2 + (width / 2.5f) * kotlin.math.cos(Math.toRadians(phase1.toDouble())).toFloat(),
                y = height / 2 + (height / 2.5f) * kotlin.math.sin(Math.toRadians(phase2.toDouble())).toFloat()
            ),
            radius = width
        )
        val gradient2 = Brush.radialGradient(
            colors = listOf(
                color2,
                Color.Transparent
            ),
            center = androidx.compose.ui.geometry.Offset(
                x = width / 2 + (width / 2.5f) * kotlin.math.sin(Math.toRadians(phase2.toDouble())).toFloat(),
                y = height / 2 + (height / 2.5f) * kotlin.math.cos(Math.toRadians(phase1.toDouble())).toFloat()
            ),
            radius = width
        )
        drawRect(brush = gradient1)
        drawRect(brush = gradient2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(rootNavController: NavController, onOpenDrawer: () -> Unit = {}, onSearchClick: () -> Unit = {}, paddingBottom: androidx.compose.ui.unit.Dp = 0.dp, context: android.content.Context = androidx.compose.ui.platform.LocalContext.current) {
    val database = remember { com.nzr.data.AppDatabase.getDatabase(context) }
    val repository = remember { com.nzr.data.WatchProgressRepository(database.watchProgressDao()) }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    
    val uiState by viewModel.uiState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState(initial = emptyList())
    val endlessMovies by viewModel.endlessMovies.collectAsState(initial = emptyList())
    val isFetchingEndless by viewModel.isFetchingEndless.collectAsState()
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolledToEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && totalItems > 0 && lastVisibleItem.index >= (totalItems - 4).coerceAtLeast(0)
        }
    }

    LaunchedEffect(isScrolledToEnd) {
        if (isScrolledToEnd) {
            viewModel.fetchEndlessMovies()
        }
    }

    var color1 by remember { mutableStateOf(Color(0x776200EE)) }
    var color2 by remember { mutableStateOf(Color(0xaa3700B3)) }
    
    val animColor1 by animateColorAsState(targetValue = color1, animationSpec = tween(2000), label = "animColor1")
    val animColor2 by animateColorAsState(targetValue = color2, animationSpec = tween(2000), label = "animColor2")

    val isTv = remember { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedMeshBackground(modifier = Modifier.fillMaxSize(), color1 = animColor1, color2 = animColor2)

        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    com.nzr.ui.shared.SkeletonDetail()
                }
            }
            is HomeUiState.Success -> {
                // Find turning items for hero
                val sections = state.data.sections ?: emptyList()
                val allItems = sections.flatMap { it.items } + endlessMovies
                val heroItem = remember(allItems) {
                    val highRated = allItems.filter { (it.ratingValue ?: 0.0) >= 8.0 }
                    val candidates = if (highRated.isNotEmpty()) highRated else allItems
                    candidates.randomOrNull() ?: allItems.firstOrNull()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = paddingBottom)
                ) {
                if (heroItem != null) {
                    item {
                        HomeHeroSection(heroItem, rootNavController) { c1, c2 ->
                            color1 = c1
                            color2 = c2
                        }
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(
                                text = "Continue Watching",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(continueWatching, key = { it.subject_id }) { progress ->
                                    var showRemove by remember { mutableStateOf(false) }
                                    Box {
                                        val contentItem = ContentItem(
                                            subject_id = progress.subject_id,
                                            title = progress.title,
                                            cover_url = progress.cover,
                                            type_id = 9 // Mock type for now
                                        )
                                        val progressRatio = if (progress.durationMs > 0) {
                                            progress.progressMs.toFloat() / progress.durationMs.toFloat()
                                        } else 0f
                                        StandardContentCard(
                                            item = contentItem,
                                            onLongClick = { showRemove = !showRemove },
                                            progressRatio = progressRatio,
                                            onClick = { 
                                                if (showRemove) {
                                                    showRemove = false
                                                } else {
                                                    rootNavController.navigate("watch/${android.net.Uri.encode(progress.subject_id)}?s=${progress.season}&e=${progress.episode}") 
                                                }
                                            }
                                        )
                                        if (showRemove) {
                                            IconButton(
                                                onClick = { viewModel.deleteProgress(progress.subject_id) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(36.dp)
                                                    .background(Color.Red.copy(alpha=0.8f), shape=androidx.compose.foundation.shape.CircleShape)
                                            ) {
                                                Icon(Icons.Rounded.Close, contentDescription="Remove", tint=Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(sections) { section ->
                    SectionRow(section.title, section.items, rootNavController)
                }

                if (endlessMovies.isNotEmpty()) {
                    val columns = if (isTv) 5 else 3

                    item {
                        Text(
                            "More to Discover",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(endlessMovies.chunked(columns), key = { row -> row.first().subject_id!! }) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    StandardContentCard(
                                        item = item,
                                        onClick = { rootNavController.navigate("detail/${android.net.Uri.encode(item.subject_id ?: "")}") }
                                    )
                                }
                            }
                            val emptyCount = columns - rowItems.size
                            repeat(emptyCount) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (isFetchingEndless) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
        is HomeUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
        }
        
        // Header with Search Bar and Hamburger Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val searchIntSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isSearchFoc by searchIntSrc.collectIsFocusedAsState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(Color(0x33FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .focusable(interactionSource = searchIntSrc)
                    .tvFocusGlow(isTv, isSearchFoc, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.LightGray)
                    Spacer(Modifier.width(8.dp))
                    Text("Search Movies, Series, Anime...", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            val menuIntSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isMenuFoc by menuIntSrc.collectIsFocusedAsState()
            IconButton(
                onClick = onOpenDrawer,
                interactionSource = menuIntSrc,
                modifier = Modifier
                    .size(48.dp)
                    .tvFocusGlow(isTv, isMenuFoc, androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x33FFFFFF), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = Color.White)
            }
        }
    }
}


@Composable
fun SectionRow(title: String, items: List<ContentItem>, navController: NavController) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.subject_id!! }) { item ->
                StandardContentCard(
                    item = item,
                    onClick = { navController.navigate("detail/${android.net.Uri.encode(item.subject_id ?: "")}") }
                )
            }
        }
    }
}
