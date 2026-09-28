package com.vtuber.ai

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var apiKey: EditText
    private lateinit var modelUrl: EditText
    private lateinit var modelName: EditText
    private lateinit var avatarSpinner: Spinner
    private lateinit var role: EditText
    private lateinit var sizeBar: SeekBar
    private lateinit var sizeLabel: TextView
    private lateinit var speechSwitch: Switch
    private var models: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        apiKey = findViewById(R.id.et_api_key)
        modelUrl = findViewById(R.id.et_model_url)
        modelName = findViewById(R.id.et_model_name)
        avatarSpinner = findViewById(R.id.sp_avatar)
        role = findViewById(R.id.et_role)
        sizeBar = findViewById(R.id.sb_size)
        sizeLabel = findViewById(R.id.tv_size)
        speechSwitch = findViewById(R.id.sw_speech)

        apiKey.setText(prefs.apiKey)
        modelUrl.setText(prefs.modelUrl)
        modelName.setText(prefs.modelName)
        role.setText(prefs.characterRole)
        speechSwitch.isChecked = prefs.speechEnabled

        models = AvatarModels.list(this)
        avatarSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            models
        )
        val index = models.indexOf(prefs.avatarModel)
        if (index >= 0) avatarSpinner.setSelection(index)

        sizeBar.max = 150
        sizeBar.progress = ((prefs.avatarScale - 0.5f) * 100f).toInt().coerceIn(0, 150)
        updateSizeLabel()

        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                updateSizeLabel()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }
        findViewById<Button>(R.id.btn_test_voice).setOnClickListener { testVoice() }
        findViewById<Button>(R.id.btn_check_update).setOnClickListener {
            UpdatePrompt(this).check(silent = false)
        }
        findViewById<TextView>(R.id.tv_version).text = "Версия: ${Updater(this).currentVersion()}"
        findViewById<Button>(R.id.btn_clear_history).setOnClickListener {
            prefs.clearHistory()
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSizeLabel() {
        val scale = 0.5f + sizeBar.progress / 100f
        sizeLabel.text = "Размер аватара: ${(scale * 100).toInt()}%"
    }

    private fun save() {
        prefs.apiKey = apiKey.text.toString()
        prefs.modelUrl = modelUrl.text.toString()
        prefs.modelName = modelName.text.toString()
        prefs.characterRole = role.text.toString()
        prefs.avatarScale = 0.5f + sizeBar.progress / 100f
        prefs.speechEnabled = speechSwitch.isChecked
        val selected = models.getOrNull(avatarSpinner.selectedItemPosition)
        if (selected != null) prefs.avatarModel = selected
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testVoice() {
        val player = SpeechPlayer(this)
        player.init(
            onReady = {
                player.speak("Привет! Я твой аватар, всё настроено.", object : SpeechPlayer.Listener {
                    override fun onSpeechStart() = Unit

                    override fun onLevel(level: Float) = Unit

                    override fun onSpeechDone() {
                        player.shutdown()
                    }
                })
            },
            onFailure = { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        )
    }
}
