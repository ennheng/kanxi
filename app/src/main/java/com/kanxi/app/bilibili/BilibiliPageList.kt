package com.kanxi.app.bilibili

/** One numbered part from the public page's embedded `videoData.pages` directory. */
data class BilibiliPageEntry(
    val page: Int,
    val title: String,
) {
    init {
        require(page > 0) { "Page number must be positive" }
        require(title == title.trim()) { "Page title must be trimmed" }
        require(title.codePointCount(0, title.length) <= MAX_TITLE_CODE_POINTS) {
            "Page title is too long"
        }
        require(title.codePoints().noneMatch(Character::isISOControl)) {
            "Page title must not contain control characters"
        }
    }
}

/** The small, explicitly allow-listed subset read from a Bilibili video's initial state. */
data class BilibiliPageList(
    val bvid: String,
    val pages: List<BilibiliPageEntry>,
) {
    init {
        require(BilibiliVideoLink.isValidBvid(bvid)) { "BVID is invalid" }
        require(pages.size <= MAX_PAGE_COUNT) { "Too many pages" }
        require(pages.map(BilibiliPageEntry::page).distinct().size == pages.size) {
            "Page numbers must be unique"
        }
    }
}

/**
 * Safe result values for both HTML parsing and the narrowly scoped network resolver.
 *
 * Failures intentionally do not contain response bodies, URLs, exception messages, or untrusted
 * page text. The UI can map every value to a short local message and keep manual P entry enabled.
 */
sealed interface BilibiliPageListResult {
    data class Success(val pageList: BilibiliPageList) : BilibiliPageListResult

    sealed interface Failure : BilibiliPageListResult

    data object InvalidBvid : Failure
    data object MissingInitialState : Failure
    data object AmbiguousInitialState : Failure
    data object MalformedInitialState : Failure
    data object MissingVideoData : Failure
    data object BvidMismatch : Failure
    data object MissingPages : Failure
    data object EmptyPages : Failure
    data class DuplicatePage(val page: Int) : Failure
    data class TooManyPages(val maximum: Int = MAX_PAGE_COUNT) : Failure
    data class HttpFailure(val statusCode: Int) : Failure
    data object UnexpectedContentType : Failure
    data object BodyTooLarge : Failure
    data object NetworkFailure : Failure
}

const val MAX_PAGE_COUNT = 500
const val MAX_TITLE_CODE_POINTS = 160

