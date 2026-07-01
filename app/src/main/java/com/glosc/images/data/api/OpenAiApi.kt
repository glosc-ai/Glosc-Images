package com.glosc.images.data.api

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
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

    @Multipart
    @POST("images/edits")
    suspend fun editImage(
        @Part images: List<MultipartBody.Part>,
        @Part("model") model: RequestBody,
        @Part("prompt") prompt: RequestBody,
        @Part("size") size: RequestBody,
        @Part("quality") quality: RequestBody,
        @Part("n") count: RequestBody,
        @Part("output_format") outputFormat: RequestBody
    ): Response<OpenAiImageGenerationResponse>

    @GET("models")
    suspend fun listModels(): Response<OpenAiModelsResponse>
}
