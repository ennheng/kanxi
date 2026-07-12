package com.kanxi.app.bilibili

/** Builds the only two Bilibili URLs that the playback UI should use. */
object PlaybackUrlFactory {
    fun playerUrl(video: BilibiliVideoLink): String =
        "https://player.bilibili.com/player.html" +
            "?bvid=${video.bvid}&p=${video.page}&autoplay=0&danmaku=0"

    fun originalUrl(video: BilibiliVideoLink): String =
        "https://www.bilibili.com/video/${video.bvid}?p=${video.page}"
}
