package com.kanxi.app.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperaDaoTest {
    private lateinit var database: KanxiDatabase
    private lateinit var dao: OperaDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            KanxiDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.operaDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateShareEditsExistingItemRatherThanAddingAnotherRow() = runBlocking {
        val categoryId = dao.addCategory("越剧")
        val first = dao.saveItem(item(categoryId, title = "梁祝"), now = 10)
        val second = dao.saveItem(item(categoryId, title = "梁山伯与祝英台"), now = 20)

        assertTrue(first is SaveOperaResult.Created)
        assertTrue(second is SaveOperaResult.UpdatedExistingDuplicate)
        assertEquals(first.itemId, second.itemId)
        assertEquals("梁山伯与祝英台", dao.getItem(first.itemId)?.title)
        assertEquals(1, dao.observeAllItems().first().size)
    }

    @Test
    fun sameBvidWithDifferentPartsCreatesSeparateItems() = runBlocking {
        val categoryId = dao.addCategory("京剧")
        val first = dao.saveItem(item(categoryId, title = "第一 P", part = 1), now = 10)
        val second = dao.saveItem(item(categoryId, title = "第二 P", part = 2), now = 20)

        assertTrue(first is SaveOperaResult.Created)
        assertTrue(second is SaveOperaResult.Created)
        assertTrue(first.itemId != second.itemId)
        assertEquals(2, dao.observeAllItems().first().size)
        assertEquals(1, dao.getItem(first.itemId)?.part)
        assertEquals(2, dao.getItem(second.itemId)?.part)
    }

    @Test
    fun deletingCategoryMovesItemsToOtherAndOtherCannotBeDeleted() = runBlocking {
        val fallbackId = dao.addCategory(OTHER_CATEGORY_NAME, isBuiltIn = true)
        val categoryId = dao.addCategory("临时分类")
        val itemId = dao.saveItem(item(categoryId), now = 10).itemId

        assertEquals(DeleteCategoryResult.Deleted(movedItemCount = 1), dao.deleteCategory(categoryId))
        assertEquals(fallbackId, dao.getItem(itemId)?.categoryId)
        assertEquals(DeleteCategoryResult.ProtectedFallback, dao.deleteCategory(fallbackId))
    }

    @Test
    fun favoriteAndRecentFlowsReflectTransactionalChanges() = runBlocking {
        val categoryId = dao.addCategory("京剧")
        val itemId = dao.saveItem(item(categoryId), now = 10).itemId

        assertFalse(dao.isFavorite(itemId))
        assertTrue(dao.toggleFavorite(itemId, now = 20))
        dao.recordOpened(itemId, openedAt = 30)
        dao.recordOpened(itemId, openedAt = 40)

        assertEquals(listOf(itemId), dao.observeFavorites().first().map { it.item.id })
        val recent = dao.observeRecent(limit = 10).first().single()
        assertEquals(itemId, recent.item.id)
        assertEquals(2, recent.openCount)
        assertEquals(40, recent.lastOpenedAt)
        assertTrue(recent.isFavorite)
    }

    @Test
    fun searchTreatsPercentAndUnderscoreAsLiteralText() = runBlocking {
        val categoryId = dao.addCategory("评剧")
        dao.saveItem(item(categoryId, title = "100%_经典"), now = 10)
        dao.saveItem(
            item(
                categoryId = categoryId,
                title = "普通经典",
                bvid = "BV1GJ411x7h7",
            ),
            now = 20,
        )

        val result = dao.search(OperaDataRules.toLikePattern("%_")).first()
        assertEquals(listOf("100%_经典"), result.map { it.item.title })
    }

    @Test
    fun changingCategoryAppendsItemAfterExistingTargetItems() = runBlocking {
        val sourceId = dao.addCategory("京剧")
        val targetId = dao.addCategory("越剧")
        dao.saveItem(item(targetId, title = "目标已有", bvid = "BV1GJ411x7h7"), now = 10)
        val movingId = dao.saveItem(item(sourceId, title = "待移动"), now = 20).itemId
        val moving = requireNotNull(dao.getItem(movingId))

        dao.saveItem(moving.copy(categoryId = targetId, sortOrder = 0), now = 30)

        assertEquals(1, dao.getItem(movingId)?.sortOrder)
    }

    @Test
    fun deletingCategoryAppendsMigratedItemsWithContinuousOrder() = runBlocking {
        val fallbackId = dao.addCategory(OTHER_CATEGORY_NAME, isBuiltIn = true)
        val sourceId = dao.addCategory("临时")
        dao.saveItem(item(fallbackId, title = "其他已有", bvid = "BV1GJ411x7h7"), now = 10)
        val first = dao.saveItem(item(sourceId, title = "第一出"), now = 20).itemId
        val second = dao.saveItem(
            item(sourceId, title = "第二出", bvid = "BV1Q541167Qg"),
            now = 30,
        ).itemId

        assertEquals(DeleteCategoryResult.Deleted(2), dao.deleteCategory(sourceId))
        assertEquals(1, dao.getItem(first)?.sortOrder)
        assertEquals(2, dao.getItem(second)?.sortOrder)
    }

    @Test
    fun collectionCrudAndItemManagement() = runBlocking {
        val categoryId = dao.addCategory("京剧")
        val first = dao.saveItem(item(categoryId, title = "第一出"), now = 10).itemId
        val second = dao.saveItem(
            item(categoryId, title = "第二出", bvid = "BV1Q541167Qg"),
            now = 20,
        ).itemId

        val collectionId = dao.addCollection("精选")
        assertEquals("精选", dao.getCollection(collectionId)?.name)

        dao.addItemToCollection(collectionId, first)
        dao.addItemToCollection(collectionId, second)
        assertTrue(dao.collectionContainsItem(collectionId, first))

        var items = dao.observeCollectionItems(collectionId).first()
        assertEquals(listOf("第一出", "第二出"), items.map { it.item.title })

        dao.reorderCollectionItems(collectionId, listOf(second, first))
        items = dao.observeCollectionItems(collectionId).first()
        assertEquals(listOf("第二出", "第一出"), items.map { it.item.title })

        assertTrue(dao.removeItemFromCollection(collectionId, first))
        assertFalse(dao.collectionContainsItem(collectionId, first))
        items = dao.observeCollectionItems(collectionId).first()
        assertEquals(listOf("第二出"), items.map { it.item.title })

        assertTrue(dao.renameCollection(collectionId, "更新精选"))
        assertEquals("更新精选", dao.getCollection(collectionId)?.name)

        val counts = dao.observeCollectionsWithItems().first()
        assertEquals(1, counts.size)
        assertEquals(1, counts.single().itemCount)

        assertTrue(dao.deleteCollection(collectionId))
        assertEquals(emptyList<OperaWithCategory>(), dao.observeCollectionItems(collectionId).first())
    }

    @Test
    fun deletingOperaRemovesItFromCollection() = runBlocking {
        val categoryId = dao.addCategory("越剧")
        val itemId = dao.saveItem(item(categoryId), now = 10).itemId
        val collectionId = dao.addCollection("合集")
        dao.addItemToCollection(collectionId, itemId)

        dao.deleteItem(itemId)

        assertEquals(emptyList<OperaWithCategory>(), dao.observeCollectionItems(collectionId).first())
        val counts = dao.observeCollectionsWithItems().first()
        assertEquals(0, counts.single().itemCount)
    }

    private fun item(
        categoryId: Long,
        title: String = "测试戏曲",
        bvid: String = "BV1xx411c7mD",
        part: Int = 1,
    ) = OperaItem(
        title = title,
        categoryId = categoryId,
        bvid = bvid,
        part = part,
    )
}
