package mcp_server

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с GitHub API.
 * Предоставляет методы для получения информации о Pull Requests.
 */
class GitHubService(
    private val httpClient: HttpClient,
    private val githubToken: String?
) {
    private val logger = LoggerFactory.getLogger(GitHubService::class.java)
    private val baseUrl = "https://api.github.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class PullRequest(
        val number: Int,
        val title: String,
        val state: String,
        val body: String?,
        @SerialName("html_url") val htmlUrl: String,
        val user: User,
        val head: Branch,
        val base: Branch,
        @SerialName("created_at") val createdAt: String,
        @SerialName("updated_at") val updatedAt: String,
        val additions: Int,
        val deletions: Int,
        @SerialName("changed_files") val changedFiles: Int
    )

    @Serializable
    data class User(
        val login: String,
        @SerialName("html_url") val htmlUrl: String
    )

    @Serializable
    data class Branch(
        val ref: String,
        val sha: String
    )

    @Serializable
    data class PullRequestFile(
        val filename: String,
        val status: String,
        val additions: Int,
        val deletions: Int,
        val changes: Int,
        val patch: String?
    )

    /**
     * Парсит URL PR и извлекает owner/repo/number
     * Пример: https://github.com/owner/repo/pull/123
     */
    fun parsePrUrl(url: String): Triple<String, String, Int>? {
        val regex = Regex("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)")
        val match = regex.find(url) ?: return null
        val (owner, repo, number) = match.destructured
        return Triple(owner, repo, number.toInt())
    }

    /**
     * 1. Получить информацию о Pull Request
     */
    suspend fun getPullRequest(owner: String, repo: String, prNumber: Int): String {
        return try {
            logger.info("Fetching PR #$prNumber from $owner/$repo")

            val response: HttpResponse = httpClient.get("$baseUrl/repos/$owner/$repo/pulls/$prNumber") {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    githubToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return "Error: ${response.status.value} - ${response.bodyAsText()}"
            }

            val pr: PullRequest = json.decodeFromString(response.bodyAsText())

            buildString {
                appendLine("=== Pull Request #${pr.number} ===")
                appendLine()
                appendLine("Title: ${pr.title}")
                appendLine("Status: ${pr.state}")
                appendLine("Author: ${pr.user.login}")
                appendLine("URL: ${pr.htmlUrl}")
                appendLine()
                appendLine("Branch: ${pr.head.ref} → ${pr.base.ref}")
                appendLine()
                appendLine("Statistics:")
                appendLine("  Files changed: ${pr.changedFiles}")
                appendLine("  Additions: +${pr.additions}")
                appendLine("  Deletions: -${pr.deletions}")
                appendLine()
                appendLine("Created: ${pr.createdAt}")
                appendLine("Updated: ${pr.updatedAt}")
                appendLine()
                if (!pr.body.isNullOrBlank()) {
                    appendLine("Description:")
                    appendLine(pr.body)
                } else {
                    appendLine("Description: (empty)")
                }
            }
        } catch (e: Exception) {
            logger.error("Error fetching PR", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 2. Получить список измененных файлов в PR
     */
    suspend fun getPullRequestFiles(owner: String, repo: String, prNumber: Int): String {
        return try {
            logger.info("Fetching files for PR #$prNumber from $owner/$repo")

            val response: HttpResponse = httpClient.get("$baseUrl/repos/$owner/$repo/pulls/$prNumber/files") {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    githubToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return "Error: ${response.status.value} - ${response.bodyAsText()}"
            }

            val files: List<PullRequestFile> = json.decodeFromString(response.bodyAsText())

            buildString {
                appendLine("=== Changed Files (${files.size}) ===")
                appendLine()
                files.forEach { file ->
                    appendLine("File: ${file.filename}")
                    appendLine("  Status: ${file.status}")
                    appendLine("  Changes: +${file.additions} -${file.deletions} (~${file.changes})")
                    appendLine()
                }
            }
        } catch (e: Exception) {
            logger.error("Error fetching PR files", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 3. Получить diff для PR
     */
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String {
        return try {
            logger.info("Fetching diff for PR #$prNumber from $owner/$repo")

            val response: HttpResponse = httpClient.get("$baseUrl/repos/$owner/$repo/pulls/$prNumber") {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github.v3.diff")
                    githubToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return "Error: ${response.status.value} - ${response.bodyAsText()}"
            }

            val diff = response.bodyAsText()

            "=== PR Diff ===\n\n$diff"
        } catch (e: Exception) {
            logger.error("Error fetching PR diff", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 4. Опубликовать комментарий к PR
     */
    suspend fun postComment(owner: String, repo: String, prNumber: Int, comment: String): String {
        return try {
            logger.info("Posting comment to PR #$prNumber in $owner/$repo")

            if (githubToken.isNullOrBlank()) {
                return "Error: GitHub token is required to post comments. Set GITHUB_TOKEN environment variable."
            }

            val response: HttpResponse = httpClient.post("$baseUrl/repos/$owner/$repo/issues/$prNumber/comments") {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append(HttpHeaders.Authorization, "Bearer $githubToken")
                    contentType(ContentType.Application.Json)
                }
                setBody("""{"body": ${Json.encodeToString(kotlinx.serialization.serializer(), comment)}}""")
            }

            if (response.status == HttpStatusCode.Created) {
                "Comment posted successfully to PR #$prNumber"
            } else {
                "Error: ${response.status.value} - ${response.bodyAsText()}"
            }
        } catch (e: Exception) {
            logger.error("Error posting comment", e)
            "Error: ${e.message}"
        }
    }
}
