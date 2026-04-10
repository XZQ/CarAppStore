package com.nio.appstore.common.ui

interface TaskCenterExtensionController<UiState, Handlers> {
    /** 根据扩展区状态绑定界面内容。 */
    fun bind(uiState: UiState)
    /** 绑定扩展区的交互回调。 */
    fun bindHandlers(handlers: Handlers)
}

data class ThreeActionHandlers(
    /** 主动作按钮的点击处理。 */
    val onPrimary: () -> Unit,
    /** 次动作按钮的点击处理。 */
    val onSecondary: () -> Unit,
    /** 第三动作按钮的点击处理。 */
    val onTertiary: () -> Unit,
)

data class FiveActionHandlers(
    /** 第一动作按钮的点击处理。 */
    val onFirst: () -> Unit,
    /** 第二动作按钮的点击处理。 */
    val onSecond: () -> Unit,
    /** 第三动作按钮的点击处理。 */
    val onThird: () -> Unit,
    /** 第四动作按钮的点击处理。 */
    val onFourth: () -> Unit,
    /** 第五动作按钮的点击处理。 */
    val onFifth: () -> Unit,
)

abstract class BaseThreeActionExtensionController<UiState>(
    /** 设置扩展区摘要文案。 */
    private val setSummary: (String) -> Unit,
    /** 设置第一按钮文案。 */
    private val setPrimaryText: (String) -> Unit,
    /** 设置第二按钮文案。 */
    private val setSecondaryText: (String) -> Unit,
    /** 设置第三按钮文案。 */
    private val setTertiaryText: (String) -> Unit,
    /** 绑定第一按钮点击事件。 */
    private val bindPrimaryClick: ((() -> Unit) -> Unit),
    /** 绑定第二按钮点击事件。 */
    private val bindSecondaryClick: ((() -> Unit) -> Unit),
    /** 绑定第三按钮点击事件。 */
    private val bindTertiaryClick: ((() -> Unit) -> Unit),
) : TaskCenterExtensionController<UiState, ThreeActionHandlers> {

    /** 绑定三动作扩展区的公共文案。 */
    protected fun bindCommon(
        summary: String,
        primaryText: String,
        secondaryText: String,
        tertiaryText: String,
    ) {
        setSummary(summary)
        setPrimaryText(primaryText)
        setSecondaryText(secondaryText)
        setTertiaryText(tertiaryText)
    }

    /** 绑定三动作扩展区的点击处理。 */
    override fun bindHandlers(handlers: ThreeActionHandlers) {
        bindPrimaryClick(handlers.onPrimary)
        bindSecondaryClick(handlers.onSecondary)
        bindTertiaryClick(handlers.onTertiary)
    }
}

abstract class BaseFiveActionExtensionController<UiState>(
    /** 设置扩展区摘要文案。 */
    private val setSummary: (String) -> Unit,
    /** 设置第一按钮文案。 */
    private val setFirstText: (String) -> Unit,
    /** 设置第二按钮文案。 */
    private val setSecondText: (String) -> Unit,
    /** 设置第三按钮文案。 */
    private val setThirdText: (String) -> Unit,
    /** 设置第四按钮文案。 */
    private val setFourthText: (String) -> Unit,
    /** 设置第五按钮文案。 */
    private val setFifthText: (String) -> Unit,
    /** 绑定第一按钮点击事件。 */
    private val bindFirstClick: ((() -> Unit) -> Unit),
    /** 绑定第二按钮点击事件。 */
    private val bindSecondClick: ((() -> Unit) -> Unit),
    /** 绑定第三按钮点击事件。 */
    private val bindThirdClick: ((() -> Unit) -> Unit),
    /** 绑定第四按钮点击事件。 */
    private val bindFourthClick: ((() -> Unit) -> Unit),
    /** 绑定第五按钮点击事件。 */
    private val bindFifthClick: ((() -> Unit) -> Unit),
) : TaskCenterExtensionController<UiState, FiveActionHandlers> {

    /** 绑定五动作扩展区的公共文案。 */
    protected fun bindCommon(
        summary: String,
        firstText: String,
        secondText: String,
        thirdText: String,
        fourthText: String,
        fifthText: String,
    ) {
        setSummary(summary)
        setFirstText(firstText)
        setSecondText(secondText)
        setThirdText(thirdText)
        setFourthText(fourthText)
        setFifthText(fifthText)
    }

    /** 绑定五动作扩展区的点击处理。 */
    override fun bindHandlers(handlers: FiveActionHandlers) {
        bindFirstClick(handlers.onFirst)
        bindSecondClick(handlers.onSecond)
        bindThirdClick(handlers.onThird)
        bindFourthClick(handlers.onFourth)
        bindFifthClick(handlers.onFifth)
    }
}
