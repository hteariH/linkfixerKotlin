package com.mamoru.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.io.File
import java.net.URLConnection

@Controller
class VideoController {
    private val logger = LoggerFactory.getLogger(VideoController::class.java)

    @Value("\${tiktok.download.path:/data/downloads}")
    private lateinit var downloadPath: String

    @GetMapping("/instagram")
    fun instagramPreview(@RequestParam("video") filename: String, request: jakarta.servlet.http.HttpServletRequest, model: Model): String {
        logger.info("Generating preview for Instagram video file: $filename")
        
        val requestUrl = request.requestURL.toString()
        val queryString = request.queryString
        val fullPageUrl = if (queryString != null) "$requestUrl?$queryString" else requestUrl

        model.addAttribute("videoUrl", "/videos/$filename")
        model.addAttribute("fullVideoUrl", "https://link-fixer-bot.fly.dev/videos/$filename")
        model.addAttribute("pageUrl", fullPageUrl)
        
        // Find thumbnail if it exists
        val videoBaseName = filename.substringBeforeLast(".")
        val thumbnailFilename = "$videoBaseName.jpg"
        val thumbnailFile = File(downloadPath, thumbnailFilename)
        if (thumbnailFile.exists()) {
            model.addAttribute("imageUrl", "https://link-fixer-bot.fly.dev/videos/$thumbnailFilename")
        } else {
            // Provide a fallback or omit
            model.addAttribute("imageUrl", "https://link-fixer-bot.fly.dev/rikroll.gif")
        }
        
        return "instagram_preview"
    }

    @GetMapping("/videos/{filename}")
    fun serveVideo(@PathVariable filename: String): ResponseEntity<Resource> {
        val file = File(downloadPath, filename)
        if (!file.exists()) {
            logger.warn("File not found: ${file.absolutePath}")
            return ResponseEntity.notFound().build()
        }

        val resource = FileSystemResource(file)
        val contentType = when (filename.substringAfterLast(".").lowercase()) {
            "mp4" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            else -> URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream"
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.name}\"")
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .body(resource)
    }
}
