package com.kanxi.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.bilibili.BilibiliVideoLink
import com.kanxi.app.bilibili.PlaybackUrlFactory
import com.kanxi.app.data.TextSizeLevel
import com.kanxi.app.playback.PlaybackScreen
import com.kanxi.app.ui.screens.CategoryManagementScreen
import com.kanxi.app.ui.screens.CategoryScreen
import com.kanxi.app.ui.screens.CollectionEditorScreen
import com.kanxi.app.ui.screens.CollectionManagementScreen
import com.kanxi.app.ui.screens.CollectionScreen
import com.kanxi.app.ui.screens.DetailScreen
import com.kanxi.app.ui.screens.FamilyManagementScreen
import com.kanxi.app.ui.screens.FavoritesScreen
import com.kanxi.app.ui.screens.HomeScreen
import com.kanxi.app.ui.screens.OperaEditorScreen
import com.kanxi.app.ui.screens.SearchScreen
import com.kanxi.app.ui.screens.SettingsScreen
import com.kanxi.app.ui.theme.FontSizePreference
import com.kanxi.app.ui.theme.KanxiTheme
import com.kanxi.app.ui.components.AccessibleDialog
import com.kanxi.app.util.DeviceNetwork
import kotlinx.coroutines.launch

private object Routes {
    const val Home = "home"
    const val Search = "search"
    const val Favorites = "favorites"
    const val Settings = "settings"
    const val FamilyManagement = "family-management"
    const val Categories = "categories"
    const val Collections = "collections"
    const val CategoryPattern = "category/{categoryId}"
    const val CollectionPattern = "collection/{collectionId}"
    const val CollectionEditorPattern = "collection-editor/{collectionId}"
    const val DetailPattern = "detail/{itemId}"
    const val EditorPattern = "editor/{itemId}"
    const val PlaybackPattern = "playback/{itemId}"

    fun category(id: Long) = "category/$id"
    fun collection(id: Long) = "collection/$id"
    fun collectionEditor(id: Long) = "collection-editor/$id"
    fun detail(id: Long) = "detail/$id"
    fun editor(id: Long) = "editor/$id"
    fun playback(id: Long) = "playback/$id"
}

@Composable
fun KanxiApp(
    viewModel: KanxiViewModel,
    pendingSharedText: String?,
    onSharedTextConsumed: () -> Unit,
) {
    val categories by viewModel.categories.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val textSizeLevel by viewModel.textSizeLevel.collectAsState()

    KanxiTheme(fontSizePreference = textSizeLevel.toThemePreference()) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val topLevelRoutes = setOf(Routes.Home, Routes.Search, Routes.Favorites)
        val showBottomBar = currentRoute in topLevelRoutes
        val isPlayback = currentRoute == Routes.PlaybackPattern
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var pendingPlaybackId by remember { mutableLongStateOf(0L) }

        fun showMessage(message: String) {
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        fun openPlayback(itemId: Long) {
            scope.launch {
                viewModel.recordOpened(itemId)
                navController.navigate(Routes.playback(itemId))
            }
        }

        fun requestPlayback(itemId: Long) {
            scope.launch {
                if (
                    DeviceNetwork.isUsingCellularData(context) &&
                    viewModel.shouldShowMobileDataWarning()
                ) {
                    pendingPlaybackId = itemId
                } else {
                    viewModel.recordOpened(itemId)
                    navController.navigate(Routes.playback(itemId))
                }
            }
        }

        if (pendingPlaybackId > 0L) {
            AccessibleDialog(
                title = "正在使用手机流量",
                confirmLabel = "继续播放",
                dismissLabel = "暂不播放",
                onConfirm = {
                    val itemId = pendingPlaybackId
                    pendingPlaybackId = 0L
                    viewModel.markMobileDataWarningShown()
                    openPlayback(itemId)
                },
                onDismiss = { pendingPlaybackId = 0L },
            ) {
                Text("播放戏曲可能消耗较多流量。是否继续？以后不再重复提醒。")
            }
        }

        LaunchedEffect(pendingSharedText) {
            if (!pendingSharedText.isNullOrBlank()) {
                navController.navigate(Routes.editor(0L)) {
                    launchSingleTop = true
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    KanxiBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isPlayback) Modifier else Modifier.padding(contentPadding)),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.Home,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.Home) {
                        HomeScreen(
                            categories = categories,
                            collections = collections,
                            allItems = allItems,
                            recentItems = recent,
                            onCategoryClick = { navController.navigate(Routes.category(it)) },
                            onCollectionClick = { navController.navigate(Routes.collection(it)) },
                            onItemClick = { navController.navigate(Routes.detail(it)) },
                            onPlay = ::requestPlayback,
                            onOpenSettings = { navController.navigate(Routes.Settings) },
                            onAddFirstOpera = { navController.navigate(Routes.editor(0L)) },
                        )
                    }
                    composable(Routes.Search) {
                        SearchScreen(
                            viewModel = viewModel,
                            onItemClick = { navController.navigate(Routes.detail(it)) },
                        )
                    }
                    composable(Routes.Favorites) {
                        FavoritesScreen(
                            favorites = favorites,
                            onItemClick = { navController.navigate(Routes.detail(it)) },
                        )
                    }
                    composable(Routes.CategoryPattern) { entry ->
                        val categoryId = entry.arguments?.getString("categoryId")?.toLongOrNull()
                        CategoryScreen(
                            viewModel = viewModel,
                            category = categories.firstOrNull { it.id == categoryId },
                            onBack = { navController.popBackStack() },
                            onItemClick = { navController.navigate(Routes.detail(it)) },
                            onPlay = ::requestPlayback,
                        )
                    }
                    composable(Routes.DetailPattern) { entry ->
                        val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                        DetailScreen(
                            viewModel = viewModel,
                            itemId = itemId,
                            onBack = { navController.popBackStack() },
                            onPlay = ::requestPlayback,
                        )
                    }
                    composable(Routes.Settings) {
                        SettingsScreen(
                            textSizeLevel = textSizeLevel,
                            onSetTextSize = viewModel::setTextSize,
                            onOpenFamilyManagement = {
                                navController.navigate(Routes.FamilyManagement)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.FamilyManagement) {
                        FamilyManagementScreen(
                            viewModel = viewModel,
                            categories = categories,
                            allItems = allItems,
                            onAddOpera = { navController.navigate(Routes.editor(0L)) },
                            onEditOpera = { navController.navigate(Routes.editor(it)) },
                            onManageCategories = { navController.navigate(Routes.Categories) },
                            onManageCollections = { navController.navigate(Routes.Collections) },
                            onBack = { navController.popBackStack() },
                            onMessage = ::showMessage,
                        )
                    }
                    composable(Routes.Categories) {
                        CategoryManagementScreen(
                            viewModel = viewModel,
                            categories = categories,
                            onBack = { navController.popBackStack() },
                            onMessage = ::showMessage,
                        )
                    }
                    composable(Routes.Collections) {
                        CollectionManagementScreen(
                            viewModel = viewModel,
                            collections = collections,
                            onBack = { navController.popBackStack() },
                            onEditCollection = { navController.navigate(Routes.collectionEditor(it)) },
                            onMessage = ::showMessage,
                        )
                    }
                    composable(Routes.CollectionPattern) { entry ->
                        val collectionId =
                            entry.arguments?.getString("collectionId")?.toLongOrNull()
                        val collection = collections.firstOrNull {
                            it.collection.id == collectionId
                        }?.collection
                        CollectionScreen(
                            viewModel = viewModel,
                            collection = collection,
                            onBack = { navController.popBackStack() },
                            onItemClick = { navController.navigate(Routes.detail(it)) },
                            onPlay = ::requestPlayback,
                        )
                    }
                    composable(Routes.CollectionEditorPattern) { entry ->
                        val collectionId =
                            entry.arguments?.getString("collectionId")?.toLongOrNull() ?: 0L
                        CollectionEditorScreen(
                            viewModel = viewModel,
                            collectionId = collectionId,
                            collections = collections,
                            allItems = allItems,
                            onBack = { navController.popBackStack() },
                            onMessage = ::showMessage,
                        )
                    }
                    composable(Routes.EditorPattern) { entry ->
                        val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                        OperaEditorScreen(
                            viewModel = viewModel,
                            itemId = itemId,
                            categories = categories,
                            initialSharedText = pendingSharedText.takeIf { itemId == 0L },
                            onSharedTextConsumed = onSharedTextConsumed,
                            onEditExistingDuplicate = { existingId ->
                                navController.popBackStack()
                                navController.navigate(Routes.editor(existingId))
                            },
                            onSaved = { savedId ->
                                navController.navigate(Routes.detail(savedId)) {
                                    popUpTo(Routes.Home)
                                }
                            },
                            onBulkSaved = {
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                            onMessage = ::showMessage,
                        )
                    }
                    composable(Routes.PlaybackPattern) { entry ->
                        val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                        val loadState by rememberOperaLoadState(viewModel, itemId)
                        val current = (loadState as? OperaLoadState.Ready)?.opera
                        if (loadState is OperaLoadState.Loading) {
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                com.kanxi.app.ui.components.LargeHeader(
                                    title = "播放",
                                    onBack = { navController.popBackStack() },
                                )
                                com.kanxi.app.ui.components.EmptyState(
                                    title = "正在加载",
                                    message = "请稍候…",
                                )
                            }
                        } else if (current == null) {
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                com.kanxi.app.ui.components.LargeHeader(
                                    title = "播放",
                                    onBack = { navController.popBackStack() },
                                )
                                com.kanxi.app.ui.components.EmptyState(
                                    title = "找不到这出戏",
                                    message = "它可能已经被删除。",
                                )
                            }
                        } else {
                            val video = BilibiliVideoLink(
                                bvid = current.item.bvid,
                                page = current.item.part,
                            )
                            PlaybackScreen(
                                title = current.item.title,
                                playerUrl = PlaybackUrlFactory.playerUrl(video),
                                originalUrl = PlaybackUrlFactory.originalUrl(video),
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanxiBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    if (LocalDensity.current.fontScale >= 1.45f) {
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LargeBottomBarButton(
                    label = "首页",
                    selected = currentRoute == Routes.Home,
                    onClick = { onNavigate(Routes.Home) },
                    modifier = Modifier.weight(1f),
                )
                LargeBottomBarButton(
                    label = "找戏",
                    selected = currentRoute == Routes.Search,
                    onClick = { onNavigate(Routes.Search) },
                    modifier = Modifier.weight(1f),
                )
                LargeBottomBarButton(
                    label = "收藏",
                    selected = currentRoute == Routes.Favorites,
                    onClick = { onNavigate(Routes.Favorites) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        NavigationBar {
            NavigationBarItem(
                selected = currentRoute == Routes.Home,
                onClick = { onNavigate(Routes.Home) },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("首页") },
            )
            NavigationBarItem(
                selected = currentRoute == Routes.Search,
                onClick = { onNavigate(Routes.Search) },
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("找戏") },
            )
            NavigationBarItem(
                selected = currentRoute == Routes.Favorites,
                onClick = { onNavigate(Routes.Favorites) },
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                label = { Text("收藏") },
            )
        }
    }
}

@Composable
private fun LargeBottomBarButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = 64.dp)
                .semantics {
                    this.selected = selected
                    role = Role.Tab
                    stateDescription = "当前页面"
                },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = 64.dp)
                .semantics {
                    this.selected = selected
                    role = Role.Tab
                    stateDescription = "未选择"
                },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) { Text(label) }
    }
}

private fun TextSizeLevel.toThemePreference(): FontSizePreference = when (this) {
    TextSizeLevel.STANDARD -> FontSizePreference.Standard
    TextSizeLevel.LARGE -> FontSizePreference.Large
    TextSizeLevel.EXTRA_LARGE -> FontSizePreference.ExtraLarge
}
