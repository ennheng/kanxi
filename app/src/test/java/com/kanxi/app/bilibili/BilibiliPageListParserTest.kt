package com.kanxi.app.bilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BilibiliPageListParserTest {
    @Test
    fun `reads all 57 pages from the reduced real public-page fixture`() {
        val html = checkNotNull(
            javaClass.getResource("/bilibili/bv1re_vj6zetf_initial_state.html"),
        ).readText()

        val result = BilibiliPageListParser.parse(EXPECTED_BVID, html)

        assertTrue(result is BilibiliPageListResult.Success)
        val pageList = (result as BilibiliPageListResult.Success).pageList
        assertEquals(EXPECTED_BVID, pageList.bvid)
        assertEquals(57, pageList.pages.size)
        assertEquals(BilibiliPageEntry(1, "《打焦赞》（雷格格）"), pageList.pages.first())
        assertEquals(
            BilibiliPageEntry(57, "《宇宙锋》陕西省戏曲研究院秦腔团演出"),
            pageList.pages.last(),
        )
    }

    @Test
    fun `balanced extraction ignores brackets and escaped quotes inside titles`() {
        val result = BilibiliPageListParser.parse(
            EXPECTED_BVID,
            html(
                """{"videoData":{"bvid":"$EXPECTED_BVID","pages":[{"page":1,"part":"标题 } ] 和引号 \"好戏\""}]}}""",
            ),
        )

        assertEquals(
            BilibiliPageListResult.Success(
                BilibiliPageList(
                    EXPECTED_BVID,
                    listOf(BilibiliPageEntry(1, "标题 } ] 和引号 \"好戏\"")),
                ),
            ),
            result,
        )
    }

    @Test
    fun `removes control characters trims and limits title to 160 code points`() {
        val longTitle = "戏".repeat(170)
        val result = BilibiliPageListParser.parse(
            EXPECTED_BVID,
            html(
                """{"videoData":{"bvid":"$EXPECTED_BVID","pages":[{"page":1,"part":"\u0000  $longTitle  \u007f"}]}}""",
            ),
        ) as BilibiliPageListResult.Success

        assertEquals("戏".repeat(160), result.pageList.pages.single().title)
        assertEquals(160, result.pageList.pages.single().title.codePointCount(0, 160))
    }

    @Test
    fun `rejects state whose video bvid does not strictly match requested bvid`() {
        val result = BilibiliPageListParser.parse(
            EXPECTED_BVID,
            html(
                """{"videoData":{"bvid":"BV1xx411c7mD","pages":[{"page":1,"part":"第一集"}]}}""",
            ),
        )

        assertEquals(BilibiliPageListResult.BvidMismatch, result)
    }

    @Test
    fun `rejects duplicate positive page numbers`() {
        val result = BilibiliPageListParser.parse(
            EXPECTED_BVID,
            html(
                """{"videoData":{"bvid":"$EXPECTED_BVID","pages":[{"page":2,"part":"甲"},{"page":2,"part":"乙"}]}}""",
            ),
        )

        assertEquals(BilibiliPageListResult.DuplicatePage(2), result)
    }

    @Test
    fun `rejects more than 500 pages without materializing the remainder`() {
        val pages = (1..501).joinToString(",") { page ->
            """{"page":$page,"part":"第${page}集"}"""
        }
        val result = BilibiliPageListParser.parse(
            EXPECTED_BVID,
            html("""{"videoData":{"bvid":"$EXPECTED_BVID","pages":[$pages]}}"""),
        )

        assertEquals(BilibiliPageListResult.TooManyPages(500), result)
    }

    @Test
    fun `rejects missing and duplicated initial-state markers`() {
        assertEquals(
            BilibiliPageListResult.MissingInitialState,
            BilibiliPageListParser.parse(EXPECTED_BVID, "<html>no state</html>"),
        )

        val validState = html(
            """{"videoData":{"bvid":"$EXPECTED_BVID","pages":[{"page":1,"part":"第一集"}]}}""",
        )
        assertEquals(
            BilibiliPageListResult.AmbiguousInitialState,
            BilibiliPageListParser.parse(
                EXPECTED_BVID,
                "$validState<script>window.__INITIAL_STATE__={}</script>",
            ),
        )
    }

    @Test
    fun `rejects malformed nesting and duplicate sensitive fields`() {
        assertEquals(
            BilibiliPageListResult.MalformedInitialState,
            BilibiliPageListParser.parse(
                EXPECTED_BVID,
                "<script>window.__INITIAL_STATE__={\"videoData\":[]]</script>",
            ),
        )

        assertEquals(
            BilibiliPageListResult.MalformedInitialState,
            BilibiliPageListParser.parse(
                EXPECTED_BVID,
                html(
                    """{"videoData":{"bvid":"$EXPECTED_BVID","bvid":"$EXPECTED_BVID","pages":[]}}""",
                ),
            ),
        )
    }

    @Test
    fun `rejects nonpositive quoted or duplicated page fields`() {
        listOf(
            """{"page":0,"part":"零"}""",
            """{"page":"1","part":"字符串"}""",
            """{"page":1,"page":2,"part":"重复"}""",
        ).forEach { pageJson ->
            assertEquals(
                BilibiliPageListResult.MalformedInitialState,
                BilibiliPageListParser.parse(
                    EXPECTED_BVID,
                    html(
                        """{"videoData":{"bvid":"$EXPECTED_BVID","pages":[$pageJson]}}""",
                    ),
                ),
            )
        }
    }

    private fun html(json: String): String =
        "<html><script>window.__INITIAL_STATE__=$json;</script></html>"

    companion object {
        private const val EXPECTED_BVID = "BV1reVJ6ZETf"
    }
}
