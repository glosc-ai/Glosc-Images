package com.glosc.images.data.api

import com.glosc.images.core.common.AppException
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.GenerateImageRequest
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Base64
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
        val response = if (request.sourceImagePaths.isEmpty()) {
            api(provider, apiKey).generateImage(
                OpenAiImageGenerationRequest(
                    model = request.model.ifBlank { provider.defaultModel },
                    prompt = buildPrompt(request.prompt, request.negativePrompt),
                    size = request.size,
                    quality = request.quality,
                    count = request.count.coerceIn(1, 4)
                )
            )
        } else {
            api(provider, apiKey).editImage(
                images = request.sourceImagePaths.take(16).map { path -> imagePart(File(path)) },
                model = textPart(request.model.ifBlank { provider.defaultModel }),
                prompt = textPart(buildPrompt(request.prompt, request.negativePrompt)),
                size = textPart(request.size),
                quality = textPart(request.quality),
                count = textPart(request.count.coerceIn(1, 4).toString()),
                outputFormat = textPart("png")
            )
        }

        if (!response.isSuccessful) {
            throw response.toAppException("图片生成失败")
        }

        val images = response.body()?.data.orEmpty().mapNotNull { image ->
            image.b64Json?.takeIf { it.isNotBlank() }
                ?: image.url?.takeIf { it.isNotBlank() }?.let { downloadImageAsBase64(it) }
        }
        if (images.isEmpty()) {
            throw AppException("图片生成成功但响应中没有可保存的图片数据")
        }
        return GenerateImageResult(images, request.model.ifBlank { provider.defaultModel })
    }

    override suspend fun test(provider: ApiProvider, apiKey: String): ProviderTestResult {
        var count = 0
        var imageModels = emptyList<String>()
        val elapsed = measureTimeMillis {
            val response = api(provider, apiKey).listModels()
            if (!response.isSuccessful) {
                throw response.toAppException("获取模型列表失败")
            }
            val models = response.body()?.data.orEmpty()
            count = models.size
            imageModels = models
                .filter { it.categories.containsImageCategory() }
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

    private fun downloadImageAsBase64(url: String): String {
        val response = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
            .newCall(Request.Builder().url(url).build())
            .execute()
        response.use {
            if (!it.isSuccessful) {
                throw AppException("图片生成成功但下载图片失败：HTTP ${it.code} ${it.message}")
            }
            val bytes = it.body?.bytes() ?: throw AppException("图片生成成功但下载内容为空")
            if (bytes.isEmpty()) throw AppException("图片生成成功但下载内容为空")
            return Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun imagePart(file: File): MultipartBody.Part {
        if (!file.exists() || !file.isFile) {
            throw AppException("参考图片不存在：${file.name}")
        }
        val mediaType = mediaTypeFor(file).toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        return MultipartBody.Part.createFormData("image", file.name, file.asRequestBody(mediaType))
    }

    private fun textPart(value: String) = value.toRequestBody("text/plain".toMediaType())

    private fun mediaTypeFor(file: File): String =
        when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

    private fun buildPrompt(prompt: String, negativePrompt: String): String =
        if (negativePrompt.isBlank()) prompt else "$prompt\n\nAvoid: $negativePrompt"

    private fun Response<*>.toAppException(prefix: String): AppException {
        val status = buildString {
            append("HTTP ")
            append(code())
            message().takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
        }
        val detail = errorBody()?.string()?.extractErrorMessage().orEmpty()
        return AppException(if (detail.isBlank()) "$prefix：$status" else "$prefix：$status\n$detail")
    }

    private fun String.extractErrorMessage(): String {
        val raw = trim()
        if (raw.isBlank()) return ""
        val parsed = runCatching {
            val root = JsonParser.parseString(raw)
            val obj = root.takeIf { it.isJsonObject }?.asJsonObject
            val error = obj?.get("error")
            when {
                error?.isJsonObject == true -> error.asJsonObject.get("message")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                obj?.get("message")?.isJsonPrimitive == true -> obj.get("message").asString
                error?.isJsonPrimitive == true -> error.asString
                else -> null
            }?.trim()
        }.getOrNull().orEmpty()
        return parsed.ifBlank { raw.take(500) }
    }

    private fun String.normalizedBaseUrl(): String {
        val trimmed = trim().ifBlank { "https://one.gloscai.com/" }
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val pathStart = withSlash.indexOf('/', startIndex = withSlash.indexOf("://") + 3)
        val path = if (pathStart == -1) "/" else withSlash.substring(pathStart)
        return if (path == "/") "${withSlash}v1/" else withSlash
    }

    private fun JsonElement?.containsImageCategory(): Boolean {
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
