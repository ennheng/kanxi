package com.kanxi.app.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.kanxi.app.KanxiApplication
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaItem
import com.kanxi.app.data.OperaRepository
import com.kanxi.app.ui.screens.CategoryScreen
import com.kanxi.app.ui.screens.DetailScreen
import com.kanxi.app.ui.screens.FamilyManagementScreen
import com.kanxi.app.ui.screens.OperaEditorScreen
import com.kanxi.app.ui.screens.SearchScreen
import com.kanxi.app.ui.theme.KanxiTheme
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI integration tests that exercise Compose against the same Room-backed ViewModel as the app.
 * Every test creates a unique BVID and removes it afterwards, so the suite does not depend on test
 * order and can safely run against an already-used test installation.
 */
class CatalogUiFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repository: OperaRepository
    private lateinit var viewModel: KanxiViewModel
    private lateinit var category: OperaCategory
    private val identitiesToDelete = linkedSetOf<Pair<String, Int>>()

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<KanxiApplication>()
        repository = application.container.operaRepository
        viewModel = KanxiViewModel(application, application.container)
        category = runBlocking {
            withTimeout(10_000) {
                repository.observeCategories().first { it.isNotEmpty() }.first()
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            identitiesToDelete.forEach { (bvid, part) ->
                repository.findItemByIdentity(bvid, part)?.let { repository.deleteItem(it.id) }
            }
        }
    }

    @Test
    fun categoryBrowseShowsOperaAndOffersDirectPlay() {
        val title = uniqueTitle("分类浏览")
        val item = seedOpera(title = title)
        val openedId = AtomicLong(0)
        val playedId = AtomicLong(0)

        composeRule.setContent {
            KanxiTheme {
                CategoryScreen(
                    viewModel = viewModel,
                    category = category,
                    onBack = {},
                    onItemClick = openedId::set,
                    onPlay = playedId::set,
                )
            }
        }

        waitForText(title)
        composeRule.onNodeWithText("直接播放《$title》")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertEquals(item.id, playedId.get()) }

        composeRule.onNodeWithText(title)
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertEquals(item.id, openedId.get()) }
    }

    @Test
    fun localSearchMatchesAliasAndOpensResult() {
        val title = uniqueTitle("搜索结果")
        val alias = "别名${UUID.randomUUID().toString().take(8)}"
        val item = seedOpera(title = title, aliases = alias)
        val openedId = AtomicLong(0)

        composeRule.setContent {
            KanxiTheme {
                SearchScreen(
                    viewModel = viewModel,
                    onItemClick = openedId::set,
                )
            }
        }

        textField("输入剧名、剧种、演员或剧团").performTextInput(alias)
        waitForText(title)
        composeRule.onNodeWithText(title).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(item.id, openedId.get()) }

        textField("输入剧名、剧种、演员或剧团")
            .performTextReplacement("肯定不存在-${UUID.randomUUID()}")
        waitForText("没有找到")
        composeRule.onNodeWithText("没有找到").assertIsDisplayed()
    }

    @Test
    fun detailFavoriteButtonPersistsAndUpdatesItsLabel() {
        val title = uniqueTitle("收藏切换")
        val item = seedOpera(title = title)

        composeRule.setContent {
            KanxiTheme {
                DetailScreen(
                    viewModel = viewModel,
                    itemId = item.id,
                    onBack = {},
                    onPlay = {},
                )
            }
        }

        waitForText("收藏")
        composeRule.onNodeWithText("收藏").performScrollTo().performClick()
        waitForText("取消收藏")
        assertTrue(runBlocking { repository.isFavorite(item.id) })
    }

    @Test
    fun sharedTextPrefillsTitleAndLinkAndIsConsumedOnce() {
        val bvid = newBvid()
        trackIdentity(bvid, 2)
        val sharedTitle = uniqueTitle("分享预填")
        val sharedText = "【$sharedTitle】 https://www.bilibili.com/video/$bvid?p=2"
        val consumedCount = AtomicInteger(0)

        composeRule.setContent {
            KanxiTheme {
                OperaEditorScreen(
                    viewModel = viewModel,
                    itemId = 0,
                    categories = listOf(category),
                    initialSharedText = sharedText,
                    onSharedTextConsumed = { consumedCount.incrementAndGet() },
                    onEditExistingDuplicate = {},
                    onSaved = {},
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        composeRule.onNodeWithText(sharedText).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, consumedCount.get()) }

        scrollEditorToBasicInfo()
        waitForText(sharedTitle)
        composeRule.onNodeWithText(sharedTitle).assertIsDisplayed()

        // A normal recomposition must not consume the same ACTION_SEND payload again.
        composeRule.runOnIdle { }
        assertEquals(1, consumedCount.get())
    }

    @Test
    fun pastedFullLinkCanBeValidatedAndSavedWithoutNetwork() {
        val bvid = newBvid()
        trackIdentity(bvid, 3)
        val title = uniqueTitle("粘贴添加")
        val savedId = AtomicLong(0)
        val latestMessage = AtomicReference("")

        composeRule.setContent {
            KanxiTheme {
                OperaEditorScreen(
                    viewModel = viewModel,
                    itemId = 0,
                    categories = listOf(category),
                    initialSharedText = null,
                    onSharedTextConsumed = {},
                    onEditExistingDuplicate = {},
                    onSaved = savedId::set,
                    onBack = {},
                    onMessage = latestMessage::set,
                )
            }
        }

        textField("粘贴或分享链接")
            .performTextInput("https://www.bilibili.com/video/$bvid?p=3")
        textField("剧名（必填）")
            .performScrollTo()
            .performTextInput(title)
        scrollEditorToActions()
        composeRule.onNodeWithText("保存").performClick()

        composeRule.waitUntil(10_000) { savedId.get() > 0 }
        assertEquals("戏曲已添加", latestMessage.get())
        val saved = runBlocking {
            withTimeout(10_000) {
                repository.observeItem(savedId.get()).first { it != null }
            }
        }
        assertEquals(title, saved?.item?.title)
        assertEquals(bvid, saved?.item?.bvid)
        assertEquals(3, saved?.item?.part)
    }

    @Test
    fun categorySelectorCanChooseAFamilyDefinedCategory() {
        val custom = category.copy(
            id = category.id + 10_000,
            name = "秦腔-${UUID.randomUUID().toString().take(4)}",
            sortOrder = category.sortOrder + 1,
            isBuiltIn = false,
        )

        composeRule.setContent {
            KanxiTheme {
                OperaEditorScreen(
                    viewModel = viewModel,
                    itemId = 0,
                    categories = listOf(category, custom),
                    initialSharedText = null,
                    onSharedTextConsumed = {},
                    onEditExistingDuplicate = {},
                    onSaved = {},
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        scrollEditorToBasicInfo()
        waitForText("已选择：${category.name}（点击更换）")
        composeRule.onNodeWithText("已选择：${category.name}（点击更换）")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(custom.name).performScrollTo().performClick()
        composeRule.onNodeWithText("使用这个剧种").performScrollTo().performClick()
        composeRule.onNodeWithText("已选择：${custom.name}（点击更换）")
            .assertIsDisplayed()
    }

    @Test
    fun editorUpdatesExistingTitleAndManualPartOnTheSameItem() {
        val oldTitle = uniqueTitle("编辑前")
        val newTitle = uniqueTitle("编辑后")
        val item = seedOpera(title = oldTitle, part = 4)
        trackIdentity(item.bvid, 6)
        val savedId = AtomicLong(0)

        composeRule.setContent {
            KanxiTheme {
                OperaEditorScreen(
                    viewModel = viewModel,
                    itemId = item.id,
                    categories = listOf(category),
                    initialSharedText = null,
                    onSharedTextConsumed = {},
                    onEditExistingDuplicate = {},
                    onSaved = savedId::set,
                    onBack = {},
                    onMessage = {},
                )
            }
        }

        waitForText("链接已验证")
        textField("分 P 编号").performScrollTo().performTextReplacement("6")
        scrollEditorToBasicInfo()
        waitForText(oldTitle)
        textField("剧名（必填）").performTextReplacement(newTitle)
        scrollEditorToActions()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) { savedId.get() == item.id }

        val updated = runBlocking {
            withTimeout(10_000) {
                repository.observeItem(item.id).first { it?.item?.title == newTitle }
            }
        }
        assertNotNull(updated)
        assertEquals(item.bvid, updated?.item?.bvid)
        assertEquals(6, updated?.item?.part)
    }

    @Test
    fun duplicateShareRoutesToTheExistingFinalPart() {
        val item = seedOpera(title = uniqueTitle("已有分P"), part = 8)
        val openedDuplicateId = AtomicLong(0)
        val latestMessage = AtomicReference("")

        composeRule.setContent {
            KanxiTheme {
                OperaEditorScreen(
                    viewModel = viewModel,
                    itemId = 0,
                    categories = listOf(category),
                    initialSharedText =
                        "分享 https://www.bilibili.com/video/${item.bvid}?p=${item.part}",
                    onSharedTextConsumed = {},
                    onEditExistingDuplicate = openedDuplicateId::set,
                    onSaved = {},
                    onBack = {},
                    onMessage = latestMessage::set,
                )
            }
        }

        scrollEditorToActions()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) { openedDuplicateId.get() == item.id }
        assertEquals("这一 P 已经在片单中，请直接修改原条目", latestMessage.get())
    }

    @Test
    fun managementRequiresConfirmationThenDeletesAndRoutesEdit() {
        val title = uniqueTitle("管理删除")
        val item = seedOpera(title = title)
        val projection = runBlocking {
            withTimeout(10_000) {
                repository.observeItem(item.id).first { it != null }!!
            }
        }
        val editedId = AtomicLong(0)
        val latestMessage = AtomicReference("")

        composeRule.setContent {
            KanxiTheme {
                FamilyManagementScreen(
                    viewModel = viewModel,
                    categories = listOf(category),
                    allItems = listOf(projection),
                    onAddOpera = {},
                    onEditOpera = editedId::set,
                    onManageCategories = {},
                    onManageCollections = {},
                    onBack = {},
                    onMessage = latestMessage::set,
                )
            }
        }

        composeRule.onNodeWithText("编辑").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(item.id, editedId.get()) }

        composeRule.onNodeWithText("删除").performScrollTo().performClick()
        composeRule.onNodeWithText("删除《$title》？").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        assertNotNull(runBlocking { repository.findItemByIdentity(item.bvid, item.part) })

        composeRule.onNodeWithText("删除").performScrollTo().performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.waitUntil(10_000) { latestMessage.get() == "已删除《$title》" }
        val deleted = runBlocking {
            withTimeout(10_000) {
                repository.observeItem(item.id).first { it == null }
            }
        }
        assertNull(deleted)
        assertFalse(runBlocking { repository.isFavorite(item.id) })
    }

    private fun textField(label: String): SemanticsNodeInteraction = composeRule.onNode(
        hasSetTextAction() and hasAnyDescendant(hasText(label, substring = true)),
        useUnmergedTree = true,
    )

    private fun waitForText(text: String) {
        // Older/slower emulator images can take more than one Room invalidation cycle to deliver
        // a newly seeded row to Compose. This remains a bounded wait and avoids timing flakes.
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollEditorToActions() {
        // LazyColumn does not compose the bottom action section until it is brought into view.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
    }

    private fun scrollEditorToBasicInfo() {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
    }

    private fun seedOpera(
        title: String,
        aliases: String = "",
        part: Int = 1,
    ): OperaItem {
        val bvid = newBvid()
        trackIdentity(bvid, part)
        val id = runBlocking {
            repository.upsertItem(
                OperaItem(
                    title = title,
                    categoryId = category.id,
                    aliases = aliases,
                    bvid = bvid,
                    part = part,
                ),
            ).itemId
        }
        return runBlocking {
            withTimeout(10_000) {
                repository.observeItem(id).first { it != null }!!.item
            }
        }
    }

    private fun trackIdentity(bvid: String, part: Int) {
        identitiesToDelete += bvid to part
    }

    private fun newBvid(): String = "BV${UUID.randomUUID().toString().replace("-", "").take(10)}"

    private fun uniqueTitle(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(8)}"
}
