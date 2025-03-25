package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class TikTokDownloaderService {
    private val logger = LoggerFactory.getLogger(TikTokDownloaderService::class.java)

    @Value("\${tiktok.download.path:./videos}")
    private lateinit var downloadPath: String

    @Value("\${ytdlp.path:yt-dlp}")
    private lateinit var ytdlpPath: String

    init {
        try {
            // Create download directory if it doesn't exist
            val dir = File(downloadPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Check yt-dlp installation on startup
            if (!checkYtDlpInstallation()) {
                logger.error("yt-dlp is not correctly installed or configured!")
            } else {
                logger.info("yt-dlp is properly configured")
            }
        } catch (e: Exception) {
            logger.error("Error initializing TikTokDownloaderService", e)
        }
    }

    /**
     * Extract TikTok video ID from URL
     */
    fun extractTikTokId(url: String): String? {
        // Pattern for TikTok URLs - adapt as needed
        val patterns = listOf(
            "tiktok\\.com/\\@[\\w.-]+/video/([\\d]+)".toRegex(), // Standard format
            "tiktok\\.com/v/([\\d]+)".toRegex(),                // Short format
            "vm\\.tiktok\\.com/([\\w]+)".toRegex(),             // vm.tiktok.com format
            "vt\\.tiktok\\.com/([\\w]+)".toRegex()             // vm.tiktok.com format
        )

        for (pattern in patterns) {
            val matchResult = pattern.find(url)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        logger.warn("Could not extract TikTok ID from URL: $url")
        return null
    }

    /**
     * Download TikTok video using yt-dlp
     * Returns file path if successful, null otherwise
     */
    fun downloadVideo(tikTokUrl: String): File? {
        logger.info("Attempting to download TikTok video: $tikTokUrl")

        try {
            val videoId = extractTikTokId(tikTokUrl)
            if (videoId == null) {
                logger.error("Failed to extract video ID from URL: $tikTokUrl")
                return null
            }

            val outputFilename = "tiktok_$videoId.%(ext)s"
            val outputPath = "$downloadPath/$outputFilename"

            logger.debug("Using output path: $outputPath")

            // Build the yt-dlp command
            val command = listOf(
                ytdlpPath,
                tikTokUrl,
                "--no-warnings",
                "-o", outputPath,
                "--force-overwrites"  // Overwrite if file exists
            )

            logger.debug("Executing command: ${command.joinToString(" ")}")

            // Execute the command
            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()

            // Read the output
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val output = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            // Wait for the process to complete
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy()
                logger.error("Timeout waiting for yt-dlp to complete")
                throw IOException("Timeout waiting for yt-dlp to complete")
            }

            if (process.exitValue() != 0) {
                val errorOutput = BufferedReader(InputStreamReader(process.errorStream))
                    .readText()
                logger.error("yt-dlp failed with exit code ${process.exitValue()}: $errorOutput")
                throw IOException("yt-dlp failed with exit code ${process.exitValue()}: $errorOutput")
            }

            logger.debug("yt-dlp output: $output")

            // Find the downloaded file (extension might vary)
            val parentDir = File(downloadPath)
            val downloadedFile = parentDir.listFiles { file ->
                file.name.startsWith("tiktok_$videoId")
            }?.firstOrNull()

            if (downloadedFile == null) {
                logger.error("Could not find downloaded file for video ID: $videoId")
                return null
            }

            logger.info("Successfully downloaded TikTok video to: ${downloadedFile.absolutePath}")
            return downloadedFile
        } catch (e: Exception) {
            logger.error("Error downloading TikTok video: ${e.message}", e)
            return null
        }
    }

    /**
     * Check if yt-dlp is installed and working
     */
    fun checkYtDlpInstallation(): Boolean {
        return try {
            logger.debug("Checking yt-dlp installation...")
            val process = ProcessBuilder(ytdlpPath, "--version").start()
            val result = process.waitFor(10, TimeUnit.SECONDS)
            val exitCode = process.exitValue()

            if (result && exitCode == 0) {
                val version = BufferedReader(InputStreamReader(process.inputStream)).readLine()
                logger.info("yt-dlp version: $version")
                true
            } else {
                logger.warn("yt-dlp check failed with exit code: $exitCode")
                false
            }
        } catch (e: Exception) {
            logger.error("Error checking yt-dlp installation: ${e.message}")
            false
        }
    }

    /**
     * Get available formats for a TikTok video
     * This is useful if you want to download specific quality
     */
    fun getAvailableFormats(tikTokUrl: String): List<String> {
        try {
            val command = listOf(
                ytdlpPath,
                tikTokUrl,
                "--list-formats",
                "--no-warnings"
            )

            val process = ProcessBuilder(command).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()

            process.waitFor(30, TimeUnit.SECONDS)

            return output.split("\n")
                .filter { it.isNotBlank() }
                .toList()
        } catch (e: Exception) {
            logger.error("Error getting available formats: ${e.message}", e)
            return emptyList()
        }
    }
}