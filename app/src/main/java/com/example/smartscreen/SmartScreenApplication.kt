package com.example.smartscreen

import android.app.Application
import android.util.Log
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.QbSdk.PreInitCallback

class SmartScreenApplication : Application() {

    companion object {
        private const val TAG = "SmartScreenApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        // 初始化X5内核
        initX5WebView()
    }

    private fun initX5WebView() {
        try {
            // 在调用TBS初始化、创建WebView之前进行如下配置
            val cb = object : PreInitCallback {
                override fun onViewInitFinished(isSuccess: Boolean) {
                    // X5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
                    if (isSuccess) {
                        Log.d(TAG, "X5内核初始化成功")
                    } else {
                        Log.w(TAG, "X5内核初始化失败，将使用系统内核")
                    }
                }

                override fun onCoreInitFinished() {
                    Log.d(TAG, "X5内核初始化完成")
                }
            }

            // x5内核初始化接口
            QbSdk.initX5Environment(applicationContext, cb)
            
            // 设置开启优化方案
            val map = HashMap<String, Any>()
            map["use_speedy_classloader"] = true
            map["use_dexloader_service"] = true
            QbSdk.initTbsSettings(map)
            
            Log.d(TAG, "开始初始化X5内核...")
        } catch (e: Exception) {
            Log.e(TAG, "初始化X5内核时出错", e)
        }
    }
} 