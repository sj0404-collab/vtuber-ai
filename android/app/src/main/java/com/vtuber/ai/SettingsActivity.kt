package com.vtuber.ai

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etApiKey = findViewById<EditText>(R.id.et_api_key)
        val etModelUrl = findViewById<EditText>(R.id.et_model_url)
        val btnSave = findViewById<Button>(R.id.btn_save)

        val prefs = getSharedPreferences("vtuber_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))
        etModelUrl.setText(prefs.getString("model_url", "https://api.zen-models.ai/v1/chat/completions"))

        btnSave.setOnClickListener {
            prefs.edit()
                .putString("api_key", etApiKey.text.toString())
                .putString("model_url", etModelUrl.text.toString())
                .apply()
            finish()
        }
    }
}
