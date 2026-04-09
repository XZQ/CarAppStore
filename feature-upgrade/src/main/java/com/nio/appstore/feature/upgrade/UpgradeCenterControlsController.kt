package com.nio.appstore.feature.upgrade

import com.nio.appstore.common.ui.BaseThreeActionExtensionController
import com.nio.appstore.data.model.UpgradeCenterControlsUiState
import com.nio.appstore.feature.upgrade.databinding.ViewUpgradeCenterControlsBinding

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
    override fun bind(uiState: UpgradeCenterControlsUiState) {
        bindCommon(
            summary = uiState.summaryText,
            primaryText = uiState.primaryText,
            secondaryText = uiState.secondaryText,
            tertiaryText = uiState.tertiaryText,
        )
    }
}
