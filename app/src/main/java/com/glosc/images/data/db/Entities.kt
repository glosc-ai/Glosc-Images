package com.glosc.images.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.GenerationTask
import com.glosc.images.domain.model.ImageAsset
import com.glosc.images.domain.model.ProviderType
import com.glosc.images.domain.model.SourceType
import com.glosc.images.domain.model.TaskStatus

@Entity(
    tableName = "image_assets",
    indices = [
        Index("sourceType"),
        Index("model"),
        Index("createdAt"),
        Index("favorite")
    ]
)
data class ImageAssetEntity(
    @PrimaryKey val id: String,
    val localPath: String,
    val remoteUrl: String?,
    val thumbnailPath: String?,
    val prompt: String,
    val negativePrompt: String?,
    val sourceType: String,
    val model: String,
    val providerId: String,
    val width: Int,
    val height: Int,
    val seed: String?,
    val favorite: Boolean,
    val tags: String,
    val parentImageId: String?,
    val placeholderKey: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain() = ImageAsset(
        id = id,
        localPath = localPath,
        remoteUrl = remoteUrl,
        thumbnailPath = thumbnailPath,
        prompt = prompt,
        negativePrompt = negativePrompt,
        sourceType = sourceType.toSourceType(),
        model = model,
        providerId = providerId,
        width = width,
        height = height,
        seed = seed,
        favorite = favorite,
        tags = tags,
        parentImageId = parentImageId,
        placeholderKey = placeholderKey,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Entity(tableName = "generation_tasks", indices = [Index("status"), Index("createdAt")])
data class GenerationTaskEntity(
    @PrimaryKey val id: String,
    val taskType: String,
    val status: String,
    val requestJson: String,
    val errorCode: String?,
    val errorMessage: String?,
    val imageAssetId: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val finishedAt: Long?
) {
    fun toDomain() = GenerationTask(
        id = id,
        taskType = taskType.toSourceType(),
        status = status.toTaskStatus(),
        requestJson = requestJson,
        errorCode = errorCode,
        errorMessage = errorMessage,
        imageAssetId = imageAssetId,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt
    )
}

@Entity(tableName = "api_providers")
data class ApiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyAlias: String,
    val providerType: String,
    val defaultModel: String,
    val imageModels: String,
    val enabled: Boolean,
    val lastTestedAt: Long?,
    val lastStatus: String?
) {
    fun toDomain() = ApiProvider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKeyAlias = apiKeyAlias,
        providerType = providerType.toProviderType(),
        defaultModel = defaultModel,
        imageModels = imageModels.split(",").map { it.trim() }.filter { it.isNotBlank() },
        enabled = enabled,
        lastTestedAt = lastTestedAt,
        lastStatus = lastStatus
    )
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "messages", indices = [Index("conversationId"), Index("createdAt")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val imageAssetId: String?,
    val createdAt: Long
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String?
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
)

private fun String.toSourceType() = runCatching { SourceType.valueOf(this) }.getOrDefault(SourceType.Generate)
private fun String.toTaskStatus() = runCatching { TaskStatus.valueOf(this) }.getOrDefault(TaskStatus.Pending)
private fun String.toProviderType() = runCatching { ProviderType.valueOf(this) }.getOrDefault(ProviderType.OpenAi)
