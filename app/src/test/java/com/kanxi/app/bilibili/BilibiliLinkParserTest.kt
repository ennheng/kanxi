package com.kanxi.app.bilibili

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliLinkParserTest {
    private val parser = BilibiliLinkParser(OkHttpClient())

    @Test
    fun `extracts desktop video link from share text and defaults to first page`() {
        val result = parser.parse(
            "【好戏分享】 https://www.bilibili.com/video/BV1xx411c7mD 快来看！",
        )

        assertEquals(
            BilibiliLinkParseResult.Success(BilibiliVideoLink("BV1xx411c7mD", 1)),
            result,
        )
    }

    @Test
    fun `parses the supplied 57 part video link before its page directory is resolved`() {
        val result = parser.parse(
            "https://www.bilibili.com/video/BV1reVJ6ZETf/" +
                "?spm_id_from=333.1007.0.0&vd_source=2d2ae562f21c93582c6dcca8ee695de3",
        )

        assertEquals(
            BilibiliLinkParseResult.Success(BilibiliVideoLink("BV1reVJ6ZETf", 1)),
            result,
        )
    }

    @Test
    fun `extracts page number from mobile link`() {
        val result = parser.parse(
            "https://m.bilibili.com/video/BV1xx411c7mD?p=3&share_source=copy_web",
        )

        assertEquals(
            BilibiliLinkParseResult.Success(BilibiliVideoLink("BV1xx411c7mD", 3)),
            result,
        )
    }

    @Test
    fun `accepts supported link without a scheme`() {
        val result = parser.parse("www.bilibili.com/video/BV1xx411c7mD?p=2")

        assertEquals(
            BilibiliLinkParseResult.Success(BilibiliVideoLink("BV1xx411c7mD", 2)),
            result,
        )
    }

    @Test
    fun `does not include closing Chinese punctuation in URL`() {
        val result = parser.parse("请看：https://www.bilibili.com/video/BV1xx411c7mD?p=4。")

        assertEquals(
            BilibiliLinkParseResult.Success(BilibiliVideoLink("BV1xx411c7mD", 4)),
            result,
        )
    }

    @Test
    fun `rejects zero page`() {
        val result = parser.parse("https://www.bilibili.com/video/BV1xx411c7mD?p=0")

        assertEquals(BilibiliLinkParseResult.InvalidPage("0"), result)
    }

    @Test
    fun `rejects nonnumeric page`() {
        val result = parser.parse("https://www.bilibili.com/video/BV1xx411c7mD?p=first")

        assertEquals(BilibiliLinkParseResult.InvalidPage("first"), result)
    }

    @Test
    fun `rejects page parameter without a value`() {
        val result = parser.parse("https://www.bilibili.com/video/BV1xx411c7mD?p")

        assertEquals(BilibiliLinkParseResult.InvalidPage(null), result)
    }

    @Test
    fun `rejects duplicate page parameters as ambiguous`() {
        val result = parser.parse(
            "https://www.bilibili.com/video/BV1xx411c7mD?p=1&p=2",
        )

        assertEquals(BilibiliLinkParseResult.InvalidPage("1,2"), result)
    }

    @Test
    fun `rejects explicit insecure scheme`() {
        val result = parser.parse("http://www.bilibili.com/video/BV1xx411c7mD")

        assertEquals(BilibiliLinkParseResult.UnsupportedScheme("http"), result)
    }

    @Test
    fun `does not accept malformed or partial BVID`() {
        val result = parser.parse("https://www.bilibili.com/video/BV1xx411c7mD9")

        assertEquals(BilibiliLinkParseResult.NoSupportedLink, result)
    }

    @Test
    fun `does not truncate an invalid path suffix after a BVID-shaped prefix`() {
        val result = parser.parse("https://www.bilibili.com/video/BV1xx411c7mD_fake")

        assertEquals(BilibiliLinkParseResult.NoSupportedLink, result)
    }

    @Test
    fun `does not accept lookalike host`() {
        val result = parser.parse(
            "https://www.bilibili.com.evil.example/video/BV1xx411c7mD",
        )

        assertEquals(BilibiliLinkParseResult.NoSupportedLink, result)
    }

    @Test
    fun `rejects a nonstandard port on an allow-listed host`() {
        val result = parser.parse(
            "https://www.bilibili.com:8443/video/BV1xx411c7mD",
        )

        assertEquals(BilibiliLinkParseResult.UntrustedPort(8443), result)
    }

    @Test
    fun `resolves a short link without following redirects automatically`() {
        val parser = redirectingParser(
            location = "https://www.bilibili.com/video/BV1xx411c7mD?p=5",
        )

        val result = parser.parse("B站短链：https://b23.tv/abc123")

        assertEquals(
            BilibiliLinkParseResult.Success(BilibiliVideoLink("BV1xx411c7mD", 5)),
            result,
        )
    }

    @Test
    fun `rejects a short-link redirect outside the allow-list`() {
        val parser = redirectingParser(
            location = "https://evil.example/video/BV1xx411c7mD",
        )

        val result = parser.parse("https://b23.tv/abc123")

        assertEquals(BilibiliLinkParseResult.UntrustedHost("evil.example"), result)
    }

    @Test
    fun `stops a short-link chain at the configured redirect limit`() {
        val parser = redirectingParser(location = "/another-short-code", maxRedirects = 1)

        val result = parser.parse("https://b23.tv/abc123")

        assertEquals(BilibiliLinkParseResult.TooManyRedirects(1), result)
    }

    @Test
    fun `video reference enforces invariants`() {
        val invalidBvid = runCatching { BilibiliVideoLink("BV-short") }
        val invalidPage = runCatching { BilibiliVideoLink("BV1xx411c7mD", 0) }

        assertTrue(invalidBvid.isFailure)
        assertTrue(invalidPage.isFailure)
    }

    private fun redirectingParser(
        location: String,
        maxRedirects: Int = BilibiliLinkParser.DEFAULT_MAX_REDIRECTS,
    ): BilibiliLinkParser {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", location)
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        return BilibiliLinkParser(client, maxRedirects)
    }
}
