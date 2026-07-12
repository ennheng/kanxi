package com.kanxi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.ui.OperaLoadState
import com.kanxi.app.ui.rememberOperaLoadState
import com.kanxi.app.data.CollectionWithItems
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaWithCategory
import com.kanxi.app.ui.components.CategoryGrid
import com.kanxi.app.ui.components.CollectionGrid
import com.kanxi.app.ui.components.EmptyState
import com.kanxi.app.ui.components.LargeHeader
import com.kanxi.app.ui.components.OperaCard
import com.kanxi.app.ui.components.SectionTitle

@Composable
fun HomeScreen(
    categories: List<OperaCategory>,
    collections: List<CollectionWithItems>,
    allItems: List<OperaWithCategory>,
    recentItems: List<com.kanxi.app.data.RecentOpera>,
    onCategoryClick: (Long) -> Unit,
    onCollectionClick: (Long) -> Unit,
    onItemClick: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onAddFirstOpera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LargeHeader(
                title = "看戏",
                actionLabel = "设置",
                onAction = onOpenSettings,
            )
        }
        item {
            SectionTitle("按剧种找戏", Modifier.padding(horizontal = 16.dp))
        }
        item {
            CategoryGrid(
                categories = categories,
                itemCounts = allItems.groupingBy { it.item.categoryId }.eachCount(),
                onCategoryClick = onCategoryClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (collections.isNotEmpty()) {
            item {
                SectionTitle("戏曲合集", Modifier.padding(horizontal = 16.dp))
            }
            item {
                CollectionGrid(
                    collections = collections,
                    onCollectionClick = onCollectionClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (allItems.isEmpty()) {
            item {
                EmptyState(
                    title = "片单还是空的",
                    message = "请让家人先添加一出公开免费的 B 站戏曲。",
                    actionLabel = "家人添加第一出戏",
                    onAction = onAddFirstOpera,
                )
            }
        } else {
            if (recentItems.isNotEmpty()) {
                item { SectionTitle("最近打开", Modifier.padding(horizontal = 16.dp)) }
                items(recentItems.take(5), key = { "recent-${it.item.id}" }) { recent ->
                    OperaCard(
                        opera = OperaWithCategory(
                            item = recent.item,
                            categoryName = recent.categoryName,
                            isFavorite = recent.isFavorite,
                        ),
                        onClick = { onItemClick(recent.item.id) },
                        onPlay = { onPlay(recent.item.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item { SectionTitle("家人精选", Modifier.padding(horizontal = 16.dp)) }
            items(allItems.take(8), key = { "featured-${it.item.id}" }) { opera ->
                OperaCard(
                    opera = opera,
                    onClick = { onItemClick(opera.item.id) },
                    onPlay = { onPlay(opera.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    viewModel: KanxiViewModel,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val resultFlow = remember(query) { viewModel.search(query) }
    val results by resultFlow.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader("找戏") }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    label = { Text("输入剧名、剧种、演员或剧团") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
                if (query.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { query = "" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text("清空搜索")
                    }
                }
            }
        }
        if (results.isEmpty()) {
            item {
                EmptyState(
                    title = if (query.isBlank()) "还没有戏曲" else "没有找到",
                    message = if (query.isBlank()) {
                        "家人添加内容后，可以在这里按剧名、剧种或演员查找。"
                    } else {
                        "请换一个更短的词，或让家人在别名中补充常用叫法。"
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        } else {
            item {
                SectionTitle(
                    text = if (query.isBlank()) "全部戏曲" else "找到 ${results.size} 出戏",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            items(results, key = { it.item.id }) { opera ->
                OperaCard(
                    opera = opera,
                    onClick = { onItemClick(opera.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    favorites: List<OperaWithCategory>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader("收藏") }
        if (favorites.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有收藏",
                    message = "打开一出戏，在详情页点“收藏”，以后就能在这里快速找到。",
                )
            }
        } else {
            item { SectionTitle("我的收藏", Modifier.padding(horizontal = 16.dp)) }
            items(favorites, key = { it.item.id }) { opera ->
                OperaCard(
                    opera = opera,
                    onClick = { onItemClick(opera.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
fun CategoryScreen(
    viewModel: KanxiViewModel,
    category: OperaCategory?,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemsFlow = remember(category?.id) {
        category?.let { viewModel.observeItems(it.id) }
    }
    val operas by (itemsFlow ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) })
        .collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader(category?.name ?: "剧种", onBack = onBack) }
        if (operas.isEmpty()) {
            item {
                EmptyState(
                    title = "这个剧种还没有内容",
                    message = "家人可以在管理页面添加或修改分类。",
                )
            }
        } else {
            items(operas, key = { it.item.id }) { opera ->
                OperaCard(
                    opera = opera,
                    onClick = { onItemClick(opera.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onPlay = { onPlay(opera.item.id) },
                )
            }
        }
    }
}

@Composable
fun DetailScreen(
    viewModel: KanxiViewModel,
    itemId: Long,
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadState by rememberOperaLoadState(viewModel, itemId)
    if (loadState is OperaLoadState.Loading) {
        Column(modifier.fillMaxSize()) {
            LargeHeader("戏曲详情", onBack = onBack)
            EmptyState("正在加载", "请稍候…")
        }
        return
    }
    val current = (loadState as OperaLoadState.Ready).opera
    if (current == null) {
        Column(modifier.fillMaxSize()) {
            LargeHeader("戏曲详情", onBack = onBack)
            EmptyState("找不到这出戏", "它可能已经被家人删除。")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LargeHeader(current.item.title, onBack = onBack) }
        item {
            Text(
                text = current.categoryName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            Button(
                onClick = { onPlay(current.item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Text("播放")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.toggleFavorite(current.item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Text(if (current.isFavorite) "取消收藏" else "收藏")
            }
        }
        current.item.actors.takeIf(String::isNotBlank)?.let { actors ->
            item { DetailLine("演员", actors) }
        }
        current.item.troupe.takeIf(String::isNotBlank)?.let { troupe ->
            item { DetailLine("剧团", troupe) }
        }
        current.item.notes.takeIf(String::isNotBlank)?.let { notes ->
            item { DetailLine("家人备注", notes) }
        }
        item {
            Text(
                text = "来源：哔哩哔哩公开页面。播放内容、广告和可用性由平台及上传者控制。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun CollectionScreen(
    viewModel: KanxiViewModel,
    collection: com.kanxi.app.data.OperaCollection?,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemsFlow = remember(collection?.id) {
        collection?.let { viewModel.observeCollectionItems(it.id) }
    }
    val operas by (itemsFlow ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) })
        .collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader(collection?.name ?: "合集", onBack = onBack) }
        if (operas.isEmpty()) {
            item {
                EmptyState(
                    title = "这个合集还没有内容",
                    message = "家人可以在管理页面往合集里添加戏曲。",
                )
            }
        } else {
            item {
                SectionTitle(
                    "共 ${operas.size} 出戏",
                    Modifier.padding(horizontal = 16.dp),
                )
            }
            items(operas, key = { "collection-${it.item.id}" }) { opera ->
                OperaCard(
                    opera = opera,
                    onClick = { onItemClick(opera.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onPlay = { onPlay(opera.item.id) },
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
