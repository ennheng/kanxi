package com.kanxi.app.bilibili

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BilibiliPageListResolverTest {
    @Test
    fun `short html body succeeds and request is exact canonical cookie-free GET`() {
        val capturedRequest = AtomicReference<Request>()
        val client = clientWithInterceptor { chain ->
            capturedRequest.set(chain.request())
            response(
                request = chain.request(),
                body = validHtml().toResponseBody(HTML_MEDIA_TYPE),
            )
        }

        val result = BilibiliPageListResolver(client).resolve(BVID)

        assertTrue(result is BilibiliPageListResult.Success)
        with(capturedRequest.get()) {
            assertEquals("GET", method)
            assertEquals("https://www.bilibili.com/video/$BVID/", url.toString())
            assertEquals("text/html", header("Accept"))
            assertEquals(
                "Kanxi/0.1 (family catalog; public-page part list)",
                header("User-Agent"),
            )
            assertFalse(headers.names().contains("Cookie"))
        }
    }

    @Test
    fun `does not follow even an allow-listed redirect`() {
        val callCount = AtomicInteger()
        val client = clientWithInterceptor { chain ->
            callCount.incrementAndGet()
            response(
                request = chain.request(),
                statusCode = 302,
                extraHeaders = mapOf(
                    "Location" to "https://www.bilibili.com/video/$BVID?p=2",
                ),
            )
        }

        val result = BilibiliPageListResolver(client).resolve(BVID)

        assertEquals(BilibiliPageListResult.HttpFailure(302), result)
        assertEquals(1, callCount.get())
    }

    @Test
    fun `caps streamed html with unknown content length at two mebibytes`() {
        val oversizedBody = object : ResponseBody() {
            override fun contentType() = HTML_MEDIA_TYPE

            override fun contentLength(): Long = -1L

            override fun source(): BufferedSource = Buffer().write(
                ByteArray(BilibiliPageListResolver.MAX_HTML_BYTES + 1) { 'x'.code.toByte() },
            )
        }
        val client = clientWithInterceptor { chain ->
            response(request = chain.request(), body = oversizedBody)
        }

        val result = BilibiliPageListResolver(client).resolve(BVID)

        assertEquals(BilibiliPageListResult.BodyTooLarge, result)
    }

    @Test
    fun `rejects non-html content without parsing it`() {
        val client = clientWithInterceptor { chain ->
            response(
                request = chain.request(),
                body = validHtml().toResponseBody("application/json".toMediaType()),
            )
        }

        assertEquals(
            BilibiliPageListResult.UnexpectedContentType,
            BilibiliPageListResolver(client).resolve(BVID),
        )
    }

    @Test
    fun `maps transport exception to safe network failure`() {
        val client = clientWithInterceptor { throw IOException("sensitive transport detail") }

        assertEquals(
            BilibiliPageListResult.NetworkFailure,
            BilibiliPageListResolver(client).resolve(BVID),
        )
    }

    @Test
    fun `rejects invalid bvid before making a request`() {
        val callCount = AtomicInteger()
        val client = clientWithInterceptor { chain ->
            callCount.incrementAndGet()
            response(request = chain.request(), body = validHtml().toResponseBody(HTML_MEDIA_TYPE))
        }

        assertEquals(
            BilibiliPageListResult.InvalidBvid,
            BilibiliPageListResolver(client).resolve("../../etc/passwd"),
        )
        assertEquals(0, callCount.get())
    }

    private fun validHtml(): String =
        """<script>window.__INITIAL_STATE__={"videoData":{"bvid":"$BVID","pages":[{"page":1,"part":"第一集"}]}};</script>"""

    private fun clientWithInterceptor(block: (Interceptor.Chain) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(block)
            .build()

    private fun response(
        request: Request,
        statusCode: Int = 200,
        body: ResponseBody = "".toResponseBody(HTML_MEDIA_TYPE),
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message(if (statusCode == 200) "OK" else "Redirect")
            .body(body)
        extraHeaders.forEach(builder::header)
        return builder.build()
    }

    companion object {
        private const val BVID = "BV1reVJ6ZETf"
        private val HTML_MEDIA_TYPE = "text/html; charset=utf-8".toMediaType()
    }
}
