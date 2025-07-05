package com.example.smartscreen.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import com.tencent.smtt.sdk.WebSettings
import com.tencent.smtt.sdk.WebChromeClient
import com.example.smartscreen.utils.EmulatorUtils

class X5WebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "X5WebView"
    }

    init {
        initWebView()
    }

    private fun initWebView() {
        try {
            // 记录环境信息
            EmulatorUtils.logEnvironmentInfo()
            
            // 为模拟器环境配置EGL优化（注意：X5WebView需要特殊处理）
            if (EmulatorUtils.isEmulator()) {
                // 在模拟器中禁用硬件加速以避免EGL错误
                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                Log.d(TAG, "检测到模拟器环境，已禁用硬件加速")
            }
            
            // 基本设置
            val webSettings = settings
            webSettings.javaScriptEnabled = true
            webSettings.domStorageEnabled = true
            webSettings.allowFileAccess = true
            webSettings.allowContentAccess = true
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            webSettings.setSupportZoom(true)
            webSettings.builtInZoomControls = true
            webSettings.displayZoomControls = false

            // 设置WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    Log.d(TAG, "正在加载URL: $url")
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "页面加载完成: $url")
                    
                    // 注入JavaScript禁用剪贴板功能
                    val js = """
                        (function() {
                            // 禁用剪贴板API
                            if (navigator.clipboard) {
                                navigator.clipboard.writeText = function() { return Promise.resolve(); };
                                navigator.clipboard.readText = function() { return Promise.reject('剪贴板访问被禁用'); };
                            }
                            
                            // 禁用复制粘贴事件
                            document.addEventListener('copy', function(e) { e.preventDefault(); }, true);
                            document.addEventListener('paste', function(e) { e.preventDefault(); }, true);
                            document.addEventListener('cut', function(e) { e.preventDefault(); }, true);
                            
                            // 禁用右键菜单
                            document.addEventListener('contextmenu', function(e) { e.preventDefault(); }, true);
                            
                            console.log('X5WebView: 剪贴板功能已禁用');
                        })();
                    """.trimIndent()
                    
                    view?.evaluateJavascript(js) { result ->
                        Log.d(TAG, "JavaScript注入结果: $result")
                    }
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e(TAG, "页面加载错误: $description (代码: $errorCode)")
                }
            }

            // 设置WebChromeClient
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    Log.d(TAG, "页面加载进度: $newProgress%")
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    Log.d(TAG, "页面标题: $title")
                }
            }

            Log.d(TAG, "X5WebView初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "X5WebView初始化失败", e)
        }
    }

    override fun loadUrl(url: String) {
        try {
            Log.d(TAG, "开始加载URL: $url")
            super.loadUrl(url)
        } catch (e: Exception) {
            Log.e(TAG, "加载URL失败: $url", e)
        }
    }

    override fun onDetachedFromWindow() {
        try {
            // 清理资源
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
            Log.d(TAG, "X5WebView资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理X5WebView资源时出错", e)
        }
        super.onDetachedFromWindow()
    }
} 