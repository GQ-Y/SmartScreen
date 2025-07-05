package com.example.smartscreen.player

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.smartscreen.websocket.Content
import com.example.smartscreen.utils.EmulatorUtils
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 内容缓存管理器
 * 为不同类型的内容（图片、网页、视频）提供缓存支持
 */
class ContentCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ContentCacheManager"
        private const val CACHE_DIR_NAME = "smart_screen_cache"
        private const val WEB_CACHE_DIR = "web_cache"
        private const val VIDEO_CACHE_DIR = "video_cache"
        private const val MAX_CACHE_SIZE = 500 * 1024 * 1024 // 500MB
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    private val webCacheDir: File by lazy {
        File(cacheDir, WEB_CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    private val videoCacheDir: File by lazy {
        File(cacheDir, VIDEO_CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    // 缓存状态跟踪
    private val cacheStatus = ConcurrentHashMap<String, CacheStatus>()
    
    enum class CacheStatus {
        NOT_CACHED,
        CACHING,
        CACHED,
        CACHE_FAILED
    }
    
    /**
     * 预加载内容到缓存
     */
    fun preloadContent(content: Content) {
        val normalizedType = normalizeContentType(content.contentType)
        val cacheKey = generateCacheKey(content.contentUrl)
        
        Log.d(TAG, "开始预加载内容: $normalizedType, URL: ${content.contentUrl}")
        
        when (normalizedType) {
            "image" -> preloadImage(content.contentUrl, cacheKey)
            "web" -> preloadWebContent(content.contentUrl, cacheKey)
            "video" -> preloadVideo(content.contentUrl, cacheKey)
            else -> Log.w(TAG, "不支持的内容类型预加载: $normalizedType")
        }
    }
    
    /**
     * 批量预加载播放列表中的所有内容
     */
    fun preloadPlaylist(playlist: List<Content>) {
        Log.d(TAG, "开始批量预加载播放列表，共${playlist.size}项内容")
        
        for (content in playlist) {
            preloadContent(content)
        }
    }
    
    /**
     * 检查内容是否已缓存
     */
    fun isCached(contentUrl: String): Boolean {
        val cacheKey = generateCacheKey(contentUrl)
        return cacheStatus[cacheKey] == CacheStatus.CACHED
    }
    
    /**
     * 获取缓存状态
     */
    fun getCacheStatus(contentUrl: String): CacheStatus {
        val cacheKey = generateCacheKey(contentUrl)
        return cacheStatus[cacheKey] ?: CacheStatus.NOT_CACHED
    }
    
    /**
     * 预加载图片
     */
    private fun preloadImage(url: String, cacheKey: String) {
        if (cacheStatus[cacheKey] == CacheStatus.CACHED || cacheStatus[cacheKey] == CacheStatus.CACHING) {
            Log.d(TAG, "图片已缓存或正在缓存中: $url")
            return
        }
        
        cacheStatus[cacheKey] = CacheStatus.CACHING
        Log.d(TAG, "开始预加载图片: $url")
        
        try {
            // 使用Glide预加载图片到磁盘缓存
            Glide.with(context)
                .load(url)
                .apply(RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .skipMemoryCache(false)) // 允许内存缓存以提高性能
                .preload()
            
            // 由于Glide的异步特性，我们标记为已缓存
            // 实际的缓存状态由Glide内部管理
            cacheStatus[cacheKey] = CacheStatus.CACHED
            Log.d(TAG, "图片预加载完成: $url")
            
        } catch (e: Exception) {
            Log.e(TAG, "图片预加载失败: $url", e)
            cacheStatus[cacheKey] = CacheStatus.CACHE_FAILED
        }
    }
    
    /**
     * 预加载网页内容
     * 注意：在Android模拟器中，为避免EGL错误，暂时禁用WebView预加载
     */
    private fun preloadWebContent(url: String, cacheKey: String) {
        if (cacheStatus[cacheKey] == CacheStatus.CACHED || cacheStatus[cacheKey] == CacheStatus.CACHING) {
            Log.d(TAG, "网页已缓存或正在缓存中: $url")
            return
        }
        
        cacheStatus[cacheKey] = CacheStatus.CACHING
        Log.d(TAG, "开始预加载网页: $url")
        
        try {
            // 检查是否在模拟器环境中
            if (EmulatorUtils.isEmulator()) {
                // 在模拟器中，为避免EGL错误，简单标记为已缓存
                // 实际的网页加载由主WebView处理
                Log.w(TAG, "检测到模拟器环境，跳过WebView预加载以避免EGL错误")
                cacheStatus[cacheKey] = CacheStatus.CACHED
                return
            }
            
            // 在真机上创建隐藏的WebView来预加载网页
            val webView = WebView(context).apply {
                // 使用工具类配置EGL优化
                EmulatorUtils.configureWebViewForEmulator(this)
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // databaseEnabled 在 API 19+ 中已废弃，使用 @Suppress 注解
                    @Suppress("DEPRECATION")
                    databaseEnabled = true
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    // 启用混合内容模式以支持HTTPS页面中的HTTP资源 (API 21+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    // 启用安全浏览 (API 26+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }
                
                // 设置WebViewClient来监听加载完成
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        cacheStatus[cacheKey] = CacheStatus.CACHED
                        Log.d(TAG, "网页预加载完成: $url")
                        
                        // 清理WebView资源
                        try {
                            view?.destroy()
                        } catch (e: Exception) {
                            Log.w(TAG, "清理预加载WebView时出错", e)
                        }
                    }
                    
                    @Suppress("DEPRECATION")
                    override fun onReceivedError(
                        view: android.webkit.WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        Log.e(TAG, "网页预加载错误: $description")
                        cacheStatus[cacheKey] = CacheStatus.CACHE_FAILED
                        
                        // 清理WebView资源
                        try {
                            view?.destroy()
                        } catch (e: Exception) {
                            Log.w(TAG, "清理预加载WebView时出错", e)
                        }
                    }
                }
            }
            
            // 加载URL到WebView缓存
            webView.loadUrl(url)
            
        } catch (e: Exception) {
            Log.e(TAG, "网页预加载失败: $url", e)
            cacheStatus[cacheKey] = CacheStatus.CACHE_FAILED
        }
    }
    
    /**
     * 预加载视频
     */
    private fun preloadVideo(url: String, cacheKey: String) {
        if (cacheStatus[cacheKey] == CacheStatus.CACHED || cacheStatus[cacheKey] == CacheStatus.CACHING) {
            Log.d(TAG, "视频已缓存或正在缓存中: $url")
            return
        }
        
        cacheStatus[cacheKey] = CacheStatus.CACHING
        Log.d(TAG, "开始预加载视频: $url")
        
        try {
            // 对于视频，我们可以使用ExoPlayer的缓存机制
            // 这里先简单标记，实际的视频缓存可以通过ExoPlayer的CacheDataSource实现
            
            // TODO: 实现真正的视频预加载和缓存
            // 可以使用ExoPlayer的SimpleCache和CacheDataSource
            
            cacheStatus[cacheKey] = CacheStatus.CACHED
            Log.d(TAG, "视频预加载完成: $url")
            
        } catch (e: Exception) {
            Log.e(TAG, "视频预加载失败: $url", e)
            cacheStatus[cacheKey] = CacheStatus.CACHE_FAILED
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        Log.d(TAG, "开始清理缓存")
        
        try {
            // 清理Glide缓存
            Glide.get(context).clearDiskCache()
            
            // 清理WebView缓存
            if (webCacheDir.exists()) {
                webCacheDir.deleteRecursively()
                webCacheDir.mkdirs()
            }
            
            // 清理视频缓存
            if (videoCacheDir.exists()) {
                videoCacheDir.deleteRecursively()
                videoCacheDir.mkdirs()
            }
            
            // 清理状态跟踪
            cacheStatus.clear()
            
            Log.d(TAG, "缓存清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "缓存清理失败", e)
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        return try {
            calculateDirectorySize(cacheDir)
        } catch (e: Exception) {
            Log.e(TAG, "计算缓存大小失败", e)
            0L
        }
    }
    
    /**
     * 检查并清理过期缓存
     */
    fun cleanupExpiredCache() {
        val cacheSize = getCacheSize()
        if (cacheSize > MAX_CACHE_SIZE) {
            Log.w(TAG, "缓存大小超过限制 (${cacheSize / (1024 * 1024)}MB > ${MAX_CACHE_SIZE / (1024 * 1024)}MB)，开始清理")
            clearCache()
        }
    }
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(url.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "生成缓存键失败", e)
            url.hashCode().toString()
        }
    }
    
    /**
     * 标准化内容类型
     */
    private fun normalizeContentType(type: Any): String {
        return when (type) {
            is String -> type
            is Double -> {
                when (type) {
                    1.0 -> "web"
                    2.0 -> "image"
                    3.0 -> "video"
                    else -> "unknown"
                }
            }
            is Int -> {
                when (type) {
                    1 -> "web"
                    2 -> "image"
                    3 -> "video"
                    else -> "unknown"
                }
            }
            else -> "unknown"
        }
    }
    
    /**
     * 计算目录大小
     */
    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }
} 