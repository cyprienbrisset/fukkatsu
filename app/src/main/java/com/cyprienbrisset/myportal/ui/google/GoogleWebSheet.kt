package com.cyprienbrisset.myportal.ui.google

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.Sumi

/**
 * Full-screen dialog showing a persistent WebView for [url] identified by [poolKey].
 * The WebView session (cookies, storage) persists across open/close cycles.
 */
@Composable
fun GoogleWebSheet(poolKey: String, url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Box(
            Modifier.fillMaxSize().background(Sumi).statusBarsPadding()
        ) {
            val webView = PersistentWebViewPool.get(poolKey, ctx)
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
                        wv.loadUrl(url)
                    }
                },
            )
            HankoSeal(
                "朱",
                size = 44.dp,
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }
    }
}
