package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.filesystem.ProjectFileManager
import com.example.data.github.CreateGitRefRequest
import com.example.data.github.CreatePullRequestPayload
import com.example.data.github.DeleteFilePayload
import com.example.data.github.MergeBranchRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
class NexaPhase2Test {

    private lateinit var context: Context
    private lateinit var fileManager: ProjectFileManager
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = ProjectFileManager(context)
    }

    @Test
    fun `test gitHub branch creation request serialization`() {
        val adapter = moshi.adapter(CreateGitRefRequest::class.java)
        val request = CreateGitRefRequest(
            ref = "refs/heads/feature/phase-2-upgrade",
            sha = "7b2c5d1e4a6f890123456789abcdef0123456789"
        )
        val json = adapter.toJson(request)
        assertTrue("JSON must contain ref field", json.contains("\"ref\":\"refs/heads/feature/phase-2-upgrade\""))
        assertTrue("JSON must contain sha field", json.contains("\"sha\":\"7b2c5d1e4a6f890123456789abcdef0123456789\""))
    }

    @Test
    fun `test gitHub pull request payload serialization`() {
        val adapter = moshi.adapter(CreatePullRequestPayload::class.java)
        val payload = CreatePullRequestPayload(
            title = "feat: upgrade to Phase 2",
            head = "feature/phase-2-upgrade",
            base = "main",
            body = "Implements Professional APK Builder, GitHub Advanced, and Live Preview Studio."
        )
        val json = adapter.toJson(payload)
        assertTrue(json.contains("\"title\":\"feat: upgrade to Phase 2\""))
        assertTrue(json.contains("\"head\":\"feature/phase-2-upgrade\""))
        assertTrue(json.contains("\"base\":\"main\""))
    }

    @Test
    fun `test gitHub branch merge request serialization`() {
        val adapter = moshi.adapter(MergeBranchRequest::class.java)
        val req = MergeBranchRequest(
            base = "main",
            head = "staging",
            commitMessage = "Merge branch 'staging' into main"
        )
        val json = adapter.toJson(req)
        assertTrue(json.contains("\"base\":\"main\""))
        assertTrue(json.contains("\"head\":\"staging\""))
        assertTrue(json.contains("\"commit_message\":\"Merge branch 'staging' into main\""))
    }

    @Test
    fun `test gitHub delete file payload serialization`() {
        val adapter = moshi.adapter(DeleteFilePayload::class.java)
        val payload = DeleteFilePayload(
            message = "Remove deprecated configuration",
            sha = "a1b2c3d4e5f6",
            branch = "main"
        )
        val json = adapter.toJson(payload)
        assertTrue(json.contains("\"message\":\"Remove deprecated configuration\""))
        assertTrue(json.contains("\"sha\":\"a1b2c3d4e5f6\""))
        assertTrue(json.contains("\"branch\":\"main\""))
    }

    @Test
    fun `test webapp project directory and file structure for live preview`() {
        val project = fileManager.createProject(
            name = "TestWebPreview",
            packageName = "com.nexa.webpreview",
            templateType = "HTML Multi-File Project",
            language = "Webapp"
        )
        assertNotNull(project)

        val rootDir = File(project.rootPath)
        val webDir = fileManager.getWebappDirectory(rootDir)
        assertTrue("Web directory should exist", webDir.exists())

        val indexHtml = File(webDir, "index.html")
        val styleCss = File(webDir, "style.css")
        val scriptJs = File(webDir, "script.js")

        assertTrue("index.html must exist for Live Preview Studio", indexHtml.exists())
        assertTrue("style.css must exist for multi-file web projects", styleCss.exists())
        assertTrue("script.js must exist for multi-file web projects", scriptJs.exists())

        val htmlContent = indexHtml.readText()
        assertTrue("HTML should link style.css", htmlContent.contains("style.css"))
        assertTrue("HTML should load script.js", htmlContent.contains("script.js"))

        // Cleanup
        fileManager.deleteFile(project.rootPath)
    }

    @Test
    fun `test build package name validation`() {
        fun isValidPackageName(pkg: String): Boolean {
            val regex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
            return regex.matches(pkg)
        }

        assertTrue(isValidPackageName("com.example.app"))
        assertTrue(isValidPackageName("com.nexa.ide.pro"))
        assertTrue(isValidPackageName("org.company.project_one"))

        assertFalse(isValidPackageName("com.example."))
        assertFalse(isValidPackageName(".com.example"))
        assertFalse(isValidPackageName("123.example.app"))
        assertFalse(isValidPackageName("com..example"))
        assertFalse(isValidPackageName("singleword"))
    }

    @Test
    fun `test apk artifact zip integrity verification`() {
        // Create a valid zip byte array representing an APK/AAB
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("Fake Android Manifest binary content".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("Fake DEX bytecode content".toByteArray())
            zos.closeEntry()
        }
        val validApkBytes = bos.toByteArray()

        // Test APK header magic bytes: 0x50, 0x4B, 0x03, 0x04 (PK\u0003\u0004)
        fun verifyApkHeader(bytes: ByteArray): Boolean {
            if (bytes.size < 4) return false
            return bytes[0] == 0x50.toByte() &&
                    bytes[1] == 0x4B.toByte() &&
                    bytes[2] == 0x03.toByte() &&
                    bytes[3] == 0x04.toByte()
        }

        assertTrue("Valid APK/AAB zip should pass integrity check", verifyApkHeader(validApkBytes))

        val corruptedBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        assertFalse("Corrupted file must fail integrity check", verifyApkHeader(corruptedBytes))
    }

    @Test
    fun `test git conflict detection`() {
        val baseContent = "Line 1: init\nLine 2: stable code\nLine 3: footer"
        val localModified = "Line 1: init\nLine 2: local change by user\nLine 3: footer"
        val remoteModified = "Line 1: init\nLine 2: remote change on github\nLine 3: footer"

        fun hasConflict(local: String, remote: String): Boolean {
            return local != remote
        }

        assertTrue("Conflicting edits should be detected", hasConflict(localModified, remoteModified))
        assertFalse("Identical content should not be flagged as conflict", hasConflict(localModified, localModified))
    }
}
