package com.cyprienbrisset.myportal.ui.google

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object PersistentWebViewPool {

    @SuppressLint("StaticFieldLeak")
    private val cache = mutableMapOf<String, WebView>()

    fun get(key: String, context: Context): WebView = cache.getOrPut(key) { create(context) }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(context: Context): WebView = WebView(context.applicationContext).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = WebViewClient()
    }
}
