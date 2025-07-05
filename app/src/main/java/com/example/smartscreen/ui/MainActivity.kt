package com.example.smartscreen.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.ui.PlayerView
import com.example.smartscreen.R
import com.example.smartscreen.player.PlayerManager
import com.example.smartscreen.websocket.WebSocketManager
import com.example.smartscreen.utils.DeviceUtils
import com.example.smartscreen.ui.X5WebView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.Context
import com.example.smartscreen.websocket.Content
import com.example.smartscreen.websocket.ContentData
import com.example.smartscreen.websocket.TempContentData
import android.os.Build

/**
 * Main activity of the application.
 * Loads the main layout.
 */
class MainActivity : AppCompatActivity(), WebSocketManager.WebSocketListenerEvents {

    private val TAG = "MainActivity"
    private lateinit var playerManager: PlayerManager
    private lateinit var webView: X5WebView
    private lateinit var statusTextView: TextView
    private lateinit var typewriterTextView: TextView
    private lateinit var networkStatusIcon: ImageView
    private lateinit var copyrightText: TextView
    private lateinit var imageView: ImageView
    private lateinit var guideLayout: LinearLayout

    // Network State Tracking
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var isDeviceNetworkConnected = false
    private var isWebSocketConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Make the root view focusable to receive key events
        findViewById<ConstraintLayout>(R.id.main_browse_fragment).requestFocus()

        val playerView = findViewById<PlayerView>(R.id.player_view)
        webView = findViewById(R.id.web_view)
        
        // 使用X5WebView替代原来的WebView
        webView.visibility = View.GONE
        Log.d(TAG, "X5WebView已初始化")
        
        statusTextView = findViewById(R.id.status_text_view)
        typewriterTextView = findViewById(R.id.typewriter_text)
        networkStatusIcon = findViewById(R.id.network_status_icon)
        copyrightText = findViewById(R.id.copyright_text)
        imageView = findViewById(R.id.image_view)
        guideLayout = findViewById(R.id.guide_layout)
        
        // 音频信息覆盖层
        val audioInfoOverlay = findViewById<LinearLayout>(R.id.audio_info_overlay)
        val audioTitle = findViewById<TextView>(R.id.audio_title)
        val audioStatus = findViewById<TextView>(R.id.audio_status)
        playerManager = PlayerManager(this, playerView, webView, imageView, audioInfoOverlay, audioTitle, audioStatus)

        // 设置固定的欢迎文本
        typewriterTextView.text = "任意屏，任意门，任意显示"

        WebSocketManager.setListener(this)
        
        Log.d("网络检查", "onCreate: 设置网络监控...")
        setupNetworkMonitoring()
        Log.d("网络检查", "onCreate: 检查初始网络状态...")
        checkInitialNetworkState()
    }

    private fun setupNetworkMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager = getSystemService(ConnectivityManager::class.java)
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("网络检查", "onAvailable: 网络可用: $network")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        if (capabilities != null) {
                            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            Log.d("网络检查", "onAvailable: 具有INTERNET能力: $hasInternet, 具有VALIDATED能力: $isValidated")
                            if (hasInternet) {
                                isDeviceNetworkConnected = true
                                runOnUiThread {
                                    updateDeviceNetworkStatusUI()
                                    // 如果网络可用，尝试连接WebSocket
                                    Log.d("网络检查", "onAvailable: 网络具有INTERNET能力，尝试连接WebSocket")
                                    WebSocketManager.connect(this@MainActivity)
                                }
                            }
                        } else {
                            Log.d("网络检查", "onAvailable: 但网络能力为空")
                        }
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("网络检查", "onLost: 网络丢失: $network")
                    isDeviceNetworkConnected = false
                    runOnUiThread { updateDeviceNetworkStatusUI() }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    Log.d("网络检查", "onCapabilitiesChanged: 网络: $network, 具有INTERNET能力: $hasInternet, 具有VALIDATED能力: $isValidated")

                    val oldState = isDeviceNetworkConnected
                    isDeviceNetworkConnected = hasInternet
                    if (oldState != hasInternet) {
                        runOnUiThread {
                            updateDeviceNetworkStatusUI()
                            if (hasInternet) {
                                // 如果网络变为可用，尝试连接WebSocket
                                Log.d("网络检查", "onCapabilitiesChanged: 网络具有INTERNET能力，尝试连接WebSocket")
                                WebSocketManager.connect(this@MainActivity)
                            }
                        }
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                // API 23-25 的替代方案
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
            Log.d("网络检查", "setupNetworkMonitoring: 网络回调已注册")
        } else {
            // API 21-22 的兼容处理
            @Suppress("DEPRECATION")
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            Log.d("网络检查", "setupNetworkMonitoring: 使用旧版本API，简化网络监控")
            // 对于较老的API版本，我们简化网络检查
            isDeviceNetworkConnected = true
            updateDeviceNetworkStatusUI()
        }
    }

    private fun checkInitialNetworkState() {
        Log.d("网络检查", "checkInitialNetworkState: 开始检查...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            if (network != null) {
                Log.d("网络检查", "checkInitialNetworkState: 发现活动网络: $network")
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    Log.d("网络检查", "checkInitialNetworkState: 具有INTERNET能力: $hasInternet, 具有VALIDATED能力: $isValidated")
                    isDeviceNetworkConnected = hasInternet
                } else {
                    Log.d("网络检查", "checkInitialNetworkState: 发现活动网络，但网络能力为空")
                    isDeviceNetworkConnected = false
                }
            } else {
                Log.d("网络检查", "checkInitialNetworkState: 未发现活动网络")
                isDeviceNetworkConnected = false
            }
        } else {
            // API 21-22 的兼容处理
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            isDeviceNetworkConnected = networkInfo?.isConnected == true
            Log.d("网络检查", "checkInitialNetworkState: 使用旧版本API检查网络状态: $isDeviceNetworkConnected")
        }
        
        runOnUiThread {
            Log.d("网络检查", "checkInitialNetworkState: 使用连接状态更新UI: $isDeviceNetworkConnected")
            updateDeviceNetworkStatusUI()
            if (isDeviceNetworkConnected) {
                Log.d("网络检查", "checkInitialNetworkState: 网络可用，尝试连接WebSocket")
                WebSocketManager.connect(this)
            }
        }
    }

    private fun updateDeviceNetworkStatusUI() {
        if (isDeviceNetworkConnected) {
            networkStatusIcon.setImageResource(R.drawable.ic_wifi_on)
        } else {
            networkStatusIcon.setImageResource(R.drawable.ic_wifi_off)
        }
    }

    // --- WebSocketListenerEvents Implementation ---

    override fun onStatusChanged(status: String) {
        Log.d("MainActivity", "WebSocket状态: $status")
        runOnUiThread {
            updateWebSocketStatusUI(status)
        }
    }

    override fun onContentReceived(data: ContentData) {
        runOnUiThread {
            Log.d(TAG, "内容已接收。显示模式: '${data.displayModeName}'。主要内容: ${data.primaryContents.size}个, 次要内容: ${data.secondaryContents.size}个")

            val finalPlaylist = mutableListOf<Content>()
            finalPlaylist.addAll(data.primaryContents.map {
                Content(it.id, it.title, it.contentType, it.contentUrl, it.thumbnail, it.duration)
            })

            // 根据规范，模式1和2也包含次要内容
            if (data.displayMode == 1 || data.displayMode == 2) {
                finalPlaylist.addAll(data.secondaryContents.map {
                    Content(it.id, it.title, it.contentType, it.contentUrl, it.thumbnail, it.duration)
                })
            }

            if (finalPlaylist.isNotEmpty()) {
                statusTextView.isVisible = false
                playerManager.play(finalPlaylist)
            } else {
                // 处理没有内容可播放的情况
                statusTextView.text = "暂无内容"
                statusTextView.isVisible = true
                Log.d(TAG, "最终播放列表为空")
            }
        }
    }

    override fun onRegistration(success: Boolean, message: String) {
        runOnUiThread {
            Log.d(TAG, "设备注册: $message")
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            // 这可能会很嘈杂，可能只记录日志
            Log.e(TAG, "WebSocket错误: $message")
        }
    }

    override fun onHeartbeatAck(success: Boolean, active: Int, msg: String) {
        runOnUiThread {
            Log.d(TAG, "心跳应答: 成功=$success, 活跃=$active, 消息=$msg")
        }
    }

    override fun onActiveStatusChanged(active: Boolean, msg: String) {
        runOnUiThread {
            Log.d(TAG, "活跃状态变更: 活跃=$active, 消息=$msg")
        }
    }

    override fun onPushContent(content: Content) {
        runOnUiThread {
            Log.d(TAG, "推送内容已接收: ${content.title}")
            statusTextView.isVisible = false
            playerManager.play(listOf(content))
        }
    }

    override fun onDisplayModeChanged(mode: Int, modeName: String) {
        runOnUiThread {
            Log.d(TAG, "显示模式变更: 模式=$mode, 名称=$modeName")
            // 可选：立即请求新内容以反映新模式
            WebSocketManager.sendGetContentMessage()
        }
    }

    override fun onTempContent(content: TempContentData) {
        runOnUiThread {
            Log.d(TAG, "临时内容已接收: ${content.title}")
            statusTextView.isVisible = false
            // 将TempContentData转换为Content供播放器使用
            val playerContent = Content(
                id = content.contentId,
                title = content.title,
                contentType = content.contentType,
                contentUrl = content.contentUrl,
                thumbnail = content.thumbnail,
                duration = content.duration
            )
            playerManager.play(listOf(playerContent))
        }
    }

    override fun onBatchControl(action: String, message: String) {
        runOnUiThread {
            Log.d(TAG, "批量控制命令: 动作=$action, 消息=$message")
            // TODO: 实现设备操作，如重启或关机
        }
    }

    override fun onRefresh(message: String) {
        runOnUiThread {
            Log.d(TAG, "刷新命令: $message")
            // 立即请求新内容
            WebSocketManager.sendGetContentMessage()
        }
    }

    private fun updateWebSocketStatusUI(status: String? = null) {
        val currentStatus = status ?: (if (isWebSocketConnected) "Connected" else "连接失败")
        Log.d(TAG, "updateWebSocketStatusUI: 状态 = $currentStatus")
        
        when {
            currentStatus == "Connecting" -> {
                statusTextView.text = "连接中..."
                statusTextView.visibility = View.VISIBLE
                typewriterTextView.visibility = View.GONE
                guideLayout.visibility = View.VISIBLE
            }
            currentStatus == "Connected" -> {
                // 连接成功时显示固定文本，隐藏引导布局
                guideLayout.visibility = View.GONE
                statusTextView.visibility = View.GONE
                typewriterTextView.visibility = View.VISIBLE
                isWebSocketConnected = true
            }
            currentStatus == "Disconnected" -> {
                isWebSocketConnected = false
                // 连接断开时显示引导布局
                statusTextView.text = "连接已断开"
                statusTextView.visibility = View.VISIBLE
                typewriterTextView.visibility = View.GONE
                guideLayout.visibility = View.VISIBLE
            }
            currentStatus.startsWith("连接失败") -> {
                isWebSocketConnected = false
                // 连接失败时显示引导布局和详细状态
                statusTextView.text = currentStatus
                statusTextView.visibility = View.VISIBLE
                typewriterTextView.visibility = View.GONE
                guideLayout.visibility = View.VISIBLE
            }
            else -> {
                isWebSocketConnected = false
                // 其他错误状态显示引导布局
                statusTextView.text = currentStatus
                statusTextView.visibility = View.VISIBLE
                typewriterTextView.visibility = View.GONE
                guideLayout.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: 页面恢复，检查配置状态")
        // 清除之前的错误消息
        statusTextView.text = ""
        // 重新检查WebSocket状态并更新UI
        checkConfigurationAndUpdateUI()
    }

    /**
     * 检查配置状态并更新UI
     */
    private fun checkConfigurationAndUpdateUI() {
        val sharedPrefs = getSharedPreferences("SmartScreenSettings", Context.MODE_PRIVATE)
        val savedIp = sharedPrefs.getString("server_ip", null)
        val savedPort = sharedPrefs.getInt("server_port", 0)
        
        Log.d(TAG, "checkConfigurationAndUpdateUI: IP=$savedIp, Port=$savedPort")
        
        // 根据智慧屏WebSocket连接标准，支持默认配置
        val hasUserConfig = !savedIp.isNullOrBlank() && savedPort > 0
        
        if (hasUserConfig) {
            Log.d(TAG, "使用用户配置的服务器地址: $savedIp:$savedPort")
        } else {
            Log.d(TAG, "使用智慧屏WebSocket连接标准默认配置")
        }
        
        // 无论是否有用户配置，都可以尝试连接（因为有默认配置）
        if (isWebSocketConnected) {
            updateWebSocketStatusUI("Connected")
        } else {
            // 显示连接状态
            val statusMessage = if (hasUserConfig) {
                "连接失败"
            } else {
                "连接失败"
            }
            updateWebSocketStatusUI(statusMessage)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ::connectivityManager.isInitialized && ::networkCallback.isInitialized) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        WebSocketManager.disconnect()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}