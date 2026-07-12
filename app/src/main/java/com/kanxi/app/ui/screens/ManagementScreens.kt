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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.data.CollectionWithItems
import com.kanxi.app.data.DeleteCategoryResult
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaCollection
import com.kanxi.app.data.OperaItem
import com.kanxi.app.data.OperaWithCategory
import com.kanxi.app.data.OTHER_CATEGORY_NAME
import com.kanxi.app.data.TextSizeLevel
import com.kanxi.app.ui.components.EmptyState
import com.kanxi.app.ui.components.AccessibleDialog
import com.kanxi.app.ui.components.LargeHeader
import com.kanxi.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    textSizeLevel: TextSizeLevel,
    onSetTextSize: (TextSizeLevel) -> Unit,
    onOpenFamilyManagement: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LargeHeader("设置", onBack = onBack) }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("文字大小")
                Text(
                    "“看戏”默认使用大字，也会跟随手机系统字号。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextSizeLevel.entries.forEach { level ->
                    val selected = level == textSizeLevel
                    if (selected) {
                        Button(
                            onClick = { onSetTextSize(level) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                        ) {
                            Text("${level.chineseLabel()}（当前）")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSetTextSize(level) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                        ) {
                            Text(level.chineseLabel())
                        }
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("家人管理")
                Text(
                    "添加、修改和整理戏曲。按你的选择，这里不设置口令。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onOpenFamilyManagement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) {
                    Text("进入家人管理")
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("本机数据说明")
                Text(
                    "片单、收藏和最近打开只保存在这台手机。卸载应用会清空全部内容；第一版不提供备份或云同步。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "应用不收集账号、位置或通讯录，也不会向外上传使用统计；最近打开只保存在本机。播放时会连接哔哩哔哩官方页面。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun FamilyManagementScreen(
    viewModel: KanxiViewModel,
    categories: List<OperaCategory>,
    allItems: List<OperaWithCategory>,
    onAddOpera: () -> Unit,
    onEditOpera: (Long) -> Unit,
    onManageCategories: () -> Unit,
    onManageCollections: () -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<OperaItem?>(null) }
    var showPresetDialog by remember { mutableStateOf(false) }

    pendingDelete?.let { item ->
        AccessibleDialog(
            title = "删除《${item.title}》？",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            onConfirm = {
                    pendingDelete = null
                    scope.launch {
                        viewModel.deleteItem(item)
                            .onSuccess { onMessage("已删除《${item.title}》") }
                            .onFailure { onMessage(it.message ?: "删除失败") }
                    }
            },
            onDismiss = { pendingDelete = null },
        ) {
            Text("删除后，这出戏的收藏和最近打开记录也会消失。")
        }
    }

    if (showPresetDialog) {
        PresetCodeDialog(
            onDismiss = { showPresetDialog = false },
            onConfirm = { code ->
                scope.launch {
                    viewModel.importPreset(code)
                        .onSuccess { result ->
                            showPresetDialog = false
                            val skippedMessage = if (result.skippedCount > 0) {
                                "，跳过 ${result.skippedCount} 出已存在"
                            } else {
                                ""
                            }
                            onMessage("已导入 ${result.createdCount} 出戏曲$skippedMessage")
                        }
                        .onFailure { error ->
                            onMessage(error.message ?: "预设导入失败")
                        }
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader("家人管理", onBack = onBack) }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onAddOpera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) { Text("添加戏曲") }
                OutlinedButton(
                    onClick = onManageCategories,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) { Text("管理剧种") }
                OutlinedButton(
                    onClick = onManageCollections,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) { Text("管理合集") }
                OutlinedButton(
                    onClick = { showPresetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) { Text("导入预设") }
            }
        }
        if (allItems.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有内容",
                    message = "可以粘贴 B 站链接，或在哔哩哔哩中选择“分享给看戏”。",
                    actionLabel = "添加第一出戏",
                    onAction = onAddOpera,
                )
            }
        } else {
            categories.forEach { category ->
                val categoryItems = allItems.filter { it.item.categoryId == category.id }
                if (categoryItems.isNotEmpty()) {
                    item(key = "category-${category.id}") {
                        SectionTitle(category.name, Modifier.padding(horizontal = 16.dp))
                    }
                    items(categoryItems, key = { it.item.id }) { opera ->
                        AdminOperaCard(
                            opera = opera,
                            canMoveUp = categoryItems.first().item.id != opera.item.id,
                            canMoveDown = categoryItems.last().item.id != opera.item.id,
                            onEdit = { onEditOpera(opera.item.id) },
                            onDelete = { pendingDelete = opera.item },
                            onMove = { delta ->
                                val currentIndex = categoryItems.indexOfFirst {
                                    it.item.id == opera.item.id
                                }
                                val target = currentIndex + delta
                                if (target in categoryItems.indices) {
                                    val ids = categoryItems.map { it.item.id }.toMutableList()
                                    val moved = ids.removeAt(currentIndex)
                                    ids.add(target, moved)
                                    scope.launch {
                                        viewModel.reorderItems(category.id, ids)
                                            .onFailure {
                                                onMessage(it.message ?: "调整顺序失败")
                                            }
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOperaCard(
    opera: OperaWithCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                "${opera.item.bvid} · 第 ${opera.item.part} P",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text("编辑") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onMove(-1) },
                    enabled = canMoveUp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                ) { Text("上移") }
                OutlinedButton(
                    onClick = { onMove(1) },
                    enabled = canMoveDown,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                ) { Text("下移") }
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text("删除") }
        }
    }
}

@Composable
fun CategoryManagementScreen(
    viewModel: KanxiViewModel,
    categories: List<OperaCategory>,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<OperaCategory?>(null) }
    var deleteTarget by remember { mutableStateOf<OperaCategory?>(null) }

    if (addDialog) {
        CategoryNameDialog(
            title = "添加剧种",
            initialValue = "",
            onDismiss = { addDialog = false },
            onConfirm = { name, reportError ->
                scope.launch {
                    viewModel.addCategory(name)
                        .onSuccess {
                            reportError(null)
                            addDialog = false
                            onMessage("已添加剧种“$name”")
                        }
                        .onFailure { reportError(categoryErrorMessage(it)) }
                }
            },
        )
    }
    renameTarget?.let { category ->
        CategoryNameDialog(
            title = "修改剧种名称",
            initialValue = category.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name, reportError ->
                scope.launch {
                    viewModel.renameCategory(category.id, name)
                        .onSuccess {
                            reportError(null)
                            renameTarget = null
                            onMessage("剧种名称已修改")
                        }
                        .onFailure { reportError(categoryErrorMessage(it)) }
                }
            },
        )
    }
    deleteTarget?.let { category ->
        AccessibleDialog(
            title = "删除“${category.name}”？",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            onConfirm = {
                    scope.launch {
                        viewModel.deleteCategory(category.id)
                            .onSuccess { result ->
                                when (result) {
                                    is DeleteCategoryResult.Deleted -> onMessage(
                                        "已删除剧种，${result.movedItemCount} 出戏移到“其他”",
                                    )
                                    DeleteCategoryResult.ProtectedFallback -> onMessage(
                                        "“其他”是备用剧种，不能删除",
                                    )
                                    DeleteCategoryResult.NotFound -> onMessage("剧种已经不存在")
                                }
                            }
                            .onFailure { onMessage(categoryErrorMessage(it)) }
                        deleteTarget = null
                    }
            },
            onDismiss = { deleteTarget = null },
        ) {
            Text("这个剧种下的戏曲会移到“其他”，不会被删除。")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader("管理剧种", onBack = onBack) }
        item {
            Button(
                onClick = { addDialog = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) { Text("添加剧种") }
        }
        items(categories, key = { it.id }) { category ->
            val index = categories.indexOfFirst { it.id == category.id }
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (category.name != OTHER_CATEGORY_NAME) {
                        Button(
                            onClick = { renameTarget = category },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("改名") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val ids = categories.map { it.id }.toMutableList()
                                val moved = ids.removeAt(index)
                                ids.add(index - 1, moved)
                                scope.launch {
                                    viewModel.reorderCategories(ids)
                                        .onFailure { onMessage(categoryErrorMessage(it)) }
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("上移") }
                        OutlinedButton(
                            onClick = {
                                val ids = categories.map { it.id }.toMutableList()
                                val moved = ids.removeAt(index)
                                ids.add(index + 1, moved)
                                scope.launch {
                                    viewModel.reorderCategories(ids)
                                        .onFailure { onMessage(categoryErrorMessage(it)) }
                                }
                            },
                            enabled = index in 0 until categories.lastIndex,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) { Text("下移") }
                    }
                    if (category.name != OTHER_CATEGORY_NAME) {
                        OutlinedButton(
                            onClick = { deleteTarget = category },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("删除") }
                    } else {
                        Text(
                            "备用剧种，不能删除或改名",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionManagementScreen(
    viewModel: KanxiViewModel,
    collections: List<CollectionWithItems>,
    onBack: () -> Unit,
    onEditCollection: (Long) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<OperaCollection?>(null) }
    var deleteTarget by remember { mutableStateOf<OperaCollection?>(null) }

    if (addDialog) {
        CollectionNameDialog(
            title = "添加合集",
            initialValue = "",
            onDismiss = { addDialog = false },
            onConfirm = { name, reportError ->
                scope.launch {
                    viewModel.addCollection(name)
                        .onSuccess {
                            reportError(null)
                            addDialog = false
                            onMessage("已添加合集“$name”")
                        }
                        .onFailure { reportError(collectionErrorMessage(it)) }
                }
            },
        )
    }
    renameTarget?.let { collection ->
        CollectionNameDialog(
            title = "修改合集名称",
            initialValue = collection.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name, reportError ->
                scope.launch {
                    viewModel.renameCollection(collection.id, name)
                        .onSuccess {
                            reportError(null)
                            renameTarget = null
                            onMessage("合集名称已修改")
                        }
                        .onFailure { reportError(collectionErrorMessage(it)) }
                }
            },
        )
    }
    deleteTarget?.let { collection ->
        AccessibleDialog(
            title = "删除“${collection.name}”？",
            confirmLabel = "确认删除",
            dismissLabel = "取消",
            onConfirm = {
                scope.launch {
                    viewModel.deleteCollection(collection.id)
                        .onSuccess {
                            onMessage("已删除合集“${collection.name}”")
                        }
                        .onFailure { onMessage(collectionErrorMessage(it)) }
                    deleteTarget = null
                }
            },
            onDismiss = { deleteTarget = null },
        ) {
            Text("合集里的戏曲不会被删除，只是不再归到这个合集。")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeHeader("管理合集", onBack = onBack) }
        item {
            Button(
                onClick = { addDialog = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) { Text("添加合集") }
        }
        if (collections.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有合集",
                    message = "添加一个合集后，可以把相关戏曲汇总在一起。",
                )
            }
        } else {
            items(collections, key = { it.collection.id }) { collection ->
                val index = collections.indexOfFirst { it.collection.id == collection.collection.id }
                Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            collection.collection.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (collection.itemCount == 0) "暂未添加戏曲" else "${collection.itemCount} 出戏",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onEditCollection(collection.collection.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("编辑内容") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val ids = collections.map { it.collection.id }.toMutableList()
                                    val moved = ids.removeAt(index)
                                    ids.add(index - 1, moved)
                                    scope.launch {
                                        viewModel.reorderCollections(ids)
                                            .onFailure { onMessage(collectionErrorMessage(it)) }
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            ) { Text("上移") }
                            OutlinedButton(
                                onClick = {
                                    val ids = collections.map { it.collection.id }.toMutableList()
                                    val moved = ids.removeAt(index)
                                    ids.add(index + 1, moved)
                                    scope.launch {
                                        viewModel.reorderCollections(ids)
                                            .onFailure { onMessage(collectionErrorMessage(it)) }
                                    }
                                },
                                enabled = index in 0 until collections.lastIndex,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            ) { Text("下移") }
                        }
                        OutlinedButton(
                            onClick = { renameTarget = collection.collection },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("改名") }
                        OutlinedButton(
                            onClick = { deleteTarget = collection.collection },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, (String?) -> Unit) -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var errorMessage by rememberSaveable(initialValue) { mutableStateOf<String?>(null) }
    AccessibleDialog(
        title = title,
        confirmLabel = "保存",
        dismissLabel = "取消",
        onConfirm = {
            errorMessage = null
            onConfirm(value.trim()) { errorMessage = it }
        },
        onDismiss = onDismiss,
        confirmEnabled = value.isNotBlank(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it.take(20)
                errorMessage = null
            },
            label = { Text("合集名称") },
            singleLine = true,
            isError = errorMessage != null,
            supportingText = errorMessage?.let { message ->
                {
                    Text(
                        text = message,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, (String?) -> Unit) -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var errorMessage by rememberSaveable(initialValue) { mutableStateOf<String?>(null) }
    AccessibleDialog(
        title = title,
        confirmLabel = "保存",
        dismissLabel = "取消",
        onConfirm = {
            errorMessage = null
            onConfirm(value.trim()) { errorMessage = it }
        },
        onDismiss = onDismiss,
        confirmEnabled = value.isNotBlank(),
    ) {
        OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.take(20)
                    errorMessage = null
                },
                label = { Text("剧种名称") },
                singleLine = true,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { message ->
                    {
                        Text(
                            text = message,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun TextSizeLevel.chineseLabel(): String = when (this) {
    TextSizeLevel.STANDARD -> "标准"
    TextSizeLevel.LARGE -> "大字"
    TextSizeLevel.EXTRA_LARGE -> "特大"
}

private fun categoryErrorMessage(error: Throwable): String = when {
    error.message?.contains("already exists", ignoreCase = true) == true -> "这个剧种已经存在"
    error.message?.contains("blank", ignoreCase = true) == true -> "剧种名称不能为空"
    else -> error.message ?: "操作失败，请重试"
}

private fun collectionErrorMessage(error: Throwable): String = when {
    error.message?.contains("already exists", ignoreCase = true) == true -> "这个合集已经存在"
    error.message?.contains("blank", ignoreCase = true) == true -> "合集名称不能为空"
    else -> error.message ?: "操作失败，请重试"
}

@Composable
private fun PresetCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    AccessibleDialog(
        title = "导入预设",
        confirmLabel = "导入",
        dismissLabel = "取消",
        onConfirm = { onConfirm(value.trim()) },
        onDismiss = onDismiss,
        confirmEnabled = value.isNotBlank(),
    ) {
        Text(
            "输入戏曲种类拼音全拼，例如“qinqiang”。当前内置的预设会自动导入并生成对应合集。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(20) },
            label = { Text("预设代码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
