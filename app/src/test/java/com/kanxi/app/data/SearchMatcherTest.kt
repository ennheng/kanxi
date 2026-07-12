package com.kanxi.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    private val opera = OperaWithCategory(
        item = OperaItem(
            id = 1,
            title = "女驸马",
            categoryId = 4,
            actors = "严凤英 王少舫",
            troupe = "安徽省黄梅戏剧院",
            aliases = "谁料皇榜中状元, 女附马",
            notes = "备注不参与搜索",
            bvid = "BV1xx411c7mD",
        ),
        categoryName = "黄梅戏",
        isFavorite = false,
    )

    @Test
    fun search_coversEverySupportedField() {
        assertTrue(SearchMatcher.matches(opera, "女驸马"))
        assertTrue(SearchMatcher.matches(opera, "黄梅戏"))
        assertTrue(SearchMatcher.matches(opera, "严凤英"))
        assertTrue(SearchMatcher.matches(opera, "安徽省"))
        assertTrue(SearchMatcher.matches(opera, "皇榜"))
    }

    @Test
    fun search_isTrimmedCaseInsensitiveAndDoesNotSearchNotes() {
        assertTrue(SearchMatcher.matches(opera, "  女附马  "))
        assertTrue(SearchMatcher.matches(opera, ""))
        assertFalse(SearchMatcher.matches(opera, "备注不参与搜索"))
        assertFalse(SearchMatcher.matches(opera, "京剧"))
    }
}
