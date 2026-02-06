package com.mamoru.service.url

import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.regex.Pattern

@Component
class TikTokUrlHandler : UrlHandler {
    companion object {
        private val TIKTOK_URL_PATTERN = Pattern.compile("https?://(?:www\\.)?(?:tiktok\\.com|vm\\.tiktok\\.com|vt\\.tiktok\\.com)/(?:@[^/]+/video/|t/|v/)?([\\w-]+)")
    }

    override fun getType(): String = "tiktok"

    override fun findUrls(text: String): List<String> {
        val matcher = TIKTOK_URL_PATTERN.matcher(text)
        val urls = mutableListOf<String>()
        while (matcher.find()) {
            urls.add(matcher.group(0))
        }
        return urls
    }

    override fun convertUrl(url: String): String {
        return url.replace("vm.tiktok.com", "kktiktok.com")
            .replace("vt.tiktok.com", "kktiktok.com")
            .replace("https://tiktok.com", "https://kktiktok.com")
            .replace("www.tiktok.com", "kktiktok.com")
    }

    override fun canHandle(url: String): Boolean = 
        url.contains("tiktok.com") || url.contains("vm.tiktok.com") || url.contains("vt.tiktok.com")
}

@Component
class TwitterUrlHandler : UrlHandler {
    companion object {
        private val TWITTER_URL_PATTERN = Pattern.compile("https?://(?:www\\.)?(?:twitter\\.com|x\\.com)/[^/]+/status/\\d+(?:\\?\\S*)?")
    }

    override fun getType(): String = "twitter"

    override fun findUrls(text: String): List<String> {
        val matcher = TWITTER_URL_PATTERN.matcher(text)
        val urls = mutableListOf<String>()
        while (matcher.find()) {
            urls.add(matcher.group(0))
        }
        return urls
    }

    override fun convertUrl(url: String): String {
        return url.replace("twitter.com", "fxtwitter.com")
            .replace("x.com", "fxtwitter.com")
    }

    override fun canHandle(url: String): Boolean = 
        url.contains("twitter.com") || url.contains("x.com")
}

@Component
class InstagramUrlHandler : UrlHandler {
    companion object {
        private val INSTAGRAM_URL_PATTERN = Pattern.compile("https?://(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:p|reel|reels)/([\\w-]+)/?(?:\\?\\S*)?")
    }

    override fun getType(): String = "instagram"

    override fun findUrls(text: String): List<String> {
        val matcher = INSTAGRAM_URL_PATTERN.matcher(text)
        val urls = mutableListOf<String>()
        while (matcher.find()) {
            urls.add(matcher.group(0))
        }
        return urls
    }

    override fun convertUrl(url: String): String {
        return url.replace("instagram.com", "kkinstagram.com")
            .replace("instagr.am", "kkinstagram.com")
    }

    override fun canHandle(url: String): Boolean = 
        url.contains("instagram.com") || url.contains("instagr.am")
}
