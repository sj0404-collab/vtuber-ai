package com.vtuber.ai

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class UpdatePrompt(private val activity: Activity) {

    private val updater = Updater(activity)

    fun check(silent: Boolean) {
        updater.check(object : Updater.Listener {
            override fun onUpdateFound(release: Updater.Release) {
                activity.runOnUiThread { askForInstall(release) }
            }

            override fun onUpToDate(version: String) {
                if (silent) return
                activity.runOnUiThread {
                    Toast.makeText(activity, "Установлена последняя версия ($version)", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailed(message: String) {
                if (silent) {
                    Log.w(TAG, "update check failed: $message")
                    return
                }
                activity.runOnUiThread {
                    Toast.makeText(activity, "Не удалось проверить обновления: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun askForInstall(release: Updater.Release) {
        val sizeMb = String.format("%.1f МБ", release.sizeBytes / 1048576.0)
        val message = buildString {
            append("Версия ").append(release.version).append(" · ").append(sizeMb).append('\n')
            if (release.notes.isNotBlank()) {
                append('\n')
                append(release.notes)
            }
        }
        AlertDialog.Builder(activity)
            .setTitle("Доступно обновление")
            .setMessage(message)
            .setPositiveButton("Скачать") { _, _ -> download(release) }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun download(release: Updater.Release) {
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val percentLabel = TextView(activity).apply {
            text = "Скачивание… 0%"
            gravity = Gravity.CENTER
        }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
            addView(percentLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Обновление ${release.version}")
            .setView(box)
            .setCancelable(false)
            .create()

        updater.download(
            release = release,
            onProgress = { value ->
                activity.runOnUiThread {
                    percentLabel.text = "Скачивание… $value%"
                    progress.progress = value
                }
            },
            onDone = { apk ->
                activity.runOnUiThread {
                    dialog.dismiss()
                    confirmInstall(release, apk)
                }
            },
            onError = { message ->
                activity.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(activity, "Ошибка загрузки: $message", Toast.LENGTH_LONG).show()
                }
            }
        )
        dialog.show()
    }

    private fun confirmInstall(release: Updater.Release, apk: java.io.File) {
        AlertDialog.Builder(activity)
            .setTitle("Установить ${release.version}?")
            .setMessage("Android спросит разрешение на установку из этого источника.")
            .setPositiveButton("Установить") { _, _ ->
                if (!updater.install(apk)) {
                    Toast.makeText(activity, "Не найдено приложение для установки APK", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Позже", null)
            .setOnDismissListener { updater.clearDownloaded() }
            .show()
    }

    private companion object {
        const val TAG = "UpdatePrompt"
    }
}
