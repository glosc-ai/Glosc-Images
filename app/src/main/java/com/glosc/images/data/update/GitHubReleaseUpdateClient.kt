package com.glosc.images.data.update

import com.glosc.images.core.common.AppException
import com.glosc.images.domain.model.AppUpdateInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class GitHubReleaseUpdateClient(
    private val updatesDir: File,
    private val owner: String = "glosc-ai",
    private val repo: String = "Glosc-Images",
    private val baseUrl: String = "https://api.github.com/",
    private val httpClient: OkHttpClient = defaultClient()
) {
    fun checkLatest(currentVersionName: String): AppUpdateInfo {
        val url = "${baseUrl.trimEnd('/')}/repos/$owner/$repo/releases/latest"
        val response = httpClient.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Glosc-Images-Android")
                .build()
        ).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw AppException("获取最新版本失败：HTTP ${it.code} ${it.message}")
            }
            val release = JsonParser.parseString(body).asJsonObject
            val tagName = release.string("tag_name")
            val latestVersionName = tagName.normalizedVersionName()
            val releaseName = release.string("name").ifBlank { tagName }
            val releaseNotes = release.string("body")
            val htmlUrl = release.string("html_url")
            val apkAsset = release.arrayObjects("assets")
                .filter { asset -> asset.string("name").endsWith(".apk", ignoreCase = true) }
                .sortedWith(compareByDescending<JsonObject> { asset ->
                    val name = asset.string("name").lowercase(Locale.US)
                    when {
                        "release" in name -> 3
                        "universal" in name -> 2
                        "debug" in name -> 1
                        else -> 0
                    }
                }.thenByDescending { asset -> asset.long("size") })
                .firstOrNull()
            val apkName = apkAsset?.string("name").orEmpty()
            val apkUrl = apkAsset?.string("browser_download_url").orEmpty()
            val apkSize = apkAsset?.long("size") ?: 0L
            val newer = compareVersions(latestVersionName, currentVersionName) > 0
            val updateAvailable = newer && apkUrl.isNotBlank()
            val message = when {
                tagName.isBlank() -> "没有找到有效的版本号"
                !newer -> "当前已经是最新版本：$currentVersionName"
                apkUrl.isBlank() -> "发现新版本 $tagName，但 Release 没有附带 APK 安装包"
                else -> "发现新版本 $tagName"
            }
            return AppUpdateInfo(
                currentVersionName = currentVersionName,
                latestVersionName = latestVersionName,
                tagName = tagName,
                releaseName = releaseName,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
                apkAssetName = apkName,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = apkSize,
                updateAvailable = updateAvailable,
                message = message
            )
        }
    }

    fun downloadApk(update: AppUpdateInfo): File {
        if (!update.updateAvailable || update.apkDownloadUrl.isBlank()) {
            throw AppException("没有可下载的 APK 更新包")
        }
        updatesDir.mkdirs()
        val safeTag = update.tagName.ifBlank { update.latestVersionName }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(updatesDir, "glosc-images-$safeTag.apk")
        val temp = File(updatesDir, "${target.name}.download")
        val response = httpClient.newCall(
            Request.Builder()
                .url(update.apkDownloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "Glosc-Images-Android")
                .build()
        ).execute()
        response.use {
            if (!it.isSuccessful) {
                throw AppException("下载更新失败：HTTP ${it.code} ${it.message}")
            }
            val body = it.body ?: throw AppException("下载更新失败：响应为空")
            temp.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
        }
        if (temp.length() <= 0L) {
            temp.delete()
            throw AppException("下载更新失败：APK 文件为空")
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    companion object {
        internal fun compareVersions(latest: String, current: String): Int {
            val a = latest.versionParts()
            val b = current.versionParts()
            val max = maxOf(a.size, b.size)
            for (index in 0 until max) {
                val left = a.getOrElse(index) { 0 }
                val right = b.getOrElse(index) { 0 }
                if (left != right) return left.compareTo(right)
            }
            return 0
        }

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

private fun String.normalizedVersionName(): String =
    trim().removePrefix("v").removePrefix("V")

private fun String.versionParts(): List<Int> =
    normalizedVersionName()
        .split('.', '-', '_')
        .mapNotNull { token -> token.takeWhile { it.isDigit() }.takeIf { it.isNotBlank() }?.toIntOrNull() }

private fun JsonObject.string(name: String): String =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString.orEmpty()

private fun JsonObject.long(name: String): Long =
    get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asLong ?: 0L

private fun JsonObject.arrayObjects(name: String): List<JsonObject> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
        .orEmpty()
