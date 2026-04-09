package com.nio.appstore.domain.appmanager

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.data.model.TaskCenterStats

interface AppManager {
    suspend fun getHomeApps(): List<AppViewData>
    suspend fun getAppDetail(appId: String): AppDetail
    suspend fun getMyApps(): List<AppViewData>
    suspend fun getHomeAppViewData(appId: String): AppViewData?
    suspend fun searchApps(keyword: String): List<AppViewData>
    suspend fun getDownloadManageApps(): List<AppViewData>
    suspend fun getDownloadTasks(): List<DownloadTaskViewData>
    suspend fun getUpgradeManageApps(): List<AppViewData>
    suspend fun getInstallTasks(): List<InstallTaskViewData>
    suspend fun getUpgradeTasks(): List<UpgradeTaskViewData>
    suspend fun getDownloadTaskStats(): TaskCenterStats
    suspend fun getInstallTaskStats(): TaskCenterStats
    suspend fun getUpgradeTaskStats(): TaskCenterStats
    fun getPolicyPrompt(): String
    fun openApp(packageName: String): Boolean
}
