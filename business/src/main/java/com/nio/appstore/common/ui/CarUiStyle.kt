package com.nio.appstore.common.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.nio.appstore.common.R

import com.nio.appstore.data.model.TaskOverallStatus
import com.nio.appstore.domain.state.AppState
import com.nio.appstore.domain.state.DownloadStatus
import com.nio.appstore.domain.state.InstallStatus
import com.nio.appstore.domain.state.PrimaryAction
import com.nio.appstore.domain.state.UpgradeStatus

enum class ActionTone {
    PRIMARY,
    SUCCESS,
    WARNING,
    DISABLED,
}

data class TagStyle(
    val text: String,
    @DrawableRes val backgroundRes: Int,
)

data class ActionStyle(
    val text: String,
    val enabled: Boolean,
    @DrawableRes val backgroundRes: Int,
)

object CarUiStyle {

    fun taskCardBackgroundRes(status: TaskOverallStatus): Int = when (status) {
        TaskOverallStatus.ACTIVE -> R.drawable.bg_task_card_active
        TaskOverallStatus.PENDING -> R.drawable.bg_task_card_pending
        TaskOverallStatus.FAILED -> R.drawable.bg_task_card_failed
        TaskOverallStatus.COMPLETED -> R.drawable.bg_task_card_completed
    }

    fun taskBucketText(status: TaskOverallStatus): String = when (status) {
        TaskOverallStatus.ACTIVE -> "执行中"
        TaskOverallStatus.PENDING -> "待处理"
        TaskOverallStatus.FAILED -> "失败待处理"
        TaskOverallStatus.COMPLETED -> "已完成"
    }

    fun taskBucketTone(status: TaskOverallStatus): StatusTone = when (status) {
        TaskOverallStatus.ACTIVE -> StatusTone.INFO
        TaskOverallStatus.PENDING -> StatusTone.WARNING
        TaskOverallStatus.FAILED -> StatusTone.ERROR
        TaskOverallStatus.COMPLETED -> StatusTone.SUCCESS
    }

    fun resolveStatusTone(state: AppState): StatusTone {
        return when {
            state.errorMessage != null -> StatusTone.ERROR
            state.installStatus == InstallStatus.FAILED -> StatusTone.ERROR
            state.downloadStatus == DownloadStatus.FAILED -> StatusTone.ERROR
            state.upgradeStatus == UpgradeStatus.FAILED -> StatusTone.ERROR
            state.installStatus == InstallStatus.INSTALLED -> StatusTone.SUCCESS
            state.upgradeStatus == UpgradeStatus.SUCCESS -> StatusTone.SUCCESS
            state.downloadStatus == DownloadStatus.RUNNING -> StatusTone.INFO
            state.downloadStatus == DownloadStatus.WAITING -> StatusTone.INFO
            state.installStatus == InstallStatus.WAITING -> StatusTone.INFO
            state.installStatus == InstallStatus.INSTALLING -> StatusTone.INFO
            state.upgradeStatus == UpgradeStatus.UPGRADING -> StatusTone.INFO
            state.downloadStatus == DownloadStatus.PAUSED -> StatusTone.WARNING
            state.downloadStatus == DownloadStatus.COMPLETED -> StatusTone.WARNING
            state.upgradeStatus == UpgradeStatus.AVAILABLE -> StatusTone.WARNING
            else -> StatusTone.NEUTRAL
        }
    }

    fun tagStyle(text: String, tone: StatusTone): TagStyle {
        return TagStyle(
            text = text,
            backgroundRes = when (tone) {
                StatusTone.NEUTRAL -> R.drawable.bg_tag_neutral
                StatusTone.INFO -> R.drawable.bg_tag_info
                StatusTone.SUCCESS -> R.drawable.bg_tag_success
                StatusTone.WARNING -> R.drawable.bg_tag_warning
                StatusTone.ERROR -> R.drawable.bg_tag_error
            },
        )
    }

    fun actionStyle(action: PrimaryAction): ActionStyle {
        val text = when (action) {
            PrimaryAction.DOWNLOAD -> "下载"
            PrimaryAction.PAUSE -> "暂停"
            PrimaryAction.RESUME -> "继续"
            PrimaryAction.INSTALL -> "安装"
            PrimaryAction.OPEN -> "打开"
            PrimaryAction.UPGRADE -> "升级"
            PrimaryAction.RETRY_DOWNLOAD -> "重试下载"
            PrimaryAction.RETRY_INSTALL -> "重试安装"
            PrimaryAction.DISABLED -> "处理中"
        }
        val tone = when (action) {
            PrimaryAction.DOWNLOAD,
            PrimaryAction.RESUME,
            PrimaryAction.INSTALL,
            PrimaryAction.UPGRADE -> ActionTone.PRIMARY
            PrimaryAction.OPEN -> ActionTone.SUCCESS
            PrimaryAction.PAUSE,
            PrimaryAction.RETRY_DOWNLOAD,
            PrimaryAction.RETRY_INSTALL -> ActionTone.WARNING
            PrimaryAction.DISABLED -> ActionTone.DISABLED
        }
        return ActionStyle(
            text = text,
            enabled = action != PrimaryAction.DISABLED,
            backgroundRes = when (tone) {
                ActionTone.PRIMARY -> R.drawable.bg_primary_button
                ActionTone.SUCCESS -> R.drawable.bg_primary_button_success
                ActionTone.WARNING -> R.drawable.bg_primary_button_warning
                ActionTone.DISABLED -> R.drawable.bg_primary_button_disabled
            },
        )
    }
}

fun TextView.applyTagStyle(style: TagStyle) {
    text = style.text
    setBackgroundResource(style.backgroundRes)
}

fun Button.applyActionStyle(style: ActionStyle) {
    text = style.text
    isEnabled = style.enabled
    setBackgroundResource(style.backgroundRes)
    alpha = if (style.enabled) 1f else 0.7f
}


fun View.applyTaskCardBackground(overallStatus: TaskOverallStatus) {
    setBackgroundResource(CarUiStyle.taskCardBackgroundRes(overallStatus))
}
