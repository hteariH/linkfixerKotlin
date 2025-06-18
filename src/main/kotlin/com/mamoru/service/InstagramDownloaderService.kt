package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.ResourceUtils
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Service
class InstagramDownloaderService {
    private val logger = LoggerFactory.getLogger(InstagramDownloaderService::class.java)

    @Value("\${instagram.download.path:./videos}")
    private lateinit var downloadPath: String

    @Value("\${ytdlp.path:yt-dlp}")
    private lateinit var ytdlpPath: String

    private var cookiesPath: String = ""

    init {
        try {
            // Create download directory if it doesn't exist
            val dir = File(downloadPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Initialize cookies path
            try {
                // First try to load cookies from the root directory of the project
                val rootCookiesFile = File("/cookies.txt")
                if (rootCookiesFile.exists()) {
                    cookiesPath = rootCookiesFile.absolutePath
                    logger.info("Cookies file found in root directory at: $cookiesPath")
                } else {
                    // Fall back to resources directory if not found in root
                    cookiesPath = ResourceUtils.getFile("classpath:cookies.txt").absolutePath
                    logger.info("Cookies file found in resources directory at: $cookiesPath")
                }
            } catch (e: Exception) {
                logger.error("Error loading cookies file: ${e.message}", e)
                // Provide a fallback value to prevent UninitializedPropertyAccessException
                cookiesPath = ""
                logger.warn("Using empty cookies path as fallback")
            }

            // Check yt-dlp installation on startup
            if (!checkYtDlpInstallation()) {
                logger.error("yt-dlp is not correctly installed or configured!")
            } else {
                logger.info("yt-dlp is properly configured")
            }
        } catch (e: Exception) {
            logger.error("Error initializing InstagramDownloaderService", e)
        }
    }

    /**
     * Extract Instagram video ID from URL
     */
    fun extractInstagramId(url: String): String? {
        // Pattern for Instagram URLs - adapt as needed
        val patterns = listOf(
            "instagram\\.com/p/([\\w-]+)".toRegex(),     // Post format
            "instagram\\.com/reel/([\\w-]+)".toRegex(),  // Reel format
            "instagr\\.am/p/([\\w-]+)".toRegex(),        // Short post format
            "instagr\\.am/reel/([\\w-]+)".toRegex()      // Short reel format
        )

        for (pattern in patterns) {
            val matchResult = pattern.find(url)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
        logger.warn("Could not extract Instagram ID from URL: $url")
        return null
    }

    /**
     * Download Instagram video using yt-dlp
     * Returns file path if successful, null otherwise
     */
    fun downloadVideo(instagramUrl: String): File? {
        logger.info("Attempting to download Instagram video: $instagramUrl")

        try {
            val videoId = extractInstagramId(instagramUrl)
            if (videoId == null) {
                logger.error("Failed to extract video ID from URL: $instagramUrl")
                return null
            }

            val outputFilename = "instagram_$videoId.%(ext)s"
            val outputPath = "$downloadPath/$outputFilename"

            logger.debug("Using output path: $outputPath")

            // Build the yt-dlp command
            val commandList = mutableListOf(
                ytdlpPath,
                instagramUrl,
                "--no-warnings",
                "-o", outputPath,
                "--force-overwrites"  // Overwrite if file exists
            )

//            // Add cookies option only if cookiesPath is not empty
//            if (cookiesPath.isNotEmpty()) {
//                commandList.add("--cookies")
//                commandList.add(cookiesPath)
//                logger.debug("Using cookies file: $cookiesPath")
//            } else {
//                logger.warn("No cookies file specified, authentication may fail")
//            }

            cookiesPath = "/cookies.txt"
            commandList.add("--cookies")
            commandList.add(cookiesPath)

            val command = commandList

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

            logger.debug("yt-dlp output: {}", output)

            // Find the downloaded file (extension might vary)
            val parentDir = File(downloadPath)
            val downloadedFile = parentDir.listFiles { file ->
                file.name.startsWith("instagram_$videoId")
            }?.firstOrNull()

            if (downloadedFile == null) {
                logger.error("Could not find downloaded file for video ID: $videoId")
                return null
            }

            logger.info("Successfully downloaded Instagram video to: ${downloadedFile.absolutePath}")
            return downloadedFile
        } catch (e: Exception) {
            logger.error("Error downloading Instagram video: ${e.message}", e)
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

}
