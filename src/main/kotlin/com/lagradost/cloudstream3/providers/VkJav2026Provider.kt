package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink

class VkJav2026Provider : MainAPI() {
    override var mainUrl = "https://m.vkvideo.ru/@jav2026"
    override var name = "VK Jav2026"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "vi"

    private val videos = []

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = videos.map { vid ->
            newMovieSearchResponse(vid["title"] as String, vid["url"] as String) {
                posterUrl = vid["poster"] as? String
            }
        }
        return newHomePageResponse("Home", items)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val vid = videos.find { it["url"] == data } ?: return false
        (vid["sources"] as List<String>).forEach { src ->
            if (src.isNotBlank()) {
                callback(ExtractorLink(name, "VK", src, "", Qualities.Unknown.value))
            }
        }
        return true
    }

    override suspend fun search(query: String) = emptyList<SearchResponse>()
    override suspend fun load(url: String) = newMovieLoadResponse("", url, TvType.NSFW, url) {}
}