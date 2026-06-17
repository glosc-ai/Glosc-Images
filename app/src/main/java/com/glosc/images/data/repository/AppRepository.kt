package com.glosc.images.data.repository

import com.glosc.images.core.common.AppException
import com.glosc.images.core.security.ApiKeyStore
import com.glosc.images.data.api.ImageGenerationClient
import com.glosc.images.data.api.ProviderTestResult
import com.glosc.images.data.db.ApiProviderEntity
import com.glosc.images.data.db.ConversationEntity
import com.glosc.images.data.db.GenerationTaskEntity
import com.glosc.images.data.db.GloscDao
import com.glosc.images.data.db.ImageAssetEntity
import com.glosc.images.data.db.MessageEntity
import com.glosc.images.data.storage.ImageFileStorage
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.ChatMessage
import com.glosc.images.domain.model.GenerateImageRequest
import com.glosc.images.domain.model.GenerationTask
import com.glosc.images.domain.model.ImageAsset
import com.glosc.images.domain.model.ProviderType
import com.glosc.images.domain.model.SourceType
import com.glosc.images.domain.model.TaskStatus
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID

class AppRepository(
    private val dao: GloscDao,
    private val storage: ImageFileStorage,
    private val keyStore: ApiKeyStore,
    private val imageClient: ImageGenerationClient
) {
    private val gson = Gson()

    val images: Flow<List<ImageAsset>> = dao.observeImages().map { list -> list.map { it.toDomain() } }
    val recentTasks: Flow<List<GenerationTask>> = dao.observeRecentTasks().map { list -> list.map { it.toDomain() } }
    val providers: Flow<List<ApiProvider>> = dao.observeProviders().map { list ->
        list.map { it.toDomain().withCompatibleImageModels() }
    }
    val chatMessages: Flow<List<ChatMessage>> = dao.observeMessages(DEFAULT_CONVERSATION_ID)
        .map { list -> list.map { it.toDomain() } }

    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        ensureDefaultProvider()
        ensureSampleImages()
        ensureConversation()
    }

    suspend fun activeProvider(): ApiProvider {
        ensureDefaultProvider()
        return dao.getActiveProvider()?.toDomain()?.withCompatibleImageModels()
            ?: throw AppException("没有启用的 API 服务商")
    }

    suspend fun isInitialized(): Boolean = withContext(Dispatchers.IO) {
        ensureDefaultProvider()
        val provider = dao.getActiveProvider()?.toDomain()?.withCompatibleImageModels() ?: return@withContext false
        keyStore.has(provider.apiKeyAlias) && provider.imageModels.isNotEmpty()
    }

    suspend fun saveProvider(
        id: String,
        name: String,
        baseUrl: String,
        apiKey: String?,
        providerType: ProviderType,
        defaultModel: String,
        enabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val cleanBaseUrl = baseUrl.trim().ifBlank { DEFAULT_PROVIDER_BASE_URL }
        val uri = runCatching { URI(cleanBaseUrl) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
            throw AppException("Base URL 需要是有效的 https:// 地址")
        }
        val alias = id.ifBlank { UUID.randomUUID().toString() }
        val existing = dao.getProvider(alias)
        apiKey?.takeIf { it.isNotBlank() && !it.contains("••") }?.let { keyStore.save(alias, it.trim()) }
        if (enabled) dao.disableAllProviders()
        val cleanDefaultModel = defaultModel
            .takeIf { it.isNotBlank() && !it.isKnownIncompatibleImageModel() }
            ?: existing?.defaultModel?.takeIf { it.isNotBlank() && !it.isKnownIncompatibleImageModel() }
            ?: ""
        dao.upsertProvider(
            ApiProviderEntity(
                id = alias,
                name = name.ifBlank { DEFAULT_PROVIDER_NAME },
                baseUrl = cleanBaseUrl,
                apiKeyAlias = alias,
                providerType = providerType.name,
                defaultModel = cleanDefaultModel,
                imageModels = existing?.imageModels.orEmpty(),
                enabled = enabled,
                lastTestedAt = null,
                lastStatus = null
            )
        )
    }

    suspend fun generateImage(request: GenerateImageRequest): List<ImageAsset> = withContext(Dispatchers.IO) {
        if (request.prompt.isBlank()) throw AppException("请输入提示词")
        val provider = activeProvider()
        val apiKey = keyStore.read(provider.apiKeyAlias)
            ?: throw AppException("请先在 API 设置中保存 API Key")
        val requestedModel = request.model.trim()
        val selectedModel = when {
            requestedModel.isNotBlank() &&
                !requestedModel.isKnownIncompatibleImageModel() &&
                (provider.imageModels.isEmpty() || requestedModel in provider.imageModels) -> requestedModel
            provider.defaultModel.isNotBlank() -> provider.defaultModel
            else -> provider.imageModels.firstOrNull().orEmpty()
        }
            .ifBlank { throw AppException("请先在 API 设置中获取 categories 包含 image 的图片模型") }
        val now = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        dao.upsertTask(
            GenerationTaskEntity(
                id = taskId,
                taskType = request.sourceType.name,
                status = TaskStatus.Running.name,
                requestJson = gson.toJson(request),
                errorCode = null,
                errorMessage = null,
                imageAssetId = null,
                createdAt = now,
                startedAt = now,
                finishedAt = null
            )
        )

        try {
            val result = imageClient.generate(
                provider = provider,
                apiKey = apiKey,
                request = request.copy(model = selectedModel)
            )
            val assets = result.base64Images.mapIndexed { index, base64 ->
                val stored = storage.saveBase64Image(base64, request.sourceType)
                val id = UUID.randomUUID().toString()
                ImageAssetEntity(
                    id = id,
                    localPath = stored.path,
                    remoteUrl = null,
                    thumbnailPath = null,
                    prompt = request.prompt,
                    negativePrompt = request.negativePrompt.ifBlank { null },
                    sourceType = request.sourceType.name,
                    model = result.model,
                    providerId = provider.id,
                    width = stored.width,
                    height = stored.height,
                    seed = request.seed.ifBlank { null },
                    favorite = false,
                    tags = defaultTags(request.sourceType),
                    parentImageId = request.parentImageId,
                    placeholderKey = "g${(index % 6) + 1}",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
            assets.forEach { dao.upsertImage(it) }
            dao.finishTask(
                id = taskId,
                status = TaskStatus.Success.name,
                errorCode = null,
                errorMessage = null,
                imageAssetId = assets.firstOrNull()?.id,
                finishedAt = System.currentTimeMillis()
            )
            assets.map { it.toDomain() }
        } catch (error: Throwable) {
            dao.finishTask(
                id = taskId,
                status = TaskStatus.Failed.name,
                errorCode = error.javaClass.simpleName,
                errorMessage = error.message ?: "未知错误",
                imageAssetId = null,
                finishedAt = System.currentTimeMillis()
            )
            throw if (error is AppException) error else AppException("生成失败：${error.message ?: "未知错误"}", error)
        }
    }

    suspend fun sendChatMessage(content: String) = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext
        ensureConversation()
        val userMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = DEFAULT_CONVERSATION_ID,
            role = "user",
            content = content.trim(),
            imageAssetId = null,
            createdAt = System.currentTimeMillis()
        )
        dao.upsertMessage(userMessage)

        try {
            val generated = generateImage(
                GenerateImageRequest(
                    prompt = content.trim(),
                    model = activeProvider().defaultModel,
                    size = "1024x1536",
                    quality = "high",
                    count = 1,
                    sourceType = SourceType.Chat
                )
            ).firstOrNull()
            dao.upsertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = DEFAULT_CONVERSATION_ID,
                    role = "assistant",
                    content = "已根据你的描述生成图片。点开可以继续编辑或保存标签。",
                    imageAssetId = generated?.id,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (error: Throwable) {
            dao.upsertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = DEFAULT_CONVERSATION_ID,
                    role = "assistant",
                    content = error.message ?: "生成失败，请稍后重试。",
                    imageAssetId = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun newChat() = withContext(Dispatchers.IO) {
        dao.clearMessages(DEFAULT_CONVERSATION_ID)
        ensureConversation()
        dao.upsertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = DEFAULT_CONVERSATION_ID,
                role = "assistant",
                content = "新会话已创建。描述你想要的画面吧。",
                imageAssetId = null,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun editImage(parentId: String, editPrompt: String, type: SourceType): List<ImageAsset> {
        val parent = dao.getImage(parentId)?.toDomain() ?: throw AppException("原图不存在")
        val prompt = "${editPrompt.ifBlank { "生成一张变体" }}\n\nReference prompt: ${parent.prompt}"
        return generateImage(
            GenerateImageRequest(
                prompt = prompt,
                model = parent.model,
                size = "${parent.width.coerceAtLeast(1024)}x${parent.height.coerceAtLeast(1024)}",
                quality = "high",
                count = 1,
                sourceType = type,
                parentImageId = parent.id
            )
        )
    }

    suspend fun testActiveProvider(): ProviderTestResult = withContext(Dispatchers.IO) {
        val provider = activeProvider()
        val apiKey = keyStore.read(provider.apiKeyAlias) ?: throw AppException("请先保存 API Key")
        val result = imageClient.test(provider, apiKey)
        val imageModels = result.imageModels.compatibleImageModels()
        val hiddenCount = result.imageModels.size - imageModels.size
        val selectedDefault = when {
            provider.defaultModel in imageModels -> provider.defaultModel
            imageModels.isNotEmpty() -> imageModels.preferredImageModel().orEmpty()
            else -> provider.defaultModel
        }
        val status = when {
            imageModels.isEmpty() -> "连接成功 · 未找到可用图片模型"
            hiddenCount > 0 -> "连接成功 · ${imageModels.size} 个可用图片模型 · 已隐藏 ${hiddenCount} 个异常模型"
            else -> "连接成功 · ${imageModels.size} 个图片模型"
        }
        dao.updateProviderModels(
            id = provider.id,
            defaultModel = selectedDefault,
            imageModels = imageModels.joinToString(","),
            testedAt = System.currentTimeMillis(),
            status = status
        )
        result.copy(imageModels = imageModels)
    }

    suspend fun toggleFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    suspend fun addTag(id: String, tag: String) = withContext(Dispatchers.IO) {
        val asset = dao.getImage(id) ?: return@withContext
        val tags = asset.tags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
            .apply { add(tag.trim()) }
            .joinToString(", ")
        dao.setTags(id, tags, System.currentTimeMillis())
    }

    suspend fun deleteImage(id: String) = withContext(Dispatchers.IO) {
        dao.getImage(id)?.let {
            storage.delete(it.localPath)
            dao.deleteImage(id)
        }
    }

    private suspend fun ensureDefaultProvider() {
        val existingDefault = dao.getProvider(DEFAULT_PROVIDER_ID)
        if (existingDefault != null) {
            if (existingDefault.baseUrl.contains("api.openai.com") || existingDefault.name == "OpenAI") {
                dao.upsertProvider(
                    existingDefault.copy(
                        name = DEFAULT_PROVIDER_NAME,
                        baseUrl = DEFAULT_PROVIDER_BASE_URL,
                        defaultModel = "",
                        imageModels = "",
                        lastStatus = null,
                        lastTestedAt = null
                    )
                )
            }
            return
        }
        if (dao.getProvidersOnce().isNotEmpty()) return
        dao.upsertProvider(
            ApiProviderEntity(
                id = DEFAULT_PROVIDER_ID,
                name = DEFAULT_PROVIDER_NAME,
                baseUrl = DEFAULT_PROVIDER_BASE_URL,
                apiKeyAlias = DEFAULT_PROVIDER_ID,
                providerType = ProviderType.OpenAi.name,
                defaultModel = "",
                imageModels = "",
                enabled = true,
                lastTestedAt = null,
                lastStatus = null
            )
        )
    }

    private suspend fun ensureSampleImages() {
        if (dao.getImagesOnce().isNotEmpty()) return
        val now = System.currentTimeMillis()
        sampleImages.forEachIndexed { index, sample ->
            dao.upsertImage(
                ImageAssetEntity(
                    id = UUID.randomUUID().toString(),
                    localPath = "",
                    remoteUrl = null,
                    thumbnailPath = null,
                    prompt = sample.prompt,
                    negativePrompt = "模糊, 低分辨率, 水印",
                    sourceType = sample.sourceType.name,
                    model = sample.model,
                    providerId = DEFAULT_PROVIDER_ID,
                    width = sample.width,
                    height = sample.height,
                    seed = sample.seed,
                    favorite = sample.favorite,
                    tags = sample.tags,
                    parentImageId = null,
                    placeholderKey = "g${(index % 6) + 1}",
                    createdAt = now - index * 86_400_000L,
                    updatedAt = now - index * 86_400_000L
                )
            )
        }
    }

    private suspend fun ensureConversation() {
        dao.upsertConversation(
            ConversationEntity(
                id = DEFAULT_CONVERSATION_ID,
                title = "创意助手",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        if (dao.getMessagesOnce(DEFAULT_CONVERSATION_ID).isEmpty()) {
            dao.upsertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = DEFAULT_CONVERSATION_ID,
                    role = "assistant",
                    content = "想生成什么图片？直接描述画面，我也可以帮你优化提示词或继续调整上一张。",
                    imageAssetId = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun defaultTags(sourceType: SourceType) = when (sourceType) {
        SourceType.Generate -> "工程, 新作品"
        SourceType.Chat -> "对话, 新作品"
        SourceType.Edit -> "编辑, 版本"
        SourceType.Transform -> "变换, 版本"
    }

    private fun ApiProvider.withCompatibleImageModels(): ApiProvider {
        val filteredModels = imageModels.compatibleImageModels()
        val filteredDefault = when {
            defaultModel.isBlank() || defaultModel.isKnownIncompatibleImageModel() -> filteredModels.preferredImageModel().orEmpty()
            filteredModels.isEmpty() -> defaultModel
            defaultModel in filteredModels -> defaultModel
            else -> filteredModels.preferredImageModel().orEmpty()
        }
        return copy(defaultModel = filteredDefault, imageModels = filteredModels)
    }

    private fun List<String>.compatibleImageModels(): List<String> =
        filterNot { it.isKnownIncompatibleImageModel() }

    private fun List<String>.preferredImageModel(): String? =
        preferredImageModelFallbacks.firstNotNullOfOrNull { preferred ->
            firstOrNull { it.equals(preferred, ignoreCase = true) }
        } ?: firstOrNull()

    private fun String.isKnownIncompatibleImageModel(): Boolean =
        knownIncompatibleImageModels.any { equals(it, ignoreCase = true) }

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        imageAssetId = imageAssetId,
        createdAt = createdAt
    )

    private data class SampleImage(
        val prompt: String,
        val sourceType: SourceType,
        val model: String,
        val width: Int,
        val height: Int,
        val seed: String,
        val favorite: Boolean,
        val tags: String
    )

    private companion object {
        const val DEFAULT_PROVIDER_ID = "openai-default"
        const val DEFAULT_PROVIDER_NAME = "Glosc AI"
        const val DEFAULT_PROVIDER_BASE_URL = "https://one.gloscai.com/"
        const val DEFAULT_CONVERSATION_ID = "default-conversation"

        val knownIncompatibleImageModels = setOf(
            "alibaba/qwen-image-2.0",
            "alibaba/qwen-image-2.0-pro",
            "google/gemini-2.5-flash-image",
            "google/gemini-3-pro-image",
            "google/gemini-3.1-flash-image-preview",
            "gpt-image-2"
        )

        val preferredImageModelFallbacks = listOf(
            "Agnes/agnes-image-2.1-flash",
            "Agnes/agnes-image-2.0-flash",
            "openai/gpt-image-2"
        )

        val sampleImages = listOf(
            SampleImage("赛博城市夜景，霓虹倒影，电影级广角，体积光", SourceType.Generate, "Glosc image model", 1024, 1536, "284197", true, "城市, 赛博, 夜景"),
            SampleImage("一只机械蜂鸟悬停在发光的玻璃花朵旁，微距摄影", SourceType.Generate, "Glosc image model", 1024, 1024, "481204", false, "自然, 微距, 冷调"),
            SampleImage("画一只在雨夜霓虹街道上的橘猫，电影感", SourceType.Chat, "Glosc image model", 1024, 1536, "902144", true, "动物, 电影感, 夜景"),
            SampleImage("夕阳沙漠里的极简产品海报", SourceType.Edit, "dall-e-3", 1536, 1024, "615332", false, "风景, 暖调"),
            SampleImage("抽象字体海报，高对比构成，玻璃质感", SourceType.Generate, "Glosc image model", 1024, 1024, "120983", false, "设计, 海报"),
            SampleImage("渐变星云与金属结构的风格转换", SourceType.Transform, "sd-xl", 1024, 1536, "776201", true, "抽象, 太空")
        )
    }
}
