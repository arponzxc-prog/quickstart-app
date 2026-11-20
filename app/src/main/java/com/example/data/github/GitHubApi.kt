package com.example.data.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    @Json(name = "avatar_url") val avatarUrl: String?,
    val name: String?,
    val email: String?,
    @Json(name = "public_repos") val publicRepos: Int = 0
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    val description: String? = null,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "forks_count") val forksCount: Int = 0,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowDispatchPayload(
    val ref: String = "main",
    val inputs: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRun(
    val id: Long,
    val name: String?,
    @Json(name = "head_branch") val headBranch: String?,
    @Json(name = "head_sha") val headSha: String?,
    val status: String, // "queued", "in_progress", "completed"
    val conclusion: String?, // "success", "failure", "cancelled"
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    val event: String? = null
)

@JsonClass(generateAdapter = true) data class GitRefObject(val sha: String)
@JsonClass(generateAdapter = true) data class GitRefResponse(@Json(name = "object") val obj: GitRefObject)
@JsonClass(generateAdapter = true) data class CreateBlobRequest(val content: String, val encoding: String = "base64")
@JsonClass(generateAdapter = true) data class ShaResponse(val sha: String)
@JsonClass(generateAdapter = true) data class TreeEntry(val path: String, val mode: String, val type: String = "blob", val sha: String)
@JsonClass(generateAdapter = true) data class CreateTreeRequest(
    val tree: List<TreeEntry>,
    @Json(name = "base_tree") val baseTree: String? = null
)
@JsonClass(generateAdapter = true) data class GitTreeRef(val sha: String)
@JsonClass(generateAdapter = true) data class GitCommitResponse(val sha: String, val tree: GitTreeRef)
@JsonClass(generateAdapter = true) data class CreateCommitRequest(val message: String, val tree: String, val parents: List<String>)
@JsonClass(generateAdapter = true) data class UpdateRefRequest(val sha: String, val force: Boolean = false)

// Phase 2: Branch Management Data Classes
@JsonClass(generateAdapter = true)
data class GitHubBranchCommit(val sha: String, val url: String? = null)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    val name: String,
    val commit: GitHubBranchCommit? = null,
    val protected: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CreateGitRefRequest(
    val ref: String, // e.g. "refs/heads/feature-name"
    val sha: String
)

@JsonClass(generateAdapter = true)
data class MergeBranchRequest(
    val base: String,
    val head: String,
    @Json(name = "commit_message") val commitMessage: String? = null
)

// Phase 2: Commits & Changes History Data Classes
@JsonClass(generateAdapter = true)
data class GitCommitAuthor(val name: String?, val email: String?, val date: String?)

@JsonClass(generateAdapter = true)
data class GitCommitDetails(val message: String, val author: GitCommitAuthor?)

@JsonClass(generateAdapter = true)
data class GitHubCommitItem(
    val sha: String,
    val commit: GitCommitDetails,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubFileChange(
    val filename: String,
    val status: String = "modified",
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetail(
    val sha: String,
    val commit: GitCommitDetails,
    val files: List<GitHubFileChange>? = null
)

// Phase 2: Pull Requests Data Classes
@JsonClass(generateAdapter = true)
data class PullRequestBranch(val label: String? = null, val ref: String? = null, val sha: String? = null)

@JsonClass(generateAdapter = true)
data class GitHubPullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String = "open", // open, closed
    @Json(name = "html_url") val htmlUrl: String,
    val body: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val user: GitHubUser? = null,
    val head: PullRequestBranch? = null,
    val base: PullRequestBranch? = null,
    val mergeable: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CreatePullRequestPayload(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null
)

// Phase 2: Workflows Data Classes
@JsonClass(generateAdapter = true)
data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    val workflows: List<GitHubWorkflow> = emptyList()
)

// Phase 2: Remote File Explorer & Deletion Data Classes
@JsonClass(generateAdapter = true)
data class GitHubContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0L,
    val type: String = "file", // "file" or "dir"
    @Json(name = "download_url") val downloadUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteFilePayload(
    val message: String,
    val sha: String,
    val branch: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRun>
)

@JsonClass(generateAdapter = true)
data class GitHubArtifact(
    val id: Long,
    val name: String,
    @Json(name = "size_in_bytes") val sizeInBytes: Long,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class GitHubArtifactsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val artifacts: List<GitHubArtifact>
)

@JsonClass(generateAdapter = true)
data class GitHubJobStep(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int = 0
)

@JsonClass(generateAdapter = true)
data class GitHubJob(
    val id: Long,
    @Json(name = "run_id") val runId: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val steps: List<GitHubJobStep>? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubJobsResponse(
    @Json(name = "total_count") val totalCount: Int = 0,
    val jobs: List<GitHubJob> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String? = "Android Project built with Nexa IDE",
    val private: Boolean = false,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitHubContentFile(
    val name: String? = null,
    val path: String? = null,
    val sha: String? = null,
    val size: Long? = 0L,
    val content: String? = null, // base64 encoded when fetching single file
    val encoding: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateOrUpdateFilePayload(
    val message: String,
    val content: String, // base64 encoded string
    val branch: String? = null,
    val sha: String? = null
)

interface GitHubService {
    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") auth: String
    ): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepositories(
        @Header("Authorization") auth: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
        @Query("type") type: String = "all",
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member"
    ): List<GitHubRepo>

    /** Same as [getAuthenticatedUser] but exposes status code, error body and X-OAuth-Scopes. */
    @GET("user")
    suspend fun getAuthenticatedUserResponse(
        @Header("Authorization") auth: String
    ): Response<GitHubUser>

    /** Same as [createRepository] but exposes status code and GitHub's error body. */
    @POST("user/repos")
    suspend fun createRepositoryResponse(
        @Header("Authorization") auth: String,
        @Body request: CreateRepoRequest
    ): Response<GitHubRepo>

    @POST("user/repos")
    suspend fun createRepository(
        @Header("Authorization") auth: String,
        @Body request: CreateRepoRequest
    ): GitHubRepo

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<GitHubContentFile>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getDirectoryContents(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): List<GitHubContentItem>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: CreateOrUpdateFilePayload
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: DeleteFilePayload
    ): Response<ResponseBody>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun triggerWorkflowDispatch(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body payload: WorkflowDispatchPayload
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun listWorkflows(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubWorkflowsResponse

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listWorkflowRuns(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): GitHubWorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun listRunArtifacts(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): GitHubArtifactsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun listRunJobs(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): GitHubJobsResponse

    @Streaming
    @GET("repos/{owner}/{repo}/actions/artifacts/{artifact_id}/zip")
    suspend fun downloadArtifactZip(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifact_id") artifactId: Long
    ): ResponseBody

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getGitRef(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "branch", encoded = true) branch: String
    ): GitRefResponse

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createGitRef(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateGitRefRequest
    ): Response<GitRefResponse>

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateBlobRequest
    ): ShaResponse

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateTreeRequest
    ): ShaResponse

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateCommitRequest
    ): ShaResponse

    @GET("repos/{owner}/{repo}/git/commits/{commit_sha}")
    suspend fun getCommit(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("commit_sha") commitSha: String
    ): GitCommitResponse

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "branch", encoded = true) branch: String,
        @Body body: UpdateRefRequest
    ): Response<ResponseBody>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/jobs/{job_id}/logs")
    suspend fun downloadJobLogs(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("job_id") jobId: Long
    ): ResponseBody

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    // Phase 2: Branch Management Endpoints
    @GET("repos/{owner}/{repo}/branches")
    suspend fun listBranches(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubBranch>

    @POST("repos/{owner}/{repo}/merges")
    suspend fun mergeBranch(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: MergeBranchRequest
    ): Response<ResponseBody>

    // Phase 2: Commits History Endpoints
    @GET("repos/{owner}/{repo}/commits")
    suspend fun listCommits(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") branch: String? = null,
        @Query("per_page") perPage: Int = 30
    ): List<GitHubCommitItem>

    @GET("repos/{owner}/{repo}/commits/{commit_sha}")
    suspend fun getCommitDetail(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("commit_sha") commitSha: String
    ): GitHubCommitDetail

    // Phase 2: Pull Requests Endpoints
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun listPullRequests(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "all",
        @Query("per_page") perPage: Int = 30
    ): List<GitHubPullRequest>

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body payload: CreatePullRequestPayload
    ): GitHubPullRequest

    companion object {
        /**
         * Adds the GitHub-recommended headers to every request and normalises the Authorization
         * header: whitespace/newlines/"Bearer " duplicates are stripped, and the Room placeholder
         * is refused locally instead of being sent to GitHub as if it were a token (which can
         * only ever produce HTTP 401).
         */
        private val headerInterceptor = Interceptor { chain ->
            val orig = chain.request()
            val builder = orig.newBuilder()
                .header("Accept", GitHubConfig.ACCEPT)
                .header("X-GitHub-Api-Version", GitHubConfig.API_VERSION)
                .header("User-Agent", GitHubConfig.USER_AGENT)
            val rawAuth = orig.header("Authorization")
            if (rawAuth != null) {
                val token = GitHubTokenSanitizer.clean(rawAuth)
                if (token.isEmpty() || GitHubTokenSanitizer.isPlaceholder(token)) {
                    throw IOException(
                        "No readable GitHub token in secure storage. Use Change Token to enter it again."
                    )
                }
                builder.header("Authorization", GitHubTokenSanitizer.bearer(token))
            }
            chain.proceed(builder.build())
        }

        fun create(): GitHubService {
            // Never log sensitive Authorization tokens in release or debug logs
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(GitHubConfig.BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(GitHubService::class.java)
        }
    }
}
