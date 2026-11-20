package com.example

import com.example.data.ai.AiChatMessage
import com.example.data.ai.GeminiAiService
import com.example.data.ai.GeminiConfig
import com.example.data.ai.GeminiErrorKind
import com.example.data.ai.GeminiErrors
import com.example.data.github.*
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test

/** Pure-logic tests (no network, no secrets). Live API checks are in the final report. */
class NexaApiIntegrationTest {

    // ---------- GitHub token handling ----------
    @Test
    fun `token sanitizer strips newline, inner spaces, quotes and Bearer prefix`() {
        val raw = "  \"Bearer ghp_AAAA BBBB\n CCCC1234567890abcdef\"\r\n"
        assertEquals("ghp_AAAABBBBCCCC1234567890abcdef", GitHubTokenSanitizer.clean(raw))
    }

    @Test
    fun `placeholder is detected and never well formed`() {
        assertTrue(GitHubTokenSanitizer.isPlaceholder(GitHubTokenSanitizer.PLACEHOLDER))
        assertFalse(GitHubTokenSanitizer.isWellFormed(GitHubTokenSanitizer.PLACEHOLDER.replace("_", " ")))
    }

    @Test
    fun `describe never reveals the full token`() {
        val token = "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
        val shown = GitHubTokenSanitizer.describe(token)
        assertFalse(shown.contains(token))
        assertFalse(shown.contains("1234567890abcdef"))
        assertTrue(shown.endsWith("(40 chars)"))
    }

    // ---------- GitHub errors ----------
    @Test
    fun `401 explanation says authentication failed and includes GitHub message`() {
        val err = GitHubErrors.parse(401, """{"message":"Bad credentials","documentation_url":"https://docs.github.com/rest"}""", null)
        val text = GitHubErrors.explain(GitHubOperation.VERIFY_TOKEN, err)
        assertTrue(text.contains("GitHub authentication failed"))
        assertTrue(text.contains("invalid, expired, revoked, or incorrectly supplied"))
        assertTrue(text.contains("Bad credentials"))
    }

    @Test
    fun `422 validation details are surfaced`() {
        val body = """{"message":"Repository creation failed.","errors":[{"resource":"Repository","code":"custom","field":"name","message":"name already exists on this account"}]}"""
        val err = GitHubErrors.parse(422, body, null)
        assertEquals(1, err.details.size)
        val text = GitHubErrors.explain(GitHubOperation.CREATE_REPO, err)
        assertTrue(text.contains("name already exists on this account"))
        assertTrue(text.contains("HTTP 422"))
    }

    @Test
    fun `each handled status code gets its own explanation`() {
        val seen = listOf(400, 401, 403, 404, 409, 422).map {
            GitHubErrors.explain(GitHubOperation.CREATE_REPO, GitHubErrors.parse(it, """{"message":"m"}""", null)).lines().first()
        }
        assertEquals(seen.size, seen.toSet().size)
    }

    @Test
    fun `403 with zero remaining is reported as rate limit`() {
        val headers = Headers.headersOf("X-RateLimit-Remaining", "0")
        val err = GitHubErrors.parse(403, """{"message":"API rate limit exceeded"}""", headers)
        assertTrue(GitHubErrors.explain(GitHubOperation.LIST_REPOS, err).contains("rate limit"))
    }

    @Test
    fun `non json error body does not throw`() {
        val err = GitHubErrors.parse(500, "<html>oops</html>", null)
        assertEquals(500, err.httpCode)
        assertTrue(err.message.isNotBlank())
    }

    // ---------- Repo validation and scopes ----------
    @Test
    fun `repo name validation`() {
        assertTrue(GitHubRepoValidator.validateName("my-app_1.0").ok)
        assertFalse(GitHubRepoValidator.validateName("").ok)
        assertFalse(GitHubRepoValidator.validateName("has space").ok)
        assertFalse(GitHubRepoValidator.validateName("..").ok)
        assertFalse(GitHubRepoValidator.validateName("a".repeat(101)).ok)
    }

    @Test
    fun `scope based repo creation capability`() {
        assertEquals(RepoCreationCapability.ALLOWED, capabilityFromScopes(listOf("repo", "workflow")))
        assertEquals(RepoCreationCapability.PUBLIC_ONLY, capabilityFromScopes(listOf("public_repo")))
        assertEquals(RepoCreationCapability.MISSING_SCOPE, capabilityFromScopes(listOf("gist")))
        assertEquals(RepoCreationCapability.UNKNOWN, capabilityFromScopes(null))
    }

    // ---------- Gemini ----------
    @Test
    fun `model id is centrally configured`() {
        assertEquals("gemini-3.8-flash", GeminiConfig.DEFAULT_MODEL_ID)
        assertEquals("gemini-3.8-flash", GeminiConfig.normalizeModelId(" models/gemini-3.8-flash\n"))
        assertTrue(GeminiConfig.generateContentUrl("gemini-3.8-flash").endsWith("/models/gemini-3.8-flash:generateContent"))
        assertFalse(GeminiConfig.generateContentUrl("gemini-3.8-flash").contains("key="))
    }

    @Test
    fun `gemini http errors map to the right kind`() {
        val body = """{"error":{"code":404,"message":"model is unavailable","status":"NOT_FOUND"}}"""
        assertEquals(GeminiErrorKind.INVALID_REQUEST, GeminiErrors.fromHttp(400, body, "m").kind)
        assertEquals(GeminiErrorKind.AUTH, GeminiErrors.fromHttp(400, """{"error":{"message":"API key not valid."}}""", "m").kind)
        assertEquals(GeminiErrorKind.AUTH, GeminiErrors.fromHttp(401, body, "m").kind)
        assertEquals(GeminiErrorKind.AUTH, GeminiErrors.fromHttp(403, body, "m").kind)
        assertEquals(GeminiErrorKind.MODEL_UNAVAILABLE, GeminiErrors.fromHttp(404, body, "m").kind)
        assertEquals(GeminiErrorKind.RATE_LIMIT, GeminiErrors.fromHttp(429, body, "m").kind)
        assertEquals(GeminiErrorKind.SERVER, GeminiErrors.fromHttp(500, body, "m").kind)
        assertEquals(GeminiErrorKind.SERVER, GeminiErrors.fromHttp(503, body, "m").kind)
        assertTrue(GeminiErrors.fromHttp(404, body, "gemini-x").message!!.contains("gemini-x"))
    }

    @Test
    fun `api keys are redacted from error text`() {
        val key = "AIzaSyA1234567890abcdefghijklmnopqrstuv"
        val e = GeminiErrors.fromHttp(400, """{"error":{"message":"bad key $key"}}""", "m")
        assertFalse(e.message!!.contains(key))
    }

    @Test
    fun `history becomes a valid alternating conversation without duplicates`() {
        val history = listOf(
            AiChatMessage(isUser = false, text = "Hello! I am Nexa AI"),
            AiChatMessage(isUser = true, text = "What is a coroutine?"),
            AiChatMessage(isUser = false, text = "A lightweight thread."),
            AiChatMessage(isUser = true, text = "ব্যাখ্যা করো")
        )
        val contents = GeminiAiService.buildContents(history, "ব্যাখ্যা করো", "ব্যাখ্যা করো")
        assertEquals("user", contents.first().role)
        assertEquals(listOf("user", "model", "user"), contents.map { it.role })
        assertEquals("ব্যাখ্যা করো", contents.last().parts!!.single().text)
    }

    @Test
    fun `model name validation for settings`() {
        assertNull(GeminiConfig.validateModelId("gemini-3.8-flash"))
        assertNull(GeminiConfig.validateModelId("models/gemini-2.5-pro"))
        assertNotNull(GeminiConfig.validateModelId(""))
        assertNotNull(GeminiConfig.validateModelId("has space"))
        assertNotNull(GeminiConfig.validateModelId("bad/model"))
        assertNotNull(GeminiConfig.validateModelId("x".repeat(65)))
    }

    @Test
    fun `saved model list parsing ignores blanks and duplicates`() {
        assertEquals(listOf("a-1", "b-2"), GeminiConfig.parseList("a-1\n\nb-2\na-1\n"))
        assertTrue(GeminiConfig.parseList(null).isEmpty())
    }
}
