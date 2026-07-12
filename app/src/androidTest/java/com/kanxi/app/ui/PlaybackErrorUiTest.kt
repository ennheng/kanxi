package com.kanxi.app.ui

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kanxi.app.playback.PlaybackScreen
import com.kanxi.app.ui.theme.KanxiTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaybackErrorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rejectedPlayerUrlStaysOnErrorPageAndOffersCanonicalExternalFallback() {
        val originalUrl = "https://www.bilibili.com/video/BV1234567890?p=2"
        val openedUri = AtomicReference<Uri?>()
        val backClicks = AtomicInteger(0)

        composeRule.setContent {
            KanxiTheme {
                PlaybackScreen(
                    title = "错误恢复测试",
                    playerUrl = "https://example.com/player.html?bvid=BV1234567890&p=2",
                    originalUrl = originalUrl,
                    onBack = { backClicks.incrementAndGet() },
                    externalOpener = { _, uri -> openedUri.set(uri) },
                )
            }
        }

        composeRule.onNodeWithText("暂时无法播放").assertIsDisplayed()
        composeRule.onNodeWithText("播放器地址不安全或格式不正确，已停止加载。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").assertIsNotEnabled()
        composeRule.onNodeWithText("用哔哩哔哩或浏览器打开")
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(originalUrl, openedUri.get()?.toString())
        }

        composeRule.onNodeWithText("返回").performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks.get()) }
    }
}
