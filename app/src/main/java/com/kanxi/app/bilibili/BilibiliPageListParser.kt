package com.kanxi.app.bilibili

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/**
 * Reads only `videoData.bvid` and `videoData.pages[].page/part` from Bilibili's public HTML.
 * All unrelated initial-state fields are skipped without being materialized.
 */
object BilibiliPageListParser {
    private const val INITIAL_STATE_MARKER = "window.__INITIAL_STATE__="

    fun parse(expectedBvid: String, html: String): BilibiliPageListResult {
        if (!BilibiliVideoLink.isValidBvid(expectedBvid)) {
            return BilibiliPageListResult.InvalidBvid
        }

        val json = try {
            extractUniqueBalancedJson(html)
        } catch (abort: ParseAbort) {
            return abort.failure
        }

        return try {
            readInitialState(expectedBvid, json)
        } catch (abort: ParseAbort) {
            abort.failure
        } catch (_: Exception) {
            BilibiliPageListResult.MalformedInitialState
        }
    }

    private fun extractUniqueBalancedJson(html: String): String {
        val markerIndex = html.indexOf(INITIAL_STATE_MARKER)
        if (markerIndex < 0) abort(BilibiliPageListResult.MissingInitialState)
        if (html.indexOf(INITIAL_STATE_MARKER, markerIndex + INITIAL_STATE_MARKER.length) >= 0) {
            abort(BilibiliPageListResult.AmbiguousInitialState)
        }

        var cursor = markerIndex + INITIAL_STATE_MARKER.length
        while (cursor < html.length && html[cursor].isWhitespace()) cursor += 1
        if (cursor >= html.length || html[cursor] != '{') {
            abort(BilibiliPageListResult.MalformedInitialState)
        }

        val jsonStart = cursor
        val closingStack = ArrayDeque<Char>()
        var insideString = false
        var escaped = false

        while (cursor < html.length) {
            val character = html[cursor]
            if (insideString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> insideString = false
                }
            } else {
                when (character) {
                    '"' -> insideString = true
                    '{' -> closingStack.addLast('}')
                    '[' -> closingStack.addLast(']')
                    '}', ']' -> {
                        if (closingStack.isEmpty() || closingStack.removeLast() != character) {
                            abort(BilibiliPageListResult.MalformedInitialState)
                        }
                        if (closingStack.isEmpty()) {
                            return html.substring(jsonStart, cursor + 1)
                        }
                    }
                }
            }
            cursor += 1
        }

        abort(BilibiliPageListResult.MalformedInitialState)
    }

    private fun readInitialState(expectedBvid: String, json: String): BilibiliPageListResult {
        JsonReader(StringReader(json)).use { reader ->
            reader.isLenient = false
            requireToken(reader, JsonToken.BEGIN_OBJECT)
            reader.beginObject()

            var videoDataSeen = false
            var parsedVideoData: ParsedVideoData? = null
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "videoData" -> {
                        if (videoDataSeen) abort(BilibiliPageListResult.MalformedInitialState)
                        videoDataSeen = true
                        parsedVideoData = readVideoData(reader)
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            requireToken(reader, JsonToken.END_DOCUMENT)

            if (!videoDataSeen) return BilibiliPageListResult.MissingVideoData
            val videoData = parsedVideoData
                ?: return BilibiliPageListResult.MalformedInitialState
            if (videoData.bvid != expectedBvid) return BilibiliPageListResult.BvidMismatch
            val pages = videoData.pages ?: return BilibiliPageListResult.MissingPages
            if (pages.isEmpty()) return BilibiliPageListResult.EmptyPages

            return BilibiliPageListResult.Success(
                BilibiliPageList(
                    bvid = expectedBvid,
                    pages = pages,
                ),
            )
        }
    }

    private fun readVideoData(reader: JsonReader): ParsedVideoData {
        requireToken(reader, JsonToken.BEGIN_OBJECT)
        reader.beginObject()

        var bvidSeen = false
        var bvid: String? = null
        var pagesSeen = false
        var pages: List<BilibiliPageEntry>? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "bvid" -> {
                    if (bvidSeen) abort(BilibiliPageListResult.MalformedInitialState)
                    bvidSeen = true
                    requireToken(reader, JsonToken.STRING)
                    bvid = reader.nextString()
                }
                "pages" -> {
                    if (pagesSeen) abort(BilibiliPageListResult.MalformedInitialState)
                    pagesSeen = true
                    pages = readPages(reader)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!bvidSeen || bvid == null) abort(BilibiliPageListResult.MalformedInitialState)
        return ParsedVideoData(bvid = bvid, pages = pages)
    }

    private fun readPages(reader: JsonReader): List<BilibiliPageEntry> {
        requireToken(reader, JsonToken.BEGIN_ARRAY)
        reader.beginArray()

        val pages = ArrayList<BilibiliPageEntry>()
        val pageNumbers = HashSet<Int>()
        while (reader.hasNext()) {
            if (pages.size >= MAX_PAGE_COUNT) {
                abort(BilibiliPageListResult.TooManyPages())
            }
            val entry = readPage(reader)
            if (!pageNumbers.add(entry.page)) {
                abort(BilibiliPageListResult.DuplicatePage(entry.page))
            }
            pages += entry
        }
        reader.endArray()
        return pages
    }

    private fun readPage(reader: JsonReader): BilibiliPageEntry {
        requireToken(reader, JsonToken.BEGIN_OBJECT)
        reader.beginObject()

        var pageSeen = false
        var page: Int? = null
        var partSeen = false
        var part: String? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "page" -> {
                    if (pageSeen) abort(BilibiliPageListResult.MalformedInitialState)
                    pageSeen = true
                    requireToken(reader, JsonToken.NUMBER)
                    val rawPage = reader.nextString()
                    if (!rawPage.matches(POSITIVE_INTEGER_PATTERN)) {
                        abort(BilibiliPageListResult.MalformedInitialState)
                    }
                    page = rawPage.toIntOrNull()
                    if (page == null || page <= 0) {
                        abort(BilibiliPageListResult.MalformedInitialState)
                    }
                }
                "part" -> {
                    if (partSeen) abort(BilibiliPageListResult.MalformedInitialState)
                    partSeen = true
                    requireToken(reader, JsonToken.STRING)
                    part = sanitizeTitle(reader.nextString())
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!pageSeen || page == null || !partSeen || part == null) {
            abort(BilibiliPageListResult.MalformedInitialState)
        }
        return BilibiliPageEntry(page = page, title = part)
    }

    private fun sanitizeTitle(value: String): String {
        val withoutControls = buildString(value.length) {
            var offset = 0
            while (offset < value.length) {
                val codePoint = value.codePointAt(offset)
                if (!Character.isISOControl(codePoint)) appendCodePoint(codePoint)
                offset += Character.charCount(codePoint)
            }
        }.trim()

        if (withoutControls.codePointCount(0, withoutControls.length) <= MAX_TITLE_CODE_POINTS) {
            return withoutControls
        }
        val end = withoutControls.offsetByCodePoints(0, MAX_TITLE_CODE_POINTS)
        return withoutControls.substring(0, end)
    }

    private fun requireToken(reader: JsonReader, token: JsonToken) {
        if (reader.peek() != token) abort(BilibiliPageListResult.MalformedInitialState)
    }

    private fun abort(failure: BilibiliPageListResult.Failure): Nothing {
        throw ParseAbort(failure)
    }

    private data class ParsedVideoData(
        val bvid: String,
        val pages: List<BilibiliPageEntry>?,
    )

    private class ParseAbort(
        val failure: BilibiliPageListResult.Failure,
    ) : RuntimeException()

    private val POSITIVE_INTEGER_PATTERN = Regex("[1-9][0-9]*")
}

