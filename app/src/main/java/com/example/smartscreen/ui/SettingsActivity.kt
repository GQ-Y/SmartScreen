package com.example.smartscreen.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smartscreen.R
import com.example.smartscreen.websocket.WebSocketManager

class SettingsActivity : AppCompatActivity() {
    
    private val TAG = "SettingsActivity"

    // 菜单导航
    private lateinit var menuSystemSettings: LinearLayout
    private lateinit var menuAboutDevice: LinearLayout
    
    // 内容区域
    private lateinit var systemSettingsContent: LinearLayout
    private lateinit var aboutDeviceContent: LinearLayout
    
    // 系统设置相关
    private lateinit var ipAddressInput: EditText
    private lateinit var portInput: EditText
    private lateinit var saveButton: Button
    
    // 关于设备相关
    private lateinit var deviceModelTextView: TextView
    private lateinit var androidVersionTextView: TextView
    private lateinit var appVersionTextView: TextView
    
    // SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "SmartScreenSettings"
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: 开始启动设置页面")
        
        try {
            setContentView(R.layout.activity_settings)
            Log.d(TAG, "onCreate: 布局文件加载成功")
            
            initViews()
            Log.d(TAG, "onCreate: 视图初始化完成")
            
            setupSharedPreferences()
            Log.d(TAG, "onCreate: SharedPreferences设置完成")
            
            setupMenuNavigation()
            Log.d(TAG, "onCreate: 菜单导航设置完成")
            
            setupSystemSettings()
            Log.d(TAG, "onCreate: 系统设置配置完成")
            
            setupAboutDevice()
            Log.d(TAG, "onCreate: 关于设备信息设置完成")
            
            loadSettings()
            Log.d(TAG, "onCreate: 设置加载完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: 初始化过程中发生错误", e)
            finish()
        }
    }

    private fun initViews() {
        try {
            // 菜单导航
            menuSystemSettings = findViewById(R.id.menu_system_settings)
            menuAboutDevice = findViewById(R.id.menu_about_device)
            
            // 内容区域
            systemSettingsContent = findViewById(R.id.system_settings_content)
            aboutDeviceContent = findViewById(R.id.about_device_content)
            
            // 系统设置相关
            ipAddressInput = findViewById(R.id.ip_address_input)
            portInput = findViewById(R.id.et_server_port)
            saveButton = findViewById(R.id.btn_save_settings)
            
            // 关于设备相关
            deviceModelTextView = findViewById(R.id.tv_device_model)
            androidVersionTextView = findViewById(R.id.tv_android_version)
            appVersionTextView = findViewById(R.id.tv_app_version)
            
            Log.d(TAG, "initViews: 所有视图组件查找成功")
        } catch (e: Exception) {
            Log.e(TAG, "initViews: 查找视图组件时发生错误", e)
            throw e
        }
    }

    private fun setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun setupMenuNavigation() {
        try {
            menuSystemSettings.setOnClickListener {
                Log.d(TAG, "用户点击了系统设置菜单")
                showSystemSettings()
            }
            
            menuAboutDevice.setOnClickListener {
                Log.d(TAG, "用户点击了关于设备菜单")
                showAboutDevice()
            }
            
            // 设置焦点监听器
            menuSystemSettings.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Log.d(TAG, "系统设置菜单获得焦点")
                    showSystemSettings()
                }
            }
            
            menuAboutDevice.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Log.d(TAG, "关于设备菜单获得焦点")
                    showAboutDevice()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupMenuNavigation: 设置菜单导航时发生错误", e)
            throw e
        }
    }

    private fun setupSystemSettings() {
        try {
            // 为IP输入框禁用剪贴板功能
            ipAddressInput.isLongClickable = false
            ipAddressInput.setTextIsSelectable(false)
            ipAddressInput.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
            }
            
            // 为端口输入框禁用剪贴板功能
            portInput.isLongClickable = false
            portInput.setTextIsSelectable(false)
            portInput.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
            }
            
            saveButton.setOnClickListener {
                Log.d(TAG, "用户点击了保存设置按钮")
                saveSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupSystemSettings: 设置系统配置时发生错误", e)
            throw e
        }
    }

    private fun setupAboutDevice() {
        try {
            // 获取设备信息
            val deviceModel = Build.MODEL
            val androidVersion = Build.VERSION.RELEASE
            val appVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                Log.w(TAG, "无法获取应用版本信息", e)
                "1.0.0"
            }
            
            // 更新UI
            deviceModelTextView.text = "设备型号: $deviceModel"
            androidVersionTextView.text = "Android版本: $androidVersion"
            appVersionTextView.text = "版本: $appVersion"
        } catch (e: Exception) {
            Log.e(TAG, "setupAboutDevice: 设置设备信息时发生错误", e)
            throw e
        }
    }

    private fun showSystemSettings() {
        try {
            systemSettingsContent.visibility = android.view.View.VISIBLE
            aboutDeviceContent.visibility = android.view.View.GONE
            Log.d(TAG, "显示系统设置页面")
        } catch (e: Exception) {
            Log.e(TAG, "showSystemSettings: 显示系统设置时发生错误", e)
        }
    }

    private fun showAboutDevice() {
        try {
            systemSettingsContent.visibility = android.view.View.GONE
            aboutDeviceContent.visibility = android.view.View.VISIBLE
            Log.d(TAG, "显示关于设备页面")
        } catch (e: Exception) {
            Log.e(TAG, "showAboutDevice: 显示关于设备时发生错误", e)
        }
    }

    private fun loadSettings() {
        try {
            val savedIp = sharedPreferences.getString(KEY_SERVER_IP, "192.168.1.100")
            val savedPort = sharedPreferences.getInt(KEY_SERVER_PORT, 9502)
            
            ipAddressInput.setText(savedIp)
            portInput.setText(savedPort.toString())
            
            Log.d(TAG, "设置加载完成 - IP地址: $savedIp, 端口: $savedPort")
        } catch (e: Exception) {
            Log.e(TAG, "loadSettings: 加载设置时发生错误", e)
        }
    }

    private fun saveSettings() {
        try {
            val ip = ipAddressInput.text.toString().trim()
            val port = portInput.text.toString().toIntOrNull() ?: 9502
            
            // 验证IP地址不为空
            if (ip.isEmpty()) {
                Log.w(TAG, "IP地址不能为空")
                return
            }
            
            // 保存设置到SharedPreferences
            sharedPreferences.edit()
                .putString(KEY_SERVER_IP, ip)
                .putInt(KEY_SERVER_PORT, port)
                .apply()
            
            Log.d(TAG, "设置保存成功 - IP地址: $ip, 端口: $port")
            
            // 立即尝试连接WebSocket
            Log.d(TAG, "开始尝试连接WebSocket服务器...")
            try {
                WebSocketManager.connect(this)
                Log.d(TAG, "WebSocket连接已启动 - 服务器地址: $ip:$port")
            } catch (e: Exception) {
                Log.e(TAG, "启动WebSocket连接失败", e)
            }
            
            // 保存并连接后立即返回主页面
            Log.d(TAG, "返回主页面查看连接状态")
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "保存设置失败", e)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                Log.d(TAG, "用户按下返回键，返回主页面")
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
} 