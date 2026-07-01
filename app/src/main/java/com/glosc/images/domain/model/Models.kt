package com.glosc.images.domain.model

enum class SourceType(val label: String) {
    Generate("Text to Image"),
    ImageToImage("Image to Image"),
    Chat("对话生成"),
    Edit("图片编辑"),
    Transform("图片变换")
}

enum class TaskStatus(val label: String) {
    Pending("排队"),
    Running("运行中"),
    Success("成功"),
    Failed("失败"),
    Cancelled("已取消")
}

enum class ProviderType {
    OpenAi,
    Custom
}

data class GenerateImageRequest(
    val prompt: String,
    val negativePrompt: String = "",
    val model: String = "flux-kontext-pro",
    val size: String = "1024x1024",
    val quality: String = "high",
    val count: Int = 1,
    val seed: String = "",
    val sourceType: SourceType = SourceType.Generate,
    val parentImageId: String? = null,
    val sourceImagePaths: List<String> = emptyList()
)

data class ImageAsset(
    val id: String,
    val localPath: String,
    val remoteUrl: String?,
    val thumbnailPath: String?,
    val prompt: String,
    val negativePrompt: String?,
    val sourceType: SourceType,
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
)

data class GenerationTask(
    val id: String,
    val taskType: SourceType,
    val status: TaskStatus,
    val requestJson: String,
    val errorCode: String?,
    val errorMessage: String?,
    val imageAssetId: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val finishedAt: Long?
)

data class ApiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyAlias: String,
    val providerType: ProviderType,
    val defaultModel: String,
    val imageModels: List<String>,
    val enabled: Boolean,
    val lastTestedAt: Long?,
    val lastStatus: String?
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val imageAssetId: String?,
    val createdAt: Long
)

data class AppUpdateInfo(
    val currentVersionName: String,
    val latestVersionName: String,
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val updateAvailable: Boolean,
    val message: String
)

data class AppUpdateStatus(
    val info: AppUpdateInfo?,
    val message: String,
    val downloadedApkPath: String? = null
)
