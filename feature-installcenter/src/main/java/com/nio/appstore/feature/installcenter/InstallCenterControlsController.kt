package com.nio.appstore.feature.installcenter

import com.nio.appstore.common.ui.BaseThreeActionExtensionController
import com.nio.appstore.data.model.InstallCenterControlsUiState
import com.nio.appstore.feature.installcenter.databinding.ViewInstallCenterControlsBinding

class InstallCenterControlsController(
    private val binding: ViewInstallCenterControlsBinding,
) : BaseThreeActionExtensionController<InstallCenterControlsUiState>(
    setSummary = { binding.tvInstallControlSummary.text = it },
    setPrimaryText = { binding.btnInstallPrimary.text = it },
    setSecondaryText = { binding.btnInstallSecondary.text = it },
    setTertiaryText = { binding.btnInstallTertiary.text = it },
    bindPrimaryClick = { binding.btnInstallPrimary.setOnClickListener { _ -> it() } },
    bindSecondaryClick = { binding.btnInstallSecondary.setOnClickListener { _ -> it() } },
    bindTertiaryClick = { binding.btnInstallTertiary.setOnClickListener { _ -> it() } },
) {
    override fun bind(uiState: InstallCenterControlsUiState) {
        bindCommon(
            summary = uiState.summaryText,
            primaryText = uiState.primaryText,
            secondaryText = uiState.secondaryText,
            tertiaryText = uiState.tertiaryText,
        )
    }
}
