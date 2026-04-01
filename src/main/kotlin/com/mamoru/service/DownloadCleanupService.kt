package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service
import java.io.File

@Service
class DownloadCleanupService : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(DownloadCleanupService::class.java)

    @Value("\${tiktok.download.path:./videos}")
    private lateinit var tiktokDownloadPath: String

    @Value("\${instagram.download.path:./videos}")
    private lateinit var instagramDownloadPath: String

    override fun run(vararg args: String?) {
        try {
            val pathsToClean = setOf(tiktokDownloadPath, instagramDownloadPath)
            
            pathsToClean.forEach { path ->
                cleanPath(path)
            }
        } catch (e: Exception) {
            logger.error("Error during download cleanup on startup", e)
        }
    }

    private fun cleanPath(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            logger.info("Cleaning download directory on startup: $path")
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (file.delete()) {
                        logger.debug("Deleted file: ${file.name}")
                    } else {
                        logger.warn("Could not delete file: ${file.name}")
                    }
                }
            }
        } else {
            logger.debug("Path $path does not exist or is not a directory, skipping cleanup")
        }
    }
}
