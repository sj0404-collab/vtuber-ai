package com.vtuber.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vtuber_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var modelUrl: String
        get() = prefs.getString(KEY_MODEL_URL, DEFAULT_MODEL_URL) ?: DEFAULT_MODEL_URL
        set(value) = prefs.edit().putString(KEY_MODEL_URL, value.trim()).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value.trim()).apply()

    var avatarModel: String
        get() = prefs.getString(KEY_AVATAR_MODEL, DEFAULT_AVATAR) ?: DEFAULT_AVATAR
        set(value) = prefs.edit().putString(KEY_AVATAR_MODEL, value).apply()

    var characterRole: String
        get() = prefs.getString(KEY_ROLE, DEFAULT_ROLE) ?: DEFAULT_ROLE
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var avatarScale: Float
        get() = prefs.getFloat(KEY_AVATAR_SCALE, 1f)
        set(value) = prefs.edit().putFloat(KEY_AVATAR_SCALE, value.coerceIn(0.5f, 2f)).apply()

    var speechEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEECH, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEECH, value).apply()

    var privacyMode: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY, value).apply()

    var overlayX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

    fun history(): JSONArray = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")

    fun addTurn(role: String, content: String) {
        val array = history()
        array.put(JSONObject().put("role", role).put("content", content))
        while (array.length() > MAX_HISTORY) {
            array.remove(0)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        const val DEFAULT_MODEL_URL = "https://api.zen-models.ai/v1/chat/completions"
        const val DEFAULT_MODEL = "zen-demo"
        const val DEFAULT_AVATAR = "whalegirl"
        const val DEFAULT_ROLE =
            "Ты дружелюбный VTuber-компаньон. Отвечай коротко, живо и по-русски, " +
                "как живой человек в голосовом чате."

        private const val MAX_HISTORY = 12
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_URL = "model_url"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_AVATAR_MODEL = "avatar_model"
        private const val KEY_ROLE = "character_role"
        private const val KEY_AVATAR_SCALE = "avatar_scale"
        private const val KEY_SPEECH = "speech_enabled"
        private const val KEY_PRIVACY = "privacy_mode"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val KEY_HISTORY = "history"
    }
}
