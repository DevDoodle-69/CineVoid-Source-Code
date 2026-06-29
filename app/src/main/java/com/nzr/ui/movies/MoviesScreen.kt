package com.nzr.ui.movies

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.nzr.api.models.BrowseResponse
import com.nzr.ui.shared.SkeletonGrid
import com.nzr.ui.shared.StandardContentCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.nzr.ui.home.toUserFriendlyMessage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

class MoviesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MoviesUiState>(MoviesUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching = _isFetching.asStateFlow()

    private var currentPage = 1
    private var currentType = "movie"
    private var hasMore = true
    private val allItems = mutableListOf<com.nzr.api.models.ContentItem>()

    fun fetchContent(type: String, isLoadMore: Boolean = false) {
        if (_isFetching.value || (!hasMore && isLoadMore)) return
        _isFetching.value = true

        if (!isLoadMore) {
            currentType = type
            currentPage = 1
            allItems.clear()
            _uiState.value = MoviesUiState.Loading
        }

        viewModelScope.launch {
            try {
                val results = when (currentType) {
                    "movie" -> ApiClient.service.getTrendingMovies(page = currentPage).data?.items?.flatMap { it.allSubjects } ?: emptyList()
                    "series" -> ApiClient.service.getTrendingSeries(page = currentPage).data?.items?.flatMap { it.allSubjects } ?: emptyList()
                    else -> ApiClient.service.getTrending(page = currentPage).data?.items?.flatMap { it.allSubjects } ?: emptyList()
                }
                if (results.isEmpty()) {
                    hasMore = false
                } else {
                    val currentIds = allItems.mapNotNull { it.subject_id }.toSet()
                    val newUnique = results.filter { it.subject_id !in currentIds && !it.subject_id.isNullOrEmpty() }
                    allItems.addAll(newUnique)
                    currentPage++
                }

                _uiState.value = MoviesUiState.Success(BrowseResponse(results = allItems.toList()))
            } catch (e: Exception) {
                if (!isLoadMore) {
                    _uiState.value = MoviesUiState.Error(e.toUserFriendlyMessage())
                }
            } finally {
                _isFetching.value = false
            }
        }
    }

    fun loadMore() {
        if (uiState.value is MoviesUiState.Success) {
            fetchContent(currentType, isLoadMore = true)
        }
    }
}

sealed class MoviesUiState {
    object Loading : MoviesUiState()
    data class Success(val data: BrowseResponse) : MoviesUiState()
    data class Error(val message: String) : MoviesUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(rootNavController: NavController, type: String, paddingBottom: androidx.compose.ui.unit.Dp = 0.dp, viewModel: MoviesViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()

    LaunchedEffect(type) {
        viewModel.fetchContent(type)
    }

    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    
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
            viewModel.loadMore()
        }
    }

    val title = when (type) {
        "movie" -> "Movies"
        "series" -> "TV Series"
        else -> "Content"
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember(context) { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0,0,0,0)
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, bottom = 8.dp)
            )
            when (val state = uiState) {
                is MoviesUiState.Loading -> {
                    Box(Modifier.fillMaxSize()) {
                        SkeletonGrid()
                    }
                }
                is MoviesUiState.Success -> {
                    LazyVerticalGrid(
                        state = listState,
                        columns = GridCells.Adaptive(minSize = if (isTv) 180.dp else 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + paddingBottom),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val elements = state.data.results ?: emptyList()
                        items(elements, key = { it.subject_id!! }) { item ->
                            StandardContentCard(
                                item = item,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { rootNavController.navigate("detail/${android.net.Uri.encode(item.subject_id ?: "")}") }
                            )
                        }
                        if (isFetching) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
                is MoviesUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
}
