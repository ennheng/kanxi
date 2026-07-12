package com.kanxi.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.SaveOperation
import com.kanxi.app.bilibili.BilibiliLinkParseResult
import com.kanxi.app.bilibili.BilibiliPageEntry
import com.kanxi.app.bilibili.BilibiliPageListResult
import com.kanxi.app.bilibili.BilibiliVideoLink
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaItem
import com.kanxi.app.data.OTHER_CATEGORY_NAME
import com.kanxi.app.ui.components.LargeHeader
import com.kanxi.app.ui.components.OperaCover
import com.kanxi.app.ui.components.SectionTitle
import com.kanxi.app.ui.components.AccessibleDialog
import com.kanxi.app.util.SharedTitleExtractor
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OperaEditorScreen(
    viewModel: KanxiViewModel,
    itemId: Long,
    categories: List<OperaCategory>,
    initialSharedText: String?,
    onSharedTextConsumed: () -> Unit,
    onEditExistingDuplicate: (Long) -> Unit,
    onSaved: (Long) -> Unit,
    onBulkSaved: () -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val existingProjection by remember(itemId) { viewModel.observeItem(itemId) }
        .collectAsState(initial = null)
    val existing = existingProjection?.item
    val saveOperation by viewModel.saveOperation.collectAsState()

    var initialized by rememberSaveable(itemId) { mutableStateOf(false) }
    var linkText by rememberSaveable(itemId) { mutableStateOf("") }
    var title by rememberSaveable(itemId) { mutableStateOf("") }
    var categoryId by rememberSaveable(itemId) { mutableLongStateOf(0L) }
    var actors by rememberSaveable(itemId) { mutableStateOf("") }
    var troupe by rememberSaveable(itemId) { mutableStateOf("") }
    var aliases by rememberSaveable(itemId) { mutableStateOf("") }
    var notes by rememberSaveable(itemId) { mutableStateOf("") }
    var coverPath by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    var originalCoverPath by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    var parsedBvid by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    var partText by rememberSaveable(itemId) { mutableStateOf("1") }
    var parsedForText by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    var validationMessage by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    var pageOptions by remember(itemId) { mutableStateOf<List<BilibiliPageEntry>>(emptyList()) }
    var showPagePicker by rememberSaveable(itemId) { mutableStateOf(false) }
    var showBulkImportDialog by rememberSaveable(itemId) { mutableStateOf(false) }
    var pendingPage by rememberSaveable(itemId) { mutableStateOf("1") }
    var titleEditedByUser by rememberSaveable(itemId) { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable(itemId) { mutableStateOf(false) }
    var pendingCategoryId by rememberSaveable(itemId) { mutableLongStateOf(0L) }
    var validating by remember(itemId) { mutableStateOf(false) }
    var importingCover by remember(itemId) { mutableStateOf(false) }
    var saveRequestId by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    val saving = saveRequestId != null
    val parsedVideo = remember(parsedBvid, partText, pageOptions) {
        val selectedPart = partText.trim().toIntOrNull()?.takeIf { it >= 1 }
        parsedBvid?.let { bvid ->
            selectedPart
                ?.takeIf { page -> pageOptions.isEmpty() || pageOptions.any { it.page == page } }
                ?.let { page -> runCatching { BilibiliVideoLink(bvid, page) }.getOrNull() }
        }
    }

    suspend fun validateCurrentLink(loadPageDirectory: Boolean = false): BilibiliVideoLink? {
        val source = linkText.trim()
        if (source.isBlank()) {
            validationMessage = "请粘贴 B 站链接"
            return null
        }
        validating = true
        try {
            val result = viewModel.parseBilibiliLink(source)
            if (linkText.trim() != source) return null
            return when (result) {
                is BilibiliLinkParseResult.Success -> {
                    parsedBvid = result.video.bvid
                    partText = result.video.page.toString()
                    pendingPage = partText
                    parsedForText = source
                    pageOptions = emptyList()
                    validationMessage = "链接有效：${result.video.bvid}，当前选择第 ${result.video.page} P"

                    if (loadPageDirectory) {
                        when (val pagesResult = viewModel.resolveBilibiliPages(result.video.bvid)) {
                            is BilibiliPageListResult.Success -> {
                                if (linkText.trim() != source || parsedBvid != result.video.bvid) {
                                    return null
                                }
                                pageOptions = pagesResult.pageList.pages
                                val selectedEntry = pageOptions.firstOrNull {
                                    it.page == result.video.page
                                }
                                if (selectedEntry != null && !titleEditedByUser) {
                                    title = selectedEntry.title
                                }
                                validationMessage = if (pageOptions.size > 1) {
                                    if (selectedEntry == null) {
                                        "发现 ${pageOptions.size} 个分 P，但没有第 ${result.video.page} P，请重新选择"
                                    } else {
                                        "已识别 ${pageOptions.size} 个分 P，当前是第 ${result.video.page} P"
                                    }
                                } else if (selectedEntry != null) {
                                    "链接有效：这是单 P 视频"
                                } else {
                                    "这个链接没有第 ${result.video.page} P，请把分 P 编号改为 1"
                                }
                                if (pageOptions.size > 1) showBulkImportDialog = true
                            }
                            else -> {
                                pageOptions = emptyList()
                                validationMessage =
                                    "链接有效；暂时没读到分 P 目录，可以手工填写分 P 编号"
                            }
                        }
                    }
                    result.video
                }
                else -> {
                    parsedBvid = null
                    parsedForText = null
                    pageOptions = emptyList()
                    showPagePicker = false
                    validationMessage = result.toChineseError()
                    null
                }
            }
        } finally {
            validating = false
        }
    }

    fun applyPageSelection(page: Int) {
        val entry = pageOptions.firstOrNull { it.page == page } ?: return
        partText = entry.page.toString()
        if (!titleEditedByUser) title = entry.title
        validationMessage = "已选择第 ${entry.page} P：${entry.title}"
    }

    fun importAllPages() {
        val bvid = parsedBvid
        val selectedCategoryId = categoryId
        if (bvid == null || selectedCategoryId <= 0L || pageOptions.isEmpty()) {
            onMessage("请先验证链接并选择剧种")
            return
        }
        val baseTitle = title.trim().takeIf { it.isNotBlank() } ?: "戏曲"
        scope.launch {
            val items = pageOptions.map { entry ->
                OperaItem(
                    id = 0,
                    title = if (titleEditedByUser) {
                        "$baseTitle · 第 ${entry.page} P"
                    } else {
                        entry.title
                    },
                    categoryId = selectedCategoryId,
                    actors = actors,
                    troupe = troupe,
                    aliases = aliases,
                    notes = notes,
                    bvid = bvid,
                    part = entry.page,
                    coverPath = coverPath,
                    sortOrder = OperaItem.UNSORTED,
                )
            }
            viewModel.bulkSaveItems(items)
                .onSuccess { result ->
                    showBulkImportDialog = false
                    showPagePicker = false
                    val skippedMessage = if (result.skippedCount > 0) {
                        "，跳过 ${result.skippedCount} 出已存在"
                    } else {
                        ""
                    }
                    onMessage("已添加 ${result.createdCount} 出戏曲$skippedMessage")
                    if (coverPath != originalCoverPath) {
                        viewModel.deleteCoverFile(originalCoverPath)
                    }
                    onBulkSaved()
                }
                .onFailure { error ->
                    onMessage(error.message ?: "批量添加失败")
                }
        }
    }

    LaunchedEffect(saveOperation, saveRequestId) {
        val requestId = saveRequestId ?: return@LaunchedEffect
        when (val operation = saveOperation) {
            is SaveOperation.Succeeded -> if (operation.requestId == requestId) {
                if (originalCoverPath != coverPath) {
                    viewModel.deleteCoverFile(originalCoverPath)
                }
                viewModel.consumeSaveOperation(requestId)
                saveRequestId = null
                onMessage(if (itemId == 0L) "戏曲已添加" else "修改已保存")
                onSaved(operation.itemId)
            }
            is SaveOperation.Failed -> if (operation.requestId == requestId) {
                viewModel.consumeSaveOperation(requestId)
                saveRequestId = null
                onMessage(saveErrorMessage(IllegalStateException(operation.message)))
            }
            SaveOperation.Idle -> {
                // The process may have been killed while a request id was saved in UI state.
                saveRequestId = null
                onMessage("上次保存被中断，请再点一次保存")
            }
            is SaveOperation.Running -> Unit
        }
    }

    LaunchedEffect(existing, initialSharedText) {
        if (!initialized && itemId > 0 && existing != null) {
            linkText = "https://www.bilibili.com/video/${existing.bvid}?p=${existing.part}"
            title = existing.title
            categoryId = existing.categoryId
            actors = existing.actors
            troupe = existing.troupe
            aliases = existing.aliases
            notes = existing.notes
            coverPath = existing.coverPath
            originalCoverPath = existing.coverPath
            parsedBvid = existing.bvid
            partText = existing.part.toString()
            parsedForText = linkText
            validationMessage = "链接已验证"
            titleEditedByUser = true
            initialized = true
        } else if (!initialized && itemId == 0L) {
            initialSharedText?.let { shared ->
                linkText = shared
                title = SharedTitleExtractor.extract(shared)
                onSharedTextConsumed()
            }
            initialized = true
            if (linkText.isNotBlank()) {
                validateCurrentLink(loadPageDirectory = true)
            }
        } else if (itemId == 0L && initialSharedText != null) {
            if (coverPath != originalCoverPath) viewModel.deleteCoverFile(coverPath)
            linkText = initialSharedText
            title = SharedTitleExtractor.extract(initialSharedText)
            categoryId = categories.firstOrNull { it.name == OTHER_CATEGORY_NAME }?.id
                ?: categories.firstOrNull()?.id
                ?: 0L
            actors = ""
            troupe = ""
            aliases = ""
            notes = ""
            coverPath = null
            originalCoverPath = null
            parsedBvid = null
            partText = "1"
            parsedForText = null
            validationMessage = null
            pageOptions = emptyList()
            showPagePicker = false
            titleEditedByUser = false
            onSharedTextConsumed()
            onMessage("已用新分享的链接开始添加")
            validateCurrentLink(loadPageDirectory = true)
        }
    }

    LaunchedEffect(categories, initialized) {
        if (initialized && categoryId == 0L && categories.isNotEmpty()) {
            categoryId = categories.firstOrNull { it.name == OTHER_CATEGORY_NAME }?.id
                ?: categories.first().id
        }
    }

    fun discardTemporaryCover() {
        if (coverPath != originalCoverPath) {
            viewModel.deleteCoverFile(coverPath)
            coverPath = originalCoverPath
        }
    }

    fun cancelEditing() {
        if (saving) return
        discardTemporaryCover()
        onBack()
    }

    BackHandler {
        if (saving) onMessage("正在保存，请稍候") else cancelEditing()
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importingCover = true
                val replaceTemporaryPath = coverPath.takeIf { it != originalCoverPath }
                viewModel.importCover(uri, replaceTemporaryPath)
                    .onSuccess { coverPath = it }
                    .onFailure { onMessage("封面导入失败，请选择 JPG、PNG 或 WebP 图片") }
                importingCover = false
            }
        }
    }

    if (showBulkImportDialog && pageOptions.size > 1) {
        AccessibleDialog(
            title = "检测到多 P 视频（共 ${pageOptions.size} P）",
            confirmLabel = "全部添加 ${pageOptions.size} P",
            dismissLabel = "只加当前 P",
            onConfirm = { importAllPages() },
            onDismiss = { showBulkImportDialog = false },
        ) {
            Text(
                "这个视频有 ${pageOptions.size} 个分 P。点“全部添加”会把每一 P 都保存成一出独立戏曲，之后可以用合集汇总；点“只加当前 P”则只保存当前选中的这一 P。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    showBulkImportDialog = false
                    showPagePicker = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("我想挑其中一 P") }
        }
    }

    if (showPagePicker && pageOptions.size > 1) {
        AccessibleDialog(
            title = "选择分 P（共 ${pageOptions.size} P）",
            confirmLabel = "保持当前选择",
            dismissLabel = "取消",
            onConfirm = {
                pendingPage.toIntOrNull()?.let(::applyPageSelection)
                showPagePicker = false
            },
            onDismiss = { showPagePicker = false },
            confirmEnabled = pendingPage.toIntOrNull()?.let { selected ->
                pageOptions.any { it.page == selected }
            } == true,
        ) {
            Text(
                "点一项就会立即选中并返回。选中的 P 会作为一条独立戏曲保存，之后可单独收藏和查找。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pageOptions.forEach { page ->
                val label = "第 ${page.page} P　${page.title}"
                if (pendingPage == page.page.toString()) {
                    Button(
                        onClick = {
                            pendingPage = page.page.toString()
                            applyPageSelection(page.page)
                            showPagePicker = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("$label（已选择）") }
                } else {
                    OutlinedButton(
                        onClick = {
                            pendingPage = page.page.toString()
                            applyPageSelection(page.page)
                            showPagePicker = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(label) }
                }
            }
        }
    }

    if (showCategoryPicker) {
        AccessibleDialog(
            title = "选择剧种",
            confirmLabel = "使用这个剧种",
            dismissLabel = "取消",
            onConfirm = {
                if (pendingCategoryId > 0L) categoryId = pendingCategoryId
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
            confirmEnabled = pendingCategoryId > 0L,
        ) {
            Text(
                "常用剧种可在“家人管理”中新增、改名和排序。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            categories.forEach { category ->
                if (category.id == pendingCategoryId) {
                    Button(
                        onClick = { pendingCategoryId = category.id },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("${category.name}（已选择）") }
                } else {
                    OutlinedButton(
                        onClick = { pendingCategoryId = category.id },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(category.name) }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LargeHeader(
                title = if (itemId == 0L) "添加戏曲" else "编辑戏曲",
                onBack = ::cancelEditing,
            )
        }
        item {
            EditorSection {
                SectionTitle("B 站公开免费视频链接")
                Text(
                    "支持 www.bilibili.com、m.bilibili.com 和 b23.tv。验证时只读取公开页面的分 P 编号和标题，不下载视频或抓取封面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = linkText,
                    onValueChange = {
                        linkText = it.take(1_000)
                        if (parsedForText != linkText.trim()) {
                            parsedBvid = null
                            partText = "1"
                            pageOptions = emptyList()
                            showPagePicker = false
                            validationMessage = null
                        }
                    },
                    label = { Text("粘贴或分享链接") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    isError = validationMessage != null && parsedVideo == null,
                )
                Button(
                    onClick = { scope.launch { validateCurrentLink(loadPageDirectory = true) } },
                    enabled = !validating,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    if (validating) CircularProgressIndicator() else Text("验证链接")
                }
                validationMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (parsedVideo != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (parsedBvid != null) {
                    OutlinedTextField(
                        value = partText,
                        onValueChange = { value ->
                            partText = value.filter(Char::isDigit).take(4)
                            partText.toIntOrNull()?.let { selected ->
                                val entry = pageOptions.firstOrNull { it.page == selected }
                                if (entry != null) {
                                    if (!titleEditedByUser) title = entry.title
                                    validationMessage = "已选择第 ${entry.page} P：${entry.title}"
                                } else if (pageOptions.isEmpty() && selected >= 1) {
                                    validationMessage = "链接有效，当前手工选择第 $selected P"
                                }
                            }
                        },
                        label = { Text("分 P 编号") },
                        supportingText = { Text("多 P 视频可在这里选择第几 P，编号从 1 开始") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = parsedVideo == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (parsedVideo == null) {
                        Text(
                            if (partText.trim().toIntOrNull()?.let { it >= 1 } != true) {
                                "请输入 1 或更大的分 P 编号"
                            } else {
                                "这个视频的目录中没有第 ${partText.trim()} P"
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (pageOptions.size > 1) {
                        OutlinedButton(
                            onClick = {
                                pendingPage = partText
                                showPagePicker = true
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        ) {
                            Text("从 ${pageOptions.size} 个分 P 中选择")
                        }
                    }
                }
            }
        }
        item {
            EditorSection {
                SectionTitle("基本信息")
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it.take(160)
                        titleEditedByUser = true
                    },
                    label = { Text("剧名（必填）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text("剧种", style = MaterialTheme.typography.titleSmall)
                Button(
                    onClick = {
                        pendingCategoryId = categoryId.takeIf { selected ->
                            categories.any { it.id == selected }
                        } ?: categories.firstOrNull()?.id ?: 0L
                        showCategoryPicker = true
                    },
                    enabled = categories.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    val selectedName = categories.firstOrNull { it.id == categoryId }?.name
                    Text(selectedName?.let { "已选择：$it（点击更换）" } ?: "选择剧种")
                }
                Text(
                    "剧种由家人自己维护，不需要保留用不到的分类。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = actors,
                    onValueChange = { actors = it.take(120) },
                    label = { Text("演员（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = troupe,
                    onValueChange = { troupe = it.take(120) },
                    label = { Text("剧团（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it.take(200) },
                    label = { Text("常用别名，方便搜索（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("家人备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        }
        item {
            EditorSection {
                SectionTitle("本地封面（可选）")
                OperaCover(
                    coverPath = coverPath,
                    categoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "戏曲",
                    title = title.ifBlank { "戏曲" },
                )
                Button(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !importingCover,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(if (importingCover) "正在导入…" else "选择本机图片")
                }
                if (coverPath != null) {
                    OutlinedButton(
                        onClick = {
                            if (coverPath != originalCoverPath) viewModel.deleteCoverFile(coverPath)
                            coverPath = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("移除封面") }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            if (title.isBlank()) {
                                onMessage("请填写剧名")
                                return@launch
                            }
                            if (categoryId <= 0L) {
                                onMessage("请选择剧种")
                                return@launch
                            }
                            val source = linkText.trim()
                            val validated = if (parsedForText == source && parsedBvid != null) {
                                parsedVideo ?: run {
                                    onMessage("分 P 编号应当是 1 或更大的数字")
                                    return@launch
                                }
                            } else {
                                validateCurrentLink() ?: return@launch
                            }
                            val duplicate = viewModel.findItemByIdentity(
                                validated.bvid,
                                validated.page,
                            )
                            if (itemId == 0L && duplicate != null) {
                                validationMessage = "第 ${validated.page} P 已经添加，正在打开原条目"
                                onMessage("这一 P 已经在片单中，请直接修改原条目")
                                discardTemporaryCover()
                                onEditExistingDuplicate(duplicate.id)
                                return@launch
                            }
                            if (itemId > 0L && duplicate != null && duplicate.id != itemId) {
                                validationMessage = "另一个条目已经使用第 ${validated.page} P"
                                onMessage("这一 P 已经被另一个条目使用")
                                return@launch
                            }
                            val old = existing
                            val item = OperaItem(
                                id = if (itemId > 0) itemId else 0,
                                title = title,
                                categoryId = categoryId,
                                actors = actors,
                                troupe = troupe,
                                aliases = aliases,
                                notes = notes,
                                bvid = validated.bvid,
                                part = validated.page,
                                coverPath = coverPath,
                                sortOrder = old
                                    ?.takeIf { it.categoryId == categoryId }
                                    ?.sortOrder
                                    ?: OperaItem.UNSORTED,
                                createdAt = old?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = old?.updatedAt ?: System.currentTimeMillis(),
                            )
                            val requestId = UUID.randomUUID().toString()
                            saveRequestId = requestId
                            viewModel.submitItemSave(requestId, item)
                        }
                    },
                    enabled = !saving && !validating && !importingCover,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Text(if (saving) "正在保存…" else "保存")
                }
                OutlinedButton(
                    onClick = ::cancelEditing,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) { Text("取消") }
            }
        }
    }
}

@Composable
private fun EditorSection(content: @Composable ColumnScope.() -> Unit) {
    // Kept as a simple padded section so 200% text can grow vertically without clipping.
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

private fun BilibiliLinkParseResult.toChineseError(): String = when (this) {
    is BilibiliLinkParseResult.Success -> "链接有效"
    BilibiliLinkParseResult.NoSupportedLink -> "没有找到支持的 B 站视频链接"
    is BilibiliLinkParseResult.UnsupportedScheme -> "只允许安全的 https 链接"
    is BilibiliLinkParseResult.UntrustedHost,
    is BilibiliLinkParseResult.UntrustedPort -> "链接跳转到了不允许的地址，已停止"
    is BilibiliLinkParseResult.InvalidPage -> "分 P 编号无效，应当是 1 或更大的数字"
    is BilibiliLinkParseResult.TooManyRedirects -> "短链接跳转次数过多，请粘贴完整链接"
    is BilibiliLinkParseResult.NetworkFailure -> "无法解析短链接，请检查网络后重试"
    is BilibiliLinkParseResult.HttpFailure -> "短链接暂时不可用，请粘贴完整链接"
    is BilibiliLinkParseResult.InvalidUrl,
    is BilibiliLinkParseResult.InvalidVideoPath,
    is BilibiliLinkParseResult.RedirectWithoutLocation -> "链接格式不正确，请重新复制"
}

private fun saveErrorMessage(error: Throwable): String = when {
    error.message?.contains("already uses", ignoreCase = true) == true ->
        "另一个条目已经使用这条 B 站视频"
    error.message?.contains("title", ignoreCase = true) == true -> "剧名不能为空"
    error.message?.contains("category", ignoreCase = true) == true -> "请选择有效的剧种"
    else -> error.message ?: "保存失败，请重试"
}
