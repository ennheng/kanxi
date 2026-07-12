package com.kanxi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.data.CollectionWithItems
import com.kanxi.app.data.OperaWithCategory
import com.kanxi.app.ui.components.AccessibleDialog
import com.kanxi.app.ui.components.EmptyState
import com.kanxi.app.ui.components.LargeHeader
import com.kanxi.app.ui.components.OperaCard
import com.kanxi.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun CollectionEditorScreen(
    viewModel: KanxiViewModel,
    collectionId: Long,
    collections: List<CollectionWithItems>,
    allItems: List<OperaWithCategory>,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val collection = remember(collections, collectionId) {
        collections.firstOrNull { it.collection.id == collectionId }?.collection
    }
    val itemsFlow = remember(collectionId) { viewModel.observeCollectionItems(collectionId) }
    val collectionItems by itemsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LargeHeader(
                title = collection?.name?.let { "编辑《$it》" } ?: "编辑合集",
                onBack = onBack,
            )
        }

        if (collection == null) {
            item {
                EmptyState(
                    title = "合集不存在",
                    message = "这个合集可能已经被删除。",
                )
            }
            return@LazyColumn
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) { Text("添加戏曲到合集") }
                Text(
                    "点“添加戏曲”从片单里挑选；上下移动调整顺序，左滑或点“移除”删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (collectionItems.isEmpty()) {
            item {
                EmptyState(
                    title = "合集还是空的",
                    message = "先点上面的按钮添加几出戏曲。",
                )
            }
        } else {
            item { SectionTitle("已加入的戏曲", Modifier.padding(horizontal = 16.dp)) }
            items(collectionItems, key = { "editor-${it.item.id}" }) { opera ->
                val canMoveUp = collectionItems.first().item.id != opera.item.id
                val canMoveDown = collectionItems.last().item.id != opera.item.id
                CollectionEditorItemCard(
                    opera = opera,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onRemove = {
                        scope.launch {
                            viewModel.removeItemFromCollection(collectionId, opera.item.id)
                                .onSuccess { onMessage("已移除《${opera.item.title}》") }
                                .onFailure { onMessage(it.message ?: "移除失败") }
                        }
                    },
                    onMove = { delta ->
                        val currentIndex = collectionItems.indexOfFirst {
                            it.item.id == opera.item.id
                        }
                        val target = currentIndex + delta
                        if (target in collectionItems.indices) {
                            val ids = collectionItems.map { it.item.id }.toMutableList()
                            val moved = ids.removeAt(currentIndex)
                            ids.add(target, moved)
                            scope.launch {
                                viewModel.reorderCollectionItems(collectionId, ids)
                                    .onFailure { onMessage(it.message ?: "调整顺序失败") }
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showAddDialog) {
        val availableItems = allItems.filter { item ->
            collectionItems.none { it.item.id == item.item.id }
        }
        AccessibleDialog(
            title = "添加戏曲",
            confirmLabel = "完成",
            dismissLabel = "取消",
            onConfirm = { showAddDialog = false },
            onDismiss = { showAddDialog = false },
        ) {
            if (availableItems.isEmpty()) {
                Text("所有戏曲都已经在这个合集里了。")
            } else {
                Text(
                    "点“加入”把戏曲放进合集。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                availableItems.forEach { opera ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = opera.item.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.addItemToCollection(collectionId, opera.item.id)
                                        .onSuccess {
                                            onMessage("《${opera.item.title}》已加入合集")
                                        }
                                        .onFailure { onMessage(it.message ?: "添加失败") }
                                }
                            },
                        ) { Text("加入") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionEditorItemCard(
    opera: OperaWithCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                opera.item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${opera.categoryName}${if (opera.item.actors.isNotBlank()) " · ${opera.item.actors}" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onMove(-1) },
                    enabled = canMoveUp,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("上移") }
                OutlinedButton(
                    onClick = { onMove(1) },
                    enabled = canMoveDown,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("下移") }
            }
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("移除") }
        }
    }
}
