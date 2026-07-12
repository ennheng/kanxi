package com.kanxi.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTitleExtractorTest {
    @Test
    fun `extracts bracketed title from common share text`() {
        val title = SharedTitleExtractor.extract(
            "【越剧《梁山伯与祝英台》-哔哩哔哩】 https://b23.tv/AbCd123",
        )

        assertEquals("越剧《梁山伯与祝英台》", title)
    }

    @Test
    fun `removes canonical link when no brackets are present`() {
        val title = SharedTitleExtractor.extract(
            "京剧锁麟囊 https://www.bilibili.com/video/BV1xx411c7mD",
        )

        assertEquals("京剧锁麟囊", title)
    }
}

