plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.smartscreen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartscreen"
        minSdk = 21
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"

    }

    // 签名配置：优先从 Gradle 属性或环境变量读取；可选回落到 debug keystore（仅限内测）
    signingConfigs {
        create("release") {
            val prop = { key: String ->
                (project.findProperty(key) as String?) ?: System.getenv(key)
            }

            val storeFilePath = prop("RELEASE_STORE_FILE")
            val storePassword = prop("RELEASE_STORE_PASSWORD")
            val keyAlias = prop("RELEASE_KEY_ALIAS")
            val keyPassword = prop("RELEASE_KEY_PASSWORD")

            if (!storeFilePath.isNullOrBlank() && !storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else if ((project.findProperty("USE_DEBUG_FOR_RELEASE") as String?).equals("true", ignoreCase = true)) {
                // 使用本机 debug.keystore 签名（仅用于内测，不可用于线上发布）
                val home = System.getProperty("user.home")
                storeFile = file("$home/.android/debug.keystore")
                this.storePassword = "android"
                this.keyAlias = "androiddebugkey"
                this.keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // WebSocket
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // Media Player
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // JSON Parser
    implementation("com.google.code.gson:gson:2.11.0")
    
    // 腾讯X5浏览器内核
    implementation("com.tencent.tbs:tbssdk:44286")
}