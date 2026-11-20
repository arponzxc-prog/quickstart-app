package com.example.data.github

import com.squareup.moshi.Moshi
import okhttp3.Headers
import retrofit2.HttpException
import java.io.IOException

/** Central GitHub API constants. */
object GitHubConfig {
    const val BASE_URL = "https://api.github.com/"
    const val API_VERSION = "2022-11-28"
    const val ACCEPT = "application/vnd.github+json"
    const val USER_AGENT = "NexaIDE-Android"
}

/**
 * Cleans and inspects GitHub tokens. Never returns or logs more than a masked form of a token.
 */
object GitHubTokenSanitizer {
    /** Value stored in Room instead of the real token. It must never be sent to GitHub. */
    const val PLACEHOLDER = "SECURE_KEYSTORE_ENCRYPTED"

    private val prefixRegex = Regex("^(?i)(bearer|token)\\s+")
    private val invisible = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')
    private val wellFormed = Regex("^[A-Za-z0-9_\\-.]{20,255}$")

    /**
     * Removes surrounding quotes, an accidental "Bearer "/"token " prefix and ALL whitespace,
     * control and zero-width characters (pasted tokens often carry a trailing newline or a
     * space in the middle).
     */
    fun clean(raw: String?): String {
        if (raw == null) return ""
        var t = raw.trim().trim('"', '\'', '`')
        t = prefixRegex.replace(t.trim(), "")
        return t.filter { !it.isWhitespace() && !it.isISOControl() && it !in invisible }
    }

    fun isPlaceholder(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return token.startsWith("SECURE_") || token.contains("••••")
    }

    /** True when the cleaned token has a plausible GitHub token shape. */
    fun isWellFormed(cleaned: String): Boolean = wellFormed.matches(cleaned)

    /** Safe description for diagnostics: prefix, length and last 4 chars only. */
    fun describe(token: String?): String {
        val t = clean(token)
        if (t.isEmpty()) return "<empty>"
        if (t.length <= 8) return "<${t.length} chars>"
        val prefix = when {
            t.startsWith("github_pat_") -> "github_pat_"
            t.length > 4 && t[3] == '_' -> t.take(4)
            else -> ""
        }
        return "$prefix…${t.takeLast(4)} (${t.length} chars)"
    }

    fun bearer(cleanedToken: String): String = "Bearer $cleanedToken"
}

/** Parsed GitHub error response. */
data class GitHubApiError(
    val httpCode: Int,
    val message: String,
    val details: List<String> = emptyList(),
    val documentationUrl: String? = null,
    val oauthScopes: String? = null,
    val acceptedScopes: String? = null,
    val rateLimitRemaining: String? = null
)

enum class GitHubOperation { VERIFY_TOKEN, LIST_REPOS, CREATE_REPO }

object GitHubErrors {
    private val moshi = Moshi.Builder().build()
    private val anyAdapter = moshi.adapter(Any::class.java)

    /** Parses GitHub's `{message, errors[], documentation_url}` JSON. Never throws. */
    fun parse(httpCode: Int, rawBody: String?, headers: Headers?): GitHubApiError {
        var message: String? = null
        var doc: String? = null
        val details = mutableListOf<String>()
        if (!rawBody.isNullOrBlank()) {
            try {
                val root = anyAdapter.fromJson(rawBody) as? Map<*, *>
                message = root?.get("message") as? String
                doc = root?.get("documentation_url") as? String
                (root?.get("errors") as? List<*>)?.forEach { e ->
                    when (e) {
                        is String -> details.add(e)
                        is Map<*, *> -> {
                            val field = listOfNotNull(e["resource"] as? String, e["field"] as? String)
                                .joinToString(".")
                            val code = e["code"] as? String
                            val msg = e["message"] as? String
                            val line = buildString {
                                if (field.isNotBlank()) append(field)
                                if (!code.isNullOrBlank()) { if (isNotEmpty()) append(": "); append(code) }
                                if (!msg.isNullOrBlank()) { if (isNotEmpty()) append(" - "); append(msg) }
                            }
                            if (line.isNotBlank()) details.add(line)
                        }
                    }
                }
            } catch (_: Exception) {
                message = rawBody.take(300)
            }
        }
        return GitHubApiError(
            httpCode = httpCode,
            message = message?.takeIf { it.isNotBlank() } ?: "No error message returned by GitHub",
            details = details,
            documentationUrl = doc,
            oauthScopes = headers?.get("X-OAuth-Scopes"),
            acceptedScopes = headers?.get("X-Accepted-OAuth-Scopes"),
            rateLimitRemaining = headers?.get("X-RateLimit-Remaining")
        )
    }

    fun fromHttpException(e: HttpException): GitHubApiError {
        val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        return parse(e.code(), body, e.response()?.headers())
    }

    /** Human-readable explanation for one failure, always including GitHub's own message. */
    fun explain(op: GitHubOperation, err: GitHubApiError): String {
        val actual = buildString {
            append("GitHub said: \"").append(err.message).append("\"")
            if (err.details.isNotEmpty()) {
                append("\nDetails:")
                err.details.forEach { append("\n • ").append(it) }
            }
        }
        val headline = when (err.httpCode) {
            400 -> "Bad request (HTTP 400). GitHub could not parse or accept the request body."
            401 -> "GitHub authentication failed (HTTP 401). The token may be invalid, expired, " +
                "revoked, or incorrectly supplied."
            403 -> {
                val m = err.message.lowercase()
                when {
                    err.rateLimitRemaining == "0" || m.contains("rate limit") ->
                        "GitHub rate limit reached (HTTP 403). Wait a while and try again."
                    m.contains("saml") || m.contains("sso") ->
                        "The token must be authorized for this organization's SAML SSO (HTTP 403)."
                    else -> "The token is accepted but is not permitted to do this (HTTP 403). " +
                        "Classic tokens need the 'repo' scope (or 'public_repo' for public repos); " +
                        "fine-grained tokens need 'Administration: write' for repository creation."
                }
            }
            404 -> when (op) {
                GitHubOperation.CREATE_REPO ->
                    "Not found (HTTP 404). GitHub also answers 404 when a token lacks the permission " +
                        "to create repositories on this account."
                else -> "Resource not found (HTTP 404)."
            }
            409 -> "Conflict (HTTP 409). The repository or Git data is in a conflicting state."
            422 -> "Validation failed (HTTP 422). GitHub rejected the repository settings " +
                "(for example the name already exists or is not allowed)."
            in 500..599 -> "GitHub server error (HTTP ${err.httpCode}). Try again shortly."
            else -> "GitHub returned HTTP ${err.httpCode}."
        }
        return "$headline\n$actual"
    }
}

/** What the token is known to be able to do for repository creation. */
enum class RepoCreationCapability {
    /** Classic token with `repo`. */
    ALLOWED,
    /** Classic token with only `public_repo`. */
    PUBLIC_ONLY,
    /** Classic token without `repo` or `public_repo`. */
    MISSING_SCOPE,
    /** Fine-grained token: GitHub does not report scopes, only a real attempt can tell. */
    UNKNOWN
}

sealed class GitHubAuthCheck {
    data class Authenticated(
        val user: GitHubUser,
        val scopes: List<String>?,
        val repoCreation: RepoCreationCapability
    ) : GitHubAuthCheck()

    data class Failed(val error: GitHubApiError) : GitHubAuthCheck()
    data class NetworkError(val message: String) : GitHubAuthCheck()
    data class MalformedToken(val reason: String) : GitHubAuthCheck()
    object NoToken : GitHubAuthCheck()
}

sealed class GitHubCreateRepoResult {
    data class Success(val repo: GitHubRepo) : GitHubCreateRepoResult()
    data class Failure(val error: GitHubApiError, val explanation: String) : GitHubCreateRepoResult()
    data class NetworkError(val message: String) : GitHubCreateRepoResult()
    data class InvalidInput(val message: String) : GitHubCreateRepoResult()
}

object GitHubRepoValidator {
    data class NameResult(val name: String, val error: String?) { val ok get() = error == null }

    private val allowed = Regex("^[A-Za-z0-9._-]+$")

    fun validateName(raw: String): NameResult {
        val n = raw.trim()
        val err = when {
            n.isEmpty() -> "Repository name is required."
            n.length > 100 -> "Repository name must be 100 characters or fewer."
            n == "." || n == ".." -> "Repository name cannot be '.' or '..'."
            !allowed.matches(n) ->
                "Repository name may only contain letters, numbers, '.', '-' and '_'."
            else -> null
        }
        return NameResult(n, err)
    }

    fun validateDescription(raw: String?): String? =
        if (raw != null && raw.length > 350) "Description must be 350 characters or fewer." else null
}

fun capabilityFromScopes(scopes: List<String>?): RepoCreationCapability = when {
    scopes == null -> RepoCreationCapability.UNKNOWN
    "repo" in scopes -> RepoCreationCapability.ALLOWED
    "public_repo" in scopes -> RepoCreationCapability.PUBLIC_ONLY
    else -> RepoCreationCapability.MISSING_SCOPE
}

/**
 * Talks to the real GitHub API. Authentication is verified with GET /user; repository-creation
 * permission is reported separately (scopes) and finally proven only by POST /user/repos itself.
 */
class GitHubAuthManager(private val service: GitHubService) {

    suspend fun verifyToken(rawToken: String?): GitHubAuthCheck {
        val token = GitHubTokenSanitizer.clean(rawToken)
        if (token.isEmpty()) return GitHubAuthCheck.NoToken
        if (GitHubTokenSanitizer.isPlaceholder(token)) {
            return GitHubAuthCheck.MalformedToken(
                "The stored token could not be read from secure storage. Please enter it again."
            )
        }
        return try {
            val resp = service.getAuthenticatedUserResponse(GitHubTokenSanitizer.bearer(token))
            val user = resp.body()
            if (resp.isSuccessful && user != null) {
                val scopeHeader = resp.headers()["X-OAuth-Scopes"]
                val scopes = scopeHeader?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                GitHubAuthCheck.Authenticated(user, scopes, capabilityFromScopes(scopes))
            } else {
                val body = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                GitHubAuthCheck.Failed(GitHubErrors.parse(resp.code(), body, resp.headers()))
            }
        } catch (e: IOException) {
            GitHubAuthCheck.NetworkError(e.message ?: "Network error")
        }
    }

    suspend fun createRepository(
        rawToken: String?,
        rawName: String,
        description: String?,
        isPrivate: Boolean
    ): GitHubCreateRepoResult {
        val name = GitHubRepoValidator.validateName(rawName)
        if (!name.ok) return GitHubCreateRepoResult.InvalidInput(name.error!!)
        GitHubRepoValidator.validateDescription(description)?.let {
            return GitHubCreateRepoResult.InvalidInput(it)
        }
        val token = GitHubTokenSanitizer.clean(rawToken)
        if (token.isEmpty() || GitHubTokenSanitizer.isPlaceholder(token)) {
            return GitHubCreateRepoResult.InvalidInput(
                "No readable GitHub token. Use Change Token to enter it again."
            )
        }
        return try {
            val request = if (description == null) {
                CreateRepoRequest(name = name.name, private = isPrivate)
            } else {
                CreateRepoRequest(name = name.name, description = description, private = isPrivate)
            }
            val resp = service.createRepositoryResponse(GitHubTokenSanitizer.bearer(token), request)
            val repo = resp.body()
            if (resp.isSuccessful && repo != null) {
                GitHubCreateRepoResult.Success(repo)
            } else {
                val body = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                val err = GitHubErrors.parse(resp.code(), body, resp.headers())
                GitHubCreateRepoResult.Failure(err, GitHubErrors.explain(GitHubOperation.CREATE_REPO, err))
            }
        } catch (e: IOException) {
            GitHubCreateRepoResult.NetworkError(e.message ?: "Network error")
        }
    }
}
