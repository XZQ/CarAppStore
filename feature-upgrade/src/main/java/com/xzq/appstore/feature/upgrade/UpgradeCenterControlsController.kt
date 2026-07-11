package com.xzq.appstore.feature.upgrade

import com.xzq.appstore.common.ui.BaseThreeActionExtensionController
import com.xzq.appstore.data.model.UpgradeCenterControlsUiState
import com.xzq.appstore.feature.upgrade.databinding.ViewUpgradeCenterControlsBinding

class UpgradeCenterControlsController(
    private val binding: ViewUpgradeCenterControlsBinding,
) : BaseThreeActionExtensionController<UpgradeCenterControlsUiState>(
    setSummary = { binding.tvUpgradeControlSummary.text = it },
    setPrimaryText = { binding.btnUpgradePrimary.text = it },
    setSecondaryText = { binding.btnUpgradeSecondary.text = it },
    setTertiaryText = { binding.btnUpgradeTertiary.text = it },
    bindPrimaryClick = { binding.btnUpgradePrimary.setOnClickListener { _ -> it() } },
    bindSecondaryClick = { binding.btnUpgradeSecondary.setOnClickListener { _ -> it() } },
    bindTertiaryClick = { binding.btnUpgradeTertiary.setOnClickListener { _ -> it() } },
) {
    /** 把升级中心扩展控制状态绑定到控件。 */
    override fun bind(uiState: UpgradeCenterControlsUiState) {
        bindCommon(summary = uiState.summaryText, primaryText = uiState.primaryText, secondaryText = uiState.secondaryText, tertiaryText = uiState.tertiaryText)
    }
}
