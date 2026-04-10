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
    /** 主按钮色调。 */
    PRIMARY,
    /** 成功动作色调。 */
    SUCCESS,
    /** 警示动作色调。 */
    WARNING,
    /** 禁用动作色调。 */
    DISABLED,
}

data class TagStyle(
    /** 状态标签上展示的文案。 */
    val text: String,
    /** 状态标签使用的背景资源。 */
    @DrawableRes val backgroundRes: Int,
)

data class ActionStyle(
    /** 主动作按钮上展示的文案。 */
    val text: String,
    /** 当前动作是否可交互。 */
    val enabled: Boolean,
    /** 动作按钮使用的背景资源。 */
    @DrawableRes val backgroundRes: Int,
)

object CarUiStyle {

    /** 根据任务分组返回卡片背景资源。 */
    fun taskCardBackgroundRes(status: TaskOverallStatus): Int = when (status) {
        TaskOverallStatus.ACTIVE -> R.drawable.bg_task_card_active
        TaskOverallStatus.PENDING -> R.drawable.bg_task_card_pending
        TaskOverallStatus.FAILED -> R.drawable.bg_task_card_failed
        TaskOverallStatus.COMPLETED -> R.drawable.bg_task_card_completed
    }

    /** 根据任务分组返回分组文案。 */
    fun taskBucketText(status: TaskOverallStatus): String = when (status) {
        TaskOverallStatus.ACTIVE -> CommonUiText.TASK_ACTIVE
        TaskOverallStatus.PENDING -> CommonUiText.TASK_PENDING
        TaskOverallStatus.FAILED -> CommonUiText.TASK_FAILED_PENDING
        TaskOverallStatus.COMPLETED -> CommonUiText.TASK_COMPLETED
    }

    /** 根据任务分组返回标签色调。 */
    fun taskBucketTone(status: TaskOverallStatus): StatusTone = when (status) {
        TaskOverallStatus.ACTIVE -> StatusTone.INFO
        TaskOverallStatus.PENDING -> StatusTone.WARNING
        TaskOverallStatus.FAILED -> StatusTone.ERROR
        TaskOverallStatus.COMPLETED -> StatusTone.SUCCESS
    }

    /** 根据应用运行态解析状态标签色调。 */
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
            state.installStatus == InstallStatus.PENDING_USER_ACTION -> StatusTone.INFO
            state.installStatus == InstallStatus.INSTALLING -> StatusTone.INFO
            state.upgradeStatus == UpgradeStatus.UPGRADING -> StatusTone.INFO
            state.downloadStatus == DownloadStatus.PAUSED -> StatusTone.WARNING
            state.downloadStatus == DownloadStatus.COMPLETED -> StatusTone.WARNING
            state.upgradeStatus == UpgradeStatus.AVAILABLE -> StatusTone.WARNING
            else -> StatusTone.NEUTRAL
        }
    }

    /** 根据文本和色调生成标签样式。 */
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

    /** 根据主动作生成按钮样式。 */
    fun actionStyle(action: PrimaryAction): ActionStyle {
        val text = when (action) {
            PrimaryAction.DOWNLOAD -> CommonUiText.ACTION_DOWNLOAD
            PrimaryAction.PAUSE -> CommonUiText.ACTION_PAUSE
            PrimaryAction.RESUME -> CommonUiText.ACTION_RESUME
            PrimaryAction.INSTALL -> CommonUiText.ACTION_INSTALL
            PrimaryAction.OPEN -> CommonUiText.ACTION_OPEN
            PrimaryAction.UPGRADE -> CommonUiText.ACTION_UPGRADE
            PrimaryAction.RETRY_DOWNLOAD -> CommonUiText.ACTION_RETRY_DOWNLOAD
            PrimaryAction.RETRY_INSTALL -> CommonUiText.ACTION_RETRY_INSTALL
            PrimaryAction.DISABLED -> CommonUiText.ACTION_PROCESSING
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

/** 把状态标签样式应用到 TextView。 */
fun TextView.applyTagStyle(style: TagStyle) {
    text = style.text
    setBackgroundResource(style.backgroundRes)
}

/** 把动作按钮样式应用到 Button。 */
fun Button.applyActionStyle(style: ActionStyle) {
    text = style.text
    isEnabled = style.enabled
    setBackgroundResource(style.backgroundRes)
    alpha = if (style.enabled) 1f else 0.7f
}

/** 把动作样式应用到 TextView 形式的轻量主按钮。 */
fun TextView.applyActionStyle(style: ActionStyle) {
    text = style.text
    isEnabled = style.enabled
    isClickable = style.enabled
    setBackgroundResource(style.backgroundRes)
    alpha = if (style.enabled) 1f else 0.7f
}


/** 按任务分组为卡片应用背景样式。 */
fun View.applyTaskCardBackground(overallStatus: TaskOverallStatus) {
    setBackgroundResource(CarUiStyle.taskCardBackgroundRes(overallStatus))
}
