package com.vtuber.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnSettings: Button

    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshState()
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, "Без микрофона аватар только слушает текст", Toast.LENGTH_LONG).show()
        }
        launchOverlay()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        launchOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        status = findViewById(R.id.tv_status)
        btnOverlay = findViewById(R.id.btn_overlay)
        btnSettings = findViewById(R.id.btn_settings)

        btnOverlay.setOnClickListener { requestAndLaunch() }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_hide).setOnClickListener { hideOverlay() }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun requestAndLaunch() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (!hasAudioPermission()) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchOverlay()
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разрешите оверле в настройках", Toast.LENGTH_LONG).show()
            return
        }
        startService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Аватар поверх приложений", Toast.LENGTH_SHORT).show()
        refreshState()
    }

    private fun hideOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        refreshState()
    }

    private fun refreshState() {
        val overlay = Settings.canDrawOverlays(this)
        val models = AvatarModels.list(this)
        val avatar = if (AvatarModels.exists(this, prefs.avatarModel)) prefs.avatarModel else "нет модели"
        status.text = buildString {
            append("Аватар: ").append(avatar).append('\n')
            append("Модели в комплекте: ").append(models.joinToString(", ").ifEmpty { "—" }).append('\n')
            append("Голос: ").append(if (hasAudioPermission()) "микрофон доступен" else "нужно разрешение").append('\n')
            append(if (overlay) "Оверле разрешён" else "Оверле ещё не разрешён")
        }
        btnOverlay.text = if (overlay) "Показать аватар" else "Разрешить и показать"
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
