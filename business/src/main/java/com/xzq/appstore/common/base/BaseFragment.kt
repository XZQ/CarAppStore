package com.xzq.appstore.common.base

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.xzq.appstore.common.navigation.MainNavigator
import com.xzq.appstore.core.logger.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * BaseFragment 为所有 feature 页面提供最小公共能力：
 * 1. 获取全局 AppServices；
 * 2. 获取导航接口。
 *
 * 当前通过 AppContainerProvider 和 MainNavigator 两个接口解耦 app 壳层，
 * feature module 不再需要直接引用 app 中的具体容器类型。
 */
abstract class BaseFragment : Fragment() {
    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                appServices.awaitReady()
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                // 启动失败由壳层显示重试；页面保持未绑定，不能越过安装对账门控。
                AppLogger().w("BaseFragment", "Unable to initialize page services", failure)
                return@launch
            }
            viewLifecycleOwner.lifecycle.withStarted { onServicesReady(view, savedInstanceState) }
        }
    }

    protected open fun onServicesReady(view: View, savedInstanceState: Bundle?) = Unit

    /** 当前页面访问业务服务的统一入口。 */
    protected val appServices: AppServices
        get() = (requireContext().applicationContext as AppContainerProvider).appServices

    /** 当前页面使用的导航接口，由宿主 Activity 提供。 */
    protected val navigator: MainNavigator
        get() = requireActivity() as MainNavigator

    /** 当前应用是否已获未知来源安装权限。 */
    protected fun canRequestPackageInstalls(): Boolean = requireContext().packageManager.canRequestPackageInstalls()

    /** 打开当前应用的未知来源安装授权设置页。 */
    protected fun openInstallPermissionSettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${requireContext().packageName}".toUri()))
    }
}
