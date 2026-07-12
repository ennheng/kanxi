package com.kanxi.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class OperaDao {
    @Query("SELECT * FROM opera_categories ORDER BY sort_order ASC, id ASC")
    abstract fun observeCategories(): Flow<List<OperaCategory>>

    @Query("SELECT * FROM opera_categories ORDER BY sort_order ASC, id ASC")
    abstract suspend fun getCategories(): List<OperaCategory>

    @Query("SELECT * FROM opera_categories WHERE id = :categoryId LIMIT 1")
    abstract suspend fun getCategory(categoryId: Long): OperaCategory?

    @Query("SELECT * FROM opera_categories WHERE name COLLATE NOCASE = :name LIMIT 1")
    abstract suspend fun getCategoryByName(name: String): OperaCategory?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCategoryInternal(category: OperaCategory): Long

    @Update
    protected abstract suspend fun updateCategoryInternal(category: OperaCategory): Int

    @Query("DELETE FROM opera_categories WHERE id = :categoryId")
    protected abstract suspend fun deleteCategoryInternal(categoryId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM opera_categories")
    protected abstract suspend fun nextCategorySortOrder(): Int

    @Query("UPDATE opera_categories SET sort_order = :sortOrder WHERE id = :categoryId")
    protected abstract suspend fun setCategorySortOrder(categoryId: Long, sortOrder: Int): Int

    @Query(
        """
        SELECT i.*, c.name AS category_name,
               CASE WHEN f.item_id IS NULL THEN 0 ELSE 1 END AS is_favorite
        FROM opera_items AS i
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        LEFT JOIN favorites AS f ON f.item_id = i.id
        ORDER BY i.sort_order ASC, i.updated_at DESC, i.id DESC
        """,
    )
    abstract fun observeAllItems(): Flow<List<OperaWithCategory>>

    @Query(
        """
        SELECT i.*, c.name AS category_name,
               CASE WHEN f.item_id IS NULL THEN 0 ELSE 1 END AS is_favorite
        FROM opera_items AS i
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        LEFT JOIN favorites AS f ON f.item_id = i.id
        WHERE i.category_id = :categoryId
        ORDER BY i.sort_order ASC, i.updated_at DESC, i.id DESC
        """,
    )
    abstract fun observeItemsByCategory(categoryId: Long): Flow<List<OperaWithCategory>>

    @Query(
        """
        SELECT i.*, c.name AS category_name,
               CASE WHEN f.item_id IS NULL THEN 0 ELSE 1 END AS is_favorite
        FROM opera_items AS i
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        LEFT JOIN favorites AS f ON f.item_id = i.id
        WHERE i.id = :itemId
        LIMIT 1
        """,
    )
    abstract fun observeItem(itemId: Long): Flow<OperaWithCategory?>

    @Query(
        """
        SELECT i.*, c.name AS category_name,
               CASE WHEN f.item_id IS NULL THEN 0 ELSE 1 END AS is_favorite
        FROM opera_items AS i
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        LEFT JOIN favorites AS f ON f.item_id = i.id
        WHERE i.title LIKE :pattern ESCAPE '\'
           OR c.name LIKE :pattern ESCAPE '\'
           OR i.actors LIKE :pattern ESCAPE '\'
           OR i.troupe LIKE :pattern ESCAPE '\'
           OR i.aliases LIKE :pattern ESCAPE '\'
        ORDER BY i.sort_order ASC, i.updated_at DESC, i.id DESC
        """,
    )
    abstract fun search(pattern: String): Flow<List<OperaWithCategory>>

    @Query(
        """
        SELECT i.*, c.name AS category_name, 1 AS is_favorite
        FROM favorites AS f
        INNER JOIN opera_items AS i ON i.id = f.item_id
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        ORDER BY f.favorited_at DESC, i.id DESC
        """,
    )
    abstract fun observeFavorites(): Flow<List<OperaWithCategory>>

    @Query(
        """
        SELECT i.*, c.name AS category_name,
               CASE WHEN f.item_id IS NULL THEN 0 ELSE 1 END AS is_favorite,
               h.last_opened_at AS last_opened_at, h.open_count AS open_count
        FROM view_history AS h
        INNER JOIN opera_items AS i ON i.id = h.item_id
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        LEFT JOIN favorites AS f ON f.item_id = i.id
        ORDER BY h.last_opened_at DESC, i.id DESC
        LIMIT :limit
        """,
    )
    abstract fun observeRecent(limit: Int): Flow<List<RecentOpera>>

    @Query("SELECT * FROM opera_items WHERE id = :itemId LIMIT 1")
    abstract suspend fun getItem(itemId: Long): OperaItem?

    @Query("SELECT * FROM opera_items WHERE bvid = :bvid AND part = :part LIMIT 1")
    abstract suspend fun getItemByIdentity(bvid: String, part: Int): OperaItem?

    @Query("SELECT id FROM opera_items WHERE category_id = :categoryId ORDER BY sort_order ASC, id ASC")
    protected abstract suspend fun getItemIdsInCategory(categoryId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertItemInternal(item: OperaItem): Long

    @Update
    protected abstract suspend fun updateItemInternal(item: OperaItem): Int

    @Query("DELETE FROM opera_items WHERE id = :itemId")
    abstract suspend fun deleteItem(itemId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM opera_items WHERE category_id = :categoryId")
    protected abstract suspend fun nextItemSortOrder(categoryId: Long): Int

    @Query("UPDATE opera_items SET sort_order = :sortOrder WHERE id = :itemId AND category_id = :categoryId")
    protected abstract suspend fun setItemSortOrder(
        categoryId: Long,
        itemId: Long,
        sortOrder: Int,
    ): Int

    @Query(
        "UPDATE opera_items SET category_id = :categoryId, sort_order = :sortOrder " +
            "WHERE id = :itemId",
    )
    protected abstract suspend fun moveItemToCategory(
        itemId: Long,
        categoryId: Long,
        sortOrder: Int,
    ): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE item_id = :itemId)")
    abstract suspend fun isFavorite(itemId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE item_id = :itemId")
    protected abstract suspend fun removeFavorite(itemId: Long): Int

    @Query("SELECT * FROM view_history WHERE item_id = :itemId LIMIT 1")
    protected abstract suspend fun getHistory(itemId: Long): ViewHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putHistory(history: ViewHistory)

    @Transaction
    open suspend fun saveItem(item: OperaItem, now: Long): SaveOperaResult {
        val normalized = OperaDataRules.normalizeItem(item)
        check(getCategory(normalized.categoryId) != null) { "Category does not exist" }
        val identityMatch = getItemByIdentity(normalized.bvid, normalized.part)
        return when (val target = OperaDataRules.resolveSaveTarget(normalized.id, identityMatch?.id)) {
            SaveTarget.Insert -> {
                val sortOrder = normalized.sortOrder.takeUnless { it == OperaItem.UNSORTED }
                    ?: nextItemSortOrder(normalized.categoryId)
                val id = insertItemInternal(
                    normalized.copy(
                        id = 0,
                        sortOrder = sortOrder,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                SaveOperaResult.Created(id)
            }

            is SaveTarget.Update -> {
                val current = getItem(target.itemId)
                    ?: error("Opera item ${target.itemId} does not exist")
                val sortOrder = if (current.categoryId != normalized.categoryId) {
                    nextItemSortOrder(normalized.categoryId)
                } else {
                    normalized.sortOrder.takeUnless { it == OperaItem.UNSORTED }
                        ?: current.sortOrder
                }
                val rows = updateItemInternal(
                    normalized.copy(
                        id = target.itemId,
                        sortOrder = sortOrder,
                        createdAt = current.createdAt,
                        updatedAt = now,
                    ),
                )
                check(rows == 1) { "Opera update failed" }
                if (target.wasDuplicateShare) {
                    SaveOperaResult.UpdatedExistingDuplicate(target.itemId)
                } else {
                    SaveOperaResult.Updated(target.itemId)
                }
            }
        }
    }

    @Transaction
    open suspend fun addCategory(name: String, isBuiltIn: Boolean = false): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Category name cannot be blank" }
        getCategoryByName(normalizedName)?.let { return it.id }
        return insertCategoryInternal(
            OperaCategory(
                name = normalizedName,
                sortOrder = nextCategorySortOrder(),
                isBuiltIn = isBuiltIn,
            ),
        )
    }

    @Transaction
    open suspend fun renameCategory(categoryId: Long, newName: String): Boolean {
        val category = getCategory(categoryId) ?: return false
        check(category.name != OTHER_CATEGORY_NAME) { "The fallback category cannot be renamed" }
        val normalizedName = newName.trim()
        require(normalizedName.isNotEmpty()) { "Category name cannot be blank" }
        val sameName = getCategoryByName(normalizedName)
        require(sameName == null || sameName.id == categoryId) { "Category name already exists" }
        return updateCategoryInternal(category.copy(name = normalizedName)) == 1
    }

    @Transaction
    open suspend fun deleteCategory(categoryId: Long): DeleteCategoryResult {
        val target = getCategory(categoryId) ?: return DeleteCategoryResult.NotFound
        val fallback = getCategoryByName(OTHER_CATEGORY_NAME)
            ?: error("The fallback category is missing")
        return when (val plan = OperaDataRules.planCategoryDeletion(target.id, fallback.id)) {
            CategoryDeletionPlan.ProtectFallback -> DeleteCategoryResult.ProtectedFallback
            is CategoryDeletionPlan.MigrateAndDelete -> {
                val movingIds = getItemIdsInCategory(plan.targetId)
                val fallbackStart = nextItemSortOrder(plan.fallbackId)
                movingIds.forEachIndexed { index, itemId ->
                    check(
                        moveItemToCategory(
                            itemId = itemId,
                            categoryId = plan.fallbackId,
                            sortOrder = fallbackStart + index,
                        ) == 1,
                    ) { "Opera item $itemId could not be moved to fallback category" }
                }
                check(deleteCategoryInternal(plan.targetId) == 1) { "Category deletion failed" }
                DeleteCategoryResult.Deleted(movingIds.size)
            }
        }
    }

    @Transaction
    open suspend fun reorderCategories(orderedIds: List<Long>) {
        val actualIds = getCategories().map(OperaCategory::id)
        OperaDataRules.requireExactOrder(actualIds, orderedIds)
        orderedIds.forEachIndexed { index, id ->
            check(setCategorySortOrder(id, index) == 1) { "Category $id does not exist" }
        }
    }

    @Transaction
    open suspend fun reorderItems(categoryId: Long, orderedIds: List<Long>) {
        val actualIds = getItemIdsInCategory(categoryId)
        OperaDataRules.requireExactOrder(actualIds, orderedIds)
        orderedIds.forEachIndexed { index, id ->
            check(setItemSortOrder(categoryId, id, index) == 1) { "Opera item $id does not exist" }
        }
    }

    @Transaction
    open suspend fun setFavorite(itemId: Long, favorite: Boolean, now: Long) {
        check(getItem(itemId) != null) { "Opera item does not exist" }
        if (favorite) {
            putFavorite(Favorite(itemId, now))
        } else {
            removeFavorite(itemId)
        }
    }

    @Transaction
    open suspend fun toggleFavorite(itemId: Long, now: Long): Boolean {
        val next = OperaDataRules.nextFavoriteState(isFavorite(itemId))
        setFavorite(itemId, next, now)
        return next
    }

    @Transaction
    open suspend fun recordOpened(itemId: Long, openedAt: Long) {
        check(getItem(itemId) != null) { "Opera item does not exist" }
        putHistory(OperaDataRules.nextHistory(getHistory(itemId), itemId, openedAt))
    }

    @Query("SELECT * FROM opera_collections ORDER BY sort_order ASC, id ASC")
    abstract fun observeCollections(): Flow<List<OperaCollection>>

    @Query("SELECT * FROM opera_collections ORDER BY sort_order ASC, id ASC")
    abstract suspend fun getCollections(): List<OperaCollection>

    @Query("SELECT * FROM opera_collections WHERE id = :collectionId LIMIT 1")
    abstract suspend fun getCollection(collectionId: Long): OperaCollection?

    @Query("SELECT * FROM opera_collections WHERE name COLLATE NOCASE = :name LIMIT 1")
    abstract suspend fun getCollectionByName(name: String): OperaCollection?

    @Query(
        """
        SELECT c.*, COUNT(ci.item_id) AS item_count
        FROM opera_collections AS c
        LEFT JOIN collection_items AS ci ON ci.collection_id = c.id
        GROUP BY c.id
        ORDER BY c.sort_order ASC, c.id ASC
        """,
    )
    abstract fun observeCollectionsWithItems(): Flow<List<CollectionWithItems>>

    @Query(
        """
        SELECT i.*, c.name AS category_name,
               CASE WHEN f.item_id IS NULL THEN 0 ELSE 1 END AS is_favorite
        FROM collection_items AS ci
        INNER JOIN opera_items AS i ON i.id = ci.item_id
        INNER JOIN opera_categories AS c ON c.id = i.category_id
        LEFT JOIN favorites AS f ON f.item_id = i.id
        WHERE ci.collection_id = :collectionId
        ORDER BY ci.sort_order ASC, i.id ASC
        """,
    )
    abstract fun observeCollectionItems(collectionId: Long): Flow<List<OperaWithCategory>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCollectionInternal(collection: OperaCollection): Long

    @Update
    protected abstract suspend fun updateCollectionInternal(collection: OperaCollection): Int

    @Query("DELETE FROM opera_collections WHERE id = :collectionId")
    protected abstract suspend fun deleteCollectionInternal(collectionId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM opera_collections")
    protected abstract suspend fun nextCollectionSortOrder(): Int

    @Query("UPDATE opera_collections SET sort_order = :sortOrder WHERE id = :collectionId")
    protected abstract suspend fun setCollectionSortOrder(
        collectionId: Long,
        sortOrder: Int,
    ): Int

    @Query("SELECT item_id FROM collection_items WHERE collection_id = :collectionId ORDER BY sort_order ASC, item_id ASC")
    protected abstract suspend fun getItemIdsInCollection(collectionId: Long): List<Long>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM collection_items WHERE collection_id = :collectionId AND item_id = :itemId)",
    )
    abstract suspend fun collectionContainsItem(collectionId: Long, itemId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCollectionItemInternal(item: CollectionItem)

    @Query(
        "DELETE FROM collection_items WHERE collection_id = :collectionId AND item_id = :itemId",
    )
    protected abstract suspend fun deleteCollectionItemInternal(
        collectionId: Long,
        itemId: Long,
    ): Int

    @Query(
        "UPDATE collection_items SET sort_order = :sortOrder " +
            "WHERE collection_id = :collectionId AND item_id = :itemId",
    )
    protected abstract suspend fun setCollectionItemSortOrder(
        collectionId: Long,
        itemId: Long,
        sortOrder: Int,
    ): Int

    @Transaction
    open suspend fun addCollection(name: String): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Collection name cannot be blank" }
        getCollectionByName(normalizedName)?.let { return it.id }
        return insertCollectionInternal(
            OperaCollection(
                name = normalizedName,
                sortOrder = nextCollectionSortOrder(),
            ),
        )
    }

    @Transaction
    open suspend fun renameCollection(collectionId: Long, newName: String): Boolean {
        val collection = getCollection(collectionId) ?: return false
        val normalizedName = newName.trim()
        require(normalizedName.isNotEmpty()) { "Collection name cannot be blank" }
        val sameName = getCollectionByName(normalizedName)
        require(sameName == null || sameName.id == collectionId) { "Collection name already exists" }
        return updateCollectionInternal(collection.copy(name = normalizedName)) == 1
    }

    @Transaction
    open suspend fun deleteCollection(collectionId: Long): Boolean {
        val collection = getCollection(collectionId) ?: return false
        return deleteCollectionInternal(collection.id) == 1
    }

    @Transaction
    open suspend fun reorderCollections(orderedIds: List<Long>) {
        val actualIds = getCollections().map(OperaCollection::id)
        OperaDataRules.requireExactOrder(actualIds, orderedIds)
        orderedIds.forEachIndexed { index, id ->
            check(setCollectionSortOrder(id, index) == 1) { "Collection $id does not exist" }
        }
    }

    @Transaction
    open suspend fun addItemToCollection(collectionId: Long, itemId: Long) {
        check(getCollection(collectionId) != null) { "Collection does not exist" }
        check(getItem(itemId) != null) { "Opera item does not exist" }
        check(!collectionContainsItem(collectionId, itemId)) {
            "Item already exists in collection"
        }
        val sortOrder = nextCollectionItemSortOrder(collectionId)
        insertCollectionItemInternal(
            CollectionItem(
                collectionId = collectionId,
                itemId = itemId,
                sortOrder = sortOrder,
            ),
        )
    }

    @Transaction
    open suspend fun removeItemFromCollection(collectionId: Long, itemId: Long): Boolean {
        return deleteCollectionItemInternal(collectionId, itemId) == 1
    }

    @Transaction
    open suspend fun reorderCollectionItems(
        collectionId: Long,
        orderedIds: List<Long>,
    ) {
        val actualIds = getItemIdsInCollection(collectionId)
        OperaDataRules.requireExactOrder(actualIds, orderedIds)
        orderedIds.forEachIndexed { index, itemId ->
            check(
                setCollectionItemSortOrder(collectionId, itemId, index) == 1,
            ) { "Collection item $itemId does not exist" }
        }
    }

    @Query(
        "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM collection_items WHERE collection_id = :collectionId",
    )
    protected abstract suspend fun nextCollectionItemSortOrder(collectionId: Long): Int
}
