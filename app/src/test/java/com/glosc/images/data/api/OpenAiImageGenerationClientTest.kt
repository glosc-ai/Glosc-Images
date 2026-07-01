package com.glosc.images.data.api

import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.GenerateImageRequest
import com.glosc.images.domain.model.ProviderType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Base64

class OpenAiImageGenerationClientTest {
    @Test
    fun generateDownloadsUrlImagesWhenBase64IsMissing() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
            val imageUrl = server.url("/assets/generated.png").toString()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"data":[{"url":"$imageUrl"}]}""")
                    .setHeader("Content-Type", "application/json")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(imageBytes))
                    .setHeader("Content-Type", "image/png")
            )

            val result = OpenAiImageGenerationClient().generate(
                provider = ApiProvider(
                    id = "test",
                    name = "Test",
                    baseUrl = server.url("/").toString(),
                    apiKeyAlias = "test",
                    providerType = ProviderType.OpenAi,
                    defaultModel = "image-model",
                    imageModels = listOf("image-model"),
                    enabled = true,
                    lastTestedAt = null,
                    lastStatus = null
                ),
                apiKey = "test-key",
                request = GenerateImageRequest(prompt = "prompt", model = "image-model")
            )

            val decoded = Base64.getDecoder().decode(result.base64Images.single())
            assertArrayEquals(imageBytes, decoded)
            assertEquals("/v1/images/generations", server.takeRequest().path)
            assertEquals("/assets/generated.png", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun generateIncludesProviderErrorMessageWhenRequestFails() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"error":{"message":"url error, please check url"}}""")
                    .setHeader("Content-Type", "application/json")
            )

            try {
                OpenAiImageGenerationClient().generate(
                    provider = ApiProvider(
                        id = "test",
                        name = "Test",
                        baseUrl = server.url("/").toString(),
                        apiKeyAlias = "test",
                        providerType = ProviderType.OpenAi,
                        defaultModel = "image-model",
                        imageModels = listOf("image-model"),
                        enabled = true,
                        lastTestedAt = null,
                        lastStatus = null
                    ),
                    apiKey = "test-key",
                    request = GenerateImageRequest(prompt = "prompt", model = "image-model")
                )
                fail("Expected request to fail")
            } catch (error: Exception) {
                assertTrue(error.message.orEmpty().contains("HTTP 400"))
                assertTrue(error.message.orEmpty().contains("url error, please check url"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun imageToImageUsesEditsEndpointWithMultipartImages() = runBlocking {
        val server = MockWebServer()
        val sourceFile = File.createTempFile("source", ".png").apply {
            writeBytes(byteArrayOf(9, 8, 7, 6))
            deleteOnExit()
        }
        server.start()
        try {
            val imageBytes = byteArrayOf(6, 7, 8, 9)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(imageBytes)}"}]}""")
                    .setHeader("Content-Type", "application/json")
            )

            val result = OpenAiImageGenerationClient().generate(
                provider = ApiProvider(
                    id = "test",
                    name = "Test",
                    baseUrl = server.url("/").toString(),
                    apiKeyAlias = "test",
                    providerType = ProviderType.OpenAi,
                    defaultModel = "image-model",
                    imageModels = listOf("image-model"),
                    enabled = true,
                    lastTestedAt = null,
                    lastStatus = null
                ),
                apiKey = "test-key",
                request = GenerateImageRequest(
                    prompt = "make it cinematic",
                    model = "image-model",
                    sourceImagePaths = listOf(sourceFile.absolutePath)
                )
            )

            assertArrayEquals(imageBytes, Base64.getDecoder().decode(result.base64Images.single()))
            val request = server.takeRequest()
            assertEquals("/v1/images/edits", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"image\""))
            assertTrue(body.contains("filename=\"${sourceFile.name}\""))
            assertTrue(body.contains("make it cinematic"))
        } finally {
            server.shutdown()
            sourceFile.delete()
        }
    }
}
