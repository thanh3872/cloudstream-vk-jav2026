package com.thanh3872.vkprovider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class VKJav2026Provider : MainAPI() {
    // THÔNG TIN CƠ BẢN
    override var name = "VK Jav2026"
    override var mainUrl = "https://m.vkvideo.ru"
    override var appNameLocalized = "VK Jav2026"
    override var lang = "vi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.NSFW)
    override val hasQuickSearch = false

    // MAIN PAGE - Lấy video từ kênh @jav2026
    override val mainPage = mainPageOf(
        Pair("/@jav2026", "Video mới nhất"),
        Pair("/@jav2026?section=popular", "Xem nhiều nhất"),
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "${mainUrl}${request.data}${if (page > 1) "&page=$page" else ""}"
        println("DEBUG - Loading main page: $url")
        
        val document = app.get(url).document
        
        // SELECTOR: Bạn cần THAY THẾ sau khi inspect
        val items = document.select("div.VideoCard, div.video_card, a.video_link")
            .mapNotNull { element ->
                element.toSearchResponse()
            }

        return newHomePageResponse(request.name, items)
    }

    // SEARCH - VK không có search nội bộ
    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }

    // LOAD - Trang chi tiết video
    override suspend fun load(url: String): LoadResponse {
        println("DEBUG - Loading video: $url")
        val document = app.get(url).document

        // LẤY THÔNG TIN CƠ BẢN
        val title = document.selectFirst("h1.video_title, h1.VideoPageTitle")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Không có tiêu đề"

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("video[poster]")?.attr("poster")
        )

        val plot = document.selectFirst("meta[name=description]")?.attr("content")
            ?: document.selectFirst("div.VideoDescription")?.text()

        // LẤY LINK IFRAME
        var iframeSrc = document.selectFirst("iframe[src*=video_ext]")?.attr("src")
        
        if (iframeSrc.isNullOrEmpty()) {
            val scripts = document.select("script").joinToString("") { it.data() }
            val regex = Regex("""(https://vk\.com/video_ext\.php[^"']*)""")
            iframeSrc = regex.find(scripts)?.groupValues?.get(0)
        }

        val videoData = iframeSrc ?: throw ErrorLoadingException("Không tìm thấy iframe video")

        return newMovieLoadResponse(title, url, TvType.Movie, videoData) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = listOf("JAV", "Adult", "VK")
        }
    }

    // LOAD LINKS - Lấy link .m3u8 từ iframe
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        println("DEBUG - Loading links from: $data")
        
        val iframeUrl = if (data.startsWith("http")) data else fixUrl(data)
        val iframeDoc = app.get(iframeUrl).document

        // CÁCH 1: Tìm trực tiếp trong thẻ video
        var videoUrl = iframeDoc.selectFirst("video source")?.attr("src")
        
        // CÁCH 2: Tìm trong script có .m3u8
        if (videoUrl.isNullOrEmpty()) {
            val scripts = iframeDoc.select("script").joinToString("
") { it.data() }
            val regex = Regex("""(https?:[^"']*?\.m3u8[^"']*)""")
            videoUrl = regex.find(scripts)?.groupValues?.get(0)
        }

        // CÁCH 3: Tìm blob URL
        if (videoUrl.isNullOrEmpty()) {
            val scripts = iframeDoc.select("script").joinToString("
") { it.html() }
            val blobRegex = Regex("""blob:https?://[^"']+""")
            videoUrl = blobRegex.find(scripts)?.value
        }

        val finalLink = fixUrl(videoUrl ?: return false)
        println("DEBUG - Found video link: $finalLink")

        callback.invoke(
            ExtractorLink(
                source = name,
                name = "VK Video",
                url = finalLink,
                referer = iframeUrl,
                quality = Qualities.Unknown.value(),
                isM3u8 = finalLink.contains(".m3u8"),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to iframeUrl,
                    "Origin" to "https://vk.com"
                ),
                type = if (finalLink.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            )
        )
        
        return true
    }

    // HÀM CHUYỂN ĐỔI ELEMENT -> SEARCH RESPONSE
    private fun Element.toSearchResponse(): SearchResponse? {
        val linkElement = when {
            this.hasAttr("href") -> this
            else -> this.selectFirst("a[href]")
        } ?: return null

        val href = linkElement.attr("href")
        val link = if (href.startsWith("http")) href else fixUrl(href)
        
        val title = when {
            this.selectFirst(".VideoCard__title, .video_card_title") != null -> 
                this.selectFirst(".VideoCard__title, .video_card_title")?.text()
            this.hasAttr("aria-label") -> this.attr("aria-label")
            else -> this.selectFirst("img")?.attr("alt")
        } ?: return null

        val poster = fixUrlNull(
            this.selectFirst("img.VideoCard__image, img.video_card_thumb")?.attr("src")
                ?: this.selectFirst("img")?.attr("src")
        )

        return newMovieSearchResponse(title, link, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}