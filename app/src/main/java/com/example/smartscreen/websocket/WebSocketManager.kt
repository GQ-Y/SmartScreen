package com.example.smartscreen.websocket

import android.content.Context
import android.util.Log
import com.example.smartscreen.ui.SettingsActivity
import com.example.smartscreen.utils.DeviceUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.Timer
import java.util.TimerTask
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper
import android.os.Build

object WebSocketManager {

    interface WebSocketListenerEvents {
        fun onRegistration(success: Boolean, message: String)
        fun onContentReceived(data: ContentData)
        fun onError(message: String)
        fun onStatusChanged(status: String)
        fun onHeartbeatAck(success: Boolean, active: Int, msg: String)
        fun onActiveStatusChanged(active: Boolean, msg: String)
        fun onPushContent(content: Content)
        fun onDisplayModeChanged(mode: Int, modeName: String)
        fun onTempContent(content: TempContentData)
        fun onBatchControl(action: String, message: String)
        fun onRefresh(message: String)
    }

    private var listener: WeakReference<WebSocketListenerEvents>? = null

    fun setListener(listener: WebSocketListenerEvents) {
        this.listener = WeakReference(listener)
    }

    private const val TAG = "WebSocketManager"
    private const val HEARTBEAT_INTERVAL = 30 * 1000L // 30 seconds
    private const val INITIAL_RETRY_DELAY_MS = 2000L  // 2 seconds
    private const val MAX_RETRY_DELAY_MS = 60000L // 1 minute
    
    // 智慧屏WebSocket连接标准默认配置
    private const val DEFAULT_HOST = "localhost"
    private const val DEFAULT_PORT = 9502
    private const val DEFAULT_PATH = "/ws"
    private const val DEFAULT_ANDROID_EMULATOR_HOST = "10.0.2.2" // Android模拟器访问主机localhost的特殊IP

    private val deviceMacAddress: String by lazy {
        DeviceUtils.getMacAddress() ?: "AA:BB:CC:DD:EE:FF".also {
            Log.w(TAG, "无法获取真实MAC地址，使用备用地址")
        }
    }

    private val gson = Gson()
    // 格式化的Gson用于打印日志
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private var heartbeatTimer: Timer? = null
    private val connectionHandler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var applicationContext: WeakReference<Context>? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    private var webSocket: WebSocket? = null

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket连接已打开")
            retryCount = 0 // 连接成功后重置重试计数
            listener?.get()?.onStatusChanged("Connected")
            sendRegisterMessage()
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "收到消息：\n${formatJsonForLog(text)}")
            handleIncomingMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket连接失败", t)
            Log.e(TAG, "🚨 连接失败详情:")
            Log.e(TAG, "   异常类型: ${t.javaClass.simpleName}")
            Log.e(TAG, "   异常消息: ${t.message}")
            Log.e(TAG, "   响应码: ${response?.code}")
            Log.e(TAG, "   响应消息: ${response?.message}")
            listener?.get()?.onStatusChanged("Disconnected")
            listener?.get()?.onError("连接失败: ${t.message}")
            stopHeartbeat()
            scheduleReconnect()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket连接正在关闭: $reason")
            stopHeartbeat()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket连接已关闭: $reason")
            listener?.get()?.onStatusChanged("Disconnected")
            stopHeartbeat()
            // 正常关闭时不重连 (code 1000)
            if (code != 1000) {
                scheduleReconnect()
            }
        }
    }

    /**
     * 格式化JSON用于日志打印
     */
    private fun formatJsonForLog(jsonString: String): String {
        return try {
            val jsonObject = gson.fromJson(jsonString, Any::class.java)
            prettyGson.toJson(jsonObject)
        } catch (e: Exception) {
            Log.w(TAG, "JSON格式化失败，显示原始文本", e)
            jsonString
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val baseMessage = gson.fromJson(text, BaseMessage::class.java)
            when (baseMessage.type) {
                "register_ack" -> {
                    val msg = gson.fromJson(text, RegisterAckMessage::class.java)
                    listener?.get()?.onRegistration(msg.success, msg.msg)
                    if (msg.success) {
                        Log.d(TAG, "设备注册成功，正在发送获取内容请求...")
                        sendGetContentMessage()
                    }
                }
                "content_response" -> {
                    val msg = gson.fromJson(text, ContentResponseMessage::class.java)
                    if (msg.success) {
                        Log.d(TAG, "内容响应已收到，主要内容项数量: ${msg.data.primaryContents.size}")
                        listener?.get()?.onContentReceived(msg.data)
                    } else {
                        Log.e(TAG, "获取内容失败: ${msg.msg}")
                        listener?.get()?.onError(msg.msg)
                    }
                }
                "heartbeat_ack" -> {
                    val msg = gson.fromJson(text, HeartbeatAckMessage::class.java)
                    Log.d(TAG, "心跳应答 - 成功: ${msg.success}, 活跃状态: ${msg.active}, 消息: ${msg.msg}")
                    listener?.get()?.onHeartbeatAck(msg.success, msg.active, msg.msg)
                }
                "active_status" -> {
                    val msg = gson.fromJson(text, ActiveStatusMessage::class.java)
                    Log.d(TAG, "活跃状态变更 - 活跃: ${msg.active}, 消息: ${msg.msg}")
                    listener?.get()?.onActiveStatusChanged(msg.active, msg.msg)
                }
                "push_content" -> {
                    val msg = gson.fromJson(text, PushContentMessage::class.java)
                    Log.d(TAG, "推送内容已收到")
                    listener?.get()?.onPushContent(msg.data)
                }
                "display_mode_change" -> {
                    val msg = gson.fromJson(text, DisplayModeChangeMessage::class.java)
                    Log.d(TAG, "显示模式变更 - 模式: ${msg.mode}, 模式名称: ${msg.modeName}")
                    listener?.get()?.onDisplayModeChanged(msg.mode, msg.modeName)
                }
                "temp_content" -> {
                    val msg = gson.fromJson(text, TempContentMessage::class.java)
                    Log.d(TAG, "临时内容已收到")
                    listener?.get()?.onTempContent(msg.data)
                }
                "batch_control" -> {
                    val msg = gson.fromJson(text, BatchControlMessage::class.java)
                    Log.d(TAG, "批量控制 - 动作: ${msg.action}, 消息: ${msg.message}")
                    listener?.get()?.onBatchControl(msg.action, msg.message)
                }
                "refresh" -> {
                    val msg = gson.fromJson(text, RefreshMessage::class.java)
                    Log.d(TAG, "刷新请求 - 消息: ${msg.message}")
                    listener?.get()?.onRefresh(msg.message)
                }
                "error" -> {
                    val msg = gson.fromJson(text, ErrorMessage::class.java)
                    Log.e(TAG, "服务器错误: ${msg.msg}")
                    listener?.get()?.onError(msg.msg)
                }
                else -> {
                    Log.w(TAG, "未知消息类型: ${baseMessage.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析消息时发生错误: $text", e)
            listener?.get()?.onError("解析服务器消息失败")
        }
    }

    fun connect(context: Context) {
        // 存储上下文用于重连
        this.applicationContext = WeakReference(context.applicationContext)

        // 在开始新连接前取消任何待处理的重连尝试
        connectionHandler.removeCallbacksAndMessages(null)

        if (webSocket != null) {
            Log.d(TAG, "已连接或正在连接中")
            return
        }
        listener?.get()?.onStatusChanged("Connecting")

        // 获取配置信息
        val sharedPrefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        var ip = sharedPrefs.getString(SettingsActivity.KEY_SERVER_IP, null)
        var port = sharedPrefs.getInt(SettingsActivity.KEY_SERVER_PORT, 0)

        // 根据智慧屏WebSocket连接标准使用默认配置
        val useDefaultConfig = ip.isNullOrBlank() || port == 0
        if (useDefaultConfig) {
            Log.d(TAG, "未找到用户配置，使用智慧屏WebSocket连接标准默认配置")
            // 检测是否在Android模拟器环境 - 改进检测逻辑
            val isEmulator = Build.FINGERPRINT.contains("generic") || 
                           Build.MODEL.contains("Emulator") ||
                           Build.MODEL.contains("Android SDK") ||
                           Build.MODEL.contains("sdk_google") ||
                           Build.BRAND.contains("generic") ||
                           Build.DEVICE.contains("generic") ||
                           Build.PRODUCT.contains("sdk") ||
                           Build.HARDWARE.contains("goldfish") ||
                           Build.HARDWARE.contains("ranchu")
            
            Log.d(TAG, "🔍 环境检测详情:")
            Log.d(TAG, "   Build.FINGERPRINT: ${Build.FINGERPRINT}")
            Log.d(TAG, "   Build.MODEL: ${Build.MODEL}")
            Log.d(TAG, "   Build.BRAND: ${Build.BRAND}")
            Log.d(TAG, "   Build.DEVICE: ${Build.DEVICE}")
            Log.d(TAG, "   Build.PRODUCT: ${Build.PRODUCT}")
            Log.d(TAG, "   Build.HARDWARE: ${Build.HARDWARE}")
            Log.d(TAG, "   检测结果: ${if (isEmulator) "模拟器" else "真机"}")
            
            // 临时强制使用模拟器地址进行调试
            val forceEmulatorAddress = true // 调试时设为true，发布时改为false
            
            ip = if (isEmulator || forceEmulatorAddress) {
                val selectedHost = DEFAULT_ANDROID_EMULATOR_HOST
                Log.d(TAG, "✅ 使用模拟器专用地址: $selectedHost ${if (forceEmulatorAddress) "(强制)" else "(检测)"}")
                selectedHost
            } else {
                Log.d(TAG, "✅ 检测到真机环境，使用标准localhost地址: $DEFAULT_HOST")
                DEFAULT_HOST
            }
            port = DEFAULT_PORT
            
            Log.d(TAG, "默认配置 - 主机: $ip, 端口: $port, 路径: $DEFAULT_PATH")
        } else {
            Log.d(TAG, "使用用户配置 - 主机: $ip, 端口: $port")
        }

        // 构建WebSocket URL，确保符合智慧屏连接标准
        val url = "ws://$ip:$port$DEFAULT_PATH"
        Log.d(TAG, "正在连接到智慧屏WebSocket服务器: $url")
        
        // 详细调试信息
        Log.d(TAG, "🔍 连接参数详情:")
        Log.d(TAG, "   IP地址: '$ip'")
        Log.d(TAG, "   端口: $port")
        Log.d(TAG, "   路径: '$DEFAULT_PATH'")
        Log.d(TAG, "   完整URL: '$url'")
        Log.d(TAG, "   使用默认配置: $useDefaultConfig")
        
        if (useDefaultConfig) {
            Log.i(TAG, "📡 使用智慧屏WebSocket连接标准默认配置连接")
            Log.i(TAG, "   默认地址: ws://$DEFAULT_HOST:$DEFAULT_PORT$DEFAULT_PATH")
            Log.i(TAG, "   实际连接: $url")
        } else {
            Log.i(TAG, "📡 使用用户自定义配置连接: $url")
        }

        val request = Request.Builder().url(url).build()
        Log.d(TAG, "🌐 OkHttp请求URL: ${request.url}")
        webSocket = client.newWebSocket(request, webSocketListener)
    }

    fun disconnect() {
        // 用户主动断开连接，取消任何计划的重连
        connectionHandler.removeCallbacksAndMessages(null)
        retryCount = 0
        stopHeartbeat()
        webSocket?.close(1000, "客户端断开连接")
        webSocket = null
        Log.d(TAG, "WebSocket连接已断开")
    }

    private fun sendRegisterMessage() {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val registerMessage = RegisterMessage(deviceMacAddress, deviceName)
        val jsonMessage = gson.toJson(registerMessage)
        Log.d(TAG, "发送设备注册消息:\n${formatJsonForLog(jsonMessage)}")
        sendMessage(jsonMessage)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                val heartbeatMessage = HeartbeatMessage(deviceMacAddress)
                val jsonMessage = gson.toJson(heartbeatMessage)
                Log.d(TAG, "发送心跳消息:\n${formatJsonForLog(jsonMessage)}")
                sendMessage(jsonMessage)
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL)
        Log.d(TAG, "心跳定时器已启动，间隔: ${HEARTBEAT_INTERVAL / 1000}秒")
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        Log.d(TAG, "心跳定时器已停止")
    }

    private fun scheduleReconnect() {
        val delay = (INITIAL_RETRY_DELAY_MS * (1 shl retryCount.coerceAtMost(6))).coerceAtMost(MAX_RETRY_DELAY_MS)
        Log.d(TAG, "计划第${retryCount + 1}次重连尝试，延迟${delay / 1000}秒")

        connectionHandler.postDelayed({
            Log.d(TAG, "执行第${retryCount + 1}次重连尝试")
            applicationContext?.get()?.let {
                connect(it)
            } ?: run {
                Log.e(TAG, "无法重连，上下文为空")
            }
        }, delay)
        retryCount++
    }

    fun sendGetContentMessage() {
        val getContentMessage = GetContentMessage(deviceMacAddress)
        val jsonMessage = gson.toJson(getContentMessage)
        Log.d(TAG, "发送获取内容消息:\n${formatJsonForLog(jsonMessage)}")
        sendMessage(jsonMessage)
    }

    private fun sendMessage(message: String) {
        webSocket?.send(message)
    }
} 