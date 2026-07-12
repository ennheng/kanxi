package com.kanxi.app.bilibili

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUrlFactoryTest {
    private val video = BilibiliVideoLink("BV1xx411c7mD", page = 6)

    @Test
    fun `builds fixed official player URL`() {
        assertEquals(
            "https://player.bilibili.com/player.html" +
                "?bvid=BV1xx411c7mD&p=6&autoplay=0&danmaku=0",
            PlaybackUrlFactory.playerUrl(video),
        )
    }

    @Test
    fun `builds fixed original page URL`() {
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD?p=6",
            PlaybackUrlFactory.originalUrl(video),
        )
    }
}
