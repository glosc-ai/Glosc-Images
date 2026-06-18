package com.glosc.images.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glosc.images.GloscImagesApp
import com.glosc.images.core.common.UiState
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.AppUpdateInfo
import com.glosc.images.domain.model.AppUpdateStatus
import com.glosc.images.domain.model.ChatMessage
import com.glosc.images.domain.model.GenerateImageRequest
import com.glosc.images.domain.model.GenerationTask
import com.glosc.images.domain.model.ImageAsset
import com.glosc.images.domain.model.ProviderType
import com.glosc.images.domain.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    Onboarding,
    Generate,
    Chat,
    Library,
    Settings,
    Detail
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as GloscImagesApp).repository

    val images: StateFlow<List<ImageAsset>> = repository.images.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val recentTasks: StateFlow<List<GenerationTask>> = repository.recentTasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val providers: StateFlow<List<ApiProvider>> = repository.providers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val messages: StateFlow<List<ChatMessage>> = repository.chatMessages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val screen = MutableStateFlow(AppScreen.Onboarding)
    val selectedImageId = MutableStateFlow<String?>(null)
    val operation = MutableStateFlow<UiState<List<ImageAsset>>>(UiState.Idle)
    val chatState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val settingsState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val updateState = MutableStateFlow<UiState<AppUpdateStatus>>(UiState.Idle)

    init {
        viewModelScope.launch {
            repository.bootstrap()
            screen.value = if (repository.isInitialized()) AppScreen.Generate else AppScreen.Onboarding
            checkForUpdates(silent = true)
        }
    }

    fun open(screen: AppScreen) {
        this.screen.value = screen
    }

    fun openDetail(id: String) {
        selectedImageId.value = id
        screen.value = AppScreen.Detail
    }

    fun generate(request: GenerateImageRequest) {
        viewModelScope.launch {
            operation.value = UiState.Loading
            operation.value = runCatching { repository.generateImage(request) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "生成失败", it) }
                )
        }
    }

    fun sendChat(content: String) {
        viewModelScope.launch {
            if (content.isBlank()) return@launch
            chatState.value = UiState.Loading
            runCatching { repository.sendChatMessage(content) }
                .onSuccess { chatState.value = UiState.Idle }
                .onFailure { chatState.value = UiState.Error(it.message ?: "发送失败", it) }
        }
    }

    fun newChat() {
        viewModelScope.launch {
            repository.newChat()
        }
    }

    fun saveProvider(
        id: String,
        name: String,
        baseUrl: String,
        apiKey: String?,
        type: ProviderType,
        model: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            settingsState.value = UiState.Loading
            settingsState.value = runCatching {
                repository.saveProvider(id, name, baseUrl, apiKey, type, model, enabled)
                if (apiKey.isNullOrBlank()) {
                    "已保存。API Key 未修改。"
                } else {
                    "已保存。API Key 已写入 Android Keystore 加密存储。"
                }
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "保存失败", it) }
            )
        }
    }

    fun testProvider() {
        viewModelScope.launch {
            settingsState.value = UiState.Loading
            settingsState.value = runCatching {
                val result = repository.testActiveProvider()
                result.toModelMessage()
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "测试失败", it) }
            )
        }
    }

    fun saveProviderAndFetchModels(
        id: String,
        name: String,
        baseUrl: String,
        apiKey: String?,
        type: ProviderType,
        model: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            settingsState.value = UiState.Loading
            settingsState.value = runCatching {
                repository.saveProvider(id, name, baseUrl, apiKey, type, model, enabled)
                repository.testActiveProvider().toModelMessage()
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "获取模型列表失败", it) }
            )
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsState.value = UiState.Loading
            val initialized = repository.isInitialized()
            if (initialized) {
                settingsState.value = UiState.Success("初始化完成")
                screen.value = AppScreen.Generate
            } else {
                settingsState.value = UiState.Error("请先保存 API Key，并获取至少一个 categories 包含 image 的模型")
            }
        }
    }

    fun saveProviderAndCompleteOnboarding(
        id: String,
        name: String,
        baseUrl: String,
        apiKey: String?,
        type: ProviderType,
        model: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            settingsState.value = UiState.Loading
            settingsState.value = runCatching {
                repository.saveProvider(id, name, baseUrl, apiKey, type, model, enabled)
                if (!repository.isInitialized()) {
                    throw IllegalStateException("请先保存 API Key，并获取至少一个 categories 包含 image 的模型")
                }
                screen.value = AppScreen.Generate
                "初始化完成"
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "初始化失败", it) }
            )
        }
    }

    fun toggleFavorite(asset: ImageAsset) {
        viewModelScope.launch {
            repository.toggleFavorite(asset.id, !asset.favorite)
        }
    }

    fun delete(asset: ImageAsset) {
        viewModelScope.launch {
            repository.deleteImage(asset.id)
            screen.value = AppScreen.Library
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) updateState.value = UiState.Loading
            val result = runCatching {
                repository.checkForUpdate(currentVersionName())
            }
            result.fold(
                onSuccess = { info ->
                    if (info.updateAvailable || !silent) {
                        updateState.value = UiState.Success(AppUpdateStatus(info = info, message = info.message))
                    }
                },
                onFailure = { error ->
                    if (!silent) updateState.value = UiState.Error(error.message ?: "检查更新失败", error)
                }
            )
        }
    }

    fun downloadUpdate(info: AppUpdateInfo) {
        viewModelScope.launch {
            updateState.value = UiState.Loading
            updateState.value = runCatching {
                val apk = repository.downloadUpdate(info)
                AppUpdateStatus(
                    info = info,
                    message = "更新包已下载：${info.apkAssetName}",
                    downloadedApkPath = apk.absolutePath
                )
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "下载更新失败", it) }
            )
        }
    }

    fun addTag(asset: ImageAsset, tag: String) {
        viewModelScope.launch {
            repository.addTag(asset.id, tag)
        }
    }

    fun edit(asset: ImageAsset, prompt: String, type: SourceType) {
        viewModelScope.launch {
            operation.value = UiState.Loading
            operation.value = runCatching { repository.editImage(asset.id, prompt, type) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "编辑失败", it) }
                )
        }
    }

    private fun com.glosc.images.data.api.ProviderTestResult.toModelMessage(): String =
        if (imageModels.isEmpty()) {
            "连接成功 · 共 ${modelCount} 个模型，但没有 categories 包含 image 的模型"
        } else {
            "连接成功 · 共 ${modelCount} 个模型 · 已筛选 ${imageModels.size} 个图片模型"
        }

    private fun currentVersionName(): String {
        val app = getApplication<Application>()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0)
        }
        return info.versionName ?: "0.0.0"
    }
}
