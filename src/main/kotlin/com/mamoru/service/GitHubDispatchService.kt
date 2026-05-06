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
    @Value("\${github.repo:}") private val repo: String,
    @Value("\${github.ref:ManagerBot_oc}") private val ref: String
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

        // Переходим на workflow_dispatch для лучшей совместимости с OpenCode
        val workflowId = "opencode-bot.yml"
        val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches"
        
        val headers = HttpHeaders().apply {
            setBearerAuth(pat)
            set("Accept", "application/vnd.github+json")
            contentType = MediaType.APPLICATION_JSON
            set("X-GitHub-Api-Version", "2022-11-28")
        }
        
        val fullInstruction = """
            User Instruction: $instruction
            
            Strict Operational Protocol:
            1. Analyze and implement the changes.
            2. Run `./gradlew build` once to verify.
            3. Upon success, immediately call the `submit` tool and terminate.
            4. CATEGORICALLY FORBIDDEN: redundant Gradle tasks (clean, assemble, check, etc.), manual git commands (unless necessary for PR), or system exploration (ls, pwd, df, top, etc.) after implementation is verified.
            5. DO NOT REVERT ANY CHANGES.
            6. MAXIMUM STEPS: 100. You must reach a conclusion and call `submit` within 100 steps.
            7. FAILURE TO SUBMIT IMMEDIATELY AFTER VERIFICATION IS A VIOLATION OF PROTOCOL.
        """.trimIndent()

        val body = mapOf(
            "ref" to ref, // Ветка, на которой запускать
            "inputs" to mapOf(
                "message" to fullInstruction,
                "ref" to ref
            )
        )
        val entity = HttpEntity(body, headers)

        return try {
            logger.info("Sending GitHub workflow_dispatch to: $url")
            val response = restTemplate.postForEntity(url, entity, String::class.java)
            if (response.statusCode == HttpStatus.NO_CONTENT || response.statusCode.is2xxSuccessful) {
                logger.info("GitHub workflow_dispatch sent successfully for instruction: $instruction")
                null
            } else {
                logger.warn("GitHub dispatch returned unexpected status: ${response.statusCode}. Body: ${response.body}")
                "⚠️ GitHub вернул статус ${response.statusCode}: ${response.body}"
            }
        } catch (e: HttpClientErrorException) {
            logger.error("GitHub dispatch client error: ${e.statusCode} ${e.responseBodyAsString}")
            "❌ Ошибка GitHub API (${e.statusCode}): ${e.responseBodyAsString}"
        } catch (e: HttpServerErrorException) {
            logger.error("GitHub dispatch server error: ${e.statusCode} ${e.responseBodyAsString}")
            "❌ Ошибка сервера GitHub (${e.statusCode}): попробуйте позже."
        } catch (e: Exception) {
            logger.error("GitHub dispatch unexpected error", e)
            "❌ Не удалось отправить команду в GitHub: ${e.message}"
        }
    }
}
