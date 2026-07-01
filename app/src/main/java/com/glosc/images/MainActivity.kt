package com.glosc.images

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.glosc.images.core.common.UiState
import com.glosc.images.core.ui.Design
import com.glosc.images.core.ui.addGap
import com.glosc.images.core.ui.addSpaced
import com.glosc.images.core.ui.artPlaceholder
import com.glosc.images.core.ui.bodyText
import com.glosc.images.core.ui.card
import com.glosc.images.core.ui.chip
import com.glosc.images.core.ui.column
import com.glosc.images.core.ui.dangerButton
import com.glosc.images.core.ui.dashedBg
import com.glosc.images.core.ui.dp
import com.glosc.images.core.ui.ghostButton
import com.glosc.images.core.ui.input
import com.glosc.images.core.ui.label
import com.glosc.images.core.ui.loadAsset
import com.glosc.images.core.ui.mono
import com.glosc.images.core.ui.primaryButton
import com.glosc.images.core.ui.roundedBg
import com.glosc.images.core.ui.row
import com.glosc.images.core.ui.title
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.AppUpdateInfo
import com.glosc.images.domain.model.AppUpdateStatus
import com.glosc.images.domain.model.GenerateImageRequest
import com.glosc.images.domain.model.GenerationTask
import com.glosc.images.domain.model.ImageAsset
import com.glosc.images.domain.model.ProviderType
import com.glosc.images.domain.model.SourceType
import com.glosc.images.domain.model.TaskStatus
import com.glosc.images.ui.AppScreen
import com.glosc.images.ui.MainViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

private const val MAX_SOURCE_IMAGES = 16

private enum class StudioMode {
    TextToImage,
    ImageToImage
}

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel
    private lateinit var root: LinearLayout

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        sourceImageUris.clear()
        sourceImageUris.addAll(uris.take(MAX_SOURCE_IMAGES))
        render()
    }

    private var studioMode = StudioMode.TextToImage
    private val sourceImageUris = mutableListOf<Uri>()
    private var promptValue = "一只机械蜂鸟悬停在发光的玻璃花朵旁，微距摄影，冷蓝色调，体积光，超精细细节"
    private var negativeValue = "模糊, 低分辨率, 水印, 畸变"
    private var selectedSize = "auto"
    private var selectedQuality = "auto"
    private var selectedCount = 1
    private var seedValue = "284197"
    private var generateModel = ""
    private var libraryQuery = ""
    private var libraryFilter = "all"
    private var settingsProviderId = ""
    private var settingsName = ""
    private var settingsBaseUrl = ""
    private var settingsKey = ""
    private var settingsModel = ""
    private var settingsEnabled = true
    private var settingsProviderType = ProviderType.OpenAi
    private var libraryGridMode = true
    private var seedRefreshGenerationKey = ""
    private var shownUpdateTag = ""
    private var launchedUpdateApkPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Design.Bg
        window.navigationBarColor = Design.Bg
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        root = column().apply {
            setBackgroundColor(Design.Bg)
        }
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.screen.collect { render() } }
                launch { vm.images.collect { render() } }
                launch { vm.recentTasks.collect { render() } }
                launch { vm.providers.collect { render() } }
                launch { vm.messages.collect { render() } }
                launch { vm.chatState.collect { render() } }
                launch { vm.operation.collect { render() } }
                launch { vm.settingsState.collect { render() } }
                launch { vm.updateState.collect { state -> handleUpdateState(state); render() } }
            }
        }
        render()
    }

    private fun render() {
        root.removeAllViews()
        when (vm.screen.value) {
            AppScreen.Onboarding -> renderOnboarding()
            AppScreen.Generate -> renderStudio()
            AppScreen.Chat -> renderStudio()
            AppScreen.Library -> renderStudio()
            AppScreen.Settings -> renderSettings()
            AppScreen.Detail -> renderDetail()
        }
    }

    private fun renderOnboarding() {
        if (settingsProviderId.isBlank()) {
            (activeProvider() ?: vm.providers.value.firstOrNull())?.let { hydrateSettings(it, force = true) }
        }
        if (settingsName.isBlank()) settingsName = "Glosc AI"
        if (settingsBaseUrl.isBlank()) settingsBaseUrl = "https://one.gloscai.com/"
        settingsEnabled = true
        settingsProviderType = ProviderType.OpenAi

        root.addView(appBar("首次启动", "配置引导"))
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        content.addSpaced(note("完成初始化后即可开始生成图片：保存 Glosc AI Key，获取图片模型列表，并选择默认模型。"))

        content.addSpaced(section("1. 连接 Glosc AI"))
        content.addSpaced(card().apply {
            addSpaced(label("渠道"))
            addSpaced(input("https://one.gloscai.com/", settingsBaseUrl).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsBaseUrl = it?.toString().orEmpty() }
            })
            addSpaced(label("API Key"))
            addSpaced(input("sk-...", settingsKey).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsKey = it?.toString().orEmpty() }
            })
            addSpaced(keyLinkPrompt())
        })

        content.addSpaced(section("2. 获取图片模型"))
        content.addSpaced(card().apply {
            addSpaced(bodyText("应用会请求 /v1/models，并只保留 categories 包含 image 的模型。", Design.Muted, 14f))
            val active = activeProvider()
            val count = active?.imageModels?.size ?: 0
            addSpaced(mono(if (count > 0) "已找到 $count 个图片模型" else "尚未获取图片模型", if (count > 0) Design.Ok else Design.Faint, 14f))
            addSpaced(primaryButton("保存 Key 并获取模型列表") {
                val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
                vm.saveProviderAndFetchModels(
                    id = settingsProviderId.ifBlank { "openai-default" },
                    name = settingsName,
                    baseUrl = settingsBaseUrl,
                    apiKey = settingsKey.takeIf { it.isNotBlank() },
                    type = settingsProviderType,
                    model = settingsModel.ifBlank { fallbackModel },
                    enabled = true
                )
            })
        })

        content.addSpaced(section("3. 选择默认图片模型"))
        content.addSpaced(card().apply {
            val modelOptions = imageModelOptions()
            addSpaced(dropdown(
                labelText = "默认模型",
                options = modelOptions,
                selected = settingsModel.ifBlank { modelOptions.firstOrNull()?.first.orEmpty() }
            ) { settingsModel = it })
            addSpaced(bodyText("后续工程生图、对话生图和图片编辑都会默认使用这个模型。", Design.Muted, 14f))
        })

        renderSettingsState(content)
        content.addSpaced(primaryButton("完成初始化并开始使用") {
            val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
            vm.saveProviderAndCompleteOnboarding(
                id = settingsProviderId.ifBlank { "openai-default" },
                name = settingsName,
                baseUrl = settingsBaseUrl,
                apiKey = settingsKey.takeIf { it.isNotBlank() },
                type = settingsProviderType,
                model = settingsModel.ifBlank { fallbackModel },
                enabled = true
            )
        })
        content.addGap(10)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderStudio() {
        refreshSeedAfterCompletedGenerate()
        val expanded = resources.configuration.screenWidthDp >= 700
        if (expanded) {
            root.addView(row(gap = 0).apply {
                addSpaced(studioSidebar(), LinearLayout.LayoutParams(dp(255), ViewGroup.LayoutParams.MATCH_PARENT))
                addSpaced(column(gap = 0).apply {
                    addSpaced(studioPromoBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
                    addSpaced(studioTopBar(expanded = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
                    addSpaced(studioWorkspace(expanded = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(column(gap = 0).apply {
                addSpaced(studioPromoBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
                addSpaced(studioTopBar(expanded = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
                addSpaced(studioMobileTabs(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
                addSpaced(studioWorkspace(expanded = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun studioPromoBar(): View = row(padding = 0, gap = 8, gravity = Gravity.CENTER).apply {
        setBackgroundColor(Design.Accent2)
        addSpaced(bodyText("Glosc One image models ready", 0xFF100D05.toInt(), 15f).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun studioTopBar(expanded: Boolean): View = row(gap = 10).apply {
        setBackgroundColor(Design.Bg)
        setPadding(dp(16), 0, dp(16), 0)
        if (!expanded) {
            addSpaced(studioLogoCompact(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            addSpaced(row(gap = 22).apply {
                addSpaced(topNavText("Text to Image", studioMode == StudioMode.TextToImage) {
                    studioMode = StudioMode.TextToImage
                    render()
                })
                addSpaced(topNavText("Image to Image", studioMode == StudioMode.ImageToImage) {
                    studioMode = StudioMode.ImageToImage
                    render()
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addSpaced(ghostButton("API") {
            (activeProvider() ?: vm.providers.value.firstOrNull())?.let { hydrateSettings(it, force = true) }
            vm.open(AppScreen.Settings)
        }, LinearLayout.LayoutParams(dp(72), dp(44)))
    }

    private fun studioLogoCompact(): View = row(gap = 10).apply {
        addSpaced(bodyText("G", Design.Accent, 18f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            roundedBg(0x22FF8A1F, radiusDp = 10, strokeColor = Design.Accent)
        }, LinearLayout.LayoutParams(dp(38), dp(38)))
        addSpaced(column(gap = 1).apply {
            addSpaced(title("Glosc Images", 20f))
            addSpaced(mono("AI Studio", Design.Faint, 12f))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun studioSidebar(): View = column(gap = 0).apply {
        setBackgroundColor(0xFF050506.toInt())
        addSpaced(row(gap = 8).apply {
            setPadding(dp(24), 0, dp(24), 0)
            addSpaced(bodyText("AI Studio", Design.Fg, 16f).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        addSpaced(row(gap = 10).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addSpaced(bodyText("G", Design.Accent, 20f).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                roundedBg(0x22FF8A1F, radiusDp = 12, strokeColor = Design.Accent)
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addSpaced(title("Glosc Images", 22f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        addSpaced(column(padding = 8, gap = 6).apply {
            addSpaced(mono("IMAGE TOOLS", Design.Faint, 12f).apply {
                setPadding(dp(16), dp(18), 0, dp(4))
            })
            addSpaced(studioNavItem("Text to Image", studioMode == StudioMode.TextToImage) {
                studioMode = StudioMode.TextToImage
                render()
            })
            addSpaced(studioNavItem("Image to Image", studioMode == StudioMode.ImageToImage) {
                studioMode = StudioMode.ImageToImage
                render()
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun studioMobileTabs(): View = row(gap = 8).apply {
        setBackgroundColor(Design.Bg)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addSpaced(studioNavItem("Text to Image", studioMode == StudioMode.TextToImage) {
            studioMode = StudioMode.TextToImage
            render()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addSpaced(studioNavItem("Image to Image", studioMode == StudioMode.ImageToImage) {
            studioMode = StudioMode.ImageToImage
            render()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
    }

    private fun studioWorkspace(expanded: Boolean): View {
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = column(padding = if (expanded) 24 else 16, gap = 16)
        if (expanded) {
            content.addSpaced(row(gap = 24, gravity = Gravity.TOP).apply {
                addSpaced(studioControlPanel(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
                addSpaced(studioResultsPanel(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f))
            })
        } else {
            content.addSpaced(studioControlPanel())
            content.addSpaced(studioResultsPanel())
        }
        scroll.addView(content)
        return scroll
    }

    private fun studioControlPanel(): View = studioPanel().apply {
        addSpaced(label("Model"))
        addSpaced(dropdown(
            labelText = "",
            options = generateModelOptions(),
            selected = activeImageModel()
        ) { generateModel = it })

        if (studioMode == StudioMode.ImageToImage) {
            addSpaced(label("Source Images"))
            addSpaced(sourceUploadPanel())
        }

        addSpaced(label("Prompt"))
        val promptInput = input(
            if (studioMode == StudioMode.ImageToImage) {
                "Describe how you want to transform the images..."
            } else {
                "Describe the image you want to generate..."
            },
            promptValue,
            minLines = 4
        ).apply {
            minHeight = dp(120)
            doAfterTextChanged { promptValue = it?.toString().orEmpty() }
        }
        addSpaced(promptInput)

        addSpaced(label("Image Size"))
        addSpaced(dropdown(
            labelText = "",
            options = imageSizeOptions(),
            selected = selectedSize
        ) { selectedSize = it })

        addSpaced(row(gap = 10).apply {
            addSpaced(label("Resolution"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpaced(resolutionButton("1K", enabled = true, selected = true))
            addSpaced(resolutionButton("2K", enabled = false, selected = false))
            addSpaced(resolutionButton("4K", enabled = false, selected = false))
        })
        addSpaced(bodyText("Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support.", Design.Faint, 13f))

        val generating = vm.operation.value is UiState.Loading
        addSpaced(bodyText(generationHint(promptInput.text?.toString().orEmpty()), Design.Muted, 14f).apply {
            gravity = Gravity.CENTER
        })
        addSpaced(primaryButton(if (generating) "Generating..." else "Generate") {
            promptValue = promptInput.text?.toString().orEmpty()
            val sourcePaths = if (studioMode == StudioMode.ImageToImage) {
                runCatching { cacheSourceImages() }.getOrElse {
                    Toast.makeText(this@MainActivity, it.message ?: "参考图片读取失败", Toast.LENGTH_LONG).show()
                    return@primaryButton
                }
            } else {
                emptyList()
            }
            vm.generate(
                GenerateImageRequest(
                    prompt = promptValue,
                    negativePrompt = "",
                    model = activeImageModel(),
                    size = selectedSize,
                    quality = selectedQuality,
                    count = selectedCount,
                    seed = seedValue,
                    sourceType = if (studioMode == StudioMode.ImageToImage) SourceType.ImageToImage else SourceType.Generate,
                    sourceImagePaths = sourcePaths
                )
            )
        }.apply { isEnabled = !generating })
    }

    private fun studioResultsPanel(): View = studioPanel(minHeightDp = 410).apply {
        val state = vm.operation.value
        val latestImages = when (state) {
            is UiState.Success -> state.data
            else -> vm.images.value
                .filter { it.localPath.isNotBlank() && File(it.localPath).exists() }
                .sortedByDescending { it.createdAt }
                .take(8)
        }
        addSpaced(row(gap = 8).apply {
            addSpaced(column(gap = 4).apply {
                addSpaced(title("Generated Images", 22f))
                addSpaced(bodyText("You have ${vm.images.value.count { it.localPath.isNotBlank() && File(it.localPath).exists() }} creations", Design.Muted, 14f))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        when (state) {
            UiState.Loading -> {
                addGap(70)
                addSpaced(ProgressBar(this@MainActivity).apply {
                    isIndeterminate = true
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                addSpaced(bodyText("Generating with Glosc One...", Design.Muted, 15f).apply {
                    gravity = Gravity.CENTER
                })
            }
            is UiState.Error -> {
                addGap(24)
                addSpaced(note(state.message, danger = true))
            }
            else -> {
                if (latestImages.isEmpty()) {
                    addGap(72)
                    addSpaced(bodyText("▧", Design.Faint, 42f).apply {
                        gravity = Gravity.CENTER
                    })
                    addSpaced(title("No images generated yet", 20f).apply {
                        gravity = Gravity.CENTER
                    })
                    addSpaced(bodyText("Enter a prompt to generate your first AI image. Your results will appear here.", Design.Muted, 14f).apply {
                        gravity = Gravity.CENTER
                    })
                } else {
                    addSpaced(imageGrid(latestImages))
                }
            }
        }
    }

    private fun studioPanel(minHeightDp: Int = 0): LinearLayout = column(padding = 24, gap = 14).apply {
        roundedBg(Design.Surface, radiusDp = 12, strokeColor = Design.Border)
        if (minHeightDp > 0) minimumHeight = dp(minHeightDp)
    }

    private fun studioNavItem(text: String, selected: Boolean, onClick: () -> Unit): View =
        FrameLayout(this).apply {
            roundedBg(if (selected) 0xFF1A1C1D.toInt() else 0x00000000, radiusDp = 6)
            val label = bodyText(text, if (selected) Design.Fg else Design.Muted, 16f).apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(18), 0)
            }
            addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
            if (selected) {
                addView(View(this@MainActivity).apply {
                    roundedBg(Design.Accent, radiusDp = 2)
                }, FrameLayout.LayoutParams(dp(3), dp(22), Gravity.END or Gravity.CENTER_VERTICAL))
            }
            setOnClickListener { onClick() }
        }

    private fun topNavText(text: String, selected: Boolean, onClick: () -> Unit): View =
        bodyText(text, if (selected) Design.Fg else Design.Faint, 15f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
        }

    private fun sourceUploadPanel(): View = column(gap = 10).apply {
        addSpaced(FrameLayout(this@MainActivity).apply {
            dashedBg(Design.Surface2, radiusDp = 10, strokeColor = 0xFF3A3D40.toInt(), dashWidthDp = 5, dashGapDp = 4)
            addView(column(gap = 8, gravity = Gravity.CENTER).apply {
                addSpaced(bodyText("⇧", Design.Muted, 34f).apply {
                    gravity = Gravity.CENTER
                })
                addSpaced(title("Upload images", 20f).apply {
                    gravity = Gravity.CENTER
                })
                addSpaced(bodyText("Tap to select reference images", Design.Muted, 14f).apply {
                    gravity = Gravity.CENTER
                })
                addSpaced(bodyText("Supports JPG, PNG, GIF, WebP · Max $MAX_SOURCE_IMAGES images", Design.Faint, 12f).apply {
                    gravity = Gravity.CENTER
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            setOnClickListener { imagePicker.launch("image/*") }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)))
        if (sourceImageUris.isNotEmpty()) {
            addSpaced(row(gap = 10).apply {
                addSpaced(bodyText("${sourceImageUris.size} selected", Design.Muted, 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(ghostButton("Clear") {
                    sourceImageUris.clear()
                    render()
                }, LinearLayout.LayoutParams(dp(78), dp(42)))
            })
            addSpaced(sourcePreviewStrip())
        }
    }

    private fun sourcePreviewStrip(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val strip = row(gap = 8)
        sourceImageUris.forEach { uri ->
            strip.addSpaced(FrameLayout(this).apply {
                roundedBg(Design.Surface2, radiusDp = 8, strokeColor = Design.Border)
                addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(this).load(uri).centerCrop().into(this)
                }, FrameLayout.LayoutParams(dp(76), dp(76)))
            }, LinearLayout.LayoutParams(dp(76), dp(76)))
        }
        scroll.addView(strip)
        return scroll
    }

    private fun resolutionButton(text: String, enabled: Boolean, selected: Boolean): View =
        bodyText(text, if (selected) 0xFF171008.toInt() else Design.Faint, 14f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            roundedBg(
                color = when {
                    selected -> Design.Accent
                    enabled -> Design.Surface2
                    else -> 0xFF121314.toInt()
                },
                radiusDp = 7,
                strokeColor = if (selected) null else Design.Border
            )
            alpha = if (enabled || selected) 1f else 0.55f
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

    private fun imageSizeOptions(): List<Pair<String, String>> = listOf(
        "auto" to "auto",
        "1024x1024" to "1:1",
        "1024x1536" to "2:3",
        "1536x1024" to "3:2"
    )

    private fun generationHint(prompt: String): String =
        when {
            studioMode == StudioMode.ImageToImage && sourceImageUris.isEmpty() && prompt.isBlank() ->
                "Please upload an image and enter a prompt to generate"
            studioMode == StudioMode.ImageToImage && sourceImageUris.isEmpty() ->
                "Please upload an image to generate"
            prompt.isBlank() -> "Please enter a prompt to generate"
            else -> "Ready to generate"
        }

    private fun cacheSourceImages(): List<String> {
        if (sourceImageUris.isEmpty()) throw IllegalStateException("请先上传参考图片")
        val dir = File(cacheDir, "source-images").apply { mkdirs() }
        return sourceImageUris.take(MAX_SOURCE_IMAGES).mapIndexed { index, uri ->
            val ext = contentResolver.fileExtension(uri)
            val outFile = File(dir, "source_${System.currentTimeMillis()}_$index.$ext")
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法读取参考图片")
            input.use { source ->
                FileOutputStream(outFile).use { target -> source.copyTo(target) }
            }
            outFile.absolutePath
        }
    }

    private fun ContentResolver.fileExtension(uri: Uri): String {
        val fromMime = getType(uri)?.let { mime ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        }?.lowercase(Locale.US)
        if (!fromMime.isNullOrBlank()) return fromMime
        val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        return name?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.US)
            ?: "png"
    }

    private fun renderGenerate() {
        refreshSeedAfterCompletedGenerate()
        root.addView(appBar("工程模式", "生成图片", action = "历史任务") { showTasks() })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        content.addSpaced(generateConfigPanel())
        content.addSpaced(label("提示词"))
        val promptInput = input("描述你想要的画面", promptValue, minLines = 3).apply {
            minHeight = dp(86)
            doAfterTextChanged { promptValue = it?.toString().orEmpty() }
        }
        content.addSpaced(promptInput)
        val negativeInput = input("负向提示词（可选）", negativeValue).apply {
            minHeight = dp(48)
            doAfterTextChanged { negativeValue = it?.toString().orEmpty() }
        }
        content.addSpaced(negativeInput)

        val generating = vm.operation.value is UiState.Loading
        content.addSpaced(primaryButton(if (generating) "生成中..." else "生成图片") {
            promptValue = promptInput.text?.toString().orEmpty()
            negativeValue = negativeInput.text?.toString().orEmpty()
            vm.generate(
                GenerateImageRequest(
                    prompt = promptValue,
                    negativePrompt = negativeValue,
                    model = activeImageModel(),
                    size = selectedSize,
                    quality = selectedQuality,
                    count = selectedCount,
                    seed = seedValue,
                    sourceType = SourceType.Generate
                )
            )
        }.apply { isEnabled = !generating })

        renderGenerationState(content, showIdle = true)
        content.addSpaced(section("最近任务"))
        content.addSpaced(taskList(vm.recentTasks.value))
        content.addGap(8)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(AppScreen.Generate))
    }

    private fun refreshSeedAfterCompletedGenerate() {
        val state = vm.operation.value as? UiState.Success<List<ImageAsset>> ?: return
        val generated = state.data.filter { it.sourceType == SourceType.Generate }
        if (generated.isEmpty()) return
        val generationKey = generated.joinToString("|") { it.id }
        if (generationKey == seedRefreshGenerationKey) return
        seedRefreshGenerationKey = generationKey
        seedValue = nextSeedValue()
    }

    private fun nextSeedValue(): String =
        ThreadLocalRandom.current().nextInt(100000, 1_000_000).toString()

    private fun renderChat() {
        root.addView(appBar("对话模式", "创意助手", action = "新会话") { vm.newChat() })
        val wrap = column()
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val list = column(padding = 18, gap = 14)
        vm.messages.value.forEach { msg ->
            val image = msg.imageAssetId?.let { id -> vm.images.value.firstOrNull { it.id == id } }
            list.addSpaced(messageBubble(msg.role, msg.content, image))
        }
        if (vm.chatState.value is UiState.Loading) {
            list.addSpaced(messageBubble("assistant", "正在生成图片并保存到图库...", null))
        }
        scroll.addView(list)
        wrap.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        wrap.addView(composer())
        root.addView(wrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(AppScreen.Chat))
    }

    private fun renderLibrary() {
        root.addView(appBar("图片资产", "${filteredImages().size} 张作品", action = if (libraryGridMode) "列表视图" else "网格视图") {
            libraryGridMode = !libraryGridMode
            render()
        })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        content.addSpaced(row(gap = 8).apply {
            addSpaced(input("搜索提示词、标签、模型…", libraryQuery).apply {
                doAfterTextChanged { libraryQuery = it?.toString().orEmpty() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpaced(ghostButton("搜索") { render() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        })
        content.addSpaced(chipRow(
            options = listOf(
                "all" to "全部",
                "fav" to "★ 收藏",
                "Generate" to "工程",
                "Chat" to "对话",
                "Edit" to "编辑",
                "Transform" to "变换"
            ),
            selected = libraryFilter
        ) { libraryFilter = it; render() })

        val images = filteredImages()
        if (images.isEmpty()) {
            content.addGap(42)
            content.addSpaced(bodyText("还没有生成图片\n去生图页生成第一张作品", Design.Faint, 16f).apply {
                gravity = Gravity.CENTER
            })
        } else {
            content.addSpaced(if (libraryGridMode) imageGrid(images) else imageList(images))
        }
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(AppScreen.Library))
    }

    private fun renderDetail() {
        val asset = vm.selectedImageId.value?.let { id -> vm.images.value.firstOrNull { it.id == id } }
        if (asset == null) {
            vm.open(AppScreen.Library)
            return
        }
        root.addView(detailBar(asset))
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout
        content.addSpaced(detailHero(asset))
        content.addSpaced(operationGrid(asset))
        content.addSpaced(section("提示词"))
        content.addSpaced(card().apply {
            addSpaced(bodyText(asset.prompt, size = 16f))
            asset.negativePrompt?.let { addSpaced(mono("负向：$it", Design.Faint, 14f)) }
        })
        content.addSpaced(section("标签"))
        content.addSpaced(tagRow(asset))
        content.addSpaced(section("信息"))
        content.addSpaced(assetMeta(asset))
        val row = row(gap = 10)
        row.addSpaced(ghostButton("导出") {
            shareImage(asset)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addSpaced(dangerButton("删除") {
            confirm("删除这张图片？将同时清理本地文件与数据库记录。") { vm.delete(asset) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addSpaced(row)
        content.addSpaced(mono("原图不会被覆盖 · 编辑结果保存为新图片", Design.Faint, 14f).apply {
            gravity = Gravity.CENTER
        })
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderSettings() {
        if (settingsProviderId.isBlank()) {
            (activeProvider() ?: vm.providers.value.firstOrNull())?.let { hydrateSettings(it, force = true) }
        }
        root.addView(appBar("Glosc One", "API 设置", action = "返回 Studio") { vm.open(AppScreen.Generate) })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        settingsName = settingsName.ifBlank { "Glosc AI" }
        settingsBaseUrl = settingsBaseUrl.ifBlank { "https://one.gloscai.com/" }
        settingsEnabled = true
        settingsProviderType = ProviderType.OpenAi

        content.addSpaced(note("当前版本只保留 Text to Image、Image to Image 和 Glosc One 模型连接。"))
        content.addSpaced(section("Glosc One"))
        content.addSpaced(card().apply {
            addSpaced(label("Base URL"))
            addSpaced(input("https://one.gloscai.com/", settingsBaseUrl).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsBaseUrl = it?.toString().orEmpty() }
            })
            addSpaced(label("API Key"))
            addSpaced(input("sk-...", settingsKey).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsKey = it?.toString().orEmpty() }
            })
            addSpaced(keyLinkPrompt())
            val modelOptions = imageModelOptions()
            addSpaced(dropdown(
                labelText = "默认模型",
                options = modelOptions,
                selected = settingsModel.ifBlank { modelOptions.firstOrNull()?.first.orEmpty() }
            ) { settingsModel = it })
        })

        val actions = row(gap = 10)
        actions.addSpaced(ghostButton("获取模型列表") {
            val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
            vm.saveProviderAndFetchModels(
                id = settingsProviderId.ifBlank { "openai-default" },
                name = settingsName,
                baseUrl = settingsBaseUrl,
                apiKey = settingsKey.takeIf { it.isNotBlank() },
                type = settingsProviderType,
                model = settingsModel.ifBlank { fallbackModel },
                enabled = true
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addSpaced(primaryButton("保存") {
            val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
            vm.saveProvider(
                id = settingsProviderId.ifBlank { "openai-default" },
                name = settingsName,
                baseUrl = settingsBaseUrl,
                apiKey = settingsKey.takeIf { it.isNotBlank() },
                type = settingsProviderType,
                model = settingsModel.ifBlank { fallbackModel },
                enabled = settingsEnabled
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addSpaced(actions)
        renderSettingsState(content)
        content.addSpaced(note("模型列表来自 /v1/models，仅使用 categories 包含 image 的模型作为图片模型。API Key 使用 Android Keystore 加密存储，不会写入明文或日志。"))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun appBar(
        eyebrow: String,
        title: String,
        action: String? = null,
        onAction: (() -> Unit)? = null
    ): View = row(padding = 18, gap = 12).apply {
        val titleCol = column(gap = 2)
        titleCol.addSpaced(mono(eyebrow.uppercase(Locale.CHINA), Design.Accent, 14f))
        titleCol.addSpaced(title(title, 28f))
        addSpaced(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null && onAction != null) {
            addSpaced(ghostButton(action, onAction), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        }
    }

    private fun detailBar(asset: ImageAsset): View = row(padding = 18, gap = 10).apply {
        addSpaced(ghostButton("‹ 返回") { vm.open(AppScreen.Library) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        addSpaced(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        addSpaced(ghostButton(if (asset.favorite) "★" else "☆") { vm.toggleFavorite(asset) }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addSpaced(ghostButton("分享") { Toast.makeText(this@MainActivity, "可通过系统分享面板发送图片", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
    }

    private fun scrollBody(): ScrollView {
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(column(padding = 18, gap = 14))
        return scroll
    }

    private fun section(text: String) = title(text, 20f).apply {
        setPadding(0, dp(8), 0, 0)
    }

    private fun generateConfigPanel(): View {
        val provider = activeProvider()
        return card(padding = 10).apply {
            val modelRow = row(gap = 8, gravity = Gravity.BOTTOM)
            modelRow.addSpaced(dropdown(
                labelText = "模型",
                options = generateModelOptions(),
                selected = activeImageModel()
            ) { generateModel = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            modelRow.addSpaced(ghostButton("设置") { vm.open(AppScreen.Settings) }, LinearLayout.LayoutParams(dp(72), dp(64)))
            addSpaced(modelRow)

            val params = row(gap = 8, gravity = Gravity.BOTTOM)
            params.addSpaced(dropdown(
                labelText = "尺寸",
                options = listOf("1024x1024" to "1024²", "1024x1536" to "1024×1536", "1536x1024" to "1536×1024"),
                selected = selectedSize
            ) { selectedSize = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            params.addSpaced(dropdown(
                labelText = "质量",
                options = listOf("medium" to "标准", "high" to "高清", "auto" to "自动"),
                selected = selectedQuality
            ) { selectedQuality = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f))
            params.addSpaced(dropdown(
                labelText = "数量",
                options = listOf("1" to "1", "2" to "2", "4" to "4"),
                selected = selectedCount.toString()
            ) { selectedCount = it.toInt() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f))
            params.addSpaced(column(gap = 4).apply {
                addSpaced(label("种子").apply { textSize = 12f })
                addSpaced(input("随机", seedValue, numeric = true).apply {
                    textSize = 14f
                    minHeight = dp(46)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    typeface = android.graphics.Typeface.MONOSPACE
                    doAfterTextChanged { seedValue = it?.toString().orEmpty() }
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpaced(params)

            addSpaced(mono("${provider?.name ?: "Glosc AI"} · ${provider.displayModel()}", Design.Faint, 12f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    private fun renderGenerationState(content: LinearLayout, showIdle: Boolean = false) {
        when (val state = vm.operation.value) {
            UiState.Idle -> {
                if (showIdle) {
                    content.addSpaced(section("结果"))
                    content.addSpaced(card(padding = 12).apply {
                        addSpaced(bodyText("生成结果会显示在这里", Design.Faint, 15f).apply {
                            gravity = Gravity.CENTER
                        })
                    })
                }
            }
            UiState.Loading -> {
                content.addSpaced(section("结果"))
                content.addSpaced(card().apply {
                    addSpaced(mono("运行中", Design.Accent))
                    addSpaced(bodyText("正在请求图片模型并保存到本地文件。", Design.Muted))
                })
            }
            is UiState.Error -> {
                content.addSpaced(section("结果"))
                content.addSpaced(note(state.message, danger = true))
            }
            is UiState.Success -> {
                content.addSpaced(section("结果"))
                content.addSpaced(imageGrid(state.data))
            }
        }
    }

    private fun taskList(tasks: List<GenerationTask>): View = card().apply {
        if (tasks.isEmpty()) {
            addSpaced(bodyText("暂无任务记录", Design.Faint))
            return@apply
        }
        tasks.take(5).forEach { task ->
            val color = when (task.status) {
                TaskStatus.Success -> Design.Ok
                TaskStatus.Running -> Design.Accent
                TaskStatus.Failed -> Design.Danger
                TaskStatus.Pending -> Design.Warn
                TaskStatus.Cancelled -> Design.Faint
            }
            addSpaced(row(gap = 8).apply {
                addSpaced(mono("${task.taskType.label} · ${formatTime(task.createdAt)}", Design.Muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(mono(task.status.label, color))
            })
        }
    }

    private fun messageBubble(role: String, text: String, image: ImageAsset?): View {
        val outer = column(gap = 6).apply {
            gravity = if (role == "user") Gravity.END else Gravity.START
        }
        if (role != "user") outer.addSpaced(mono("助手", Design.Faint, 14f))
        val bubble = bodyText(text, if (role == "user") 0xFF102025.toInt() else Design.Fg, 16f).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            roundedBg(if (role == "user") Design.Accent else Design.Surface, radiusDp = 16, strokeColor = if (role == "user") null else Design.Border)
            maxWidth = resources.displayMetrics.widthPixels - dp(86)
        }
        outer.addSpaced(bubble, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        image?.let {
            outer.addSpaced(imageTile(it, 210), LinearLayout.LayoutParams(dp(210), dp(210)))
        }
        return outer
    }

    private fun composer(): View {
        val row = row(padding = 12, gap = 8).apply {
            roundedBg(0xEE20232A.toInt(), radiusDp = 0, strokeColor = Design.Border)
        }
        val sending = vm.chatState.value is UiState.Loading
        val input = input("继续描述或修改…", "", minLines = 1)
        input.isEnabled = !sending
        row.addSpaced(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addSpaced(primaryButton(if (sending) "生成中" else "发送") {
            val value = input.text?.toString().orEmpty()
            if (value.isNotBlank()) {
                input.setText("")
                vm.sendChat(value)
            }
        }.apply { isEnabled = !sending }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        return row
    }

    private fun imageGrid(images: List<ImageAsset>): GridLayout = GridLayout(this).apply {
        columnCount = 2
        images.forEachIndexed { index, asset ->
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(if (index % 3 == 0) 230 else 190)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(6))
            }
            addView(imageTile(asset, if (index % 3 == 0) 230 else 190), params)
        }
    }

    private fun imageTile(asset: ImageAsset, heightDp: Int): View = FrameLayout(this).apply {
        roundedBg(Design.Surface2, radiusDp = 12)
        val image = ImageView(this@MainActivity).apply { loadAsset(asset) }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)))
        val badge = mono(asset.prompt.take(18), Design.Fg, 13f).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            roundedBg(0xAA101218.toInt(), radiusDp = 6)
        }
        addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(8), dp(8), dp(8), dp(8))
        })
        if (asset.favorite) {
            addView(bodyText("★", Design.Warn, 18f), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                setMargins(dp(8), dp(6), dp(8), dp(8))
            })
        }
        setOnClickListener { vm.openDetail(asset.id) }
    }

    private fun imageList(images: List<ImageAsset>): View = column(gap = 10).apply {
        images.forEach { asset ->
            addSpaced(row(padding = 10, gap = 12).apply {
                roundedBg(Design.Surface, radiusDp = 12, strokeColor = Design.Border)
                addSpaced(FrameLayout(this@MainActivity).apply {
                    roundedBg(Design.Surface2, radiusDp = 10)
                    addView(ImageView(this@MainActivity).apply { loadAsset(asset) }, FrameLayout.LayoutParams(dp(76), dp(76)))
                }, LinearLayout.LayoutParams(dp(76), dp(76)))
                addSpaced(column(gap = 4).apply {
                    addSpaced(bodyText(asset.prompt, Design.Fg, 16f).apply {
                        maxLines = 2
                    })
                    addSpaced(mono("${asset.sourceType.label} · ${asset.model}", Design.Faint, 14f))
                    addSpaced(mono(formatDate(asset.createdAt), Design.Faint, 14f))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(bodyText(if (asset.favorite) "★" else "☆", if (asset.favorite) Design.Warn else Design.Faint, 18f))
                setOnClickListener { vm.openDetail(asset.id) }
            })
        }
    }

    private fun detailHero(asset: ImageAsset): View = FrameLayout(this).apply {
        roundedBg(Design.Surface2, radiusDp = 14)
        val image = ImageView(this@MainActivity).apply { loadAsset(asset) }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))
        val badge = mono("${asset.width}×${asset.height} · ${asset.model}", Design.Fg, 14f).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            roundedBg(0xAA101218.toInt(), radiusDp = 7)
        }
        addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(10), dp(10), dp(10), dp(10))
        })
    }

    private fun operationGrid(asset: ImageAsset): View = GridLayout(this).apply {
        columnCount = 4
        listOf(
            SourceType.Edit to "局部重绘",
            SourceType.Edit to "变体",
            SourceType.Transform to "超分",
            SourceType.Edit to "扩图"
        ).forEach { (type, label) ->
            val item = bodyText(label, Design.Fg, 15f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(14), dp(5), dp(14))
                roundedBg(Design.Surface, radiusDp = 10, strokeColor = Design.Border)
                setOnClickListener { showEditDialog(asset, type, label) }
            }
            addView(item, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun tagRow(asset: ImageAsset): View {
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = row(gap = 8)
        asset.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
            row.addSpaced(chip(it, selected = it == "动物" || it == "电影感"))
        }
        row.addSpaced(chip("+ 添加") { askText("添加标签") { tag -> vm.addTag(asset, tag) } })
        scroller.addView(row)
        return scroller
    }

    private fun assetMeta(asset: ImageAsset): View = card().apply {
        val rows = listOf(
            "来源" to asset.sourceType.label,
            "模型" to asset.model,
            "尺寸" to "${asset.width} × ${asset.height}",
            "种子" to (asset.seed ?: "随机"),
            "创建" to formatDate(asset.createdAt),
            "存储" to if (asset.localPath.isBlank()) "示例占位" else "本地文件"
        )
        rows.forEach { (k, v) ->
            addSpaced(row(gap = 12).apply {
                addSpaced(mono(k, Design.Faint, 14f), LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT))
                addSpaced(bodyText(v, Design.Fg, 16f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun providerListItem(provider: ApiProvider): View = row(padding = 16, gap = 12).apply {
        roundedBg(if (provider.enabled) 0x222FD7E8 else Design.Surface, radiusDp = 14, strokeColor = if (provider.enabled) Design.Accent else Design.Border)
        val logo = bodyText(provider.name.take(2).uppercase(Locale.CHINA), if (provider.enabled) Design.Ok else Design.Accent2, 16f).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            roundedBg(if (provider.enabled) 0x2270E09A else 0x22D18CFF, radiusDp = 11)
        }
        addSpaced(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        addSpaced(column(gap = 3).apply {
            addSpaced(bodyText(provider.name, Design.Fg, 17f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
            addSpaced(mono("${provider.providerType.name.lowercase(Locale.CHINA)} · ${provider.baseUrl.removePrefix("https://")} · ${provider.displayModel()}", Design.Faint, 14f))
            provider.lastStatus?.let { addSpaced(mono(it, Design.Ok, 14f)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addSpaced(mono(if (provider.enabled) "已启用" else "未启用", if (provider.enabled) Design.Ok else Design.Faint, 14f))
        setOnClickListener {
            hydrateSettings(provider, force = true)
            render()
        }
    }

    private fun note(text: String, danger: Boolean = false): View = row(padding = 14, gap = 8).apply {
        roundedBg(if (danger) 0x22FF6F61 else 0x225ED7E8, radiusDp = 10, strokeColor = if (danger) Design.Danger else Design.Accent)
        addSpaced(
            bodyText(text, if (danger) Design.Danger else Design.Muted, 14f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun renderSettingsState(content: LinearLayout) {
        when (val state = vm.settingsState.value) {
            UiState.Idle -> Unit
            UiState.Loading -> content.addSpaced(note("正在处理..."))
            is UiState.Success -> content.addSpaced(note(state.data))
            is UiState.Error -> content.addSpaced(note(state.message, danger = true))
        }
    }

    private fun updatePanel(): View = card().apply {
        addSpaced(mono("当前版本 ${currentVersionName()}", Design.Faint, 14f))
        when (val state = vm.updateState.value) {
            UiState.Idle -> {
                addSpaced(bodyText("从 GitHub Releases 检查最新 APK。", Design.Muted, 14f))
                addSpaced(primaryButton("检查更新") { vm.checkForUpdates() })
            }
            UiState.Loading -> {
                addSpaced(note("正在检查或下载更新..."))
            }
            is UiState.Error -> {
                addSpaced(note(state.message, danger = true))
                addSpaced(primaryButton("重新检查") { vm.checkForUpdates() })
            }
            is UiState.Success -> {
                val status = state.data
                val info = status.info
                addSpaced(note(status.message))
                if (info != null) {
                    addSpaced(mono("最新版本 ${info.tagName.ifBlank { info.latestVersionName }}", Design.Faint, 14f))
                    if (info.apkAssetName.isNotBlank()) {
                        addSpaced(mono("${info.apkAssetName} · ${formatBytes(info.apkSizeBytes)}", Design.Faint, 14f))
                    }
                    info.releaseNotes.takeIf { it.isNotBlank() }?.let {
                        addSpaced(bodyText(it.take(220), Design.Muted, 14f).apply {
                            maxLines = 4
                            ellipsize = TextUtils.TruncateAt.END
                        })
                    }
                }
                val actions = row(gap = 10)
                actions.addSpaced(ghostButton("重新检查") { vm.checkForUpdates() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                when {
                    !status.downloadedApkPath.isNullOrBlank() -> {
                        actions.addSpaced(primaryButton("安装更新") { installUpdateApk(status.downloadedApkPath) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    info?.updateAvailable == true -> {
                        actions.addSpaced(primaryButton("下载并安装") { vm.downloadUpdate(info) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    info?.htmlUrl?.isNotBlank() == true -> {
                        actions.addSpaced(ghostButton("发布页") { openUrl(info.htmlUrl) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                }
                addSpaced(actions)
            }
        }
    }

    private fun chipRow(
        options: List<Pair<String, String>>,
        selected: String,
        onSelect: (String) -> Unit
    ): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = row(gap = 8)
        options.forEach { (value, label) ->
            row.addSpaced(chip(label, selected = value == selected) { onSelect(value) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun dropdown(
        labelText: String,
        options: List<Pair<String, String>>,
        selected: String,
        onSelect: (String) -> Unit
    ): View {
        val values = options.ifEmpty { listOf("" to "无可用选项") }
        val selectedIndex = values.indexOfFirst { it.first == selected }.takeIf { it >= 0 } ?: 0
        val labels = values.map { it.second }
        var lastValue = values[selectedIndex].first
        return column(gap = 4).apply {
            if (labelText.isNotBlank()) {
                addSpaced(label(labelText).apply { textSize = 12f })
            }
            addSpaced(Spinner(this@MainActivity).apply {
                adapter = object : ArrayAdapter<String>(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    labels
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        return styleSpinnerText(super.getView(position, convertView, parent), dropdown = false)
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        return styleSpinnerText(super.getDropDownView(position, convertView, parent), dropdown = true)
                    }
                }.apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                roundedBg(Design.Surface2, radiusDp = 9, strokeColor = Design.Border)
                minimumHeight = dp(46)
                setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Design.Surface2))
                setSelection(selectedIndex, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val value = values[position].first
                        if (value != lastValue) {
                            lastValue = value
                            onSelect(value)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }
    }

    private fun styleSpinnerText(view: View, dropdown: Boolean): View {
        (view as? TextView)?.apply {
            setTextColor(Design.Fg)
            textSize = 14f
            typeface = if (dropdown) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(if (dropdown) 12 else 8), dp(10), dp(if (dropdown) 12 else 8))
            if (!dropdown) text = "${text}  ▾"
            if (dropdown) setBackgroundColor(Design.Surface2)
        }
        return view
    }

    private fun bottomNav(current: AppScreen): View = row(padding = 10, gap = 4).apply {
        roundedBg(0xEE20232A.toInt(), radiusDp = 0, strokeColor = Design.Border)
        listOf(
            AppScreen.Generate to "生图",
            AppScreen.Chat to "对话",
            AppScreen.Library to "图片库",
            AppScreen.Settings to "设置"
        ).forEach { (screen, label) ->
            val item = bodyText(label, if (screen == current) Design.Accent else Design.Faint, 14f).apply {
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { vm.open(screen) }
            }
            addSpaced(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun activeProvider(): ApiProvider? = vm.providers.value.firstOrNull { it.enabled } ?: vm.providers.value.firstOrNull()

    private fun activeImageModel(): String {
        val provider = activeProvider()
        val models = provider?.imageModels.orEmpty()
        return generateModel.takeIf { selected -> selected.isNotBlank() && (models.isEmpty() || selected in models) }
            ?: provider?.defaultModel
            ?.ifBlank { provider.imageModels.firstOrNull().orEmpty() }
            .orEmpty()
    }

    private fun hydrateSettings(provider: ApiProvider, force: Boolean = false) {
        if (!force && settingsProviderId == provider.id) return
        settingsProviderId = provider.id
        settingsName = provider.name
        settingsBaseUrl = provider.baseUrl
        settingsKey = ""
        settingsModel = provider.defaultModel.ifBlank { provider.imageModels.firstOrNull().orEmpty() }
        settingsEnabled = provider.enabled
        settingsProviderType = provider.providerType
    }

    private fun imageModelOptions(): List<Pair<String, String>> {
        val provider = vm.providers.value.firstOrNull { it.id == settingsProviderId }
            ?: if (settingsProviderId.isBlank()) activeProvider() else null
        val models = provider?.imageModels.orEmpty()
        val selected = settingsModel.ifBlank { provider?.defaultModel.orEmpty() }
        val options = (models + selected)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (options.isEmpty()) {
            listOf("" to "先获取模型列表")
        } else {
            options.map { it to it }
        }
    }

    private fun generateModelOptions(): List<Pair<String, String>> {
        val provider = activeProvider()
        val options = (provider?.imageModels.orEmpty() + provider?.defaultModel.orEmpty() + generateModel)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (options.isEmpty()) {
            listOf("" to "先获取模型")
        } else {
            options.map { it to it }
        }
    }

    private fun handleUpdateState(state: UiState<AppUpdateStatus>) {
        val status = (state as? UiState.Success)?.data ?: return
        status.downloadedApkPath?.takeIf { it.isNotBlank() }?.let { path ->
            if (path != launchedUpdateApkPath) {
                launchedUpdateApkPath = path
                installUpdateApk(path)
            }
            return
        }
        val info = status.info ?: return
        if (info.updateAvailable && info.tagName != shownUpdateTag) {
            shownUpdateTag = info.tagName
            showUpdateDialog(info)
        }
    }

    private fun showUpdateDialog(info: AppUpdateInfo) {
        val notes = info.releaseNotes.take(300).ifBlank { "此版本没有填写发布说明。" }
        AlertDialog.Builder(this)
            .setTitle("发现新版本 ${info.tagName}")
            .setMessage(
                "当前版本：${info.currentVersionName}\n" +
                    "安装包：${info.apkAssetName} · ${formatBytes(info.apkSizeBytes)}\n\n" +
                    notes
            )
            .setPositiveButton("下载并安装") { _, _ -> vm.downloadUpdate(info) }
            .setNeutralButton("发布页") { _, _ -> openUrl(info.htmlUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun installUpdateApk(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "更新包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("允许安装更新")
                .setMessage("需要先允许 GloscAI Images 安装 APK。授权后回到设置页，点击“安装更新”。")
                .setPositiveButton("去授权") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "无法打开系统安装器：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "无法打开链接：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun shareImage(asset: ImageAsset) {
        val file = asset.localPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (file == null || !file.exists()) {
            Toast.makeText(this, "示例图暂无可导出的本地文件", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "导出图片"))
    }

    private fun ApiProvider?.displayModel(): String {
        if (this == null) return "未获取图片模型"
        return defaultModel.ifBlank { imageModels.firstOrNull().orEmpty() }.ifBlank { "未获取图片模型" }
    }

    private fun keyLinkPrompt(): View {
        val text = "从 这里 获取 key"
        val span = SpannableString(text)
        val start = text.indexOf("这里")
        span.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://one.gloscai.com/keys")))
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Design.Accent
                    ds.isUnderlineText = true
                    ds.typeface = Typeface.DEFAULT_BOLD
                }
            },
            start,
            start + 2,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return bodyText("", Design.Muted, 14f).apply {
            setText(span)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = android.graphics.Color.TRANSPARENT
        }
    }

    private fun filteredImages(): List<ImageAsset> {
        val q = libraryQuery.trim().lowercase(Locale.CHINA)
        return vm.images.value.filter { asset ->
            asset.localPath.isNotBlank() && File(asset.localPath).exists()
        }.filter { asset ->
            val okFilter = when (libraryFilter) {
                "all" -> true
                "fav" -> asset.favorite
                else -> asset.sourceType.name == libraryFilter
            }
            val haystack = "${asset.prompt} ${asset.tags} ${asset.model}".lowercase(Locale.CHINA)
            okFilter && (q.isBlank() || haystack.contains(q))
        }
    }

    private fun showEditDialog(asset: ImageAsset, type: SourceType, label: String) {
        val input = input("例如：把背景换成晴朗白天，保留主体姿态", "把氛围调得更冷，加入微雪", minLines = 3)
        AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage("描述要修改的区域与目标效果，将生成一张新图片。")
            .setView(input)
            .setPositiveButton("应用并生成") { _, _ -> vm.edit(asset, input.text?.toString().orEmpty(), type) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTasks() {
        val text = vm.recentTasks.value.take(8).joinToString("\n") {
            "${it.taskType.label} · ${it.status.label} · ${formatTime(it.createdAt)}"
        }.ifBlank { "暂无任务" }
        AlertDialog.Builder(this)
            .setTitle("任务历史")
            .setMessage(text)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun confirm(message: String, onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("确认") { _, _ -> onOk() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun askText(title: String, onOk: (String) -> Unit) {
        val input = input(title)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                input.text?.toString()?.takeIf { it.isNotBlank() }?.let(onOk)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(time))

    private fun formatDate(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(time))

    private fun currentVersionName(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return info.versionName ?: "0.0.0"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "未知大小"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit += 1
        }
        return if (unit == 0) "${bytes}B" else String.format(Locale.US, "%.1f%s", value, units[unit])
    }
}
