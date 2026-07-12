package com.kanxi.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanxi.app.bilibili.BilibiliLinkParseResult
import com.kanxi.app.bilibili.BilibiliPageListResult
import com.kanxi.app.data.BulkSaveResult
import com.kanxi.app.data.CollectionWithItems
import com.kanxi.app.data.DeleteCategoryResult
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaCollection
import com.kanxi.app.data.OperaDataRules
import com.kanxi.app.data.OperaItem
import com.kanxi.app.data.OperaWithCategory
import com.kanxi.app.data.RecentOpera
import com.kanxi.app.data.SaveOperaResult
import com.kanxi.app.data.TextSizeLevel
import com.kanxi.app.util.CoverImageStore
import com.kanxi.app.util.PresetImportResult
import com.kanxi.app.util.PresetImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KanxiViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val repository = container.operaRepository
    private val preferences = container.preferencesRepository

    private val _saveOperation = MutableStateFlow<SaveOperation>(SaveOperation.Idle)
    val saveOperation = _saveOperation.asStateFlow()

    val categories: StateFlow<List<OperaCategory>> = repository.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val allItems: StateFlow<List<OperaWithCategory>> = repository.observeItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val favorites: StateFlow<List<OperaWithCategory>> = repository.observeFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val recent: StateFlow<List<RecentOpera>> = repository.observeRecent().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val collections: StateFlow<List<CollectionWithItems>> =
        repository.observeCollectionsWithItems().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val textSizeLevel: StateFlow<TextSizeLevel> = preferences.textSizeLevel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TextSizeLevel.LARGE,
    )
    fun observeItems(categoryId: Long): Flow<List<OperaWithCategory>> =
        repository.observeItems(categoryId)

    fun observeCollectionItems(collectionId: Long): Flow<List<OperaWithCategory>> =
        repository.observeCollectionItems(collectionId)

    fun observeItem(itemId: Long): Flow<OperaWithCategory?> = repository.observeItem(itemId)

    fun search(query: String): Flow<List<OperaWithCategory>> = repository.search(query)

    suspend fun parseBilibiliLink(text: String): BilibiliLinkParseResult =
        withContext(Dispatchers.IO) { container.bilibiliLinkParser.parse(text) }

    suspend fun resolveBilibiliPages(bvid: String): BilibiliPageListResult =
        withContext(Dispatchers.IO) { container.bilibiliPageListResolver.resolve(bvid) }

    suspend fun findItemByIdentity(bvid: String, part: Int): OperaItem? =
        repository.findItemByIdentity(bvid, part)

    suspend fun saveItem(item: OperaItem): Result<SaveOperaResult> = runCatching {
        repository.upsertItem(item)
    }

    suspend fun bulkSaveItems(items: List<OperaItem>): Result<BulkSaveResult> = runCatching {
        withContext(Dispatchers.IO) {
            var created = 0
            var skipped = 0
            items.forEach { item ->
                val normalized = OperaDataRules.normalizeItem(item)
                val existing = repository.findItemByIdentity(normalized.bvid, normalized.part)
                if (existing == null) {
                    repository.upsertItem(normalized)
                    created++
                } else {
                    skipped++
                }
            }
            BulkSaveResult(createdCount = created, skippedCount = skipped)
        }
    }

    suspend fun importPreset(code: String): Result<PresetImportResult> = runCatching {
        withContext(Dispatchers.IO) {
            val packs = PresetImporter.loadPacks(getApplication()).getOrThrow()
            val pack = packs[code.lowercase()]
                ?: throw IllegalArgumentException("没有找到代码“$code”对应的预设")

            val categoryId = repository.addCategory(pack.categoryName)
            var created = 0
            var skipped = 0
            val itemIds = mutableListOf<Long>()

            pack.items.forEach { presetItem ->
                val existing = repository.findItemByIdentity(presetItem.bvid, presetItem.part)
                if (existing == null) {
                    val result = repository.upsertItem(
                        OperaItem(
                            id = 0,
                            title = presetItem.title,
                            categoryId = categoryId,
                            bvid = presetItem.bvid,
                            part = presetItem.part,
                        ),
                    )
                    itemIds += result.itemId
                    created++
                } else {
                    itemIds += existing.id
                    skipped++
                }
            }

            val collectionId = repository.addCollection(pack.name)
            itemIds.forEach { itemId ->
                if (!repository.collectionContainsItem(collectionId, itemId)) {
                    repository.addItemToCollection(collectionId, itemId)
                }
            }

            PresetImportResult(
                createdCount = created,
                skippedCount = skipped,
                collectionId = collectionId,
            )
        }
    }

    fun submitItemSave(requestId: String, item: OperaItem) {
        _saveOperation.value = SaveOperation.Running(requestId)
        viewModelScope.launch {
            runCatching { repository.upsertItem(item) }
                .onSuccess { result ->
                    _saveOperation.value = SaveOperation.Succeeded(requestId, result.itemId)
                }
                .onFailure { error ->
                    _saveOperation.value = SaveOperation.Failed(
                        requestId = requestId,
                        message = error.message ?: "保存失败，请重试",
                    )
                }
        }
    }

    fun consumeSaveOperation(requestId: String) {
        if (_saveOperation.value.requestId == requestId) {
            _saveOperation.value = SaveOperation.Idle
        }
    }

    suspend fun importCover(uri: Uri, previousPath: String?): Result<String> =
        CoverImageStore.import(getApplication(), uri, previousPath)

    fun deleteCoverFile(path: String?) {
        CoverImageStore.deleteOwnedCover(getApplication(), path)
    }

    suspend fun deleteItem(item: OperaItem): Result<Unit> = runCatching {
        check(repository.deleteItem(item.id)) { "戏曲不存在或已经删除" }
        CoverImageStore.deleteOwnedCover(getApplication(), item.coverPath)
    }

    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch { repository.toggleFavorite(itemId) }
    }

    suspend fun recordOpened(itemId: Long): Result<Unit> = runCatching {
        repository.recordOpened(itemId)
    }

    suspend fun addCategory(name: String): Result<Long> = runCatching {
        require(!repository.categoryNameExists(name)) { "Category name already exists" }
        repository.addCategory(name)
    }

    suspend fun renameCategory(categoryId: Long, name: String): Result<Boolean> = runCatching {
        repository.renameCategory(categoryId, name)
    }

    suspend fun deleteCategory(categoryId: Long): Result<DeleteCategoryResult> = runCatching {
        repository.deleteCategory(categoryId)
    }

    suspend fun reorderCategories(orderedIds: List<Long>): Result<Unit> = runCatching {
        repository.reorderCategories(orderedIds)
    }

    suspend fun reorderItems(categoryId: Long, orderedIds: List<Long>): Result<Unit> = runCatching {
        repository.reorderItems(categoryId, orderedIds)
    }

    suspend fun addCollection(name: String): Result<Long> = runCatching {
        require(!repository.collectionNameExists(name)) { "合集名称已经存在" }
        repository.addCollection(name)
    }

    suspend fun renameCollection(collectionId: Long, name: String): Result<Boolean> = runCatching {
        repository.renameCollection(collectionId, name)
    }

    suspend fun deleteCollection(collectionId: Long): Result<Boolean> = runCatching {
        repository.deleteCollection(collectionId)
    }

    suspend fun reorderCollections(orderedIds: List<Long>): Result<Unit> = runCatching {
        repository.reorderCollections(orderedIds)
    }

    suspend fun addItemToCollection(collectionId: Long, itemId: Long): Result<Unit> = runCatching {
        repository.addItemToCollection(collectionId, itemId)
    }

    suspend fun removeItemFromCollection(
        collectionId: Long,
        itemId: Long,
    ): Result<Boolean> = runCatching {
        repository.removeItemFromCollection(collectionId, itemId)
    }

    suspend fun reorderCollectionItems(
        collectionId: Long,
        orderedIds: List<Long>,
    ): Result<Unit> = runCatching {
        repository.reorderCollectionItems(collectionId, orderedIds)
    }

    fun setTextSize(level: TextSizeLevel) {
        viewModelScope.launch { preferences.setTextSizeLevel(level) }
    }

    fun markMobileDataWarningShown() {
        viewModelScope.launch { preferences.markMobileDataWarningShown() }
    }

    suspend fun shouldShowMobileDataWarning(): Boolean =
        !preferences.hasShownMobileDataWarning.first()
}

sealed interface SaveOperation {
    val requestId: String?

    data object Idle : SaveOperation {
        override val requestId: String? = null
    }

    data class Running(override val requestId: String) : SaveOperation
    data class Succeeded(
        override val requestId: String,
        val itemId: Long,
    ) : SaveOperation

    data class Failed(
        override val requestId: String,
        val message: String,
    ) : SaveOperation
}

class KanxiViewModelFactory(
    private val application: KanxiApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(KanxiViewModel::class.java))
        return KanxiViewModel(application, application.container) as T
    }
}
