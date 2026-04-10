package com.nio.appstore.domain.appmanager

import com.nio.appstore.data.model.AppDetail
import com.nio.appstore.data.model.AppViewData
import com.nio.appstore.data.model.DownloadTaskViewData
import com.nio.appstore.data.model.InstallTaskViewData
import com.nio.appstore.data.model.UpgradeTaskViewData
import com.nio.appstore.data.model.TaskCenterStats

interface AppManager {
    /** 获取首页应用卡片列表。 */
    suspend fun getHomeApps(): List<AppViewData>

    /** 获取指定应用的详情数据。 */
    suspend fun getAppDetail(appId: String): AppDetail

    /** 获取“我的应用”页面需要展示的应用列表。 */
    suspend fun getMyApps(): List<AppViewData>

    /** 获取首页中单个应用对应的聚合视图数据。 */
    suspend fun getHomeAppViewData(appId: String): AppViewData?

    /** 根据关键词搜索应用。 */
    suspend fun searchApps(keyword: String): List<AppViewData>

    /** 获取下载管理页顶部应用卡片需要使用的数据。 */
    suspend fun getDownloadManageApps(): List<AppViewData>

    /** 获取下载任务中心任务列表。 */
    suspend fun getDownloadTasks(): List<DownloadTaskViewData>

    /** 获取升级管理页顶部应用卡片需要使用的数据。 */
    suspend fun getUpgradeManageApps(): List<AppViewData>

    /** 获取安装中心任务列表。 */
    suspend fun getInstallTasks(): List<InstallTaskViewData>

    /** 获取升级中心任务列表。 */
    suspend fun getUpgradeTasks(): List<UpgradeTaskViewData>

    /** 获取下载中心统计信息。 */
    suspend fun getDownloadTaskStats(): TaskCenterStats

    /** 获取安装中心统计信息。 */
    suspend fun getInstallTaskStats(): TaskCenterStats

    /** 获取升级中心统计信息。 */
    suspend fun getUpgradeTaskStats(): TaskCenterStats

    /** 生成当前策略提示文案。 */
    fun getPolicyPrompt(): String

    /** 尝试打开指定包名的应用。 */
    fun openApp(packageName: String): Boolean
}
