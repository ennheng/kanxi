package com.kanxi.app.bilibili

/** A validated reference to one public Bilibili video page. */
data class BilibiliVideoLink(
    val bvid: String,
    val page: Int = 1,
) {
    init {
        require(isValidBvid(bvid)) { "BVID must be BV followed by 10 ASCII letters or digits" }
        require(page >= 1) { "Page number must be at least 1" }
    }

    companion object {
        private val bvidPattern = Regex("^BV[A-Za-z0-9]{10}$")

        fun isValidBvid(value: String): Boolean = bvidPattern.matches(value)
    }
}

/**
 * Exhaustive result type intended to be mapped to localized UI messages by the caller.
 * No error text from this layer should be shown directly to an older user.
 */
sealed interface BilibiliLinkParseResult {
    data class Success(val video: BilibiliVideoLink) : BilibiliLinkParseResult

    sealed interface Failure : BilibiliLinkParseResult

    /** The supplied text did not contain a supported Bilibili or b23.tv link. */
    data object NoSupportedLink : Failure

    data class InvalidUrl(val value: String) : Failure

    /** Explicit HTTP links and any non-HTTPS redirect are rejected. */
    data class UnsupportedScheme(val scheme: String) : Failure

    /** A redirect attempted to leave the exact host allow-list. */
    data class UntrustedHost(val host: String) : Failure

    /** Only the standard HTTPS port is accepted, including on allow-listed hosts. */
    data class UntrustedPort(val port: Int) : Failure

    data class InvalidVideoPath(val url: String) : Failure

    data class InvalidPage(val value: String?) : Failure

    data class RedirectWithoutLocation(
        val url: String,
        val statusCode: Int,
    ) : Failure

    data class TooManyRedirects(val maximum: Int) : Failure

    data class HttpFailure(
        val url: String,
        val statusCode: Int,
    ) : Failure

    data class NetworkFailure(
        val url: String,
        val reason: String?,
    ) : Failure
}
