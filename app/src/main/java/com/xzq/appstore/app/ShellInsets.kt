package com.xzq.appstore.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach

/** 壳层统一处理系统栏、刘海和键盘；基于初始 padding，重复分发不会累加。 */
internal fun View.applyShellInsets(onImeVisibility: (Boolean) -> Unit = {}) {
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
        view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
        onImeVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()))
        WindowInsetsCompat.CONSUMED
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}
