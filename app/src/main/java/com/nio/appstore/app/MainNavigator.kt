package com.nio.appstore.app

import com.nio.appstore.common.navigation.MainNavigator

/**
 * 兼容占位文件。
 *
 * MainNavigator 的正式定义已经迁到 common/navigation，
 * app 壳层只负责实现该接口。
 */
typealias AppMainNavigator = MainNavigator
