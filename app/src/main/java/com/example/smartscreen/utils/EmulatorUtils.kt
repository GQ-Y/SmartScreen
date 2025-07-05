package com.example.smartscreen.utils

import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebView

/**
 * 模拟器环境检测和EGL优化工具类
 */
object EmulatorUtils {
    
    private const val TAG = "EmulatorUtils"
    
    /**
     * 检测是否在Android模拟器环境中运行
     */
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("google_sdk") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("sdk_gphone") ||
                Build.PRODUCT.contains("emulator")
    }
    
    /**
     * 为WebView配置EGL优化设置
     */
    fun configureWebViewForEmulator(webView: WebView) {
        if (isEmulator()) {
            try {
                // 在模拟器中禁用硬件加速以避免EGL错误
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                Log.d(TAG, "已为WebView禁用硬件加速（模拟器环境）")
            } catch (e: Exception) {
                Log.e(TAG, "配置WebView EGL优化时出错", e)
            }
        }
    }
    
    /**
     * 获取环境信息字符串
     */
    fun getEnvironmentInfo(): String {
        return buildString {
            append("设备环境信息:\n")
            append("- 制造商: ${Build.MANUFACTURER}\n")
            append("- 型号: ${Build.MODEL}\n")
            append("- 产品: ${Build.PRODUCT}\n")
            append("- 硬件: ${Build.HARDWARE}\n")
            append("- 指纹: ${Build.FINGERPRINT}\n")
            append("- 是否模拟器: ${isEmulator()}")
        }
    }
    
    /**
     * 记录环境信息到日志
     */
    fun logEnvironmentInfo() {
        Log.d(TAG, getEnvironmentInfo())
    }
} 