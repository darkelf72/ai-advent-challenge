package mcp_server

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Сервис для выполнения Git операций через JGit.
 * Использует git-репозиторий проекта ai-advent-challenge.
 */
class GitService(private val repositoryPath: String = ".") {
    private val logger = LoggerFactory.getLogger(GitService::class.java)

    /**
     * Открывает git репозиторий
     */
    private fun openGit(): Git {
        val repo = FileRepositoryBuilder()
            .setGitDir(File(repositoryPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        return Git(repo)
    }

    /**
     * 1. git_status - Получить статус репозитория
     */
    fun getStatus(): String {
        return try {
            openGit().use { git ->
                val status = git.status().call()
                buildString {
                    appendLine("=== Git Status ===")
                    appendLine("Branch: ${git.repository.branch}")
                    appendLine()

                    if (status.isClean) {
                        appendLine("Working tree clean")
                    } else {
                        if (status.added.isNotEmpty()) {
                            appendLine("Added files:")
                            status.added.forEach { appendLine("  + $it") }
                            appendLine()
                        }
                        if (status.changed.isNotEmpty()) {
                            appendLine("Changed files:")
                            status.changed.forEach { appendLine("  M $it") }
                            appendLine()
                        }
                        if (status.modified.isNotEmpty()) {
                            appendLine("Modified files:")
                            status.modified.forEach { appendLine("  M $it") }
                            appendLine()
                        }
                        if (status.removed.isNotEmpty()) {
                            appendLine("Removed files:")
                            status.removed.forEach { appendLine("  - $it") }
                            appendLine()
                        }
                        if (status.missing.isNotEmpty()) {
                            appendLine("Missing files:")
                            status.missing.forEach { appendLine("  ! $it") }
                            appendLine()
                        }
                        if (status.untracked.isNotEmpty()) {
                            appendLine("Untracked files:")
                            status.untracked.forEach { appendLine("  ? $it") }
                            appendLine()
                        }
                        if (status.conflicting.isNotEmpty()) {
                            appendLine("Conflicting files:")
                            status.conflicting.forEach { appendLine("  U $it") }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting git status", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 2. git_log - Показать историю коммитов
     */
    fun getLog(limit: Int = 10, branch: String? = null): String {
        return try {
            openGit().use { git ->
                val logCommand = git.log().setMaxCount(limit)

                if (branch != null) {
                    val ref = git.repository.findRef(branch)
                    if (ref != null) {
                        logCommand.add(ref.objectId)
                    }
                }

                val commits = logCommand.call()

                buildString {
                    appendLine("=== Git Log (last $limit commits) ===")
                    appendLine()
                    commits.forEachIndexed { index, commit ->
                        appendLine("Commit ${index + 1}:")
                        appendLine("  Hash: ${commit.name}")
                        appendLine("  Author: ${commit.authorIdent.name} <${commit.authorIdent.emailAddress}>")
                        appendLine("  Date: ${commit.authorIdent.`when`}")
                        appendLine("  Message: ${commit.shortMessage}")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting git log", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 3. git_diff - Показать изменения
     */
    fun getDiff(cached: Boolean = false, fileName: String? = null): String {
        return try {
            openGit().use { git ->
                val outputStream = ByteArrayOutputStream()
                val formatter = DiffFormatter(outputStream)
                formatter.setRepository(git.repository)

                val diffCommand = git.diff()
                    .setCached(cached)

                if (fileName != null) {
                    diffCommand.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(fileName))
                }

                val diffs = diffCommand.call()
                diffs.forEach { formatter.format(it) }

                val result = outputStream.toString("UTF-8")
                if (result.isEmpty()) {
                    "No changes to show"
                } else {
                    "=== Git Diff ${if (cached) "(Staged)" else "(Working Directory)"} ===\n$result"
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting git diff", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 4. git_branch_list - Список веток
     */
    fun listBranches(listMode: String = "all"): String {
        return try {
            openGit().use { git ->
                val mode = when (listMode.lowercase()) {
                    "local" -> ListBranchCommand.ListMode.ALL
                    "remote" -> ListBranchCommand.ListMode.REMOTE
                    else -> ListBranchCommand.ListMode.ALL
                }

                val branches = git.branchList()
                    .setListMode(mode)
                    .call()

                val currentBranch = git.repository.branch

                buildString {
                    appendLine("=== Git Branches ===")
                    appendLine("Current branch: $currentBranch")
                    appendLine()

                    val localBranches = branches.filter { !it.name.startsWith("refs/remotes/") }
                    if (localBranches.isNotEmpty()) {
                        appendLine("Local branches:")
                        localBranches.forEach { ref ->
                            val branchName = ref.name.removePrefix("refs/heads/")
                            val marker = if (branchName == currentBranch) "* " else "  "
                            appendLine("$marker$branchName")
                        }
                        appendLine()
                    }

                    val remoteBranches = branches.filter { it.name.startsWith("refs/remotes/") }
                    if (remoteBranches.isNotEmpty() && listMode != "local") {
                        appendLine("Remote branches:")
                        remoteBranches.forEach { ref ->
                            val branchName = ref.name.removePrefix("refs/remotes/")
                            appendLine("  $branchName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error listing branches", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 5. git_branch_create - Создать ветку
     */
    fun createBranch(branchName: String, startPoint: String? = null): String {
        return try {
            openGit().use { git ->
                val branchCommand = git.branchCreate()
                    .setName(branchName)

                if (startPoint != null) {
                    val ref = git.repository.findRef(startPoint)
                    if (ref != null) {
                        branchCommand.setStartPoint(startPoint)
                    } else {
                        return "Error: Start point '$startPoint' not found"
                    }
                }

                val ref = branchCommand.call()
                "Branch '${ref.name.removePrefix("refs/heads/")}' created successfully"
            }
        } catch (e: Exception) {
            logger.error("Error creating branch", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 6. git_checkout - Переключить ветку
     */
    fun checkout(branchName: String, createIfNotExists: Boolean = false): String {
        return try {
            openGit().use { git ->
                val checkoutCommand = git.checkout()
                    .setName(branchName)
                    .setCreateBranch(createIfNotExists)

                checkoutCommand.call()
                "Switched to branch '$branchName'"
            }
        } catch (e: Exception) {
            logger.error("Error checking out branch", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 7. git_commit - Создать коммит
     */
    fun commit(message: String, addAll: Boolean = false): String {
        return try {
            openGit().use { git ->
                if (addAll) {
                    git.add().addFilepattern(".").call()
                }

                val commit = git.commit()
                    .setMessage(message)
                    .call()

                "Committed successfully\nHash: ${commit.name}\nMessage: ${commit.shortMessage}"
            }
        } catch (e: Exception) {
            logger.error("Error committing", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 8. git_push - Отправить на remote
     */
    fun push(remote: String = "origin", branch: String? = null, username: String? = null, password: String? = null): String {
        return try {
            openGit().use { git ->
                val pushCommand = git.push()
                    .setRemote(remote)

                if (branch != null) {
                    pushCommand.setRefSpecs(org.eclipse.jgit.transport.RefSpec("refs/heads/$branch:refs/heads/$branch"))
                }

                if (username != null && password != null) {
                    pushCommand.setCredentialsProvider(
                        UsernamePasswordCredentialsProvider(username, password)
                    )
                }

                val results = pushCommand.call()

                buildString {
                    appendLine("=== Push Results ===")
                    results.forEach { result ->
                        result.remoteUpdates.forEach { update ->
                            appendLine("${update.remoteName}: ${update.status}")
                            if (update.message != null) {
                                appendLine("  ${update.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error pushing", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 9. git_pull - Получить изменения
     */
    fun pull(remote: String = "origin", branch: String? = null, username: String? = null, password: String? = null): String {
        return try {
            openGit().use { git ->
                val pullCommand = git.pull()
                    .setRemote(remote)

                if (branch != null) {
                    pullCommand.setRemoteBranchName(branch)
                }

                if (username != null && password != null) {
                    pullCommand.setCredentialsProvider(
                        UsernamePasswordCredentialsProvider(username, password)
                    )
                }

                val result = pullCommand.call()

                buildString {
                    appendLine("=== Pull Results ===")
                    appendLine("Success: ${result.isSuccessful}")
                    appendLine("Merge Status: ${result.mergeResult?.mergeStatus}")
                    if (result.fetchResult != null) {
                        appendLine("Fetched from: ${result.fetchResult.uri}")
                        appendLine("Messages: ${result.fetchResult.messages}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error pulling", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 10. git_show_file - Показать содержимое файла в определенном коммите
     */
    fun showFile(commitHash: String, filePath: String): String {
        return try {
            openGit().use { git ->
                val objectId = git.repository.resolve(commitHash)
                    ?: return "Error: Commit '$commitHash' not found"

                val revWalk = RevWalk(git.repository)
                val commit = revWalk.parseCommit(objectId)
                val treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(git.repository, filePath, commit.tree)
                    ?: return "Error: File '$filePath' not found in commit '$commitHash'"

                val objectLoader = git.repository.open(treeWalk.getObjectId(0))
                val content = String(objectLoader.bytes)

                "=== File: $filePath (commit: ${commitHash.take(8)}) ===\n$content"
            }
        } catch (e: Exception) {
            logger.error("Error showing file", e)
            "Error: ${e.message}"
        }
    }
}
