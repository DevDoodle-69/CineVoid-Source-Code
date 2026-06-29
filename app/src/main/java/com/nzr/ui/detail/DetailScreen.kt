package com.nzr.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nzr.api.ApiClient
import com.nzr.api.models.ContentItem
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import com.nzr.api.models.Episode
import com.nzr.ui.home.SectionRow
import com.nzr.ui.shared.SkeletonDetail
import com.nzr.ui.shared.tvFocusGlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.nzr.ui.home.toUserFriendlyMessage
import androidx.compose.ui.text.style.TextAlign

class DetailViewModel(private val id: String) : ViewModel() {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState = _uiState.asStateFlow()
    
    private var currentDetail: ContentItem? = null
    private var currentRelated: List<ContentItem> = emptyList()

    init {
        fetchDetail()
    }

    private fun fetchDetail() {
        viewModelScope.launch {
            try {
                _uiState.value = DetailUiState.Loading
                val response = ApiClient.service.getTestLiveItem(id)
                var detailUnmapped = response.subject
                
                if (detailUnmapped == null) {
                    _uiState.value = DetailUiState.Error("Failed to fetch details")
                    return@launch
                }

                // If it's a series, always fetch full details to ensure we get all seasons
                if (detailUnmapped.subject_type == 2) {
                    try {
                        val seriesDetails = ApiClient.service.getInfo(id).data?.subject
                        if (seriesDetails != null && !seriesDetails.seasons.isNullOrEmpty()) {
                            detailUnmapped = detailUnmapped.copy(seasons = seriesDetails.seasons)
                        } else if (seriesDetails != null && (seriesDetails.total_seasons ?: 0) > 0) {
                            val maxS = seriesDetails.total_seasons!!
                            detailUnmapped = detailUnmapped.copy(seasons = (1..maxS).map { com.nzr.api.models.SeasonInfo(season = it) })
                        } else if (detailUnmapped.se_num != null && detailUnmapped.se_num!! > 0) {
                            detailUnmapped = detailUnmapped.copy(seasons = (1..detailUnmapped.se_num!!).map { com.nzr.api.models.SeasonInfo(season = it) })
                        }
                    } catch (e: Exception) {
                        try {
                            if (detailUnmapped.se_num != null && detailUnmapped.se_num!! > 0) {
                                detailUnmapped = detailUnmapped.copy(seasons = (1..detailUnmapped.se_num!!).map { com.nzr.api.models.SeasonInfo(season = it) })
                            }
                        } catch (e2: Exception) {}
                    }
                }
                
                val detail = detailUnmapped.copy(
                    resource_detectors = listOf(com.nzr.api.models.ResourceDetector(resolution_list = response.streams))
                )
                
                currentDetail = detail
                // You can fetch related via search or just omit it for now
                val related = try { ApiClient.service.getTrending(1).data?.items?.flatMap { it.allSubjects }?.shuffled()?.take(12) ?: emptyList() } catch(e: Exception) { emptyList() }
                val trendingMovies = try { ApiClient.service.getTrendingMovies(2).data?.items?.flatMap { it.allSubjects }?.shuffled()?.take(12) ?: emptyList() } catch(e: Exception) { emptyList() }
                val trendingSeries = try { ApiClient.service.getTrendingSeries(2).data?.items?.flatMap { it.allSubjects }?.shuffled()?.take(12) ?: emptyList() } catch(e: Exception) { emptyList() }
                currentRelated = related
                
                val resolutionList = detail.resource_detectors.firstOrNull()?.resolution_list ?: emptyList()
                val seasonsMap = resolutionList.groupBy { it.se }
                
                val newSeasons = seasonsMap.keys.sorted().map { s ->
                    com.nzr.api.models.SeasonInfo(
                        season = s,
                        episode_count = seasonsMap[s]?.map { it.ep }?.distinct()?.size ?: 0
                    )
                }

                val seasonsFromApi = detail.seasons
                var finalDetail = detail
                
                if (detail.subject_type == 2 && (seasonsFromApi.isNullOrEmpty() || seasonsFromApi.size <= 2)) {
                    // Parallel probing to discover all seasons efficiently
                    val probedSeasons = mutableListOf<com.nzr.api.models.SeasonInfo>()
                    try {
                        kotlinx.coroutines.coroutineScope {
                            val deferreds = (1..20).map { s ->
                                async {
                                    try {
                                        val probeRes = ApiClient.service.getTestLiveItem(id, s)
                                        val eps = probeRes.streams.filter { it.se == s || (it.se == 0 && s == 1) }
                                        if (eps.isNotEmpty()) {
                                            com.nzr.api.models.SeasonInfo(
                                                season = s,
                                                episode_count = eps.map { it.ep }.distinct().size
                                            )
                                        } else null
                                    } catch (e: Exception) { null }
                                }
                            }
                            val results = deferreds.awaitAll()
                            results.filterNotNull().forEach { probedSeasons.add(it) }
                        }
                    } catch (e: Exception) {}
                    
                    if (probedSeasons.isNotEmpty()) {
                        probedSeasons.sortBy { it.seasonNumber }
                        finalDetail = detail.copy(seasons = probedSeasons)
                    } else if (seasonsFromApi.isNullOrEmpty() && newSeasons.isNotEmpty()) {
                        val filteredSeasons = newSeasons.filter { (it.seasonNumber ?: 0) > 0 }
                        if (filteredSeasons.isNotEmpty()) {
                            finalDetail = detail.copy(seasons = filteredSeasons)
                        }
                    }
                } else if (detail.subject_type == 2 && seasonsFromApi.isNullOrEmpty() && newSeasons.isNotEmpty()) {
                    val filteredSeasons = newSeasons.filter { (it.seasonNumber ?: 0) > 0 }
                    if (filteredSeasons.isNotEmpty()) {
                        finalDetail = detail.copy(seasons = filteredSeasons)
                    }
                }
                
                currentDetail = finalDetail

                val firstSeason = finalDetail.seasons?.firstOrNull()?.seasonNumber ?: (if (finalDetail.subject_type == 2) 1 else 0)

                var rawEps = seasonsMap[firstSeason] ?: emptyList()
                if (finalDetail.subject_type == 2 && rawEps.isEmpty()) {
                    try {
                        val epsResponse = ApiClient.service.getTestLiveItem(id, firstSeason)
                        rawEps = epsResponse.streams.filter { it.se == firstSeason || it.se == 0 || epsResponse.streams.all { s -> s.se == 0 } }
                    } catch (e: Exception) {}
                }
                
                val episodesList = rawEps.distinctBy { it.ep }.map {
                    com.nzr.api.models.Episode(
                        episode = it.ep,
                        title = it.title,
                        thumbnail = detail.coverUrlResolved
                    )
                }.sortedBy { it.episode }
                
                _uiState.value = DetailUiState.Success(finalDetail, related, trendingMovies, trendingSeries, episodesList, firstSeason)
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.toUserFriendlyMessage())
            }
        }
    }
    
    fun selectSeason(seasonNo: Int) {
        val detail = currentDetail ?: return
        val related = currentRelated
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                var tMovies = emptyList<ContentItem>()
                var tSeries = emptyList<ContentItem>()
                if (currentState is DetailUiState.Success) {
                    tMovies = currentState.trendingMovies
                    tSeries = currentState.trendingSeries
                }

                // Fetch new episodes for the selected season
                val response = ApiClient.service.getTestLiveItem(id, seasonNo)
                val newEps = response.streams

                val episodesList = newEps.filter { it.se == seasonNo || it.se == 0 || newEps.all { s -> s.se == 0 } }.distinctBy { it.ep }.map {
                    com.nzr.api.models.Episode(
                        episode = it.ep,
                        title = it.title,
                        thumbnail = detail.coverUrlResolved
                    )
                }.sortedBy { it.episode }
                
                _uiState.value = DetailUiState.Success(detail, related, tMovies, tSeries, episodesList, seasonNo)
            } catch (e: Exception) {
                // Keep old state
            }
        }
    }
}

class DetailViewModelFactory(private val id: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailViewModel(id) as T
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val detail: ContentItem,
        val related: List<ContentItem>,
        val trendingMovies: List<ContentItem>,
        val trendingSeries: List<ContentItem>,
        val episodes: List<Episode>,
        val selectedSeason: Int
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(navController: NavController, id: String) {
    val viewModel: DetailViewModel = viewModel(factory = DetailViewModelFactory(id))
    val uiState by viewModel.uiState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0,0,0,0)
    ) { innerPadding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    SkeletonDetail()
                }
            }
            is DetailUiState.Success -> {
                val detail = state.detail
                LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isTv) 220.dp else 400.dp)
                                .focusable(interactionSource = interactionSource)
                                .border(if (isFocused) 3.dp else 0.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent)
                        ) {
                            AsyncImage(
                                model = detail.stills?.url ?: detail.coverUrlResolved ?: "",
                                contentDescription = detail.title ?: "Unknown",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.8f), MaterialTheme.colorScheme.background),
                                                startY = 0f
                                            ))
                                    )
                            
                            // Back Button overlays Header
                            if (!isTv) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.align(Alignment.TopStart).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(if (isTv) 16.dp else 16.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                AsyncImage(
                                    model = detail.coverUrlResolved ?: "",
                                    contentDescription = detail.title ?: "Unknown",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(if (isTv) 100.dp else 120.dp)
                                        .height(if (isTv) 150.dp else 180.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .border(2.dp, Color.White.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    val titleInteractionSource = remember { MutableInteractionSource() }
                                    val isTitleFocused by titleInteractionSource.collectIsFocusedAsState()
                                    Text(
                                        text = detail.title ?: "Unknown", 
                                        style = MaterialTheme.typography.displayMedium,
                                        color = Color.White,
                                        modifier = Modifier
                                            .focusable(interactionSource = titleInteractionSource)
                                            .background(if (isTitleFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            .padding(if (isTitleFocused) 4.dp else 0.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = detail.release_date ?: detail.year ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                        Spacer(Modifier.width(8.dp))
                                        if (detail.ratingValue != null) {
                                            Text(text = "⭐ ${detail.ratingValue}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        if (!detail.duration.isNullOrEmpty()) {
                                            Text(text = "· ${detail.duration}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (!detail.genre.isNullOrEmpty()) {
                                        Text(text = detail.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primaryContainer)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(16.dp))
                                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                                val btnIntSrc = remember { MutableInteractionSource() }
                                val isBtnFoc by btnIntSrc.collectIsFocusedAsState()
                                Button(
                                    onClick = { 
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        val id = detail.subject_id
                                    if (!id.isNullOrEmpty()) {
                                        val isSeries = detail.subject_type == 2
                                        val defS = if (isSeries) state.selectedSeason else 0
                                        val defE = if (isSeries) {
                                            state.episodes.firstOrNull()?.episode ?: 1
                                        } else 1
                                        navController.navigate("watch/${android.net.Uri.encode(id)}?s=$defS&e=$defE") 
                                    }
                                },
                                interactionSource = btnIntSrc,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                modifier = Modifier
                                    .then(if (isTv) Modifier.width(160.dp) else Modifier.fillMaxWidth())
                                    .height(if (isTv) 44.dp else 52.dp)
                                    .tvFocusGlow(isTv, isBtnFoc, MaterialTheme.shapes.extraLarge)
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            }
                            Spacer(Modifier.height(16.dp))
                            if (!detail.description.isNullOrEmpty()) {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .then(if (isTv) Modifier.widthIn(max = 800.dp) else Modifier.fillMaxWidth())
                                        .focusable(interactionSource = interactionSource)
                                        .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .padding(if (isFocused) 8.dp else 0.dp)
                                ) {
                                    Text(
                                        text = detail.description, 
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isFocused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (detail.staff_list.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Text("Cast & Crew", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(detail.staff_list) { staff ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp).focusable()) {
                                            AsyncImage(
                                                model = staff.avatar_url,
                                                contentDescription = staff.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(staff.name ?: "Unknown", style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 1)
                                            if (!staff.character.isNullOrEmpty()) {
                                                Text(staff.character, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E0E0), maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (detail.subject_type == 2 && (state.episodes.isNotEmpty() || (detail.seasons != null && detail.seasons.isNotEmpty()))) {
                        item {
                            Column(
                                modifier = Modifier
                                    .then(if (isTv) Modifier.widthIn(max = 800.dp) else Modifier.fillMaxWidth())
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Episodes", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                
                                if (detail.seasons != null && detail.seasons.size > 1) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    ) {
                                        items(detail.seasons) { s ->
                                            val isSelected = s.seasonNumber == state.selectedSeason
                                            val chipIntSrc = remember { MutableInteractionSource() }
                                            val isChipFoc by chipIntSrc.collectIsFocusedAsState()
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.selectSeason(s.seasonNumber) },
                                                label = { Text("Season ${s.seasonNumber}") },
                                                interactionSource = chipIntSrc,
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier.focusable(interactionSource = chipIntSrc).tvFocusGlow(isTv, isChipFoc, MaterialTheme.shapes.small)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        items(state.episodes) { ep ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            
                            Card(
                                onClick = { 
                                    val subjectId = detail.subject_id
                                    if (!subjectId.isNullOrEmpty()) {
                                        navController.navigate("watch/${android.net.Uri.encode(subjectId)}?s=${state.selectedSeason}&e=${ep.episode}") 
                                    }
                                },
                                modifier = Modifier
                                    .then(if (isTv) Modifier.widthIn(max = 800.dp) else Modifier.fillMaxWidth())
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .tvFocusGlow(isTv, isFocused, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                interactionSource = interactionSource,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = if (!isTv && isFocused) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ep.thumbnail ?: detail.coverUrlResolved,
                                        contentDescription = "Episode thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(120.dp).height(68.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(text = "E${ep.episode} ${ep.title?.let { "· $it" } ?: ""}", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                    if (state.related.isNotEmpty()) {
                        item {
                            SectionRow("Explore More", state.related, navController)
                        }
                    }
                    if (state.trendingMovies.isNotEmpty()) {
                        item {
                            SectionRow("Trending Movies", state.trendingMovies, navController)
                        }
                    }
                    if (state.trendingSeries.isNotEmpty()) {
                        item {
                            SectionRow("Trending TV Shows", state.trendingSeries, navController)
                        }
                    }
                    item {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
            is DetailUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}
