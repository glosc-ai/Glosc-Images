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
    const val Bg = 0xFF16181E.toInt()
    const val Surface = 0xFF20232A.toInt()
    const val Surface2 = 0xFF282C34.toInt()
    const val Fg = 0xFFF3F5F8.toInt()
    const val Muted = 0xFFA2AAB8.toInt()
    const val Faint = 0xFF747C89.toInt()
    const val Border = 0xFF3D4350.toInt()
    const val Accent = 0xFF5ED7E8.toInt()
    const val Accent2 = 0xFFD18CFF.toInt()
    const val Ok = 0xFF70E09A.toInt()
    const val Warn = 0xFFEAC95E.toInt()
    const val Danger = 0xFFFF6F61.toInt()
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
    addView(view, params ?: LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ))
}

fun Context.label(text: String) = TextView(this).apply {
    this.text = text
    setTextColor(Design.Muted)
    textSize = 12f
    typeface = Typeface.DEFAULT_BOLD
}

fun Context.title(text: String, size: Float = 22f) = TextView(this).apply {
    this.text = text
    setTextColor(Design.Fg)
    textSize = size
    typeface = Typeface.DEFAULT_BOLD
}

fun Context.bodyText(text: String, color: Int = Design.Fg, size: Float = 14f) = TextView(this).apply {
    this.text = text
    setTextColor(color)
    textSize = size
    setLineSpacing(0f, 1.12f)
}

fun Context.mono(text: String, color: Int = Design.Muted, size: Float = 12f) = TextView(this).apply {
    this.text = text
    setTextColor(color)
    textSize = size
    typeface = Typeface.MONOSPACE
}

fun Context.card(padding: Int = 14) = column(padding = padding, gap = 8).apply {
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
    textSize = 14f
    setMinLines(minLines)
    setMaxLines(if (minLines > 1) 6 else 1)
    gravity = Gravity.TOP or Gravity.START
    setPadding(dp(12), dp(10), dp(12), dp(10))
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
    textSize = 12f
    typeface = Typeface.MONOSPACE
    gravity = Gravity.CENTER
    setPadding(dp(12), dp(7), dp(12), dp(7))
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
    setTextColor(0xFF102025.toInt())
    textSize = 15f
    typeface = Typeface.DEFAULT_BOLD
    roundedBg(Design.Accent, radiusDp = 10)
    setOnClickListener { onClick() }
}

fun Context.ghostButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(Design.Fg)
    textSize = 14f
    roundedBg(Design.Surface2, radiusDp = 10, strokeColor = Design.Border)
    setOnClickListener { onClick() }
}

fun Context.dangerButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setTextColor(Design.Danger)
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
