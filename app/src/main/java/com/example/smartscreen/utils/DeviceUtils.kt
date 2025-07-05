package com.example.smartscreen.utils

import android.util.Log
import java.net.NetworkInterface
import java.util.*

object DeviceUtils {

    private const val TAG = "DeviceUtils"

    fun getMacAddress(): String? {
        try {
            val allInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in allInterfaces) {
                // TV可以通过Wi-Fi (wlan0)或以太网(eth0)连接
                if (!intf.name.equals("wlan0", ignoreCase = true) && !intf.name.equals("eth0", ignoreCase = true)) {
                    continue
                }

                val macBytes = intf.hardwareAddress ?: continue

                val macAddress = StringBuilder()
                for (b in macBytes) {
                    macAddress.append(String.format("%02X:", b))
                }

                if (macAddress.isNotEmpty()) {
                    macAddress.deleteCharAt(macAddress.length - 1)
                }
                
                Log.d(TAG, "发现MAC地址: '${macAddress.toString()}' 网络接口: ${intf.name}")
                return macAddress.toString()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "获取MAC地址时发生异常", ex)
        }
        
        Log.w(TAG, "无法找到wlan0或eth0的MAC地址")
        return null
    }
} 