package com.nio.appstore.feature.downloadmanager

import android.view.View
import com.nio.appstore.common.ui.BaseFiveActionExtensionController
import com.nio.appstore.data.model.DownloadCenterPreferencesUiState
import com.nio.appstore.feature.downloadmanager.databinding.ViewDownloadCenterPreferencesBinding

class DownloadCenterPreferencesController(
    private val binding: ViewDownloadCenterPreferencesBinding,
) : BaseFiveActionExtensionController<DownloadCenterPreferencesUiState>(
    setSummary = { binding.tvPreferenceSummary.apply { visibility = View.VISIBLE; text = it } },
    setFirstText = { binding.btnAutoResume.text = it },
    setSecondText = { binding.btnAutoRetry.text = it },
    setThirdText = { binding.btnPolicyWifi.text = it },
    setFourthText = { binding.btnPolicyParking.text = it },
    setFifthText = { binding.btnPolicyStorage.text = it },
    bindFirstClick = { binding.btnAutoResume.setOnClickListener { _ -> it() } },
    bindSecondClick = { binding.btnAutoRetry.setOnClickListener { _ -> it() } },
    bindThirdClick = { binding.btnPolicyWifi.setOnClickListener { _ -> it() } },
    bindFourthClick = { binding.btnPolicyParking.setOnClickListener { _ -> it() } },
    bindFifthClick = { binding.btnPolicyStorage.setOnClickListener { _ -> it() } },
) {

    override fun bind(uiState: DownloadCenterPreferencesUiState) {
        bindCommon(
            summary = uiState.summaryText,
            firstText = uiState.autoResumeText,
            secondText = uiState.autoRetryText,
            thirdText = uiState.wifiText,
            fourthText = uiState.parkingText,
            fifthText = uiState.storageText,
        )
    }
}
