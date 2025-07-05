package com.example.smartscreen.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.smartscreen.ui.X5WebView
import com.example.smartscreen.websocket.Content

class PlayerManager(
    private val context: Context,
    private val playerView: PlayerView,
    private val webView: X5WebView,
    private val imageView: ImageView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null
    private var playlist: List<Content> = emptyList()
    private var currentIndex = -1
    
    // 缓存管理器
    private val cacheManager = ContentCacheManager(context)

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
                        Log.d("PlayerManager", "视频播放状态变化: $playbackState")
                        // 不再根据视频播放结束自动切换，完全由duration字段控制
                        // 如果需要循环播放单个视频，可以设置repeatMode
                        if (playbackState == Player.STATE_ENDED) {
                            // 如果是单个内容且duration为0，循环播放
                            if (playlist.size == 1 && currentIndex >= 0 && currentIndex < playlist.size) {
                                val content = playlist[currentIndex]
                                if (content.duration <= 0.0) {
                                    Log.d("PlayerManager", "单个视频内容duration为0，重新播放")
                                    exoPlayer?.seekTo(0)
                                    exoPlayer?.play()
                                }
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
        playerView.visibility = View.GONE
        webView.visibility = View.GONE
        imageView.visibility = View.GONE

        // Cancel any pending timed transitions from previous content
        handler.removeCallbacksAndMessages(null)
        
        // 停止之前的视频播放（如果有）
        exoPlayer?.stop()

        when (normalizedType) {
            "web" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的网页内容")
                    skipToNext()
                    return
                }
                
                // 检查缓存状态
                val cacheStatus = cacheManager.getCacheStatus(correctedContent.contentUrl)
                Log.d("PlayerManager", "网页缓存状态: $cacheStatus")
                
                // 使用X5WebView播放网页内容
                webView.visibility = View.VISIBLE
                webView.loadUrl(correctedContent.contentUrl)
                Log.d("PlayerManager", "✅ 开始播放网页内容，持续时间: ${correctedContent.duration}秒 ${if (cacheStatus == ContentCacheManager.CacheStatus.CACHED) "(已缓存)" else "(网络加载)"}")
                scheduleNext(correctedContent.duration)
            }
            "image" -> {
                if (correctedContent.contentUrl.isBlank()) {
                    Log.e("PlayerManager", "跳过空白URL的图片内容")
                    skipToNext()
                    return
                }
                imageView.visibility = View.VISIBLE
                
                // 检查缓存状态
                val cacheStatus = cacheManager.getCacheStatus(correctedContent.contentUrl)
                Log.d("PlayerManager", "图片缓存状态: $cacheStatus")
                
                // 使用Glide加载图片，启用缓存
                Glide.with(context)
                    .load(correctedContent.contentUrl)
                    .apply(RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(imageView)
                    
                Log.d("PlayerManager", "✅ 开始显示图片内容，持续时间: ${correctedContent.duration}秒 ${if (cacheStatus == ContentCacheManager.CacheStatus.CACHED) "(已缓存)" else "(网络加载)"}")
                scheduleNext(correctedContent.duration)
            }
            "video", "rtsp" -> {
                // 检查缓存状态
                val cacheStatus = cacheManager.getCacheStatus(correctedContent.contentUrl)
                Log.d("PlayerManager", "视频缓存状态: $cacheStatus")
                
                playerView.visibility = View.VISIBLE
                val mediaItem = buildMediaItem(correctedContent)
                if (mediaItem != null) {
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                    Log.d("PlayerManager", "✅ 开始播放视频内容，持续时间: ${correctedContent.duration}秒 ${if (cacheStatus == ContentCacheManager.CacheStatus.CACHED) "(已缓存)" else "(网络加载)"}")
                    // 视频也遵循duration字段控制，而不是视频本身的长度
                    scheduleNext(correctedContent.duration)
                } else {
                    Log.e("PlayerManager", "无法为以下URL构建媒体项: ${correctedContent.contentUrl}")
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
     * We assume a convention here: 1.0=video, 2.0=image, 3.0=web
     */
    private fun normalizeContentType(type: Any): String {
        return when (type) {
            is String -> type
            is Double -> {
                when (type) {
                    1.0 -> "web"
                    2.0 -> "image"
                    3.0 -> "video"
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
            "video", "rtsp" -> MediaItem.fromUri(uri)
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
} 