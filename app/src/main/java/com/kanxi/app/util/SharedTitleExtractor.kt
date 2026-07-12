package com.kanxi.app.util

object SharedTitleExtractor {
    fun extract(text: String): String {
        val bracketedTitle = Regex("【([^】]{1,160})】").find(text)?.groupValues?.getOrNull(1)
        val withoutUrls = (bracketedTitle ?: text)
            .replace(Regex("(?i)(?:https?://)?(?:www\\.|m\\.)?bilibili\\.com/\\S+"), " ")
            .replace(Regex("(?i)(?:https?://)?b23\\.tv/\\S+"), " ")
            .replace(Regex("(?i)[-_ ]*哔哩哔哩(?:_bilibili)?[ ]*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '：', ':', '-', '—', '，', ',', '。')
        return withoutUrls.take(160)
    }
}

