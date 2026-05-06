package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@Service
class GitHubDispatchService(
    private val restTemplate: RestTemplate,
    @Value("\${github.pat:}") private val pat: String,
    @Value("\${github.owner:}") private val owner: String,
    @Value("\${github.repo:}") private val repo: String
) {
    private val logger = LoggerFactory.getLogger(GitHubDispatchService::class.java)

    /**
     * Sends a repository_dispatch event to GitHub Actions with the given instruction.
     * Returns null on success, or an error message string on failure.
     */
    fun dispatch(instruction: String): String? {
        if (pat.isBlank() || owner.isBlank() || repo.isBlank()) {
            logger.error("GitHub dispatch is not configured (pat/owner/repo missing)")
            return "❌ GitHub dispatch не налаштований: перевірте змінні GITHUB_PAT, GITHUB_OWNER, GITHUB_REPO."
        }

        val url = "https://api.github.com/repos/$owner/$repo/dispatches"
        val headers = HttpHeaders().apply {
            setBearerAuth(pat)
            accept = listOf(MediaType.parseMediaType("application/vnd.github+json"))
            contentType = MediaType.APPLICATION_JSON
            set("X-GitHub-Api-Version", "2022-11-28")
        }
        val body = mapOf(
            "event_type" to "telegram_cmd",
            "client_payload" to mapOf("message" to instruction)
        )
        val entity = HttpEntity(body, headers)
        logger.info("Sending GitHub dispatch for instruction: $instruction, url: $url, headers: $headers, body: $body")
        return try {
            val response = restTemplate.postForEntity(url, entity, String::class.java)
            if (response.statusCode == HttpStatus.NO_CONTENT || response.statusCode.is2xxSuccessful) {
                logger.info("GitHub dispatch sent successfully for instruction: $instruction")
                null
            } else {
                logger.warn("GitHub dispatch returned unexpected status: ${response.statusCode}")
                "⚠️ GitHub повернув несподіваний статус: ${response.statusCode}"
            }
        } catch (e: HttpClientErrorException) {
            logger.error("GitHub dispatch client error: ${e.statusCode} ${e.responseBodyAsString}")
            "❌ Помилка GitHub API (${e.statusCode}): ${e.responseBodyAsString}"
        } catch (e: HttpServerErrorException) {
            logger.error("GitHub dispatch server error: ${e.statusCode} ${e.responseBodyAsString}")
            "❌ Помилка сервера GitHub (${e.statusCode}): спробуйте пізніше."
        } catch (e: Exception) {
            logger.error("GitHub dispatch unexpected error", e)
            "❌ Не вдалося надіслати команду до GitHub: ${e.message}"
        }
    }
}
