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
import org.junit.Test
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
}
