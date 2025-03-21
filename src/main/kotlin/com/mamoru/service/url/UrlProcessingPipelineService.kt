package com.mamoru.service.url

import org.springframework.stereotype.Service

data class ProcessedUrl(
    val original: String,
    val converted: String,
    val type: String
)

@Service
class UrlProcessingPipeline(private val urlHandlers: List<UrlHandler>) {
    
    fun processText(text: String): List<ProcessedUrl> {
        return urlHandlers.flatMap { handler ->
            handler.findUrls(text).map { url ->
                ProcessedUrl(
                    original = url,
                    converted = handler.convertUrl(url),
                    type = handler.getType()
                )
            }
        }
    }
    
    fun processUrl(url: String): ProcessedUrl? {
        val handler = urlHandlers.find { it.canHandle(url) } ?: return null
        return ProcessedUrl(
            original = url,
            converted = handler.convertUrl(url),
            type = handler.getType()
        )
    }
}