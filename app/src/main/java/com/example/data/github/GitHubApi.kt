package com.example.data.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
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
    @Json(name = "html_url") val htmlUrl: String
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
@JsonClass(generateAdapter = true) data class CreateTreeRequest(val tree: List<TreeEntry>)
@JsonClass(generateAdapter = true) data class CreateCommitRequest(val message: String, val tree: String, val parents: List<String>)
@JsonClass(generateAdapter = true) data class UpdateRefRequest(val sha: String, val force: Boolean = false)

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
    val size: Long? = 0L
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

    @retrofit2.http.PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: CreateOrUpdateFilePayload
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun triggerWorkflowDispatch(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body payload: WorkflowDispatchPayload
    ): Response<Unit>

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

    @retrofit2.http.PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
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
    companion object {
        fun create(): GitHubService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(GitHubService::class.java)
        }
    }
}
