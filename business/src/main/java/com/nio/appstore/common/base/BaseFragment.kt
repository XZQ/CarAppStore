package com.nio.appstore.common.base

import androidx.fragment.app.Fragment
import com.nio.appstore.common.navigation.MainNavigator

/**
 * BaseFragment 为所有 feature 页面提供最小公共能力：
 * 1. 获取全局 AppServices；
 * 2. 获取导航接口。
 *
 * 当前通过 AppContainerProvider 和 MainNavigator 两个接口解耦 app 壳层，
 * feature module 不再需要直接引用 app 中的具体容器类型。
 */
abstract class BaseFragment : Fragment() {
    protected val appServices: AppServices
        get() = (requireContext().applicationContext as AppContainerProvider).appServices

    protected val navigator: MainNavigator
        get() = requireActivity() as MainNavigator
}
