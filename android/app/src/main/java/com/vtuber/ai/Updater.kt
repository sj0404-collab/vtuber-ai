package com.vtuber.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class Updater(private val context: Context) {

    interface Listener {
        fun onUpdateFound(release: Release)

        fun onUpToDate(version: String)

        fun onFailed(message: String)
    }

    data class Release(
        val version: String,
        val versionCode: Int,
        val apkUrl: String,
        val apkName: String,
        val sizeBytes: Long,
        val notes: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun currentVersion(): String = "${versionName()} (${installedVersionCode()})"

    fun check(listener: Listener) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "check failed", e)
                listener.onFailed(e.message ?: "сеть недоступна")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.code == 404) {
                        listener.onUpToDate(versionName())
                        return
                    }
                    if (!it.isSuccessful) {
                        listener.onFailed("HTTP ${it.code}")
                        return
                    }
                    val release = parse(it.body?.string().orEmpty())
                    if (release == null) {
                        listener.onFailed("не удалось разобрать релиз")
                        return
                    }
                    if (release.versionCode > installedVersionCode() ||
                        (release.versionCode == 0 && release.version != versionName())
                    ) {
                        listener.onUpdateFound(release)
                    } else {
                        listener.onUpToDate(versionName())
                    }
                }
            }
        })
    }

    fun download(
        release: Release,
        onProgress: (Int) -> Unit,
        onDone: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val target = File(updateDir(), "vtuber-ai-${release.version}.apk")
        val request = Request.Builder().url(release.apkUrl).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "download failed", e)
                onError(e.message ?: "сеть недоступна")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("HTTP ${it.code}")
                        return
                    }
                    val body = it.body
                    val stream = body?.byteStream()
                    if (stream == null) {
                        onError("пустой ответ")
                        return
                    }
                    val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                    try {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = stream.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        target.delete()
                        onError(e.message ?: "ошибка записи")
                        return
                    }
                    if (target.length() < MIN_APK_SIZE) {
                        target.delete()
                        onError("файл повреждён")
                        return
                    }
                    onProgress(100)
                    onDone(target)
                }
            }
        })
    }

    fun install(apk: File): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "cannot expose apk", e)
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun clearDownloaded() {
        updateDir().listFiles()?.forEach { it.delete() }
    }

    private fun updateDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun installedVersionCode(): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    private fun parse(body: String): Release? = runCatching {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        if (tag.isBlank()) return null
        val assets = json.optJSONArray("assets") ?: return null
        var apkName = ""
        var apkUrl = ""
        var size = 0L
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkName = name
                apkUrl = asset.optString("browser_download_url")
                size = asset.optLong("size")
                break
            }
        }
        if (apkUrl.isBlank()) return null
        Release(
            version = tag.removePrefix("v"),
            versionCode = versionCodeOf(tag.removePrefix("v")),
            apkUrl = apkUrl,
            apkName = apkName,
            sizeBytes = size,
            notes = json.optString("body").take(NOTES_LIMIT)
        )
    }.getOrNull()

    private fun versionCodeOf(version: String): Int {
        val parts = version.split(".")
        if (parts.size < 3) return 0
        val major = parts[0].toIntOrNull() ?: return 0
        val minor = parts[1].toIntOrNull() ?: return 0
        val patch = parts[2].substringBefore('-').toIntOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }

    private companion object {
        const val TAG = "Updater"
        const val MIN_APK_SIZE = 512L * 1024
        const val NOTES_LIMIT = 400
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/sj0404-collab/vtuber-ai/releases/latest"
    }
}
