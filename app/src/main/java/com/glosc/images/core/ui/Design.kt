package com.glosc.images.core.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.glosc.images.domain.model.ImageAsset
import java.io.File

object Design {
    const val Bg = 0xFF070809.toInt()
    const val Surface = 0xFF101112.toInt()
    const val Surface2 = 0xFF181A1B.toInt()
    const val Fg = 0xFFF3F5F8.toInt()
    const val Muted = 0xFFA2A5AA.toInt()
    const val Faint = 0xFF72767D.toInt()
    const val Border = 0xFF2A2D30.toInt()
    const val Accent = 0xFFFF8A1F.toInt()
    const val Accent2 = 0xFFFFD42A.toInt()
    const val Ok = 0xFF70E09A.toInt()
    const val Warn = 0xFFFFD42A.toInt()
    const val Danger = 0xFFFF6F61.toInt()

    const val TextLabel = 14f
    const val TextTitle = 24f
    const val TextBody = 16f
    const val TextMeta = 14f
    const val TextInput = 16f
    const val TextChip = 14f
    const val TextButton = 16f
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun View.roundedBg(
    color: Int,
    radiusDp: Int = 12,
    strokeColor: Int? = null,
    strokeDp: Int = 1
) {
    background = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(context.dp(strokeDp), it) }
    }
}

fun View.dashedBg(
    color: Int,
    radiusDp: Int = 12,
    strokeColor: Int = Design.Border,
    strokeDp: Int = 1,
    dashWidthDp: Int = 6,
    dashGapDp: Int = 4
) {
    background = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp).toFloat()
        setStroke(
            context.dp(strokeDp),
            strokeColor,
            context.dp(dashWidthDp).toFloat(),
            context.dp(dashGapDp).toFloat()
        )
    }
}

fun Context.column(
    padding: Int = 0,
    gap: Int = 0,
    gravity: Int = Gravity.NO_GRAVITY
) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    this.gravity = gravity
    tag = gap
}

fun Context.row(
    padding: Int = 0,
    gap: Int = 0,
    gravity: Int = Gravity.CENTER_VERTICAL
) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    this.gravity = gravity
    tag = gap
}

fun LinearLayout.addGap(heightDp: Int) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(heightDp)))
}

fun LinearLayout.addSpaced(view: View, params: LinearLayout.LayoutParams? = null) {
    val gap = (tag as? Int).orZero()
    if (childCount > 0 && gap > 0) {
        val spacerSize = context.dp(gap)
        if (orientation == LinearLayout.VERTICAL) {
            addView(View(context), LinearLayout.LayoutParams(1, spacerSize))
        } else {
            addView(View(context), LinearLayout.LayoutParams(spacerSize, 1))
        }
    }
    val defaultParams = if (orientation == LinearLayout.VERTICAL) {
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    } else {
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    addView(view, params ?: defaultParams)
}

fun Context.label(text: String) = TextView(this).apply {
    this.text = text
    setTextColor(Design.Muted)
    textSize = Design.TextLabel
    typeface = Typeface.DEFAULT_BOLD
}

fun Context.title(text: String, size: Float = Design.TextTitle) = TextView(this).apply {
    this.text = text
    setTextColor(Design.Fg)
    textSize = size
    typeface = Typeface.DEFAULT_BOLD
}

fun Context.bodyText(text: String, color: Int = Design.Fg, size: Float = Design.TextBody) = TextView(this).apply {
    this.text = text
    setTextColor(color)
    textSize = size
    setLineSpacing(0f, 1.18f)
}

fun Context.mono(text: String, color: Int = Design.Muted, size: Float = Design.TextMeta) = TextView(this).apply {
    this.text = text
    setTextColor(color)
    textSize = size
    typeface = Typeface.MONOSPACE
}

fun Context.card(padding: Int = 16) = column(padding = padding, gap = 10).apply {
    roundedBg(Design.Surface, radiusDp = 14, strokeColor = Design.Border)
}

fun Context.input(
    hint: String,
    value: String = "",
    minLines: Int = 1,
    numeric: Boolean = false
) = EditText(this).apply {
    setText(value)
    setHint(hint)
    setTextColor(Design.Fg)
    setHintTextColor(Design.Faint)
    textSize = Design.TextInput
    setMinLines(minLines)
    setMaxLines(if (minLines > 1) 6 else 1)
    gravity = Gravity.TOP or Gravity.START
    setPadding(dp(14), dp(12), dp(14), dp(12))
    minHeight = dp(if (minLines > 1) 96 else 52)
    inputType = if (numeric) {
        InputType.TYPE_CLASS_NUMBER
    } else if (minLines > 1) {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    } else {
        InputType.TYPE_CLASS_TEXT
    }
    roundedBg(Design.Surface2, radiusDp = 10, strokeColor = Design.Border)
}

fun Context.chip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) = TextView(this).apply {
    this.text = text
    setTextColor(if (selected) Design.Accent else Design.Muted)
    textSize = Design.TextChip
    typeface = Typeface.MONOSPACE
    gravity = Gravity.CENTER
    setPadding(dp(14), dp(9), dp(14), dp(9))
    roundedBg(
        color = if (selected) 0x223FD7E8 else Design.Surface,
        radiusDp = 999,
        strokeColor = if (selected) Design.Accent else Design.Border
    )
    onClick?.let { setOnClickListener { it() } }
}

fun Context.primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(0xFF171008.toInt())
    textSize = Design.TextButton
    typeface = Typeface.DEFAULT_BOLD
    minHeight = dp(50)
    roundedBg(Design.Accent, radiusDp = 10)
    setOnClickListener { onClick() }
}

fun Context.ghostButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(Design.Fg)
    textSize = Design.TextButton
    minHeight = dp(50)
    roundedBg(Design.Surface2, radiusDp = 10, strokeColor = Design.Border)
    setOnClickListener { onClick() }
}

fun Context.dangerButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(Design.Danger)
    textSize = Design.TextButton
    minHeight = dp(50)
    roundedBg(0x22FF6F61, radiusDp = 10, strokeColor = Design.Danger)
    setOnClickListener { onClick() }
}

fun Context.artPlaceholder(key: String): GradientDrawable {
    val colors = when (key) {
        "g1" -> intArrayOf(0xFF294D5F.toInt(), 0xFF1E252F.toInt())
        "g2" -> intArrayOf(0xFF40566D.toInt(), 0xFF232B35.toInt())
        "g3" -> intArrayOf(0xFF2E5A68.toInt(), 0xFF171D25.toInt())
        "g4" -> intArrayOf(0xFF365D58.toInt(), 0xFF222934.toInt())
        "g5" -> intArrayOf(0xFF5A6076.toInt(), 0xFF252B35.toInt())
        else -> intArrayOf(0xFF30475C.toInt(), 0xFF161B22.toInt())
    }
    return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        cornerRadius = dp(12).toFloat()
    }
}

fun ImageView.loadAsset(asset: ImageAsset) {
    scaleType = ImageView.ScaleType.CENTER_CROP
    background = context.artPlaceholder(asset.placeholderKey)
    if (asset.localPath.isNotBlank() && File(asset.localPath).exists()) {
        Glide.with(this)
            .load(Uri.fromFile(File(asset.localPath)))
            .placeholder(context.artPlaceholder(asset.placeholderKey))
            .error(context.artPlaceholder(asset.placeholderKey))
            .centerCrop()
            .into(this)
    } else {
        setImageDrawable(null)
    }
}

fun sourceLabel(source: SourceTypeName) = source.label

private fun Int?.orZero() = this ?: 0

typealias SourceTypeName = com.glosc.images.domain.model.SourceType
