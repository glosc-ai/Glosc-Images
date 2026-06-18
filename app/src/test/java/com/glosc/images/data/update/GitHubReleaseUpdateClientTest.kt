package com.glosc.images.data.update

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GitHubReleaseUpdateClientTest {
    @Test
    fun checkLatestSelectsApkAssetAndDownloadsIt() {
        val server = MockWebServer()
        val updatesDir = Files.createTempDirectory("glosc-updates-test").toFile()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "tag_name": "v0.2.0",
                          "name": "Version 0.2.0",
                          "body": "Release notes",
                          "html_url": "https://github.com/glosc-ai/Glosc-Images/releases/tag/v0.2.0",
                          "assets": [
                            {
                              "name": "notes.txt",
                              "size": 12,
                              "browser_download_url": "${server.url("/downloads/notes.txt")}"
                            },
                            {
                              "name": "Glosc-Images-release.apk",
                              "size": 4,
                              "browser_download_url": "${server.url("/downloads/app.apk")}"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(byteArrayOf(1, 2, 3, 4)))
                    .setHeader("Content-Type", "application/vnd.android.package-archive")
            )

            val client = GitHubReleaseUpdateClient(
                updatesDir = updatesDir,
                baseUrl = server.url("/").toString()
            )
            val update = client.checkLatest(currentVersionName = "0.1.0")
            val apk = client.downloadApk(update)

            assertTrue(update.updateAvailable)
            assertEquals("v0.2.0", update.tagName)
            assertEquals("Glosc-Images-release.apk", update.apkAssetName)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), apk.readBytes())
            assertEquals("/repos/glosc-ai/Glosc-Images/releases/latest", server.takeRequest().path)
            assertEquals("/downloads/app.apk", server.takeRequest().path)
        } finally {
            server.shutdown()
            updatesDir.deleteRecursively()
        }
    }

    @Test
    fun compareVersionsHandlesCommonTagFormats() {
        assertTrue(GitHubReleaseUpdateClient.compareVersions("v1.2.0", "1.1.9") > 0)
        assertEquals(0, GitHubReleaseUpdateClient.compareVersions("v1.0.0", "1.0"))
        assertTrue(GitHubReleaseUpdateClient.compareVersions("1.0.1", "1.0.10") < 0)
    }
}
