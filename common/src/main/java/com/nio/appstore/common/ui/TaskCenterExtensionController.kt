package com.nio.appstore.common.ui

interface TaskCenterExtensionController<UiState, Handlers> {
    fun bind(uiState: UiState)
    fun bindHandlers(handlers: Handlers)
}

data class ThreeActionHandlers(
    val onPrimary: () -> Unit,
    val onSecondary: () -> Unit,
    val onTertiary: () -> Unit,
)

data class FiveActionHandlers(
    val onFirst: () -> Unit,
    val onSecond: () -> Unit,
    val onThird: () -> Unit,
    val onFourth: () -> Unit,
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
