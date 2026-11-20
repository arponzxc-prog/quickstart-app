package com.example.builder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.filesystem.ProjectFileManager
import com.example.data.github.*
import com.example.data.local.NexaDatabase
import com.example.data.local.entity.BuildArtifactEntity
import com.example.data.local.entity.BuildEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.security.SecureTokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

sealed class BuildProgressState {
    object Idle : BuildProgressState()
    data class Active(
        val projectName: String,
        val currentStep: String,
        val progressPercent: Float,
        val liveLogs: String,
        val status: String,
        val runHtmlUrl: String? = null
    ) : BuildProgressState()
}

class BuildEngine(
    private val context: Context,
    private val database: NexaDatabase,
    private val fileManager: ProjectFileManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var currentBuildJob: Job? = null
    private var currentActiveRunInfo: Triple<String, String, Long>? = null

    private val _buildState = MutableStateFlow<BuildProgressState>(BuildProgressState.Idle)
    val buildState: StateFlow<BuildProgressState> = _buildState.asStateFlow()

    private val gitHubService by lazy { GitHubService.create() }

    fun triggerBuild(
        project: ProjectEntity,
        buildType: String = "DEBUG",
        jdk: String = "17",
        gradle: String = "8.10.2",
        compileSdk: String = "35",
        clean: Boolean = true
    ) {
        currentBuildJob?.cancel()
        currentBuildJob = scope.launch {
            val buildId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()
            val logBuilder = StringBuilder()
            var runId: Long? = null
            var extractedErrorMessage: String? = null

            fun appendLog(msg: String) {
                val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                logBuilder.append("[$time] $msg\n")
            }

            fun updateProgress(step: String, percent: Float, status: String, runUrl: String? = null) {
                _buildState.value = BuildProgressState.Active(
                    projectName = project.name,
                    currentStep = step,
                    progressPercent = percent,
                    liveLogs = logBuilder.toString(),
                    status = status,
                    runHtmlUrl = runUrl
                )
            }

            try {
                appendLog("=== Nexa IDE Cloud APK Builder ===")
                appendLog("Target Project: ${project.name} (${project.packageName})")
                appendLog("Build Variant: $buildType")
                appendLog("JDK: OpenJDK $jdk | Gradle: version from the project's own wrapper | Compile SDK: API $compileSdk (informational)")
                appendLog("Mode: Real GitHub Actions CI/CD (No Mock / No Demo)")
                updateProgress("Verifying GitHub credentials", 0.05f, "INITIALIZING")

                // Step 1: Verify GitHub connection with Keystore-backed SecureTokenManager
                val connection = database.gitHubConnectionDao().getConnection()
                val token = SecureTokenManager.getGitHubToken(context)?.takeIf { it.isNotBlank() }
                    ?: connection?.token
                        ?.let { com.example.data.github.GitHubTokenSanitizer.clean(it) }
                        ?.takeIf { it.isNotBlank() && !com.example.data.github.GitHubTokenSanitizer.isPlaceholder(it) }

                if (token.isNullOrBlank()) {
                    throw IllegalStateException(
                        "No GitHub Token found. Connect your GitHub Personal Access Token (PAT) with 'repo' and 'workflow' scopes in GitHub screen before building."
                    )
                }

                val targetRepo = connection?.activeRepo?.trim()
                if (targetRepo.isNullOrBlank() || !targetRepo.contains("/")) {
                    throw IllegalStateException(
                        "No target repository selected. Please open GitHub screen and select or create an active repository (e.g. ${connection?.username ?: "username"}/my-app)."
                    )
                }

                val repoParts = targetRepo.split("/")
                val repoOwner = repoParts[0]
                val repoName = repoParts[1]
                val authHeader = com.example.data.github.GitHubTokenSanitizer.bearer(com.example.data.github.GitHubTokenSanitizer.clean(token))

                appendLog("Target GitHub Repository: $targetRepo")
                updateProgress("Inspecting repository metadata", 0.10f, "PREPARING")

                val repoDetails = try {
                    gitHubService.getRepository(authHeader, repoOwner, repoName)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Could not access repository '$targetRepo' (${e.message}). Ensure your GitHub token has 'repo' scope and write permissions."
                    )
                }

                val targetBranch = repoDetails.defaultBranch.ifBlank { "main" }
                appendLog("Repository default branch: $targetBranch")

                // Step 2: Sync project files and workflow directly via Git Data API
                updateProgress("Syncing ${project.name} files to GitHub", 0.20f, "SYNCING")
                appendLog("Syncing ${project.name} source files to branch '$targetBranch' in a single commit...")
                syncProjectFiles(project, authHeader, repoOwner, repoName, targetBranch, logBuilder)
                appendLog("Project source files and workflow synced to GitHub successfully.")

                // Step 3: Record known run IDs before dispatching
                val initialRuns = try {
                    gitHubService.listWorkflowRuns(authHeader, repoOwner, repoName, perPage = 30)
                } catch (e: Exception) {
                    null
                }
                val knownIds = initialRuns?.workflowRuns?.map { it.id }?.toSet() ?: emptySet()

                // Step 4: Dispatch workflow with retry for 404
                appendLog("Dispatching workflow dispatch event to '.github/workflows/android-build.yml' on branch '$targetBranch'...")
                updateProgress("Triggering GitHub Actions runner", 0.40f, "TRIGGERING")

                var dispatchResp: retrofit2.Response<Unit>? = null
                var lastDispatchException: Exception? = null
                for (attempt in 1..5) {
                    try {
                        val resp = gitHubService.triggerWorkflowDispatch(
                            auth = authHeader,
                            owner = repoOwner,
                            repo = repoName,
                            workflowId = "android-build.yml",
                            payload = WorkflowDispatchPayload(
                                ref = targetBranch,
                                inputs = mapOf(
                                    "build_type" to buildType.lowercase(),
                                    "jdk_version" to jdk,
                                    "clean_build" to clean.toString()
                                )
                            )
                        )
                        dispatchResp = resp
                        if (resp.isSuccessful) break
                        if (resp.code() == 404 && attempt < 5) {
                            appendLog("Workflow not registered yet (404), waiting 4s before retry ($attempt/5)...")
                            delay(4000L)
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        lastDispatchException = e
                        if (attempt < 5) {
                            delay(4000L)
                        }
                    }
                }

                if (dispatchResp == null || !dispatchResp.isSuccessful) {
                    val code = dispatchResp?.code() ?: 0
                    val errorBody = dispatchResp?.errorBody()?.string() ?: lastDispatchException?.message ?: ""
                    appendLog("GitHub API Error HTTP $code: $errorBody")
                    if (code == 404) {
                        throw IllegalStateException(
                            "Workflow or Repository Not Found (404). Ensure '$targetRepo' exists and contains '.github/workflows/android-build.yml' on branch '$targetBranch'."
                        )
                    } else if (code == 403) {
                        throw IllegalStateException(
                            "GitHub API Error 403: 'workflow' scope is missing from your Personal Access Token. Please generate a new PAT with BOTH 'repo' and 'workflow' checkboxes enabled."
                        )
                    } else {
                        throw IllegalStateException("GitHub dispatch failed (HTTP $code): $errorBody")
                    }
                }

                appendLog("Workflow dispatch accepted by GitHub.")
                updateProgress("Waiting for GitHub runner to pick up job...", 0.45f, "QUEUED")

                // Step 5: Find the exact newly created workflow run
                var runHtmlUrl: String? = null

                for (attempt in 1..30) {
                    delay(3000)
                    try {
                        val runsResponse = gitHubService.listWorkflowRuns(authHeader, repoOwner, repoName, perPage = 30)
                        val targetRun = runsResponse.workflowRuns.firstOrNull { run ->
                            run.id !in knownIds &&
                                run.headBranch == targetBranch &&
                                run.event == "workflow_dispatch"
                        } ?: runsResponse.workflowRuns.firstOrNull { run ->
                            run.id !in knownIds && run.headBranch == targetBranch
                        }

                        if (targetRun != null) {
                            runId = targetRun.id
                            runHtmlUrl = targetRun.htmlUrl
                            currentActiveRunInfo = Triple(repoOwner, repoName, targetRun.id)
                            appendLog("Found GitHub Actions Run #${targetRun.id} (Status: ${targetRun.status})")
                            appendLog("View Live: ${targetRun.htmlUrl}")
                            updateProgress("GitHub Actions running #${targetRun.id}", 0.50f, targetRun.status.uppercase(), runHtmlUrl)
                            break
                        }
                    } catch (e: Exception) {
                        appendLog("Run discovery warning: ${e.message}")
                    }
                }

                if (runId == null) {
                    throw IllegalStateException("Dispatched workflow run did not appear in GitHub Actions within 90 seconds.")
                }

                // Step 6: Monitor run progress and jobs until completed
                var runConclusion: String? = null
                val maxWaitMs = 15 * 60 * 1000L
                val monitorStart = System.currentTimeMillis()

                while (System.currentTimeMillis() - monitorStart < maxWaitMs) {
                    delay(5000)
                    try {
                        val runs = gitHubService.listWorkflowRuns(authHeader, repoOwner, repoName, perPage = 10)
                        val currentRun = runs.workflowRuns.find { it.id == runId }
                        if (currentRun != null) {
                            val status = currentRun.status
                            val conclusion = currentRun.conclusion

                            // Fetch jobs for granular progress
                            var stepName = "Running GitHub Actions..."
                            var computedProgress = when (status) {
                                "queued" -> 0.52f
                                "in_progress" -> 0.65f
                                "completed" -> 0.88f
                                else -> 0.60f
                            }

                            try {
                                val jobsResp = gitHubService.listRunJobs(authHeader, repoOwner, repoName, runId)
                                val activeJob = jobsResp.jobs.firstOrNull()
                                if (activeJob != null) {
                                    val steps = activeJob.steps ?: emptyList()
                                    if (steps.isNotEmpty()) {
                                        val completedCount = steps.count { it.status == "completed" }
                                        val totalCount = steps.size
                                        computedProgress = 0.50f + 0.38f * (completedCount.toFloat() / totalCount.toFloat())
                                        val currentStepObj = steps.find { it.status == "in_progress" } ?: steps.find { it.status == "queued" }
                                        if (currentStepObj != null) {
                                            stepName = currentStepObj.name
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // ignore job query error
                            }

                            appendLog("Run #$runId status: $status | step: $stepName | conclusion: ${conclusion ?: "running"}")
                            updateProgress(
                                step = stepName,
                                percent = computedProgress,
                                status = status.uppercase(),
                                runUrl = runHtmlUrl
                            )

                            if (status == "completed") {
                                runConclusion = conclusion
                                break
                            }
                        }
                    } catch (e: Exception) {
                        appendLog("Status check notice: ${e.message}")
                    }
                }

                if (runConclusion != "success") {
                    var failDetail = "GitHub Actions run ended with conclusion: '${runConclusion ?: "failure"}'."
                    var logSnippet = ""

                    if (runId != null) {
                        try {
                            val jobsResp = gitHubService.listRunJobs(authHeader, repoOwner, repoName, runId)
                            val failedJob = jobsResp.jobs.find { it.conclusion == "failure" } ?: jobsResp.jobs.firstOrNull()
                            if (failedJob != null) {
                                val failedStep = failedJob.steps?.find { it.conclusion == "failure" }
                                if (failedStep != null) {
                                    failDetail = "Failed at step '${failedStep.name}' in job '${failedJob.name}'."
                                    appendLog("Step Failure: ${failedStep.name} (Step #${failedStep.number})")
                                }

                                // Download job logs to extract real Gradle error
                                try {
                                    val logsBody = gitHubService.downloadJobLogs(authHeader, repoOwner, repoName, failedJob.id)
                                    val rawLogText = logsBody.string()

                                    // Clean ANSI escape codes and leading timestamps
                                    val ansiCleaned = rawLogText.replace(Regex("\u001B\\[[;\\d]*m"), "")
                                    val lines = ansiCleaned.lines()
                                    val extractedLines = mutableListOf<String>()

                                    var capturingWhatWentWrong = false
                                    var whatWentWrongCount = 0
                                    val kotlinErrors = mutableListOf<String>()

                                    for (line in lines) {
                                        // strip timestamp e.g. "2026-11-11T21:30:00.0000000Z "
                                        val cleanLine = line.replace(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z\\s+"), "")

                                        if (cleanLine.contains("What went wrong:")) {
                                            capturingWhatWentWrong = true
                                            extractedLines.add(cleanLine)
                                            whatWentWrongCount = 0
                                            continue
                                        }

                                        if (capturingWhatWentWrong) {
                                            extractedLines.add(cleanLine)
                                            whatWentWrongCount++
                                            if (whatWentWrongCount > 8) capturingWhatWentWrong = false
                                        }

                                        if ((cleanLine.contains("error:") || cleanLine.startsWith("e: ")) && kotlinErrors.size < 20) {
                                            kotlinErrors.add(cleanLine)
                                        }

                                        if (cleanLine.contains("Execution failed for task") && !extractedLines.contains(cleanLine)) {
                                            extractedLines.add(cleanLine)
                                        }

                                        if (cleanLine.contains("Minimum supported Gradle version")) {
                                            if (!extractedLines.contains(cleanLine)) extractedLines.add(cleanLine)
                                        }
                                    }

                                    if (kotlinErrors.isNotEmpty()) {
                                        extractedLines.add("--- Kotlin Errors ---")
                                        extractedLines.addAll(kotlinErrors)
                                    }

                                    logSnippet = extractedLines.take(60).joinToString("\n")
                                    if (logSnippet.isNotBlank()) {
                                        appendLog("\n--- Extracted Gradle Error Summary ---\n$logSnippet\n")
                                    }
                                } catch (e: Exception) {
                                    appendLog("Could not download job logs: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            appendLog("Could not query job steps: ${e.message}")
                        }
                    }

                    val finalError = if (logSnippet.isNotBlank()) {
                        "$failDetail\n$logSnippet"
                    } else {
                        failDetail
                    }
                    extractedErrorMessage = finalError

                    val failMsg = "$failDetail Check full compilation logs on GitHub:\n$runHtmlUrl"
                    appendLog("BUILD FAILED: $failMsg")
                    throw IllegalStateException(finalError)
                }

                // Step 7: Download real compiled APK artifact from GitHub
                appendLog("Build succeeded on GitHub Actions! Fetching generated APK artifact...")
                updateProgress("Downloading compiled APK from GitHub", 0.90f, "DOWNLOADING", runHtmlUrl)

                val artifactsResp = gitHubService.listRunArtifacts(authHeader, repoOwner, repoName, runId!!)
                val apkArtifact = artifactsResp.artifacts.firstOrNull {
                    it.name.contains("APK", ignoreCase = true) || it.name.contains("AAB", ignoreCase = true) || it.name.contains("Android", ignoreCase = true)
                } ?: artifactsResp.artifacts.firstOrNull()

                if (apkArtifact == null) {
                    throw IllegalStateException("GitHub Actions build succeeded, but no APK artifact was uploaded by the runner.")
                }

                appendLog("Found artifact: ${apkArtifact.name} (${apkArtifact.sizeInBytes / 1024} KB)")

                val artifactZipBody = gitHubService.downloadArtifactZip(authHeader, repoOwner, repoName, apkArtifact.id)
                val targetExt = if (buildType == "BUNDLE") "aab" else "apk"
                val outputApkFile = File(fileManager.downloadsDir, "${project.name.replace(' ', '_')}-${buildType.lowercase()}.$targetExt")

                ZipInputStream(artifactZipBody.byteStream()).use { zis ->
                    var entry = zis.nextEntry
                    var found = false
                    while (entry != null) {
                        if (!entry.isDirectory && (entry.name.endsWith(".apk") || entry.name.endsWith(".aab"))) {
                            FileOutputStream(outputApkFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            found = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                    if (!found) {
                        throw IllegalStateException("Artifact archive did not contain a valid .apk or .aab file.")
                    }
                }

                // Verify downloaded artifact integrity
                if (!outputApkFile.exists() || outputApkFile.length() < 1024) {
                    throw IllegalStateException("Downloaded APK is empty or corrupted (size: ${outputApkFile.length()} bytes)")
                }
                val magicHeader = ByteArray(4)
                java.io.FileInputStream(outputApkFile).use { it.read(magicHeader) }
                if (magicHeader[0] != 0x50.toByte() || magicHeader[1] != 0x4B.toByte()) {
                    throw IllegalStateException("Downloaded file failed validation: invalid ZIP/APK magic header.")
                }

                appendLog("APK successfully saved to: ${outputApkFile.absolutePath}")
                appendLog("File Size: ${String.format(Locale.US, "%.2f MB", outputApkFile.length() / (1024.0 * 1024.0))}")
                appendLog("=== BUILD SUCCESSFUL ===")

                val endTime = System.currentTimeMillis()
                val durationMs = endTime - startTime
                val maxNum = database.buildDao().getMaxBuildNumber() ?: 0
                val nextBuildNum = maxNum + 1

                val buildEntity = BuildEntity(
                    id = buildId,
                    buildNumber = nextBuildNum,
                    projectId = project.id,
                    projectName = project.name,
                    buildType = buildType,
                    status = "SUCCESSFUL",
                    logs = logBuilder.toString(),
                    durationSeconds = durationMs / 1000L,
                    startedAt = startTime,
                    completedAt = endTime,
                    artifactName = outputApkFile.name,
                    artifactSize = outputApkFile.length(),
                    localArtifactPath = outputApkFile.absolutePath,
                    errorMessage = null,
                    githubRunId = runId
                )
                database.buildDao().insertBuild(buildEntity)

                val artifactEntity = BuildArtifactEntity(
                    id = UUID.randomUUID().toString(),
                    buildId = buildId,
                    projectId = project.id,
                    projectName = project.name,
                    fileName = outputApkFile.name,
                    fileType = targetExt.uppercase(),
                    localFilePath = outputApkFile.absolutePath,
                    fileSizeBytes = outputApkFile.length(),
                    packageName = project.packageName,
                    versionName = "1.0",
                    versionCode = 1,
                    downloadedAt = endTime,
                    isVerified = true
                )
                database.buildArtifactDao().insertArtifact(artifactEntity)

                database.notificationDao().insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        title = "Build Succeeded: ${project.name}",
                        message = "Your $buildType APK is ready to install (${outputApkFile.name}).",
                        type = "BUILD_SUCCESS"
                    )
                )

                _buildState.value = BuildProgressState.Idle
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Build finished: ${outputApkFile.name}", Toast.LENGTH_LONG).show()
                }

            } catch (ce: CancellationException) {
                appendLog("Build cancelled by user.")
                _buildState.value = BuildProgressState.Idle
            } catch (e: Exception) {
                val displayError = extractedErrorMessage ?: e.message ?: "GitHub Actions compilation error occurred."
                appendLog("ERROR: $displayError")
                val endTime = System.currentTimeMillis()
                val durationMs = endTime - startTime
                val maxNum = database.buildDao().getMaxBuildNumber() ?: 0
                val nextBuildNum = maxNum + 1

                database.buildDao().insertBuild(
                    BuildEntity(
                        id = buildId,
                        buildNumber = nextBuildNum,
                        projectId = project.id,
                        projectName = project.name,
                        buildType = buildType,
                        status = "FAILED",
                        logs = logBuilder.toString(),
                        durationSeconds = durationMs / 1000L,
                        startedAt = startTime,
                        completedAt = endTime,
                        artifactName = null,
                        artifactSize = null,
                        localArtifactPath = null,
                        errorMessage = displayError,
                        githubRunId = runId
                    )
                )

                database.notificationDao().insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        title = "Build Failed: ${project.name}",
                        message = displayError.take(1500),
                        type = "BUILD_FAILED"
                    )
                )

                _buildState.value = BuildProgressState.Idle
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${project.name} build failed: ${displayError.take(120)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun syncProjectFiles(
        project: ProjectEntity,
        authHeader: String,
        owner: String,
        repo: String,
        branch: String,
        logBuilder: StringBuilder
    ) {
        val rootDir = File(project.rootPath)
        if (!rootDir.exists()) return

        val rootHasBuildOrSettings = File(rootDir, "settings.gradle").exists() ||
                File(rootDir, "settings.gradle.kts").exists() ||
                File(rootDir, "build.gradle").exists() ||
                File(rootDir, "build.gradle.kts").exists()

        val subDirs = rootDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
        val effectiveDir = if (rootHasBuildOrSettings) {
            rootDir
        } else if (subDirs.size == 1 && (
                    File(subDirs[0], "settings.gradle").exists() ||
                    File(subDirs[0], "settings.gradle.kts").exists()
                )) {
            subDirs[0]
        } else {
            rootDir
        }

        val hasGradle = File(effectiveDir, "build.gradle").exists() ||
                File(effectiveDir, "build.gradle.kts").exists() ||
                effectiveDir.walkTopDown().any { it.name == "build.gradle" || it.name == "build.gradle.kts" }
        val hasSettings = File(effectiveDir, "settings.gradle").exists() ||
                File(effectiveDir, "settings.gradle.kts").exists() ||
                effectiveDir.walkTopDown().any { it.name == "settings.gradle" || it.name == "settings.gradle.kts" }

        if (!hasGradle || !hasSettings) {
            throw IllegalStateException("Project is missing settings.gradle / build.gradle, cannot build")
        }

        val excludedDirs = setOf(".gradle", "build", ".git", ".idea", ".kotlin", "captures", ".cxx", "apk")
        val allFiles = effectiveDir.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                val rel = file.relativeTo(effectiveDir).invariantSeparatorsPath
                val dirs = rel.split('/').dropLast(1)
                dirs.none { it in excludedDirs } &&
                    !rel.endsWith(".apk") && !rel.endsWith(".aab") && !rel.endsWith(".zip") &&
                    !rel.startsWith(".github/") &&
                    file.length() < 50L * 1024 * 1024 &&
                    rel != "local.properties" && rel != ".env"
            }.toList()

        val gradleKeyFileNames = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle-wrapper.properties")
        val gradleBuildFiles = allFiles
            .filter { it.name in gradleKeyFileNames }
            .map { it.relativeTo(effectiveDir).invariantSeparatorsPath }
        logBuilder.append("[Sync] Gradle build files: ${gradleBuildFiles.joinToString(", ")}\n")

        logBuilder.append("[Sync] Preparing to upload ${allFiles.size} project files in a single commit...\n")

        // 1. Get head commit sha
        val refResponse = gitHubService.getGitRef(authHeader, owner, repo, branch)
        val headSha = refResponse.obj.sha

        // 2. Create blobs for all project files
        val treeEntries = mutableListOf<TreeEntry>()
        for (file in allFiles) {
            val relPath = file.relativeTo(effectiveDir).invariantSeparatorsPath
            val fileBytes = file.readBytes()
            val base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

            var blobSha: String? = null
            var lastException: Exception? = null
            for (attempt in 1..3) {
                try {
                    val blobResp = gitHubService.createBlob(
                        authHeader, owner, repo,
                        CreateBlobRequest(content = base64Content, encoding = "base64")
                    )
                    blobSha = blobResp.sha
                    break
                } catch (e: Exception) {
                    lastException = e
                    delay(5000L * attempt)
                }
            }

            if (blobSha == null) {
                throw IllegalStateException("Failed to create blob for $relPath after 3 attempts: ${lastException?.message}")
            }

            val mode = if (relPath == "gradlew" || relPath.endsWith("/gradlew")) "100755" else "100644"
            treeEntries.add(TreeEntry(path = relPath, mode = mode, type = "blob", sha = blobSha))
        }

        // 3. Add workflow asset blob
        val workflowAssetContent = try {
            context.assets.open("workflows/android-build.yml").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
        if (workflowAssetContent.isNotBlank()) {
            val workflowBase64 = Base64.encodeToString(workflowAssetContent.toByteArray(), Base64.NO_WRAP)
            val workflowBlob = gitHubService.createBlob(
                authHeader, owner, repo,
                CreateBlobRequest(content = workflowBase64, encoding = "base64")
            )
            treeEntries.add(TreeEntry(path = ".github/workflows/android-build.yml", mode = "100644", type = "blob", sha = workflowBlob.sha))
        }

        // 4. Create Tree with parent commit base_tree to preserve unrelated repository files
        val baseTreeSha = try {
            val commitObj = gitHubService.getCommit(authHeader, owner, repo, headSha)
            commitObj.tree.sha
        } catch (e: Exception) {
            null
        }

        val treeResp = gitHubService.createTree(
            authHeader, owner, repo,
            CreateTreeRequest(tree = treeEntries, baseTree = baseTreeSha)
        )
        val newTreeSha = treeResp.sha

        // 5. Create Commit
        val commitResp = gitHubService.createCommit(
            authHeader, owner, repo,
            CreateCommitRequest(
                message = "Nexa IDE build sync: ${project.name}",
                tree = newTreeSha,
                parents = listOf(headSha)
            )
        )
        val newCommitSha = commitResp.sha

        // 6. Update Ref
        val updateRefResp = gitHubService.updateRef(
            authHeader, owner, repo,
            branch = branch,
            body = UpdateRefRequest(sha = newCommitSha, force = false)
        )

        if (!updateRefResp.isSuccessful) {
            val code = updateRefResp.code()
            if (code == 403 || code == 404) {
                throw IllegalStateException("GitHub API Error HTTP $code: Your token probably lacks the 'workflow' scope or write permissions to update reference.")
            } else {
                throw IllegalStateException("Failed to update branch reference (HTTP $code)")
            }
        }

        logBuilder.append("[Sync] Successfully committed ${treeEntries.size} files. Commit SHA: $newCommitSha\n")
    }

    fun cancelCurrentBuild() {
        val runInfo = currentActiveRunInfo
        currentBuildJob?.cancel()
        if (runInfo != null) {
            scope.launch {
                try {
                    val token = SecureTokenManager.getGitHubToken(context)
                    if (!token.isNullOrBlank()) {
                        gitHubService.cancelWorkflowRun(com.example.data.github.GitHubTokenSanitizer.bearer(com.example.data.github.GitHubTokenSanitizer.clean(token)), runInfo.first, runInfo.second, runInfo.third)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        _buildState.value = BuildProgressState.Idle
    }

    fun installApk(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "APK file not found: $filePath", Toast.LENGTH_SHORT).show()
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Allow Nexa IDE to install apps, then tap Install again", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:" + context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = android.content.ClipData.newRawUri("", apkUri)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareApk(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist: $filePath", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share APK via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
