package com.kanxi.app.bilibili

import java.io.IOException
import java.nio.charset.StandardCharsets
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer

/**
 * Fetches exactly one canonical public video page, then delegates to [BilibiliPageListParser].
 *
 * This resolver is synchronous and must run off the main thread. It never follows redirects,
 * sends cookies, calls a media/API endpoint, or reads fields other than the page directory.
 */
class BilibiliPageListResolver(okHttpClient: OkHttpClient) {
    private val pageClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    fun resolve(bvid: String): BilibiliPageListResult {
        if (!BilibiliVideoLink.isValidBvid(bvid)) {
            return BilibiliPageListResult.InvalidBvid
        }

        // The official desktop page treats the trailing slash as canonical. Omitting it causes
        // a 301, which this security-sensitive client intentionally does not follow.
        val canonicalUrl = "https://www.bilibili.com/video/$bvid/".toHttpUrl()
        val request = Request.Builder()
            .url(canonicalUrl)
            .header("Accept", "text/html")
            // Bilibili returns 412 to OkHttp's library-default UA. Identify this app honestly
            // without impersonating a browser or opting into the mobile-page redirect.
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = try {
            pageClient.newCall(request).execute()
        } catch (_: IOException) {
            return BilibiliPageListResult.NetworkFailure
        }

        response.use {
            if (it.code != 200) {
                return BilibiliPageListResult.HttpFailure(it.code)
            }

            val body = it.body ?: return BilibiliPageListResult.UnexpectedContentType
            val contentType = body.contentType()
            if (
                contentType == null ||
                contentType.type != "text" ||
                contentType.subtype != "html"
            ) {
                return BilibiliPageListResult.UnexpectedContentType
            }

            if (body.contentLength() > MAX_HTML_BYTES) {
                return BilibiliPageListResult.BodyTooLarge
            }

            val bytes = try {
                val source = body.source()
                val buffer = Buffer()
                var remaining = MAX_HTML_BYTES + 1L
                while (remaining > 0L) {
                    val read = source.read(buffer, minOf(READ_CHUNK_BYTES, remaining))
                    if (read == -1L) break
                    if (read == 0L) break
                    remaining -= read
                }
                buffer.readByteArray()
            } catch (_: IOException) {
                return BilibiliPageListResult.NetworkFailure
            }
            if (bytes.size > MAX_HTML_BYTES) {
                return BilibiliPageListResult.BodyTooLarge
            }

            val charset = try {
                contentType.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            } catch (_: IllegalArgumentException) {
                return BilibiliPageListResult.UnexpectedContentType
            }
            return BilibiliPageListParser.parse(
                expectedBvid = bvid,
                html = bytes.toString(charset),
            )
        }
    }

    companion object {
        const val MAX_HTML_BYTES = 2 * 1024 * 1024
        private const val READ_CHUNK_BYTES = 8 * 1024L
        private const val USER_AGENT = "Kanxi/0.1 (family catalog; public-page part list)"
    }
}
