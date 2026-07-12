@file:Suppress("DEPRECATION")

package com.kanxi.app.playback

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Secure Bilibili playback surface.
 *
 * Both URLs are deliberately injected. This component never constructs, resolves, or scrapes a
 * Bilibili URL. [playerUrl] must be an HTTPS `player.bilibili.com/player.html` URL and
 * [originalUrl] must be an HTTPS Bilibili `/video/...` URL.
 *
 * The caller should show any first-use mobile-data warning before navigating to this screen.
 */
@Composable
fun PlaybackScreen(
    title: String,
    playerUrl: String,
    originalUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    externalOpener: (Context, Uri) -> Unit = ::openExternalUrl,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = activity as? LifecycleOwner
    val playerUrlIsValid = remember(playerUrl) {
        PlaybackUrlPolicy.isAllowedPlayerUrl(playerUrl)
    }
    val originalUrlIsValid = remember(originalUrl) {
        PlaybackUrlPolicy.isAllowedOriginalUrl(originalUrl)
    }

    var webView by remember(playerUrl) { mutableStateOf<WebView?>(null) }
    var runtimeError by remember(playerUrl) { mutableStateOf<String?>(null) }
    var isLoading by remember(playerUrl) { mutableStateOf(playerUrlIsValid) }
    var isFullscreen by remember { mutableStateOf(false) }
    var rendererGone by remember(playerUrl) { mutableStateOf(false) }
    var webViewGeneration by remember(playerUrl) { mutableIntStateOf(0) }

    val fullscreenContainer = remember(context) {
        FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    val fullscreenController = remember(activity, fullscreenContainer) {
        FullscreenController(
            activity = activity,
            container = fullscreenContainer,
            onFullscreenChanged = { isFullscreen = it },
        )
    }

    val validationError = when {
        !playerUrlIsValid -> "播放器地址不安全或格式不正确，已停止加载。"
        !originalUrlIsValid -> "原视频地址不安全或格式不正确，已停止加载。"
        else -> null
    }
    val visibleError = validationError ?: runtimeError

    fun reload() {
        if (!playerUrlIsValid || !originalUrlIsValid) return
        fullscreenController.hide()
        runtimeError = null
        isLoading = true
        if (rendererGone) {
            rendererGone = false
            webViewGeneration += 1
        } else {
            webView?.run {
                stopLoading()
                loadUrl(playerUrl)
            }
        }
    }

    fun openOriginal() {
        if (!originalUrlIsValid) {
            runtimeError = "原视频地址不安全或格式不正确，无法打开。"
            return
        }

        try {
            externalOpener(context, originalUrl.toUri())
        } catch (_: ActivityNotFoundException) {
            runtimeError = "手机上没有可以打开此链接的应用。"
        } catch (_: SecurityException) {
            runtimeError = "系统阻止了外部应用打开此链接。"
        }
    }

    BackHandler {
        when {
            fullscreenController.isShowing -> fullscreenController.hide()
            fullscreenController.consumeRecentWebViewExit() -> Unit
            else -> onBack()
        }
    }

    ObserveWebViewLifecycle(webView = webView, lifecycleOwner = lifecycleOwner)

    DisposableEffect(fullscreenController) {
        onDispose { fullscreenController.hide() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .semantics {
                    if (isFullscreen) hideFromAccessibility()
                },
        ) {
            PlaybackToolbar(
                title = title,
                onBack = onBack,
                onReload = ::reload,
                onOpenOriginal = ::openOriginal,
                reloadEnabled = playerUrlIsValid && originalUrlIsValid &&
                    (webView != null || rendererGone),
                externalEnabled = originalUrlIsValid,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
            ) {
                if (playerUrlIsValid && originalUrlIsValid) {
                    key(playerUrl, webViewGeneration) {
                        AndroidView(
                            factory = { viewContext ->
                                createSecureWebView(
                                    context = viewContext,
                                    playerUrl = playerUrl,
                                    fullscreenController = fullscreenController,
                                    onLoadingChanged = { isLoading = it },
                                    onFailure = { message ->
                                        fullscreenController.hide()
                                        runtimeError = message
                                        isLoading = false
                                    },
                                    onRendererGone = { failedWebView ->
                                        if (webView === failedWebView) webView = null
                                        fullscreenController.hide()
                                        rendererGone = true
                                        runtimeError = "播放器被系统暂时关闭，请点“重新加载”。"
                                        isLoading = false
                                    },
                                ).also { createdWebView ->
                                    webView = createdWebView
                                    // AndroidView's factory runs before its first measurement. Loading the
                                    // mobile player at 0 px height makes its 100%-height video stay collapsed.
                                    createdWebView.doOnLayout {
                                        if (createdWebView.url == null) {
                                            createdWebView.loadUrl(playerUrl)
                                        }
                                    }
                                }
                            },
                            update = { view ->
                                view.visibility = if (visibleError == null) {
                                    View.VISIBLE
                                } else {
                                    View.INVISIBLE
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                if (isLoading && visibleError == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (visibleError != null) {
                    PlaybackErrorPanel(
                        message = visibleError,
                    )
                }
            }
        }

        if (isFullscreen) {
            AndroidView(
                factory = { fullscreenContainer },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun PlaybackToolbar(
    title: String,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenOriginal: () -> Unit,
    reloadEnabled: Boolean,
    externalEnabled: Boolean,
) {
    val fontScale = LocalDensity.current.fontScale

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                Text(text = "返回", style = MaterialTheme.typography.labelLarge)
            }

            Text(
                text = title.ifBlank { "播放戏曲" },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackActions = maxWidth < 430.dp || (fontScale >= 1.5f && maxWidth < 600.dp)
            if (stackActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReloadButton(
                        onClick = onReload,
                        enabled = reloadEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExternalButton(
                        onClick = onOpenOriginal,
                        enabled = externalEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ReloadButton(
                        onClick = onReload,
                        enabled = reloadEnabled,
                        modifier = Modifier.weight(1f),
                    )
                    ExternalButton(
                        onClick = onOpenOriginal,
                        enabled = externalEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReloadButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Text(text = "重新加载", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ExternalButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Text(text = "用哔哩哔哩或浏览器打开", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PlaybackErrorPanel(
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "暂时无法播放",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ObserveWebViewLifecycle(
    webView: WebView?,
    lifecycleOwner: LifecycleOwner?,
) {
    DisposableEffect(webView, lifecycleOwner) {
        if (webView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> webView.onResume()
                    Lifecycle.Event.ON_PAUSE -> webView.onPause()
                    else -> Unit
                }
            }

            lifecycleOwner?.lifecycle?.addObserver(observer)
            if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
                webView.onResume()
            }

            onDispose {
                lifecycleOwner?.lifecycle?.removeObserver(observer)
                runCatching {
                    webView.onPause()
                    webView.stopLoading()
                    webView.removeAllViews()
                    webView.destroy()
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSecureWebView(
    context: Context,
    playerUrl: String,
    fullscreenController: FullscreenController,
    onLoadingChanged: (Boolean) -> Unit,
    onFailure: (String) -> Unit,
    onRendererGone: (WebView) -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(android.graphics.Color.BLACK)
    isFocusable = true
    isFocusableInTouchMode = true

    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        safeBrowsingEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_NO_CACHE
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        saveFormData = false
        mediaPlaybackRequiresUserGesture = true
    }

    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

    webViewClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        Api27SecurePlaybackWebViewClient(
            expectedPlayerUrl = playerUrl,
            onLoadingChanged = onLoadingChanged,
            safeBrowsingFailure = onFailure,
            onRendererGone = onRendererGone,
        )
    } else {
        SecurePlaybackWebViewClient(
            expectedPlayerUrl = playerUrl,
            onLoadingChanged = onLoadingChanged,
            onFailure = onFailure,
            onRendererGone = onRendererGone,
        )
    }
    webChromeClient = SecurePlaybackChromeClient(fullscreenController)
}

private open class SecurePlaybackWebViewClient(
    private val expectedPlayerUrl: String,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onRendererGone: (WebView) -> Unit,
) : WebViewClient() {

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        onRendererGone(view)
        return true
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (!request.isForMainFrame) return false
        if (!request.method.equals("GET", ignoreCase = true)) {
            failClosed(view, "播放器尝试提交不允许的页面请求，已停止加载。")
            return true
        }
        return blockDisallowedTopLevelNavigation(view, request.url.toString())
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return blockDisallowedTopLevelNavigation(view, url)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url == SAFE_BLANK_PAGE) {
            onLoadingChanged(false)
            return
        }
        if (
            url == null ||
            !PlaybackUrlPolicy.isAllowedPlayerUrl(url) ||
            !PlaybackUrlPolicy.samePlayerDocument(expectedPlayerUrl, url)
        ) {
            failClosed(view, "播放器试图跳转到不允许的页面，已停止加载。")
            return
        }
        onLoadingChanged(true)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (
            url != null &&
            PlaybackUrlPolicy.isMobilePlayerUrl(url) &&
            PlaybackUrlPolicy.samePlayerDocument(expectedPlayerUrl, url)
        ) {
            // The official mobile document resolves 100%/100vh to 0 in Android WebView even
            // though window.innerHeight is valid. Set only its three layout roots to that
            // already-exposed viewport height; no page data is read and no native bridge exists.
            view.evaluateJavascript(MOBILE_PLAYER_VIEWPORT_FIX_SCRIPT, null)
        }
        onLoadingChanged(false)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            failClosed(view, "无法加载视频，请检查网络后重试。")
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) {
            failClosed(
                view,
                "视频页面暂时不可用（HTTP ${errorResponse.statusCode}）。",
            )
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        failClosed(view, "安全连接验证失败，已停止加载。")
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) {
        handler.cancel()
        failClosed(view, "播放器要求额外身份验证，已停止内嵌加载。")
    }

    private fun blockDisallowedTopLevelNavigation(view: WebView, url: String): Boolean {
        if (url == SAFE_BLANK_PAGE) return false
        if (PlaybackUrlPolicy.isAllowedPlayerUrl(url) &&
            PlaybackUrlPolicy.samePlayerDocument(expectedPlayerUrl, url)
        ) {
            return false
        }

        failClosed(view, "播放器试图跳转到不允许的页面，已停止加载。")
        return true
    }

    private fun failClosed(view: WebView, message: String) {
        onFailure(message)
        view.stopLoading()
        if (view.url != SAFE_BLANK_PAGE) {
            view.loadUrl(SAFE_BLANK_PAGE)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
private class Api27SecurePlaybackWebViewClient(
    expectedPlayerUrl: String,
    onLoadingChanged: (Boolean) -> Unit,
    private val safeBrowsingFailure: (String) -> Unit,
    onRendererGone: (WebView) -> Unit,
) : SecurePlaybackWebViewClient(
    expectedPlayerUrl,
    onLoadingChanged,
    safeBrowsingFailure,
    onRendererGone,
) {
    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        // Keep Safe Browsing protection without sending a threat report from this family app.
        callback.backToSafety(false)
        safeBrowsingFailure("安全浏览检测到风险内容，已停止加载。")
        view.stopLoading()
        if (view.url != SAFE_BLANK_PAGE) view.loadUrl(SAFE_BLANK_PAGE)
    }
}

private class SecurePlaybackChromeClient(
    private val fullscreenController: FullscreenController,
) : WebChromeClient() {

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        fullscreenController.show(view, callback)
    }

    override fun onHideCustomView() {
        fullscreenController.hide(fromWebView = true)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean = false

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }
}

private class FullscreenController(
    private val activity: Activity?,
    private val container: FrameLayout,
    private val onFullscreenChanged: (Boolean) -> Unit,
) {
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var systemUiSnapshot: SystemUiSnapshot? = null
    private var webViewExitUptimeMillis: Long = 0

    val isShowing: Boolean
        get() = customView != null

    fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        (view.parent as? ViewGroup)?.removeView(view)
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        customView = view
        customViewCallback = callback
        systemUiSnapshot = activity?.enterPlaybackFullscreen()
        onFullscreenChanged(true)
    }

    fun hide(fromWebView: Boolean = false) {
        if (customView == null) return

        val callback = customViewCallback
        customView = null
        customViewCallback = null
        container.removeAllViews()
        activity?.leavePlaybackFullscreen(systemUiSnapshot)
        systemUiSnapshot = null
        onFullscreenChanged(false)
        webViewExitUptimeMillis = if (fromWebView) SystemClock.uptimeMillis() else 0
        callback?.onCustomViewHidden()
    }

    /**
     * Some WebView versions hide their custom view before Compose receives the same back event.
     * Consume that one event so exiting video fullscreen cannot also navigate away from playback.
     */
    fun consumeRecentWebViewExit(): Boolean {
        val exitedAt = webViewExitUptimeMillis
        webViewExitUptimeMillis = 0
        return exitedAt > 0 && SystemClock.uptimeMillis() - exitedAt <= WEB_VIEW_BACK_GUARD_MS
    }

    private companion object {
        const val WEB_VIEW_BACK_GUARD_MS = 1_000L
    }
}

private data class SystemUiSnapshot(
    val visibility: Int,
    val wasFullscreen: Boolean,
)

@Suppress("DEPRECATION")
private fun Activity.enterPlaybackFullscreen(): SystemUiSnapshot {
    val decorView = window.decorView
    val snapshot = SystemUiSnapshot(
        visibility = decorView.systemUiVisibility,
        wasFullscreen = window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0,
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    decorView.systemUiVisibility =
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    return snapshot
}

@Suppress("DEPRECATION")
private fun Activity.leavePlaybackFullscreen(snapshot: SystemUiSnapshot?) {
    if (snapshot == null) return
    window.decorView.systemUiVisibility = snapshot.visibility
    if (snapshot.wasFullscreen) {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

internal object PlaybackUrlPolicy {
    private const val DESKTOP_PLAYER_HOST = "player.bilibili.com"
    private const val DESKTOP_PLAYER_PATH = "/player.html"
    private const val MOBILE_PLAYER_HOST = "www.bilibili.com"
    private const val MOBILE_PLAYER_PATH = "/blackboard/webplayer/mbplayer.html"
    private val originalHosts = setOf("www.bilibili.com", "m.bilibili.com")
    private val bvidPattern = Regex("^BV[0-9A-Za-z]{10}$")
    private val playerQueryNames = setOf("bvid", "p", "autoplay", "danmaku")

    fun isAllowedPlayerUrl(rawUrl: String): Boolean = parsePlayerDocument(rawUrl) != null

    fun isMobilePlayerUrl(rawUrl: String): Boolean {
        val uri = rawUrl.toHttpsUriOrNull() ?: return false
        return uri.host.equals(MOBILE_PLAYER_HOST, ignoreCase = true) &&
            uri.path == MOBILE_PLAYER_PATH &&
            parsePlayerDocument(rawUrl) != null
    }

    fun isAllowedOriginalUrl(rawUrl: String): Boolean {
        val uri = rawUrl.toHttpsUriOrNull() ?: return false
        if (uri.host?.lowercase() !in originalHosts) return false
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "video") return false
        if (uri.queryParameterNames != setOf("p")) return false
        val page = uri.singleQueryValue("p")?.toIntOrNull() ?: return false
        return bvidPattern.matches(segments[1]) && page >= 1
    }

    fun samePlayerDocument(expectedUrl: String, candidateUrl: String): Boolean {
        val expected = parsePlayerDocument(expectedUrl) ?: return false
        val candidate = parsePlayerDocument(candidateUrl) ?: return false
        return expected.bvid == candidate.bvid && expected.page == candidate.page
    }

    private fun parsePlayerDocument(rawUrl: String): PlayerDocument? {
        val uri = rawUrl.toHttpsUriOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val isKnownPlayerDocument =
            (host == DESKTOP_PLAYER_HOST && uri.path == DESKTOP_PLAYER_PATH) ||
                (host == MOBILE_PLAYER_HOST && uri.path == MOBILE_PLAYER_PATH)
        if (!isKnownPlayerDocument || uri.queryParameterNames != playerQueryNames) return null

        val bvid = uri.singleQueryValue("bvid") ?: return null
        val page = uri.singleQueryValue("p")?.toIntOrNull() ?: return null
        if (!bvidPattern.matches(bvid) || page < 1) return null
        if (uri.singleQueryValue("autoplay") != "0") return null
        if (uri.singleQueryValue("danmaku") != "0") return null
        return PlayerDocument(bvid = bvid, page = page)
    }

    private fun String.toHttpsUriOrNull(): Uri? = try {
        toUri().takeIf { uri ->
            uri.isHierarchical &&
                uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443)
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun Uri.singleQueryValue(name: String): String? =
        getQueryParameters(name).singleOrNull()

    private data class PlayerDocument(
        val bvid: String,
        val page: Int,
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}

private fun openExternalUrl(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    if (context.findActivity() == null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private const val SAFE_BLANK_PAGE = "about:blank"

private const val MOBILE_PLAYER_VIEWPORT_FIX_SCRIPT = """
    (() => {
        const viewportHeight = Math.round(window.innerHeight);
        if (!Number.isFinite(viewportHeight) || viewportHeight <= 0) return;
        const height = viewportHeight + 'px';
        const player = document.getElementById('w-player');
        document.documentElement.style.height = height;
        if (document.body) document.body.style.height = height;
        if (player) player.style.height = height;
        window.dispatchEvent(new Event('resize'));
    })();
"""
