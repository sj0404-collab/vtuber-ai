package com.vtuber.ai

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatClient {

    interface Listener {
        fun onReply(text: String)
        fun onFailure(message: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    fun send(prefs: Prefs, userMessage: String, listener: Listener) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", prefs.characterRole))
        val history = prefs.history()
        for (index in 0 until history.length()) {
            val turn = history.optJSONObject(index) ?: continue
            messages.put(turn)
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val payload = JSONObject()
            .put("model", prefs.modelName)
            .put("messages", messages)
            .put("temperature", 0.8)
            .put("max_tokens", 400)
            .toString()

        val request = Request.Builder()
            .url(prefs.modelUrl)
            .post(payload.toRequestBody(json))
            .header("Authorization", "Bearer ${prefs.apiKey}")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "request failed", e)
                listener.onFailure(e.message ?: "сеть недоступна")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        listener.onFailure("HTTP ${it.code}")
                        return
                    }
                    val content = try {
                        JSONObject(body)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    } catch (e: Exception) {
                        Log.e(TAG, "bad response", e)
                        null
                    }
                    if (content.isNullOrBlank()) {
                        listener.onFailure("пустой ответ модели")
                    } else {
                        listener.onReply(content.trim())
                    }
                }
            }
        })
    }

    companion object {
        private const val TAG = "ChatClient"
    }
}
