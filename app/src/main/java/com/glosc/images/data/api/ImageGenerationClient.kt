package com.glosc.images.data.api

import com.glosc.images.core.common.AppException
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.GenerateImageRequest
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

data class GenerateImageResult(
    val base64Images: List<String>,
    val model: String,
    val providerRawResponse: String = ""
)

data class ProviderTestResult(
    val latencyMs: Long,
    val modelCount: Int,
    val imageModels: List<String>
)

interface ImageGenerationClient {
    suspend fun generate(
        provider: ApiProvider,
        apiKey: String,
        request: GenerateImageRequest
    ): GenerateImageResult

    suspend fun test(provider: ApiProvider, apiKey: String): ProviderTestResult
}

class OpenAiImageGenerationClient : ImageGenerationClient {
    override suspend fun generate(
        provider: ApiProvider,
        apiKey: String,
        request: GenerateImageRequest
    ): GenerateImageResult {
        val response = api(provider, apiKey).generateImage(
            OpenAiImageGenerationRequest(
                model = request.model.ifBlank { provider.defaultModel },
                prompt = buildPrompt(request.prompt, request.negativePrompt),
                size = request.size,
                quality = request.quality,
                count = request.count.coerceIn(1, 4)
            )
        )

        if (!response.isSuccessful) {
            throw AppException("图片生成失败：HTTP ${response.code()} ${response.message()}")
        }

        val images = response.body()?.data.orEmpty().mapNotNull { it.b64Json }
        if (images.isEmpty()) {
            throw AppException("图片生成成功但响应中没有 base64 图片数据")
        }
        return GenerateImageResult(images, request.model.ifBlank { provider.defaultModel })
    }

    override suspend fun test(provider: ApiProvider, apiKey: String): ProviderTestResult {
        var count = 0
        var imageModels = emptyList<String>()
        val elapsed = measureTimeMillis {
            val response = api(provider, apiKey).listModels()
            if (!response.isSuccessful) {
                throw AppException("获取模型列表失败：HTTP ${response.code()} ${response.message()}")
            }
            val models = response.body()?.data.orEmpty()
            count = models.size
            imageModels = models
                .filter { it.tags.containsImageTag() }
                .mapNotNull { it.id }
                .distinct()
        }
        return ProviderTestResult(elapsed, count, imageModels)
    }

    private fun api(provider: ApiProvider, apiKey: String): OpenAiApi {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logger)
            .build()

        return Retrofit.Builder()
            .baseUrl(provider.baseUrl.normalizedBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }

    private fun buildPrompt(prompt: String, negativePrompt: String): String =
        if (negativePrompt.isBlank()) prompt else "$prompt\n\nAvoid: $negativePrompt"

    private fun String.normalizedBaseUrl(): String {
        val trimmed = trim().ifBlank { "https://one.gloscai.com/" }
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val pathStart = withSlash.indexOf('/', startIndex = withSlash.indexOf("://") + 3)
        val path = if (pathStart == -1) "/" else withSlash.substring(pathStart)
        return if (path == "/") "${withSlash}v1/" else withSlash
    }

    private fun JsonElement?.containsImageTag(): Boolean {
        val values = mutableListOf<String>()
        fun collect(element: JsonElement?) {
            when {
                element == null || element.isJsonNull -> Unit
                element.isJsonPrimitive -> values += element.asString
                element.isJsonArray -> element.asJsonArray.forEach { collect(it) }
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                    values += key
                    collect(value)
                }
            }
        }
        collect(this)
        return values.any { it.contains("image", ignoreCase = true) }
    }
}
