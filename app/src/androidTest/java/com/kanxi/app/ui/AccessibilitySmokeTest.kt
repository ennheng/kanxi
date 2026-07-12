package com.kanxi.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kanxi.app.data.OperaCategory
import com.kanxi.app.data.OperaItem
import com.kanxi.app.data.OperaWithCategory
import com.kanxi.app.ui.components.CategoryGrid
import com.kanxi.app.ui.components.EmptyState
import com.kanxi.app.ui.screens.FavoritesScreen
import com.kanxi.app.ui.screens.HomeScreen
import com.kanxi.app.ui.theme.KanxiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccessibilitySmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateActionHasVisibleTextAndClickTarget() {
        var clicks = 0
        composeRule.setContent {
            KanxiTheme {
                EmptyState(
                    title = "片单还是空的",
                    message = "请让家人添加内容",
                    actionLabel = "家人添加第一出戏",
                    onAction = { clicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("片单还是空的").assertIsDisplayed()
        composeRule.onNodeWithText("家人添加第一出戏")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun categoryCardExposesACompleteTalkBackDescription() {
        val category = OperaCategory(id = 7, name = "越剧", sortOrder = 0)
        var selectedId = 0L
        composeRule.setContent {
            KanxiTheme {
                CategoryGrid(
                    categories = listOf(category),
                    itemCounts = mapOf(7L to 3),
                    onCategoryClick = { selectedId = it },
                )
            }
        }

        composeRule.onNode(hasText("越剧") and hasText("3 出戏"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(7L, selectedId)
    }

    @Test
    fun emptyHomeKeepsCategoryBrowsingAndFamilyAddActionAvailable() {
        val category = OperaCategory(id = 11, name = "京剧", sortOrder = 0)
        var selectedCategory = 0L
        var addClicks = 0

        composeRule.setContent {
            KanxiTheme {
                HomeScreen(
                    categories = listOf(category),
                    allItems = emptyList(),
                    recentItems = emptyList(),
                    onCategoryClick = { selectedCategory = it },
                    onItemClick = {},
                    onPlay = {},
                    onOpenSettings = {},
                    onAddFirstOpera = { addClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("片单还是空的").assertIsDisplayed()
        composeRule.onNodeWithText("京剧")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("家人添加第一出戏")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(11L, selectedCategory)
            assertEquals(1, addClicks)
        }
    }

    @Test
    fun favoritesListAnnouncesFavoriteAndOpensSelectedOpera() {
        val opera = OperaWithCategory(
            item = OperaItem(
                id = 23,
                title = "测试收藏戏",
                categoryId = 11,
                bvid = "BV1234567890",
            ),
            categoryName = "京剧",
            isFavorite = true,
        )
        var selectedItem = 0L

        composeRule.setContent {
            KanxiTheme {
                FavoritesScreen(
                    favorites = listOf(opera),
                    onItemClick = { selectedItem = it },
                )
            }
        }

        composeRule.onNodeWithText("我的收藏").assertIsDisplayed()
        composeRule.onNodeWithText("已收藏").assertIsDisplayed()
        composeRule.onNodeWithText("测试收藏戏")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(23L, selectedItem) }
    }

    @Test
    fun homeFeaturedOperaOffersOneTapEntryToTheOfficialPlayer() {
        val category = OperaCategory(id = 11, name = "京剧", sortOrder = 0)
        val opera = OperaWithCategory(
            item = OperaItem(
                id = 31,
                title = "首页直接播放测试",
                categoryId = category.id,
                bvid = "BV1234567890",
            ),
            categoryName = category.name,
            isFavorite = false,
        )
        var playedItem = 0L

        composeRule.setContent {
            KanxiTheme {
                HomeScreen(
                    categories = listOf(category),
                    allItems = listOf(opera),
                    recentItems = emptyList(),
                    onCategoryClick = {},
                    onItemClick = {},
                    onPlay = { playedItem = it },
                    onOpenSettings = {},
                    onAddFirstOpera = {},
                )
            }
        }

        composeRule.onNodeWithText("直接播放《首页直接播放测试》")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(31L, playedItem) }
    }
}
