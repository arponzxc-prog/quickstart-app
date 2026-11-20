package com.example

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.DiffLineType
import com.example.data.ai.GeminiAiService
import com.example.data.filesystem.ProjectFileManager
import com.example.data.security.SecureTokenManager
import com.example.ui.editor.CodeSyntaxHighlighter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexaSecurityAndEditorTest {

    private lateinit var context: Context
    private lateinit var fileManager: ProjectFileManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = ProjectFileManager(context)
    }

    @Test
    fun `test zip slip prevention`() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // Create a malicious zip entry trying path traversal
            zos.putNextEntry(ZipEntry("../malicious.txt"))
            zos.write("malicious content".toByteArray())
            zos.closeEntry()
        }

        val inputStream = bos.toByteArray().inputStream()
        val imported = fileManager.importProjectFromZip(inputStream, "MaliciousApp")
        // Should be rejected or safely handled without escaping project root
        assertNull("Malicious zip with path traversal should fail import", imported)
    }

    @Test
    fun `test file save and read persistence`() {
        val project = fileManager.createProject(
            name = "TestApp",
            packageName = "com.test.app",
            templateType = "Kotlin Android Project",
            language = "Kotlin"
        )
        assertNotNull(project)

        val rootDir = File(project.rootPath)
        val testFile = File(rootDir, "test_code.kt")
        val content = "fun main() { println(\"Hello Nexa\") }"

        val success = fileManager.writeFileContent(testFile.absolutePath, content)
        assertTrue(success)

        val readContent = fileManager.readFileContent(testFile.absolutePath)
        assertEquals(content, readContent)

        fileManager.deleteFile(project.rootPath)
    }

    @Test
    fun `test secure token encryption and decryption`() {
        val rawToken = "ghp_SampleSecretGitHubToken1234567890"
        SecureTokenManager.saveGitHubToken(context, rawToken)

        assertTrue(SecureTokenManager.hasGitHubToken(context))
        val retrieved = SecureTokenManager.getGitHubToken(context)
        assertEquals(rawToken, retrieved)

        // Masking test: Never display raw token
        val masked = SecureTokenManager.maskToken(retrieved)
        assertTrue(masked.startsWith("ghp_••••••••"))
        assertFalse(masked.contains("SampleSecret"))

        // Secure wipe test
        SecureTokenManager.clearGitHubToken(context)
        assertFalse(SecureTokenManager.hasGitHubToken(context))
        assertNull(SecureTokenManager.getGitHubToken(context))
    }

    @Test
    fun `test legacy token migration`() {
        val legacy = "ghp_LegacyPlaintextRoomToken"
        val migrated = SecureTokenManager.migrateLegacyTokenIfNeeded(context, legacy)
        assertTrue(migrated)

        val retrieved = SecureTokenManager.getGitHubToken(context)
        assertEquals(legacy, retrieved)

        // Second call with placeholder should not re-migrate
        val secondMigrate = SecureTokenManager.migrateLegacyTokenIfNeeded(context, "SECURE_KEYSTORE_ENCRYPTED")
        assertFalse(secondMigrate)

        SecureTokenManager.clearGitHubToken(context)
    }

    @Test
    fun `test syntax highlighter produces styled annotations`() {
        val kotlinCode = "package com.example\nfun main() {\n    val greeting = \"Hello Nexa\"\n    println(greeting)\n}"
        val highlighter = CodeSyntaxHighlighter(extension = "kt", theme = "dark")
        val result = highlighter.filter(AnnotatedString(kotlinCode))

        assertNotNull(result)
        assertEquals(kotlinCode, result.text.text)
        assertTrue("Highlighter should apply span styles to keywords and strings", result.text.spanStyles.isNotEmpty())
    }

    @Test
    fun `test ai safe diff computation`() {
        val aiService = GeminiAiService(context)
        val original = "line 1\nline 2\nline 3"
        val proposed = "line 1\nline 2 modified\nline 3\nline 4 added"

        val diff = aiService.computeDiff(original, proposed)
        assertTrue(diff.isNotEmpty())
        assertTrue(diff.any { it.type == DiffLineType.ADDED })
    }
}
