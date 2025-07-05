package com.example.smartscreen.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.smartscreen.ui.X5WebView
import com.example.smartscreen.websocket.Content
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C

class PlayerManager(
    private val context: Context,
    private val playerView: PlayerView,
    private val webView: X5WebView,
    private val imageView: ImageView,
    private val audioInfoOverlay: LinearLayout,
    private val audioTitle: TextView,
    private val audioStatus: TextView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var nextContentTask: Runnable? = null
    
    /**
     * 隐藏所有视图
     */
    private fun hideAllViews() {
        playerView.visibility = View.GONE
        webView.visibility = View.GONE
        imageView.visibility = View.GONE
        audioInfoOverlay.visibility = View.GONE
    }
    
    /**
     * 检查并设置音频循环播放
     */
    private fun checkAndSetupAudioLooping(content: Content) {
        val duration = content.duration
        val isOnlyAudioContent = playlist.size == 1 && playlist[0].contentType == 5.0
        
        Log.d("PlayerManager", "音频循环检查: 播放策略时长=${duration}秒, 仅音频内容=${isOnlyAudioContent}")
        
        // 设置播放器监听器来获取音频实际时长
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val audioDurationMs = exoPlayer?.duration ?: 0L
                    val audioDurationSec = audioDurationMs / 1000.0
                    
                    Log.d("PlayerManager", "音频实际时长: ${audioDurationSec}秒")
                    
                    val shouldLoop = when {
                        // 永久播放策略（仅在只有音频内容时）
                        duration == 0.0 && isOnlyAudioContent -> {
                            Log.d("PlayerManager", "检测到永久播放策略且仅有音频内容，启用循环播放")
                            true
                        }
                        // 播放策略时间大于音频总时长
                        duration > audioDurationSec -> {
                            Log.d("PlayerManager", "播放策略时长(${duration}秒) > 音频时长(${audioDurationSec}秒)，启用循环播放")
                            true
                        }
                        else -> {
                            Log.d("PlayerManager", "使用正常播放模式，不循环")
                            false
                        }
                    }
                    
                    if (shouldLoop) {
                        // 启用循环播放
                        exoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                        // 更新音频状态显示
                        if (audioInfoOverlay.visibility == View.VISIBLE) {
                            audioStatus.text = "♪ 循环播放中..."
                        }
                        Log.d("PlayerManager", "已启用音频循环播放")
                        
                        // 根据播放策略设置切换时间
                        if (duration > 0) {
                            scheduleNext(duration)
                        }
                        // 如果是永久播放(duration=0)，则不设置切换时间
                    } else {
                        // 正常播放，不循环
                        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                        scheduleNext(duration)
                    }
                    
                    // 移除监听器，避免重复触发
                    exoPlayer?.removeListener(this)
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerManager", "音频播放错误: ${error.message}")
                exoPlayer?.removeListener(this)
                skipToNext()
            }
        })
    }
    
    /**
     * 检查并设置视频循环播放
     */
    private fun checkAndSetupVideoLooping(content: Content) {
        val duration = content.duration
        
        Log.d("PlayerManager", "视频循环检查: 播放策略时长=${duration}秒")
        
        // 设置播放器监听器来监听视频播放完成
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val videoDurationMs = exoPlayer?.duration ?: 0L
                    val videoDurationSec = videoDurationMs / 1000.0
                    
                    Log.d("PlayerManager", "视频实际时长: ${videoDurationSec}秒")
                    
                    val shouldLoop = when {
                        // 永久播放策略
                        duration == 0.0 -> {
                            Log.d("PlayerManager", "检测到永久播放策略，启用视频循环播放")
                            true
                        }
                        // 播放策略时间大于视频总时长
                        duration > videoDurationSec -> {
                            Log.d("PlayerManager", "播放策略时长(${duration}秒) > 视频时长(${videoDurationSec}秒)，启用视频循环播放")
                            true
                        }
                        else -> {
                            Log.d("PlayerManager", "使用正常播放模式，不循环")
                            false
                        }
                    }
                    
                    if (shouldLoop) {
                        // 启用循环播放
                        exoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                        Log.d("PlayerManager", "已启用视频循环播放")
                        
                        // 根据播放策略设置切换时间
                        if (duration > 0) {
                            scheduleNext(duration)
                        }
                        // 如果是永久播放(duration=0)，则不设置切换时间
                    } else {
                        // 正常播放，不循环
                        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                        scheduleNext(duration)
                    }
                    
                    // 移除监听器，避免重复触发
                    exoPlayer?.removeListener(this)
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerManager", "视频播放错误: ${error.message}")
                exoPlayer?.removeListener(this)
                skipToNext()
            }
        })
    }
    
    // ExoPlayer实例
    private var exoPlayer: ExoPlayer? = null
    private var playlist: List<Content> = emptyList()
    private var currentIndex = -1
    
    // 缓存管理器
    private val cacheManager = ContentCacheManager(context)

    private var retryCount = 0
    private val maxRetryCount = 3
    
    private var useUdpTransport = false // 传输方式切换标志
    
    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(context)
            .build()
            .apply {
                // We will handle repeat logic manually with the playlist
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d("PlayerManager", "播放状态变化: $playbackState")
                        // 播放状态变化时的通用处理
                        // 具体的循环逻辑由各自的内容类型处理方法管理
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PlayerManager", "播放错误: ${error.message}", error)
                        Log.e("PlayerManager", "错误代码: ${error.errorCode}")
                        Log.e("PlayerManager", "错误类型: ${error.javaClass.simpleName}")
                        
                        // 特殊处理RTSP错误
                        if (error.message?.contains("RTSP") == true || error.message?.contains("SETUP") == true) {
                            Log.e("PlayerManager", "RTSP协议错误，尝试不同的传输方式")
                            retryWithDifferentConfig()
                            return
                        }
                        
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                                Log.w("PlayerManager", "网络连接问题，尝试重新播放")
                                retryCurrentContent()
                            }
                            else -> {
                                Log.e("PlayerManager", "播放失败，跳过当前内容")
                                skipToNext()
                            }
                        }
                    }
                })
            }
        playerView.player = exoPlayer
    }

    fun play(contentList: List<Content>) {
        if (contentList.isEmpty()) {
            Log.w("PlayerManager", "播放列表为空，无法播放")
            return
        }

        Log.d("PlayerManager", "开始播放内容列表，共${contentList.size}项内容")
        analyzePlaylist(contentList)

        // 预加载播放列表中的所有内容到缓存
        cacheManager.preloadPlaylist(contentList)
        
        // 检查并清理过期缓存
        cacheManager.cleanupExpiredCache()

        // Clean up previous playback before starting a new one
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.stop()

        playlist = contentList
        currentIndex = -1 // Start before the first item
        playNext()
    }

    private fun playNext() {
        if (playlist.isEmpty()) return

        currentIndex++
        // Loop back to the beginning if we've reached the end of the playlist
        if (currentIndex >= playlist.size) {
            currentIndex = 0
        }

        playCurrent()
    }

    private fun skipToNext() {
        Log.d("PlayerManager", "跳过当前内容，1秒后切换到下一项")
        handler.postDelayed({ playNext() }, 1000)
    }

    private fun analyzePlaylist(contentList: List<Content>) {
        val typeCount = mutableMapOf<String, Int>()
        val permanentCount = contentList.count { it.duration <= 0.0 }
        val timedCount = contentList.count { it.duration > 0.0 }
        var cachedCount = 0
        
        for (i in contentList.indices) {
            val content = contentList[i]
            val normalizedType = normalizeContentType(content.contentType)
            typeCount[normalizedType] = if (typeCount.containsKey(normalizedType)) {
                typeCount[normalizedType]!! + 1
            } else {
                1
            }
            
            val cacheStatus = cacheManager.getCacheStatus(content.contentUrl)
            if (cacheStatus == ContentCacheManager.CacheStatus.CACHED) {
                cachedCount++
            }
            
            val durationText = if (content.duration <= 0.0) "永久" else "${content.duration}秒"
            val cacheText = when (cacheStatus) {
                ContentCacheManager.CacheStatus.CACHED -> "✅已缓存"
                ContentCacheManager.CacheStatus.CACHING -> "⏳缓存中"
                ContentCacheManager.CacheStatus.CACHE_FAILED -> "❌缓存失败"
                else -> "📥未缓存"
            }
            Log.d("PlayerManager", "  [$i] $normalizedType (${content.contentType}) - $durationText - $cacheText")
        }
        
        Log.d("PlayerManager", "播放列表分析:")
        Log.d("PlayerManager", "  内容类型统计: $typeCount")
        Log.d("PlayerManager", "  永久显示: ${permanentCount}项, 定时显示: ${timedCount}项")
        Log.d("PlayerManager", "  缓存状态: ${cachedCount}/${contentList.size}项已缓存")
        
        if (permanentCount > 0 && contentList.size > 1) {
            Log.w("PlayerManager", "⚠️  混合播放列表中包含永久显示内容，可能影响自动切换")
        }
    }

    private fun playCurrent() {
        if (currentIndex < 0 || currentIndex >= playlist.size) {
            return
        }

        // 重置重试计数
        retryCount = 0
        // 重置传输方式，从TCP开始
        useUdpTransport = false
        
        val content = playlist[currentIndex]
        Log.d("PlayerManager", "播放第${currentIndex + 1}/${playlist.size}项内容")
        
        // HACK: Replace localhost with emulator-accessible address for testing
        val correctedUrl = if (content.contentUrl.contains("127.0.0.1")) {
            content.contentUrl.replace("127.0.0.1", "10.0.2.2")
        } else {
            content.contentUrl
        }
        val correctedContent = content.copy(contentUrl = correctedUrl)
        val normalizedType = normalizeContentType(correctedContent.contentType)
        
        Log.d("PlayerManager", "内容详情: 类型=${correctedContent.contentType} -> $normalizedType, 时长=${correctedContent.duration}秒, URL=${correctedContent.contentUrl}")

        // Hide all views initially, then show the correct one
        hideAllViews()
        
        // 重置循环模式
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF

        // Cancel any pending timed transitions from previous content
        handler.removeCallbacksAndMessages(null)
        
        // 停止之前的视频播放（如果有）
        exoPlayer?.stop()

        when (normalizedType) {
            "web" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的Web内容")
                    skipToNext()
                    return
                }
                
                // Web内容
                hideAllViews()
                webView.visibility = View.VISIBLE
                webView.loadUrl(correctedContent.contentUrl)
                Log.d("PlayerManager", "✅ 开始显示Web内容，持续时间: ${correctedContent.duration}秒")
                scheduleNext(correctedContent.duration)
            }
            "image" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的图片内容")
                    skipToNext()
                    return
                }
                
                // 图片内容
                hideAllViews()
                imageView.visibility = View.VISIBLE
                
                // 加载图片
                Glide.with(context)
                    .load(correctedContent.contentUrl)
                    .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(imageView)
                Log.d("PlayerManager", "✅ 开始显示图片内容，持续时间: ${correctedContent.duration}秒")
                scheduleNext(correctedContent.duration)
            }
            "video" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的视频内容")
                    skipToNext()
                    return
                }
                
                // 检查缓存状态
                val cacheStatus = cacheManager.getCacheStatus(correctedContent.contentUrl)
                Log.d("PlayerManager", "视频缓存状态: $cacheStatus")
                
                // 视频播放
                hideAllViews()
                playerView.visibility = View.VISIBLE
                val mediaItem = buildMediaItem(correctedContent)
                if (mediaItem != null) {
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    Log.d("PlayerManager", "✅ 开始播放视频内容，持续时间: ${correctedContent.duration}秒 ${if (cacheStatus == ContentCacheManager.CacheStatus.CACHED) "(已缓存)" else "(网络加载)"}")
                    
                    // 检查是否需要循环播放视频
                    checkAndSetupVideoLooping(correctedContent)
                } else {
                    Log.e("PlayerManager", "无法为以下URL构建视频媒体项: ${correctedContent.contentUrl}")
                    skipToNext()
                }
            }
            "rtsp" -> {
                // 检查缓存状态
                val cacheStatus = cacheManager.getCacheStatus(correctedContent.contentUrl)
                Log.d("PlayerManager", "RTSP缓存状态: $cacheStatus")
                
                // RTSP直播流播放
                hideAllViews()
                playerView.visibility = View.VISIBLE
                val mediaItem = buildMediaItem(correctedContent)
                if (mediaItem != null) {
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    Log.d("PlayerManager", "✅ 开始播放RTSP直播流，持续时间: ${correctedContent.duration}秒")
                    scheduleNext(correctedContent.duration)
                } else {
                    Log.e("PlayerManager", "无法为以下URL构建视频媒体项: ${correctedContent.contentUrl}")
                    skipToNext()
                }
            }
            "live_stream" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的直播流内容")
                    skipToNext()
                    return
                }
                
                Log.d("PlayerManager", "直播流内容不支持缓存，直接播放")
                Log.d("PlayerManager", "RTSP URL: ${correctedContent.contentUrl}")
                
                // 直播流播放
                hideAllViews()
                playerView.visibility = View.VISIBLE
                val mediaSource = buildRtspMediaSource(correctedContent)
                if (mediaSource != null) {
                    exoPlayer?.setMediaSource(mediaSource)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    Log.d("PlayerManager", "✅ 开始播放直播流内容，持续时间: ${correctedContent.duration}秒 (实时流)")
                    // 直播流也遵循duration字段控制
                    scheduleNext(correctedContent.duration)
                } else {
                    Log.e("PlayerManager", "无法为以下URL构建直播流媒体项: ${correctedContent.contentUrl}")
                    skipToNext()
                }
            }
            "audio" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的音频内容")
                    skipToNext()
                    return
                }
                
                // 检查缓存状态
                val cacheStatus = cacheManager.getCacheStatus(correctedContent.contentUrl)
                Log.d("PlayerManager", "音频缓存状态: $cacheStatus")
                
                // 检查是否有封面图片
                val hasThumbnail = !correctedContent.thumbnail.isNullOrBlank()
                Log.d("PlayerManager", "音频封面状态: ${if (hasThumbnail) "有封面" else "无封面"}")
                
                if (hasThumbnail) {
                    // 有封面时，显示封面图片，音频在后台播放
                    hideAllViews()
                    imageView.visibility = View.VISIBLE
                    audioInfoOverlay.visibility = View.VISIBLE
                    
                    // 加载封面图片
                    Glide.with(context)
                        .load(correctedContent.thumbnail)
                        .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                        .into(imageView)
                    
                    // 设置音频信息
                    audioTitle.text = correctedContent.title.ifBlank { "未知音频" }
                    audioStatus.text = "♪ 正在播放..."
                    
                    Log.d("PlayerManager", "显示音频封面: ${correctedContent.thumbnail}")
                } else {
                    // 无封面时，显示音频播放控制界面
                    hideAllViews()
                    playerView.visibility = View.VISIBLE
                    Log.d("PlayerManager", "显示音频播放控制界面")
                }
                
                // 开始播放音频（后台播放）
                val mediaItem = buildMediaItem(correctedContent)
                if (mediaItem != null) {
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    val displayMode = if (hasThumbnail) "封面显示" else "控制界面"
                    Log.d("PlayerManager", "✅ 开始播放音频内容($displayMode)，持续时间: ${correctedContent.duration}秒 ${if (cacheStatus == ContentCacheManager.CacheStatus.CACHED) "(已缓存)" else "(网络加载)"}")
                    
                    // 检查是否需要循环播放
                    checkAndSetupAudioLooping(correctedContent)
                } else {
                    Log.e("PlayerManager", "无法为以下URL构建音频媒体项: ${correctedContent.contentUrl}")
                    skipToNext()
                }
            }
            else -> {
                Log.e("PlayerManager", "不支持的内容类型: '${correctedContent.contentType}' -> '$normalizedType'，跳过")
                skipToNext()
            }
        }
    }

    /**
     * Normalizes contentType from the server, which can be a number or a string.
     * We assume a convention here: 1.0=web, 2.0=image, 3.0=video, 4.0=live_stream, 5.0=audio
     */
    private fun normalizeContentType(type: Any): String {
        return when (type) {
            is String -> type
            is Double -> {
                when (type) {
                    1.0 -> "web"
                    2.0 -> "image"
                    3.0 -> "video"
                    4.0 -> "live_stream"
                    5.0 -> "audio"
                    // Add other mappings as needed
                    else -> "unknown"
                }
            }
            // Handle integer types as well, just in case
            is Int -> {
                when (type) {
                    1 -> "web"
                    2 -> "image"
                    3 -> "video"
                    4 -> "live_stream"
                    5 -> "audio"
                    else -> "unknown"
                }
            }
            else -> "unknown"
        }
    }

    private fun scheduleNext(durationInSeconds: Double) {
        Log.d("PlayerManager", "计划下一项播放，持续时间: ${durationInSeconds}秒")
        
        // 如果duration为0，表示永久显示，不自动切换
        if (durationInSeconds <= 0.0) {
            Log.d("PlayerManager", "duration为0，永久显示当前内容，不自动切换")
            return
        }
        
        // 如果只有一个内容，不需要切换
        if (playlist.size <= 1) {
            Log.d("PlayerManager", "播放列表只有一个内容，不需要切换")
            return
        }
        
        Log.d("PlayerManager", "设置定时器，${durationInSeconds}秒后切换到下一项")
        handler.postDelayed({ 
            Log.d("PlayerManager", "定时器触发，切换到下一项")
            playNext() 
        }, (durationInSeconds * 1000).toLong())
    }

    @Suppress("unused")
    fun play(url: String, contentType: String) {
        val content = Content(id = 0, title = "", contentUrl = url, contentType = contentType, thumbnail = null, duration = 0.0)
        play(listOf(content))
    }

    @Suppress("unused")
    fun operate(action: String) {
        when (action) {
            "pause" -> {
                exoPlayer?.pause()
                // Pause the timer for image/web as well
                handler.removeCallbacksAndMessages(null)
            }
            "resume" -> {
                exoPlayer?.play()
                // 恢复播放时，重新开始计时（简化实现，不保存剩余时间）
                if (currentIndex >= 0 && currentIndex < playlist.size) {
                    val content = playlist[currentIndex]
                    val normalizedType = normalizeContentType(content.contentType)
                    if (normalizedType == "image" || normalizedType == "web") {
                        Log.d("PlayerManager", "恢复播放，重新开始计时")
                        scheduleNext(content.duration)
                    } else if (normalizedType == "video") {
                        Log.d("PlayerManager", "恢复视频播放，重新开始计时")
                        scheduleNext(content.duration)
                    }
                }
            }
            "stop" -> {
                exoPlayer?.stop()
                handler.removeCallbacksAndMessages(null)
                playlist = emptyList()
                currentIndex = -1
            }
        }
    }

    private fun buildMediaItem(content: Content): MediaItem? {
        if (content.contentUrl.isBlank()) {
            return null
        }
        // HACK: Replace localhost with emulator-accessible address for testing
        val correctedUrl = if (content.contentUrl.contains("127.0.0.1")) {
            content.contentUrl.replace("127.0.0.1", "10.0.2.2")
        } else {
            content.contentUrl
        }
        val uri = correctedUrl.toUri()
        return when (normalizeContentType(content.contentType)) {
            "video", "audio" -> MediaItem.fromUri(uri)
            "rtsp" -> MediaItem.fromUri(uri) // 保留向后兼容
            // Image and other types are not ExoPlayer media items
            else -> null
        }
    }

    @Suppress("unused")
    fun releasePlayer() {
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
    }
    
    /**
     * 获取缓存大小（MB）
     */
    fun getCacheSizeMB(): Long {
        return cacheManager.getCacheSize() / (1024 * 1024)
    }
    
    /**
     * 清理所有缓存
     */
    fun clearAllCache() {
        cacheManager.clearCache()
        Log.d("PlayerManager", "已清理所有缓存")
    }
    
    /**
     * 检查内容是否已缓存
     */
    fun isContentCached(contentUrl: String): Boolean {
        return cacheManager.isCached(contentUrl)
    }
    
    /**
     * 获取内容缓存状态
     */
    fun getContentCacheStatus(contentUrl: String): ContentCacheManager.CacheStatus {
        return cacheManager.getCacheStatus(contentUrl)
    }

    private fun retryCurrentContent() {
        if (retryCount < maxRetryCount && currentIndex >= 0 && currentIndex < playlist.size) {
            retryCount++
            Log.d("PlayerManager", "第${retryCount}次重试播放当前内容")
            handler.postDelayed({
                playCurrent()
            }, 2000) // 2秒后重试
        } else {
            Log.e("PlayerManager", "重试次数已达上限，跳过当前内容")
            retryCount = 0
            skipToNext()
        }
    }

    private fun retryWithDifferentConfig() {
        if (retryCount < maxRetryCount && currentIndex >= 0 && currentIndex < playlist.size) {
            retryCount++
            useUdpTransport = !useUdpTransport // 切换传输方式
            Log.d("PlayerManager", "第${retryCount}次重试，切换传输方式为: ${if (useUdpTransport) "UDP" else "TCP"}")
            handler.postDelayed({
                playCurrent()
            }, 3000) // 3秒后重试，给更多时间
        } else {
            Log.e("PlayerManager", "重试次数已达上限，跳过当前内容")
            retryCount = 0
            useUdpTransport = false // 重置传输方式
            skipToNext()
        }
    }

    @UnstableApi
    private fun buildRtspMediaSource(content: Content): MediaSource? {
        if (content.contentUrl.isBlank()) {
            return null
        }
        
        val correctedUrl = if (content.contentUrl.contains("127.0.0.1")) {
            content.contentUrl.replace("127.0.0.1", "10.0.2.2")
        } else {
            content.contentUrl
        }
        
        return try {
            Log.d("PlayerManager", "构建RTSP媒体源: $correctedUrl")
            
            // 检查URL是否包含认证信息
            val hasAuth = correctedUrl.contains("@")
            if (hasAuth) {
                Log.d("PlayerManager", "检测到RTSP认证信息")
            }
            
            Log.d("PlayerManager", "使用传输方式: ${if (useUdpTransport) "UDP" else "TCP"}")
            Log.d("PlayerManager", "重试次数: $retryCount")
            
            // 创建RTSP媒体源，设置超时和重试参数
            RtspMediaSource.Factory()
                .setTimeoutMs(15000) // 15秒超时，给认证更多时间
                .setForceUseRtpTcp(!useUdpTransport) // 根据标志切换传输方式
                .setDebugLoggingEnabled(true) // 启用调试日志
                .createMediaSource(MediaItem.fromUri(correctedUrl))
        } catch (e: Exception) {
            Log.e("PlayerManager", "构建RTSP媒体源失败: ${e.message}", e)
            null
        }
    }
} 