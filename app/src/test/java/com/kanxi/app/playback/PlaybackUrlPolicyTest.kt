package com.kanxi.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackUrlPolicyTest {
    private val desktop =
        "https://player.bilibili.com/player.html" +
            "?bvid=BV1xx411c7mD&p=3&autoplay=0&danmaku=0"
    private val mobile =
        "https://www.bilibili.com/blackboard/webplayer/mbplayer.html" +
            "?bvid=BV1xx411c7mD&p=3&autoplay=0&danmaku=0"

    @Test
    fun `allows official mobile redirect for the same validated video`() {
        assertTrue(PlaybackUrlPolicy.isAllowedPlayerUrl(desktop))
        assertTrue(PlaybackUrlPolicy.isAllowedPlayerUrl(mobile))
        assertFalse(PlaybackUrlPolicy.isMobilePlayerUrl(desktop))
        assertTrue(PlaybackUrlPolicy.isMobilePlayerUrl(mobile))
        assertTrue(PlaybackUrlPolicy.samePlayerDocument(desktop, mobile))
        assertTrue(PlaybackUrlPolicy.samePlayerDocument(desktop, "$mobile#player"))
    }

    @Test
    fun `rejects another video or part after an official mobile redirect`() {
        assertFalse(
            PlaybackUrlPolicy.samePlayerDocument(
                desktop,
                mobile.replace("BV1xx411c7mD", "BV1GJ411x7h7"),
            ),
        )
        assertFalse(PlaybackUrlPolicy.samePlayerDocument(desktop, mobile.replace("p=3", "p=4")))
    }

    @Test
    fun `rejects lookalike hosts paths ports and insecure mobile player links`() {
        assertFalse(
            PlaybackUrlPolicy.isAllowedPlayerUrl(
                mobile.replace("www.bilibili.com", "www.bilibili.com.evil.example"),
            ),
        )
        assertFalse(
            PlaybackUrlPolicy.isAllowedPlayerUrl(
                mobile.replace("/mbplayer.html", "/mbplayer.html/extra"),
            ),
        )
        assertFalse(
            PlaybackUrlPolicy.isAllowedPlayerUrl(
                mobile.replace("www.bilibili.com", "www.bilibili.com:8443"),
            ),
        )
        assertFalse(
            PlaybackUrlPolicy.isAllowedPlayerUrl(
                mobile.replace("https://", "https://name:secret@"),
            ),
        )
        assertFalse(PlaybackUrlPolicy.isAllowedPlayerUrl(mobile.replace("https://", "http://")))
    }

    @Test
    fun `requires exactly one fixed value for every player query parameter`() {
        assertFalse(PlaybackUrlPolicy.isAllowedPlayerUrl("$mobile&bvid=BV1GJ411x7h7"))
        assertFalse(PlaybackUrlPolicy.isAllowedPlayerUrl("$mobile&autoplay=1"))
        assertFalse(PlaybackUrlPolicy.isAllowedPlayerUrl("$mobile&extra=1"))
        assertFalse(PlaybackUrlPolicy.isAllowedPlayerUrl(mobile.replace("danmaku=0", "danmaku=1")))
    }

    @Test
    fun `original fallback is also constrained to one canonical part parameter`() {
        val original = "https://www.bilibili.com/video/BV1xx411c7mD?p=3"
        assertTrue(PlaybackUrlPolicy.isAllowedOriginalUrl(original))
        assertFalse(PlaybackUrlPolicy.isAllowedOriginalUrl("$original&p=4"))
        assertFalse(PlaybackUrlPolicy.isAllowedOriginalUrl("$original&extra=1"))
    }
}
