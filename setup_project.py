from pathlib import Path
root = Path('/mnt/data/CarAppStore')
files = {}

def add(path, content):
    files[path] = content.lstrip('\n')

add('settings.gradle.kts', '''
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CarAppStore"
include(":app")
''')

add('build.gradle.kts', '''
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
''')

add('gradle.properties', '''
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
''')

add('gradle/libs.versions.toml', '''
[versions]
androidx-core-ktx = "1.13.1"
androidx-appcompat = "1.7.0"
material = "1.12.0"
constraintlayout = "2.1.4"
fragment-ktx = "1.8.2"
activity-ktx = "1.9.1"
lifecycle = "2.8.4"
coroutines = "1.8.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core-ktx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidx-appcompat" }
google-material = { module = "com.google.android.material:material", version.ref = "material" }
androidx-constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout" }
androidx-fragment-ktx = { module = "androidx.fragment:fragment-ktx", version.ref = "fragment-ktx" }
androidx-activity-ktx = { module = "androidx.activity:activity-ktx", version.ref = "activity-ktx" }
androidx-lifecycle-viewmodel-ktx = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-livedata-ktx = { module = "androidx.lifecycle:lifecycle-livedata-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
''')

add('README.md', '''
# CarAppStore

一个基于 MVVM 的车载应用商店 Android 工程骨架。

## 已落地约束
- MVVM
- 不使用 Hilt
- 不使用 Navigation
- 使用手动依赖注入（AppContainer）
- 使用 FragmentManager 管理页面切换
- 业务层保留 7 个核心模块：
  - 下载模块
  - 安装模块
  - 升级模块
  - 应用管理模块
  - 状态中心
  - 策略中心
  - Repository

## 当前骨架包含
- 首页 / 详情页 / 我的应用 基础页面
- AppContainer 手动注入
- feature / domain / data / core / common 分层目录
- Fake 数据源，可直接作为后续真实实现的替身

## 下一步建议
1. 将 Fake Repository 替换为真实 remote/local/system 数据源
2. 补充下载模块与安装模块的真实实现
3. 扩展状态中心为统一 Flow 状态源
4. 再接入升级模块和策略中心细节
''')

add('app/build.gradle.kts', '''
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.carappstore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.carappstore"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
''')

add('app/proguard-rules.pro', '''
# Intentionally empty for skeleton project.
''')

add('app/src/main/AndroidManifest.xml', '''
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".app.App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CarAppStore">
        <activity
            android:name=".app.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
''')

add('app/src/main/res/values/strings.xml', '''
<resources>
    <string name="app_name">CarAppStore</string>
    <string name="title_home">首页</string>
    <string name="title_detail">详情页</string>
    <string name="title_my_apps">我的应用</string>
    <string name="title_upgrade">升级管理</string>
    <string name="loading">加载中...</string>
    <string name="download">下载</string>
    <string name="install">安装</string>
    <string name="open">打开</string>
    <string name="upgrade">升级</string>
    <string name="go_my_apps">我的应用</string>
    <string name="back_home">返回首页</string>
</resources>
''')

add('app/src/main/res/values/colors.xml', '''
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
''')

add('app/src/main/res/values/themes.xml', '''
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.CarAppStore" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/teal_200</item>
        <item name="colorSecondaryVariant">@color/teal_700</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:statusBarColor" tools:targetApi="l">@color/purple_700</item>
    </style>
</resources>
''')

add('app/src/main/res/layout/activity_main.xml', '''
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/darker_gray"
        android:padding="16dp"
        android:text="CarAppStore"
        android:textColor="@android:color/white"
        android:textSize="20sp" />

    <FrameLayout
        android:id="@+id/fragmentContainer"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
''')

add('app/src/main/res/layout/fragment_home.xml', '''
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvHomeTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="首页（示例应用）"
        android:textSize="20sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tvAppName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:textSize="18sp" />

    <TextView
        android:id="@+id/tvAppDesc"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp" />

    <Button
        android:id="@+id/btnDetail"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="进入详情页" />

    <Button
        android:id="@+id/btnMyApps"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="我的应用" />
</LinearLayout>
''')

add('app/src/main/res/layout/fragment_detail.xml', '''
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvDetailName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="22sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tvDetailVersion"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp" />

    <TextView
        android:id="@+id/tvDetailDesc"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp" />

    <TextView
        android:id="@+id/tvState"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="当前状态：未安装" />

    <Button
        android:id="@+id/btnPrimaryAction"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        android:text="下载" />

    <Button
        android:id="@+id/btnGoMyApps"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="我的应用" />

    <Button
        android:id="@+id/btnBackHome"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="返回首页" />
</LinearLayout>
''')

add('app/src/main/res/layout/fragment_my_app.xml', '''
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvMyAppTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="我的应用"
        android:textSize="20sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/tvInstalledApps"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="暂无已安装应用" />

    <Button
        android:id="@+id/btnBackHome"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="返回首页" />
</LinearLayout>
''')

# app layer
add('app/src/main/java/com/example/carappstore/app/App.kt', '''
package com.example.carappstore.app

import android.app.Application

class App : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
''')

add('app/src/main/java/com/example/carappstore/app/AppContainer.kt', '''
package com.example.carappstore.app

import android.content.Context
import com.example.carappstore.core.logger.AppLogger
import com.example.carappstore.core.tracker.EventTracker
import com.example.carappstore.data.datasource.local.AppLocalDataSource
import com.example.carappstore.data.datasource.remote.AppRemoteDataSource
import com.example.carappstore.data.datasource.system.AppSystemDataSource
import com.example.carappstore.data.repository.AppRepository
import com.example.carappstore.data.repository.FakeAppRepository
import com.example.carappstore.domain.appmanager.AppManager
import com.example.carappstore.domain.appmanager.DefaultAppManager
import com.example.carappstore.domain.download.DefaultDownloadManager
import com.example.carappstore.domain.download.DownloadManager
import com.example.carappstore.domain.install.DefaultInstallManager
import com.example.carappstore.domain.install.InstallManager
import com.example.carappstore.domain.policy.DefaultPolicyCenter
import com.example.carappstore.domain.policy.PolicyCenter
import com.example.carappstore.domain.state.DefaultStateCenter
import com.example.carappstore.domain.state.StateCenter
import com.example.carappstore.domain.upgrade.DefaultUpgradeManager
import com.example.carappstore.domain.upgrade.UpgradeManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val logger: AppLogger by lazy { AppLogger() }
    val tracker: EventTracker by lazy { EventTracker() }

    private val remoteDataSource: AppRemoteDataSource by lazy { AppRemoteDataSource() }
    private val localDataSource: AppLocalDataSource by lazy { AppLocalDataSource() }
    private val systemDataSource: AppSystemDataSource by lazy { AppSystemDataSource(appContext) }

    val repository: AppRepository by lazy {
        FakeAppRepository(remoteDataSource, localDataSource, systemDataSource)
    }

    val stateCenter: StateCenter by lazy { DefaultStateCenter() }
    val policyCenter: PolicyCenter by lazy { DefaultPolicyCenter() }

    val downloadManager: DownloadManager by lazy {
        DefaultDownloadManager(repository, stateCenter, policyCenter, logger, tracker)
    }

    val installManager: InstallManager by lazy {
        DefaultInstallManager(repository, stateCenter, policyCenter, logger, tracker)
    }

    val upgradeManager: UpgradeManager by lazy {
        DefaultUpgradeManager(repository, stateCenter, policyCenter, downloadManager, installManager, logger, tracker)
    }

    val appManager: AppManager by lazy {
        DefaultAppManager(repository, stateCenter)
    }
}
''')

add('app/src/main/java/com/example/carappstore/app/MainActivity.kt', '''
package com.example.carappstore.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.carappstore.R
import com.example.carappstore.databinding.ActivityMainBinding
import com.example.carappstore.feature.detail.DetailFragment
import com.example.carappstore.feature.home.HomeFragment
import com.example.carappstore.feature.myapp.MyAppFragment

class MainActivity : AppCompatActivity(), MainNavigator {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            openHome()
        }
    }

    override fun updateTitle(title: String) {
        binding.tvTitle.text = title
    }

    override fun openHome() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment.newInstance())
            .commit()
        updateTitle(getString(R.string.title_home))
    }

    override fun openDetail(appId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, DetailFragment.newInstance(appId))
            .addToBackStack(null)
            .commit()
        updateTitle(getString(R.string.title_detail))
    }

    override fun openMyApps() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MyAppFragment.newInstance())
            .addToBackStack(null)
            .commit()
        updateTitle(getString(R.string.title_my_apps))
    }
}
''')

add('app/src/main/java/com/example/carappstore/app/MainNavigator.kt', '''
package com.example.carappstore.app

interface MainNavigator {
    fun openHome()
    fun openDetail(appId: String)
    fun openMyApps()
    fun updateTitle(title: String)
}
''')

# common
add('app/src/main/java/com/example/carappstore/common/base/BaseViewModel.kt', '''
package com.example.carappstore.common.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseViewModel<T>(initialState: T) : ViewModel() {
    protected val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<T> = _uiState.asStateFlow()
}
''')

add('app/src/main/java/com/example/carappstore/common/base/BaseFragment.kt', '''
package com.example.carappstore.common.base

import androidx.fragment.app.Fragment
import com.example.carappstore.app.App
import com.example.carappstore.app.AppContainer
import com.example.carappstore.app.MainNavigator

abstract class BaseFragment : Fragment() {
    protected val appContainer: AppContainer
        get() = (requireActivity().application as App).appContainer

    protected val navigator: MainNavigator
        get() = requireActivity() as MainNavigator
}
''')

add('app/src/main/java/com/example/carappstore/common/result/AppResult.kt', '''
package com.example.carappstore.common.result

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String) : AppResult<Nothing>()
}
''')

# core
add('app/src/main/java/com/example/carappstore/core/logger/AppLogger.kt', '''
package com.example.carappstore.core.logger

import android.util.Log

class AppLogger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
''')

add('app/src/main/java/com/example/carappstore/core/tracker/EventTracker.kt', '''
package com.example.carappstore.core.tracker

import android.util.Log

class EventTracker {
    fun track(event: String) {
        Log.d("EventTracker", event)
    }
}
''')

# data models
add('app/src/main/java/com/example/carappstore/data/model/AppInfo.kt', '''
package com.example.carappstore.data.model

data class AppInfo(
    val appId: String,
    val packageName: String,
    val name: String,
    val description: String,
    val versionName: String,
)
''')

add('app/src/main/java/com/example/carappstore/data/model/AppDetail.kt', '''
package com.example.carappstore.data.model

data class AppDetail(
    val appId: String,
    val packageName: String,
    val name: String,
    val description: String,
    val versionName: String,
    val apkUrl: String,
)
''')

add('app/src/main/java/com/example/carappstore/data/model/InstalledApp.kt', '''
package com.example.carappstore.data.model

data class InstalledApp(
    val appId: String,
    val packageName: String,
    val name: String,
    val versionName: String,
)
''')

add('app/src/main/java/com/example/carappstore/data/model/UpgradeInfo.kt', '''
package com.example.carappstore.data.model

data class UpgradeInfo(
    val appId: String,
    val latestVersion: String,
    val apkUrl: String,
    val hasUpgrade: Boolean,
)
''')

add('app/src/main/java/com/example/carappstore/data/model/AppViewData.kt', '''
package com.example.carappstore.data.model

import com.example.carappstore.domain.state.ButtonState

data class AppViewData(
    val appId: String,
    val name: String,
    val description: String,
    val versionName: String,
    val stateText: String,
    val buttonState: ButtonState,
)
''')

# data sources
add('app/src/main/java/com/example/carappstore/data/datasource/remote/AppRemoteDataSource.kt', '''
package com.example.carappstore.data.datasource.remote

import com.example.carappstore.data.model.AppDetail
import com.example.carappstore.data.model.AppInfo
import com.example.carappstore.data.model.UpgradeInfo

class AppRemoteDataSource {
    fun getHomeApps(): List<AppInfo> = listOf(
        AppInfo(
            appId = "gaode_map",
            packageName = "com.demo.gaode",
            name = "高德地图车机版",
            description = "导航与出行服务",
            versionName = "1.0.0",
        )
    )

    fun getAppDetail(appId: String): AppDetail = AppDetail(
        appId = appId,
        packageName = "com.demo.gaode",
        name = "高德地图车机版",
        description = "这是一个用于演示的详情页数据。",
        versionName = "1.0.0",
        apkUrl = "https://example.com/demo.apk",
    )

    fun getUpgradeInfo(appId: String): UpgradeInfo = UpgradeInfo(
        appId = appId,
        latestVersion = "1.1.0",
        apkUrl = "https://example.com/demo_v110.apk",
        hasUpgrade = true,
    )
}
''')

add('app/src/main/java/com/example/carappstore/data/datasource/local/AppLocalDataSource.kt', '''
package com.example.carappstore.data.datasource.local

import com.example.carappstore.data.model.InstalledApp

class AppLocalDataSource {
    private val installedApps = mutableListOf<InstalledApp>()

    fun getInstalledApps(): List<InstalledApp> = installedApps.toList()

    fun saveInstalledApp(app: InstalledApp) {
        installedApps.removeAll { it.appId == app.appId }
        installedApps.add(app)
    }
}
''')

add('app/src/main/java/com/example/carappstore/data/datasource/system/AppSystemDataSource.kt', '''
package com.example.carappstore.data.datasource.system

import android.content.Context

class AppSystemDataSource(
    private val context: Context,
) {
    fun openApp(packageName: String): Boolean {
        return true
    }
}
''')

# repository
add('app/src/main/java/com/example/carappstore/data/repository/AppRepository.kt', '''
package com.example.carappstore.data.repository

import com.example.carappstore.data.model.AppDetail
import com.example.carappstore.data.model.AppInfo
import com.example.carappstore.data.model.InstalledApp
import com.example.carappstore.data.model.UpgradeInfo

interface AppRepository {
    suspend fun getHomeApps(): List<AppInfo>
    suspend fun getAppDetail(appId: String): AppDetail
    suspend fun getInstalledApps(): List<InstalledApp>
    suspend fun markInstalled(appId: String)
    suspend fun getUpgradeInfo(appId: String): UpgradeInfo
    fun openApp(packageName: String): Boolean
}
''')

add('app/src/main/java/com/example/carappstore/data/repository/FakeAppRepository.kt', '''
package com.example.carappstore.data.repository

import com.example.carappstore.data.datasource.local.AppLocalDataSource
import com.example.carappstore.data.datasource.remote.AppRemoteDataSource
import com.example.carappstore.data.datasource.system.AppSystemDataSource
import com.example.carappstore.data.model.AppDetail
import com.example.carappstore.data.model.AppInfo
import com.example.carappstore.data.model.InstalledApp
import com.example.carappstore.data.model.UpgradeInfo
import kotlinx.coroutines.delay

class FakeAppRepository(
    private val remote: AppRemoteDataSource,
    private val local: AppLocalDataSource,
    private val system: AppSystemDataSource,
) : AppRepository {

    override suspend fun getHomeApps(): List<AppInfo> {
        delay(200)
        return remote.getHomeApps()
    }

    override suspend fun getAppDetail(appId: String): AppDetail {
        delay(200)
        return remote.getAppDetail(appId)
    }

    override suspend fun getInstalledApps(): List<InstalledApp> {
        delay(100)
        return local.getInstalledApps()
    }

    override suspend fun markInstalled(appId: String) {
        val detail = remote.getAppDetail(appId)
        local.saveInstalledApp(
            InstalledApp(
                appId = detail.appId,
                packageName = detail.packageName,
                name = detail.name,
                versionName = detail.versionName,
            )
        )
    }

    override suspend fun getUpgradeInfo(appId: String): UpgradeInfo {
        delay(100)
        return remote.getUpgradeInfo(appId)
    }

    override fun openApp(packageName: String): Boolean = system.openApp(packageName)
}
''')

# domain state
add('app/src/main/java/com/example/carappstore/domain/state/AppState.kt', '''
package com.example.carappstore.domain.state

data class AppState(
    val appId: String,
    val statusText: String = "未安装",
    val buttonState: ButtonState = ButtonState.DOWNLOAD,
)

enum class ButtonState {
    DOWNLOAD,
    INSTALL,
    OPEN,
    UPGRADE,
}
''')

add('app/src/main/java/com/example/carappstore/domain/state/StateCenter.kt', '''
package com.example.carappstore.domain.state

import kotlinx.coroutines.flow.StateFlow

interface StateCenter {
    fun observe(appId: String): StateFlow<AppState>
    fun update(appId: String, state: AppState)
}
''')

add('app/src/main/java/com/example/carappstore/domain/state/DefaultStateCenter.kt', '''
package com.example.carappstore.domain.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class DefaultStateCenter : StateCenter {
    private val stateMap = ConcurrentHashMap<String, MutableStateFlow<AppState>>()

    override fun observe(appId: String): StateFlow<AppState> {
        return stateMap.getOrPut(appId) { MutableStateFlow(AppState(appId = appId)) }
    }

    override fun update(appId: String, state: AppState) {
        stateMap.getOrPut(appId) { MutableStateFlow(AppState(appId = appId)) }.value = state
    }
}
''')

# policy
add('app/src/main/java/com/example/carappstore/domain/policy/PolicyCenter.kt', '''
package com.example.carappstore.domain.policy

interface PolicyCenter {
    fun canDownload(appId: String): PolicyResult
    fun canInstall(appId: String): PolicyResult
    fun canUpgrade(appId: String): PolicyResult
}

data class PolicyResult(
    val allow: Boolean,
    val reason: String = "",
)
''')

add('app/src/main/java/com/example/carappstore/domain/policy/DefaultPolicyCenter.kt', '''
package com.example.carappstore.domain.policy

class DefaultPolicyCenter : PolicyCenter {
    override fun canDownload(appId: String): PolicyResult = PolicyResult(true)
    override fun canInstall(appId: String): PolicyResult = PolicyResult(true)
    override fun canUpgrade(appId: String): PolicyResult = PolicyResult(true)
}
''')

# download
add('app/src/main/java/com/example/carappstore/domain/download/DownloadManager.kt', '''
package com.example.carappstore.domain.download

interface DownloadManager {
    suspend fun startDownload(appId: String)
}
''')

add('app/src/main/java/com/example/carappstore/domain/download/DefaultDownloadManager.kt', '''
package com.example.carappstore.domain.download

import com.example.carappstore.core.logger.AppLogger
import com.example.carappstore.core.tracker.EventTracker
import com.example.carappstore.data.repository.AppRepository
import com.example.carappstore.domain.policy.PolicyCenter
import com.example.carappstore.domain.state.AppState
import com.example.carappstore.domain.state.ButtonState
import com.example.carappstore.domain.state.StateCenter
import kotlinx.coroutines.delay

class DefaultDownloadManager(
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val policyCenter: PolicyCenter,
    private val logger: AppLogger,
    private val tracker: EventTracker,
) : DownloadManager {

    override suspend fun startDownload(appId: String) {
        val policy = policyCenter.canDownload(appId)
        if (!policy.allow) {
            stateCenter.update(appId, AppState(appId, "下载受限：${policy.reason}", ButtonState.DOWNLOAD))
            return
        }
        logger.d("DownloadManager", "startDownload: $appId")
        tracker.track("download_start_$appId")
        stateCenter.update(appId, AppState(appId, "下载中", ButtonState.INSTALL))
        delay(600)
        stateCenter.update(appId, AppState(appId, "下载完成", ButtonState.INSTALL))
    }
}
''')

# install
add('app/src/main/java/com/example/carappstore/domain/install/InstallManager.kt', '''
package com.example.carappstore.domain.install

interface InstallManager {
    suspend fun install(appId: String)
}
''')

add('app/src/main/java/com/example/carappstore/domain/install/DefaultInstallManager.kt', '''
package com.example.carappstore.domain.install

import com.example.carappstore.core.logger.AppLogger
import com.example.carappstore.core.tracker.EventTracker
import com.example.carappstore.data.repository.AppRepository
import com.example.carappstore.domain.policy.PolicyCenter
import com.example.carappstore.domain.state.AppState
import com.example.carappstore.domain.state.ButtonState
import com.example.carappstore.domain.state.StateCenter
import kotlinx.coroutines.delay

class DefaultInstallManager(
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val policyCenter: PolicyCenter,
    private val logger: AppLogger,
    private val tracker: EventTracker,
) : InstallManager {

    override suspend fun install(appId: String) {
        val policy = policyCenter.canInstall(appId)
        if (!policy.allow) {
            stateCenter.update(appId, AppState(appId, "安装受限：${policy.reason}", ButtonState.INSTALL))
            return
        }
        logger.d("InstallManager", "install: $appId")
        tracker.track("install_start_$appId")
        stateCenter.update(appId, AppState(appId, "安装中", ButtonState.OPEN))
        delay(500)
        repository.markInstalled(appId)
        stateCenter.update(appId, AppState(appId, "已安装", ButtonState.OPEN))
    }
}
''')

# appmanager
add('app/src/main/java/com/example/carappstore/domain/appmanager/AppManager.kt', '''
package com.example.carappstore.domain.appmanager

import com.example.carappstore.data.model.AppDetail
import com.example.carappstore.data.model.AppInfo
import com.example.carappstore.data.model.AppViewData

interface AppManager {
    suspend fun getHomeApps(): List<AppInfo>
    suspend fun getAppDetail(appId: String): AppDetail
    suspend fun getMyApps(): List<AppViewData>
    fun openApp(packageName: String): Boolean
}
''')

add('app/src/main/java/com/example/carappstore/domain/appmanager/DefaultAppManager.kt', '''
package com.example.carappstore.domain.appmanager

import com.example.carappstore.data.model.AppDetail
import com.example.carappstore.data.model.AppInfo
import com.example.carappstore.data.model.AppViewData
import com.example.carappstore.data.repository.AppRepository
import com.example.carappstore.domain.state.ButtonState
import com.example.carappstore.domain.state.StateCenter

class DefaultAppManager(
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
) : AppManager {

    override suspend fun getHomeApps(): List<AppInfo> = repository.getHomeApps()

    override suspend fun getAppDetail(appId: String): AppDetail = repository.getAppDetail(appId)

    override suspend fun getMyApps(): List<AppViewData> {
        return repository.getInstalledApps().map {
            AppViewData(
                appId = it.appId,
                name = it.name,
                description = "已安装应用",
                versionName = it.versionName,
                stateText = stateCenter.observe(it.appId).value.statusText,
                buttonState = ButtonState.OPEN,
            )
        }
    }

    override fun openApp(packageName: String): Boolean = repository.openApp(packageName)
}
''')

# upgrade
add('app/src/main/java/com/example/carappstore/domain/upgrade/UpgradeManager.kt', '''
package com.example.carappstore.domain.upgrade

interface UpgradeManager {
    suspend fun startUpgrade(appId: String)
}
''')

add('app/src/main/java/com/example/carappstore/domain/upgrade/DefaultUpgradeManager.kt', '''
package com.example.carappstore.domain.upgrade

import com.example.carappstore.core.logger.AppLogger
import com.example.carappstore.core.tracker.EventTracker
import com.example.carappstore.data.repository.AppRepository
import com.example.carappstore.domain.download.DownloadManager
import com.example.carappstore.domain.install.InstallManager
import com.example.carappstore.domain.policy.PolicyCenter
import com.example.carappstore.domain.state.AppState
import com.example.carappstore.domain.state.ButtonState
import com.example.carappstore.domain.state.StateCenter

class DefaultUpgradeManager(
    private val repository: AppRepository,
    private val stateCenter: StateCenter,
    private val policyCenter: PolicyCenter,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val logger: AppLogger,
    private val tracker: EventTracker,
) : UpgradeManager {

    override suspend fun startUpgrade(appId: String) {
        val policy = policyCenter.canUpgrade(appId)
        if (!policy.allow) {
            stateCenter.update(appId, AppState(appId, "升级受限：${policy.reason}", ButtonState.UPGRADE))
            return
        }
        logger.d("UpgradeManager", "startUpgrade: $appId")
        tracker.track("upgrade_start_$appId")
        stateCenter.update(appId, AppState(appId, "升级中", ButtonState.OPEN))
        downloadManager.startDownload(appId)
        installManager.install(appId)
        stateCenter.update(appId, AppState(appId, "升级完成", ButtonState.OPEN))
    }
}
''')

# feature home
add('app/src/main/java/com/example/carappstore/feature/home/HomeUiState.kt', '''
package com.example.carappstore.feature.home

import com.example.carappstore.data.model.AppInfo

data class HomeUiState(
    val loading: Boolean = true,
    val app: AppInfo? = null,
)
''')

add('app/src/main/java/com/example/carappstore/feature/home/HomeViewModel.kt', '''
package com.example.carappstore.feature.home

import androidx.lifecycle.viewModelScope
import com.example.carappstore.common.base.BaseViewModel
import com.example.carappstore.domain.appmanager.AppManager
import kotlinx.coroutines.launch

class HomeViewModel(
    private val appManager: AppManager,
) : BaseViewModel<HomeUiState>(HomeUiState()) {

    fun load() {
        viewModelScope.launch {
            val firstApp = appManager.getHomeApps().firstOrNull()
            _uiState.value = HomeUiState(
                loading = false,
                app = firstApp,
            )
        }
    }
}
''')

add('app/src/main/java/com/example/carappstore/feature/home/HomeViewModelFactory.kt', '''
package com.example.carappstore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.carappstore.domain.appmanager.AppManager

class HomeViewModelFactory(
    private val appManager: AppManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(appManager) as T
    }
}
''')

add('app/src/main/java/com/example/carappstore/feature/home/HomeFragment.kt', '''
package com.example.carappstore.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carappstore.common.base.BaseFragment
import com.example.carappstore.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(appContainer.appManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle("首页")
        observeState()
        viewModel.load()

        binding.btnDetail.setOnClickListener {
            viewModel.uiState.value.app?.let { app ->
                navigator.openDetail(app.appId)
            }
        }
        binding.btnMyApps.setOnClickListener {
            navigator.openMyApps()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvAppName.text = state.app?.name ?: "暂无应用"
                    binding.tvAppDesc.text = state.app?.description ?: ""
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
''')

# feature detail
add('app/src/main/java/com/example/carappstore/feature/detail/DetailUiState.kt', '''
package com.example.carappstore.feature.detail

import com.example.carappstore.data.model.AppDetail
import com.example.carappstore.domain.state.ButtonState

data class DetailUiState(
    val appDetail: AppDetail? = null,
    val stateText: String = "未安装",
    val buttonState: ButtonState = ButtonState.DOWNLOAD,
)
''')

add('app/src/main/java/com/example/carappstore/feature/detail/DetailViewModel.kt', '''
package com.example.carappstore.feature.detail

import androidx.lifecycle.viewModelScope
import com.example.carappstore.common.base.BaseViewModel
import com.example.carappstore.domain.appmanager.AppManager
import com.example.carappstore.domain.download.DownloadManager
import com.example.carappstore.domain.install.InstallManager
import com.example.carappstore.domain.state.ButtonState
import com.example.carappstore.domain.state.StateCenter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DetailViewModel(
    private val appManager: AppManager,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val stateCenter: StateCenter,
) : BaseViewModel<DetailUiState>(DetailUiState()) {

    private lateinit var currentAppId: String

    fun load(appId: String) {
        currentAppId = appId
        viewModelScope.launch {
            val detail = appManager.getAppDetail(appId)
            _uiState.value = _uiState.value.copy(appDetail = detail)
        }
        stateCenter.observe(appId)
            .onEach { appState ->
                _uiState.value = _uiState.value.copy(
                    stateText = appState.statusText,
                    buttonState = appState.buttonState,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onPrimaryClick() {
        when (_uiState.value.buttonState) {
            ButtonState.DOWNLOAD -> viewModelScope.launch { downloadManager.startDownload(currentAppId) }
            ButtonState.INSTALL -> viewModelScope.launch { installManager.install(currentAppId) }
            ButtonState.OPEN -> _uiState.value.appDetail?.let { appManager.openApp(it.packageName) }
            ButtonState.UPGRADE -> Unit
        }
    }
}
''')

add('app/src/main/java/com/example/carappstore/feature/detail/DetailViewModelFactory.kt', '''
package com.example.carappstore.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.carappstore.domain.appmanager.AppManager
import com.example.carappstore.domain.download.DownloadManager
import com.example.carappstore.domain.install.InstallManager
import com.example.carappstore.domain.state.StateCenter

class DetailViewModelFactory(
    private val appManager: AppManager,
    private val downloadManager: DownloadManager,
    private val installManager: InstallManager,
    private val stateCenter: StateCenter,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailViewModel(appManager, downloadManager, installManager, stateCenter) as T
    }
}
''')

add('app/src/main/java/com/example/carappstore/feature/detail/DetailFragment.kt', '''
package com.example.carappstore.feature.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carappstore.common.base.BaseFragment
import com.example.carappstore.databinding.FragmentDetailBinding
import com.example.carappstore.domain.state.ButtonState
import kotlinx.coroutines.launch

class DetailFragment : BaseFragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val appId: String by lazy {
        requireArguments().getString(ARG_APP_ID).orEmpty()
    }

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory(
            appManager = appContainer.appManager,
            downloadManager = appContainer.downloadManager,
            installManager = appContainer.installManager,
            stateCenter = appContainer.stateCenter,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle("详情页")
        observeState()
        viewModel.load(appId)

        binding.btnPrimaryAction.setOnClickListener { viewModel.onPrimaryClick() }
        binding.btnGoMyApps.setOnClickListener { navigator.openMyApps() }
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvDetailName.text = state.appDetail?.name ?: ""
                    binding.tvDetailVersion.text = "版本：${state.appDetail?.versionName.orEmpty()}"
                    binding.tvDetailDesc.text = state.appDetail?.description ?: ""
                    binding.tvState.text = "当前状态：${state.stateText}"
                    binding.btnPrimaryAction.text = when (state.buttonState) {
                        ButtonState.DOWNLOAD -> "下载"
                        ButtonState.INSTALL -> "安装"
                        ButtonState.OPEN -> "打开"
                        ButtonState.UPGRADE -> "升级"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_APP_ID = "arg_app_id"

        fun newInstance(appId: String) = DetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_APP_ID, appId)
            }
        }
    }
}
''')

# feature myapp
add('app/src/main/java/com/example/carappstore/feature/myapp/MyAppUiState.kt', '''
package com.example.carappstore.feature.myapp

import com.example.carappstore.data.model.AppViewData

data class MyAppUiState(
    val apps: List<AppViewData> = emptyList(),
)
''')

add('app/src/main/java/com/example/carappstore/feature/myapp/MyAppViewModel.kt', '''
package com.example.carappstore.feature.myapp

import androidx.lifecycle.viewModelScope
import com.example.carappstore.common.base.BaseViewModel
import com.example.carappstore.domain.appmanager.AppManager
import kotlinx.coroutines.launch

class MyAppViewModel(
    private val appManager: AppManager,
) : BaseViewModel<MyAppUiState>(MyAppUiState()) {

    fun load() {
        viewModelScope.launch {
            _uiState.value = MyAppUiState(appManager.getMyApps())
        }
    }
}
''')

add('app/src/main/java/com/example/carappstore/feature/myapp/MyAppViewModelFactory.kt', '''
package com.example.carappstore.feature.myapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.carappstore.domain.appmanager.AppManager

class MyAppViewModelFactory(
    private val appManager: AppManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MyAppViewModel(appManager) as T
    }
}
''')

add('app/src/main/java/com/example/carappstore/feature/myapp/MyAppFragment.kt', '''
package com.example.carappstore.feature.myapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carappstore.common.base.BaseFragment
import com.example.carappstore.databinding.FragmentMyAppBinding
import kotlinx.coroutines.launch

class MyAppFragment : BaseFragment() {

    private var _binding: FragmentMyAppBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyAppViewModel by viewModels {
        MyAppViewModelFactory(appContainer.appManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMyAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle("我的应用")
        observeState()
        viewModel.load()
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvInstalledApps.text = if (state.apps.isEmpty()) {
                        "暂无已安装应用"
                    } else {
                        state.apps.joinToString(separator = "\n\n") {
                            "${it.name}\n版本：${it.versionName}\n状态：${it.stateText}"
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = MyAppFragment()
    }
}
''')

for path, content in files.items():
    p = root / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')

print(f'Wrote {len(files)} files.')
