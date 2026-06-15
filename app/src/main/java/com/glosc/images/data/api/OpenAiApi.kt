package com.glosc.images.data.api

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class OpenAiImageGenerationRequest(
    val model: String,
    val prompt: String,
    val size: String,
    val quality: String,
    @SerializedName("n") val count: Int,
    @SerializedName("output_format") val outputFormat: String = "png"
)

data class OpenAiImageGenerationResponse(
    val data: List<OpenAiImageData>?
)

data class OpenAiImageData(
    @SerializedName("b64_json") val b64Json: String?,
    val url: String?
)

data class OpenAiModelsResponse(
    val data: List<OpenAiModel>?
)

data class OpenAiModel(
    val id: String?,
    val categories: JsonElement?
)

interface OpenAiApi {
    @POST("images/generations")
    suspend fun generateImage(
        @Body request: OpenAiImageGenerationRequest
    ): Response<OpenAiImageGenerationResponse>

    @GET("models")
    suspend fun listModels(): Response<OpenAiModelsResponse>
}
