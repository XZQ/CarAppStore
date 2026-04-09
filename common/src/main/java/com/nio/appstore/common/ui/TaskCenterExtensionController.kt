package com.nio.appstore.common.ui

interface TaskCenterExtensionController<UiState, Handlers> {
    fun bind(uiState: UiState)
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
    private val setSummary: (String) -> Unit,
    private val setPrimaryText: (String) -> Unit,
    private val setSecondaryText: (String) -> Unit,
    private val setTertiaryText: (String) -> Unit,
    private val bindPrimaryClick: ((() -> Unit) -> Unit),
    private val bindSecondaryClick: ((() -> Unit) -> Unit),
    private val bindTertiaryClick: ((() -> Unit) -> Unit),
) : TaskCenterExtensionController<UiState, ThreeActionHandlers> {

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

    override fun bindHandlers(handlers: ThreeActionHandlers) {
        bindPrimaryClick(handlers.onPrimary)
        bindSecondaryClick(handlers.onSecondary)
        bindTertiaryClick(handlers.onTertiary)
    }
}

abstract class BaseFiveActionExtensionController<UiState>(
    private val setSummary: (String) -> Unit,
    private val setFirstText: (String) -> Unit,
    private val setSecondText: (String) -> Unit,
    private val setThirdText: (String) -> Unit,
    private val setFourthText: (String) -> Unit,
    private val setFifthText: (String) -> Unit,
    private val bindFirstClick: ((() -> Unit) -> Unit),
    private val bindSecondClick: ((() -> Unit) -> Unit),
    private val bindThirdClick: ((() -> Unit) -> Unit),
    private val bindFourthClick: ((() -> Unit) -> Unit),
    private val bindFifthClick: ((() -> Unit) -> Unit),
) : TaskCenterExtensionController<UiState, FiveActionHandlers> {

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

    override fun bindHandlers(handlers: FiveActionHandlers) {
        bindFirstClick(handlers.onFirst)
        bindSecondClick(handlers.onSecond)
        bindThirdClick(handlers.onThird)
        bindFourthClick(handlers.onFourth)
        bindFifthClick(handlers.onFifth)
    }
}
