package com.kanxi.app.ui

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.kanxi.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShareIntentUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun actionSendTextNavigatesToEditorAndPrefillsSharedFields() {
        val sharedTitle = "分享入口仪器测试"
        val sharedText =
            "【$sharedTitle】 https://www.bilibili.com/video/BV1234567890?p=2"
        val shareIntent = Intent(composeRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        composeRule.runOnUiThread { composeRule.activity.startActivity(shareIntent) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("添加戏曲").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("添加戏曲").assertIsDisplayed()
        composeRule.onNodeWithText(sharedText).assertIsDisplayed()

        // The title field is in the third LazyColumn item, below the link section.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        composeRule.onNodeWithText(sharedTitle).assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithText(sharedTitle).fetchSemanticsNodes().size,
        )
    }
}
