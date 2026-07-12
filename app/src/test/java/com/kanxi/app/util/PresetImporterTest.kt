package com.kanxi.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetImporterTest {
    @Test
    fun `parses preset packs from json string`() {
        val packs = PresetImporter.parsePacks(
            """
            {
                "qinqiang": {
                    "name": "秦腔精选",
                    "categoryName": "秦腔",
                    "items": [
                        {"bvid": "BV1reVJ6ZETf", "part": 1, "title": "第一出"},
                        {"bvid": "BV1reVJ6ZETf", "part": 2, "title": "第二出"}
                    ]
                }
            }
            """.trimIndent(),
        )

        val qinqiang = requireNotNull(packs["qinqiang"])
        assertEquals("秦腔精选", qinqiang.name)
        assertEquals("秦腔", qinqiang.categoryName)
        assertEquals(2, qinqiang.items.size)
        assertEquals(PresetItem(bvid = "BV1reVJ6ZETf", part = 1, title = "第一出"), qinqiang.items[0])
        assertEquals(PresetItem(bvid = "BV1reVJ6ZETf", part = 2, title = "第二出"), qinqiang.items[1])
    }

    @Test
    fun `lookup is case insensitive`() {
        val packs = PresetImporter.parsePacks(
            """
            {
                "JingJu": {
                    "name": "京剧",
                    "categoryName": "京剧",
                    "items": []
                }
            }
            """.trimIndent(),
        )

        assertEquals("京剧", requireNotNull(packs["jingju"]).name)
    }
}
