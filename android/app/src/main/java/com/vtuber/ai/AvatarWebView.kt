package com.vtuber.ai

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class AvatarWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onModelReady(model: String)
        fun onModelError(message: String)
        fun onAvatarTap(x: Float, y: Float)
    }

    private val webView = WebView(context)
    private val queued = ArrayList<String>()
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    private var ready = false

    var listener: Listener? = null

    val cornerRadius: Float
        get() = minOf(width, height) * 0.11f

    private val bridge = object {
        @JavascriptInterface
        fun onReady(model: String) {
            post {
                ready = true
                queued.forEach { script -> webView.evaluateJavascript(script, null) }
                queued.clear()
                listener?.onModelReady(model)
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            post { listener?.onModelError(message) }
        }

        @JavascriptInterface
        fun onTap(x: Float, y: Float) {
            post { listener?.onAvatarTap(x, y) }
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = true

        webView.apply {
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
            addJavascriptInterface(bridge, "VtuberAvatar")
        }
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun load(model: String) {
        ready = false
        webView.loadUrl("$BASE_URL/avatar/index.html?model=$model")
    }

    fun mood(mood: String) = call("mood", "'$mood'")

    fun speak(on: Boolean) = call("speak", on.toString())

    fun level(value: Float) = call("level", value.toString())

    fun caption(text: String) = call("caption", quote(text))

    fun status(text: String) = call("status", quote(text))

    fun gaze(x: Float, y: Float) = call("gaze", "$x, $y")

    fun tilt(degrees: Float) = call("tilt", degrees.toString())

    fun pulse() = call("pulse", "")

    fun dim(value: Float) = call("dim", value.toString())

    fun switchModel(model: String) = call("model", quote(model))

    fun isModelReady(): Boolean = ready

    private fun call(method: String, args: String) {
        val script = "if (window.vtuberApi) { window.vtuberApi.$method($args); }"
        if (ready) {
            webView.evaluateJavascript(script, null)
        } else {
            queued.add(script)
        }
    }

    private fun quote(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ")
        return "'$escaped'"
    }

    fun release() {
        queued.clear()
        webView.removeJavascriptInterface("VtuberAvatar")
        (webView.parent as? FrameLayout)?.removeView(webView)
        webView.destroy()
    }

    companion object {
        private const val BASE_URL = "https://appassets.androidplatform.net"
    }
}
