package com.nzr.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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
import com.nzr.ui.shared.SkeletonGrid
import com.nzr.ui.shared.StandardContentCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.nzr.ui.home.toUserFriendlyMessage
import androidx.compose.ui.text.style.TextAlign

class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState = _uiState.asStateFlow()
    
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private var searchJob: Job? = null
    
    private val _isFetching = MutableStateFlow(false)
    val isFetching = _isFetching.asStateFlow()
    
    private var currentPage = 1
    private var hasMore = true
    private val allItems = mutableListOf<ContentItem>()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.length > 2) {
            searchJob = viewModelScope.launch {
                delay(400)
                performSearch(newQuery, false)
            }
        } else if (newQuery.isEmpty()) {
            _uiState.value = SearchUiState.Initial
        }
    }

    private suspend fun performSearch(query: String, isLoadMore: Boolean) {
        if (_isFetching.value || (!hasMore && isLoadMore)) return
        _isFetching.value = true

        if (!isLoadMore) {
            currentPage = 1
            allItems.clear()
            _uiState.value = SearchUiState.Loading
        }

        try {
            val response = ApiClient.service.search(query = query, page = currentPage)
            val results = response.data?.all ?: emptyList()
            if (results.isEmpty()) {
                hasMore = false
            } else {
                val currentIds = allItems.mapNotNull { it.subject_id }.toSet()
                val newUnique = results.filter { it.subject_id !in currentIds && !it.subject_id.isNullOrEmpty() }
                allItems.addAll(newUnique)
                currentPage++
            }
            _uiState.value = SearchUiState.Success(allItems.toList())
        } catch (e: Exception) {
            if (!isLoadMore) {
                _uiState.value = SearchUiState.Error(e.toUserFriendlyMessage())
            }
        } finally {
            _isFetching.value = false
        }
    }
    
    fun loadMore() {
        if (uiState.value is SearchUiState.Success && query.value.length > 2) {
            viewModelScope.launch {
                performSearch(query.value, true)
            }
        }
    }
}

sealed class SearchUiState {
    object Initial : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<ContentItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(rootNavController: NavController, paddingBottom: androidx.compose.ui.unit.Dp = 0.dp, viewModel: SearchViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    var text by remember { mutableStateOf("") }
    
    val listState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val isScrolledToEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && totalItems > 0 && lastVisibleItem.index >= (totalItems - 4).coerceAtLeast(0)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember(context) { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }

    LaunchedEffect(isScrolledToEnd) {
        if (isScrolledToEnd) {
            viewModel.loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
        OutlinedTextField(
            value = text,
            onValueChange = { 
                text = it
                viewModel.onQueryChange(it) 
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search movies, series...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = MaterialTheme.shapes.extraLarge
        )

        when (val state = uiState) {
            is SearchUiState.Initial -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Search for anything", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is SearchUiState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    SkeletonGrid()
                }
            }
            is SearchUiState.Success -> {
                if (state.results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        state = listState,
                        columns = GridCells.Adaptive(minSize = if (isTv) 180.dp else 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + paddingBottom),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.results, key = { it.subject_id!! }) { item ->
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
            }
            is SearchUiState.Error -> {
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
