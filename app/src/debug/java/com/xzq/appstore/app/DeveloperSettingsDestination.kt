package com.xzq.appstore.app

import androidx.fragment.app.Fragment
import com.xzq.appstore.feature.debug.DeveloperSettingsFragment

/** 开发页面仅参与 Debug 装配。 */
internal object DeveloperSettingsDestination {
    fun create(): Fragment? = DeveloperSettingsFragment.newInstance()
}
