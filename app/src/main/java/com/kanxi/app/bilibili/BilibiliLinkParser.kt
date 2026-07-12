package com.kanxi.app.bilibili

import java.io.IOException
import java.util.Locale
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extracts a supported link from share/paste text and resolves b23.tv redirects.
 *
 * This method is synchronous because OkHttp's injected [OkHttpClient] is synchronous. Call [parse]
 * from an IO dispatcher. The client is copied with automatic redirects disabled so every hop can
 * be checked before another request is made. No video page or metadata endpoint is fetched.
 */
class BilibiliLinkParser(
    okHttpClient: OkHttpClient,
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) {
    init {
        require(maxRedirects >= 0) { "maxRedirects cannot be negative" }
    }

    private val redirectSafeClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun parse(text: String): BilibiliLinkParseResult {
        val candidate = supportedLinkPattern.find(text)?.value
            ?: return BilibiliLinkParseResult.NoSupportedLink
        val normalized = if (candidate.contains("://")) candidate else "https://$candidate"
        val url = normalized.toHttpUrlOrNull()
            ?: return BilibiliLinkParseResult.InvalidUrl(candidate)

        validateAllowedUrl(url)?.let { return it }

        return when (url.host.lowercase(Locale.ROOT)) {
            SHORT_HOST -> resolveShortLink(url)
            in VIDEO_HOSTS -> parseVideoUrl(url)
            else -> BilibiliLinkParseResult.UntrustedHost(url.host)
        }
    }

    private fun resolveShortLink(initialUrl: HttpUrl): BilibiliLinkParseResult {
        var currentUrl = initialUrl
        var redirectCount = 0

        while (true) {
            validateAllowedUrl(currentUrl)?.let { return it }

            if (currentUrl.host.lowercase(Locale.ROOT) in VIDEO_HOSTS) {
                return parseVideoUrl(currentUrl)
            }
            if (redirectCount >= maxRedirects) {
                return BilibiliLinkParseResult.TooManyRedirects(maxRedirects)
            }

            val request = Request.Builder()
                .url(currentUrl)
                .get()
                .build()

            val response = try {
                redirectSafeClient.newCall(request).execute()
            } catch (error: IOException) {
                return BilibiliLinkParseResult.NetworkFailure(
                    url = currentUrl.toString(),
                    reason = error.message,
                )
            }

            response.use {
                if (it.code !in REDIRECT_STATUS_CODES) {
                    return BilibiliLinkParseResult.HttpFailure(
                        url = currentUrl.toString(),
                        statusCode = it.code,
                    )
                }

                val location = it.header("Location")
                    ?: return BilibiliLinkParseResult.RedirectWithoutLocation(
                        url = currentUrl.toString(),
                        statusCode = it.code,
                    )
                val nextUrl = currentUrl.resolve(location)
                    ?: return BilibiliLinkParseResult.InvalidUrl(location)

                validateAllowedUrl(nextUrl)?.let { failure -> return failure }
                redirectCount += 1
                currentUrl = nextUrl
            }
        }
    }

    private fun parseVideoUrl(url: HttpUrl): BilibiliLinkParseResult {
        val segments = url.pathSegments
        if (
            segments.size < 2 ||
            segments[0] != "video" ||
            !BilibiliVideoLink.isValidBvid(segments[1]) ||
            segments.drop(2).any { it.isNotEmpty() }
        ) {
            return BilibiliLinkParseResult.InvalidVideoPath(url.toString())
        }

        val pageValues = url.queryParameterValues(PAGE_QUERY_PARAMETER)
        if (pageValues.size > 1) {
            return BilibiliLinkParseResult.InvalidPage(pageValues.joinToString(","))
        }
        val page = if (pageValues.isEmpty()) {
            1
        } else {
            val rawPage = pageValues.single()
            val parsedPage = rawPage?.toIntOrNull()
                ?: return BilibiliLinkParseResult.InvalidPage(rawPage)
            if (parsedPage < 1) {
                return BilibiliLinkParseResult.InvalidPage(rawPage)
            }
            parsedPage
        }

        return BilibiliLinkParseResult.Success(
            BilibiliVideoLink(
                bvid = segments[1],
                page = page,
            ),
        )
    }

    private fun validateAllowedUrl(url: HttpUrl): BilibiliLinkParseResult.Failure? {
        if (url.scheme != HTTPS_SCHEME) {
            return BilibiliLinkParseResult.UnsupportedScheme(url.scheme)
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return BilibiliLinkParseResult.InvalidUrl(url.toString())
        }

        val host = url.host.lowercase(Locale.ROOT)
        if (host != SHORT_HOST && host !in VIDEO_HOSTS) {
            return BilibiliLinkParseResult.UntrustedHost(url.host)
        }
        if (url.port != HTTPS_PORT) {
            return BilibiliLinkParseResult.UntrustedPort(url.port)
        }
        return null
    }

    companion object {
        const val DEFAULT_MAX_REDIRECTS = 5

        private const val HTTPS_SCHEME = "https"
        private const val HTTPS_PORT = 443
        private const val SHORT_HOST = "b23.tv"
        private const val PAGE_QUERY_PARAMETER = "p"
        private val VIDEO_HOSTS = setOf("www.bilibili.com", "m.bilibili.com")
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

        // Common Chinese/Markdown closing punctuation is excluded so it is not treated as URL data.
        private const val URL_TAIL =
            "[^\\s<>\\\"'\\uFF0C\\u3002\\uFF01\\uFF1F\\uFF1B\\u3001\\uFF09\\u300B\\u3011}]*"
        private const val BVID_BOUNDARY =
            "(?![A-Za-z0-9._~%!\\u0024&'()*+,;=:@-])"
        private val supportedLinkPattern = Regex(
            "(?:(?:(?i:https?)://)?(?:(?i:www|m)\\.(?i:bilibili\\.com))(?::\\d+)?" +
                "/video/BV[A-Za-z0-9]{10}$BVID_BOUNDARY(?:[/?#]$URL_TAIL)?|" +
                "(?:(?i:https?)://)?(?i:b23\\.tv)(?::\\d+)?" +
                "/[A-Za-z0-9_-]+(?:[/?#]$URL_TAIL)?)",
        )
    }
}
