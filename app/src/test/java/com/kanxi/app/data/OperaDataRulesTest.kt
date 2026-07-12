package com.kanxi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OperaDataRulesTest {
    @Test
    fun defaultCategories_areStableAndEndWithFallback() {
        assertEquals(
            listOf("京剧", "越剧", "其他"),
            DefaultOperaCategories.names,
        )
        assertEquals(3, DefaultOperaCategories.names.distinct().size)
        assertEquals(OTHER_CATEGORY_NAME, DefaultOperaCategories.names.last())
    }

    @Test
    fun normalizeItem_trimsFieldsAndCanonicalizesBvPrefix() {
        val normalized = OperaDataRules.normalizeItem(
            item(
                title = "  梁祝  ",
                bvid = "  bv1xx411c7mD ",
                actors = " 袁雪芬 ",
                coverPath = "  ",
            ),
        )

        assertEquals("梁祝", normalized.title)
        assertEquals("BV1xx411c7mD", normalized.bvid)
        assertEquals("袁雪芬", normalized.actors)
        assertNull(normalized.coverPath)
    }

    @Test
    fun normalizeItem_rejectsInvalidBvidAndPart() {
        expectFailure { OperaDataRules.normalizeItem(item(bvid = "not-a-bvid")) }
        expectFailure { OperaDataRules.normalizeItem(item(part = 0)) }
    }

    @Test
    fun duplicateSharedIdentity_updatesExistingRow() {
        assertEquals(SaveTarget.Insert, OperaDataRules.resolveSaveTarget(0, null))
        assertEquals(
            SaveTarget.Update(itemId = 42, wasDuplicateShare = true),
            OperaDataRules.resolveSaveTarget(incomingId = 0, existingIdentityId = 42),
        )
        assertEquals(
            SaveTarget.Update(itemId = 7, wasDuplicateShare = false),
            OperaDataRules.resolveSaveTarget(incomingId = 7, existingIdentityId = 7),
        )
    }

    @Test
    fun editingIntoAnotherRowsIdentity_isRejected() {
        try {
            OperaDataRules.resolveSaveTarget(incomingId = 7, existingIdentityId = 42)
            fail("Expected DuplicateOperaIdentityException")
        } catch (error: DuplicateOperaIdentityException) {
            assertEquals(42, error.existingItemId)
        }
    }

    @Test
    fun categoryDeletion_migratesToOtherButProtectsOtherItself() {
        assertEquals(
            CategoryDeletionPlan.MigrateAndDelete(targetId = 3, fallbackId = 7),
            OperaDataRules.planCategoryDeletion(targetId = 3, fallbackId = 7),
        )
        assertEquals(
            CategoryDeletionPlan.ProtectFallback,
            OperaDataRules.planCategoryDeletion(targetId = 7, fallbackId = 7),
        )
    }

    @Test
    fun favoriteToggleAndHistoryIncrement_areDeterministic() {
        assertTrue(OperaDataRules.nextFavoriteState(currentlyFavorite = false))
        assertFalse(OperaDataRules.nextFavoriteState(currentlyFavorite = true))

        val first = OperaDataRules.nextHistory(existing = null, itemId = 9, openedAt = 100)
        val second = OperaDataRules.nextHistory(existing = first, itemId = 9, openedAt = 250)
        assertEquals(1, first.openCount)
        assertEquals(2, second.openCount)
        assertEquals(250, second.lastOpenedAt)
    }

    @Test
    fun likePattern_escapesUserWildcards() {
        assertEquals("%100\\%\\_戏\\\\曲%", OperaDataRules.toLikePattern("100%_戏\\曲"))
        assertEquals("%%", OperaDataRules.toLikePattern("   "))
    }

    @Test
    fun exactReorder_rejectsMissingOrRepeatedIds() {
        OperaDataRules.requireExactOrder(listOf(1, 2, 3), listOf(3, 1, 2))
        expectFailure { OperaDataRules.requireExactOrder(listOf(1, 2, 3), listOf(1, 2)) }
        expectFailure { OperaDataRules.requireExactOrder(listOf(1, 2, 3), listOf(1, 1, 3)) }
    }

    private fun item(
        title: String = "测试戏曲",
        bvid: String = "BV1xx411c7mD",
        part: Int = 1,
        actors: String = "",
        coverPath: String? = null,
    ) = OperaItem(
        title = title,
        categoryId = 1,
        actors = actors,
        bvid = bvid,
        part = part,
        coverPath = coverPath,
    )

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
