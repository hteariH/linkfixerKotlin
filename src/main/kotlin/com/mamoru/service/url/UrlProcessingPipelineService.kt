package com.mamoru.service.url

import org.springframework.stereotype.Service

data class ProcessedUrl(
    val original: String,
    val converted: String,
    val type: String
)

data class ProcessedText(
    val originalText: String,
    val modifiedText: String,
    val processedUrls: List<ProcessedUrl>
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

    fun processTextAndReplace(text: String): ProcessedText {
        val processedUrls = processText(text)
        var modifiedText = text

        // Replace all URLs in the original text with their converted versions
        processedUrls.forEach { processed ->
            if (processed.original != processed.converted) {
                modifiedText = modifiedText.replace(processed.original, processed.converted)
            }
        }

        return ProcessedText(
            originalText = text,
            modifiedText = modifiedText,
            processedUrls = processedUrls
        )
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