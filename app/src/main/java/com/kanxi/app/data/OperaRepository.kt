package com.kanxi.app.data

import kotlinx.coroutines.flow.Flow

/** Single UI-facing entry point for all catalog, favorite and history data. */
class OperaRepository(private val dao: OperaDao) {
    fun observeCategories(): Flow<List<OperaCategory>> = dao.observeCategories()

    fun observeItems(categoryId: Long? = null): Flow<List<OperaWithCategory>> =
        if (categoryId == null) dao.observeAllItems() else dao.observeItemsByCategory(categoryId)

    fun observeFavorites(): Flow<List<OperaWithCategory>> = dao.observeFavorites()

    fun observeRecent(limit: Int = 12): Flow<List<RecentOpera>> {
        require(limit > 0) { "Recent item limit must be positive" }
        return dao.observeRecent(limit)
    }

    fun observeItem(id: Long): Flow<OperaWithCategory?> = dao.observeItem(id)

    fun search(query: String): Flow<List<OperaWithCategory>> =
        dao.search(OperaDataRules.toLikePattern(query))

    suspend fun findItemByIdentity(bvid: String, part: Int): OperaItem? =
        dao.getItemByIdentity(OperaDataRules.normalizeBvid(bvid), OperaDataRules.normalizePart(part))

    suspend fun upsertItem(
        item: OperaItem,
        now: Long = System.currentTimeMillis(),
    ): SaveOperaResult = dao.saveItem(item, now)

    suspend fun deleteItem(itemId: Long): Boolean = dao.deleteItem(itemId) == 1

    suspend fun toggleFavorite(
        itemId: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean = dao.toggleFavorite(itemId, now)

    suspend fun setFavorite(
        itemId: Long,
        favorite: Boolean,
        now: Long = System.currentTimeMillis(),
    ) = dao.setFavorite(itemId, favorite, now)

    suspend fun isFavorite(itemId: Long): Boolean = dao.isFavorite(itemId)

    suspend fun recordOpened(
        itemId: Long,
        openedAt: Long = System.currentTimeMillis(),
    ) = dao.recordOpened(itemId, openedAt)

    suspend fun addCategory(name: String): Long = dao.addCategory(name)

    suspend fun categoryNameExists(name: String): Boolean =
        dao.getCategoryByName(name.trim()) != null

    suspend fun renameCategory(categoryId: Long, newName: String): Boolean =
        dao.renameCategory(categoryId, newName)

    suspend fun deleteCategory(categoryId: Long): DeleteCategoryResult =
        dao.deleteCategory(categoryId)

    suspend fun reorderCategories(orderedIds: List<Long>) = dao.reorderCategories(orderedIds)

    suspend fun reorderItems(categoryId: Long, orderedIds: List<Long>) =
        dao.reorderItems(categoryId, orderedIds)

    fun observeCollections(): Flow<List<OperaCollection>> = dao.observeCollections()

    fun observeCollectionsWithItems(): Flow<List<CollectionWithItems>> =
        dao.observeCollectionsWithItems()

    fun observeCollectionItems(collectionId: Long): Flow<List<OperaWithCategory>> =
        dao.observeCollectionItems(collectionId)

    suspend fun addCollection(name: String): Long = dao.addCollection(name)

    suspend fun collectionNameExists(name: String): Boolean =
        dao.getCollectionByName(name.trim()) != null

    suspend fun renameCollection(collectionId: Long, newName: String): Boolean =
        dao.renameCollection(collectionId, newName)

    suspend fun deleteCollection(collectionId: Long): Boolean =
        dao.deleteCollection(collectionId)

    suspend fun reorderCollections(orderedIds: List<Long>) = dao.reorderCollections(orderedIds)

    suspend fun addItemToCollection(collectionId: Long, itemId: Long) =
        dao.addItemToCollection(collectionId, itemId)

    suspend fun collectionContainsItem(collectionId: Long, itemId: Long): Boolean =
        dao.collectionContainsItem(collectionId, itemId)

    suspend fun removeItemFromCollection(collectionId: Long, itemId: Long): Boolean =
        dao.removeItemFromCollection(collectionId, itemId)

    suspend fun reorderCollectionItems(
        collectionId: Long,
        orderedIds: List<Long>,
    ) = dao.reorderCollectionItems(collectionId, orderedIds)
}
