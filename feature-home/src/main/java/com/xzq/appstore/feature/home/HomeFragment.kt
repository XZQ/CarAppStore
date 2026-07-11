package com.xzq.appstore.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.common.ui.AppImageLoader
import com.xzq.appstore.common.ui.CarUiStyle
import com.xzq.appstore.common.ui.applyActionStyle
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.feature.home.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import com.xzq.appstore.common.R as CommonR
import com.xzq.appstore.feature.home.R as HomeR

class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding 已销毁" }

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            appServices.appManager,
            appServices.stateCenter,
            appServices.downloadManager,
            appServices.installManager,
            appServices.upgradeManager,
            appServices.policyCenter,
            appServices.eventTracker,
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(CommonR.string.screen_home_title))
        bindStaticClicks()
        observeState()
        viewModel.load()
    }

    private fun bindStaticClicks() {
        binding.tvHomeSearch.setOnClickListener { navigator.openSearch() }
        binding.entryCategory.setOnClickListener { navigator.openSearch() }
        binding.entryRank.setOnClickListener { navigator.openSearch() }
        binding.entryEssential.setOnClickListener { navigator.openSearch() }
        binding.entryFactory.setOnClickListener { navigator.openMyApps() }
        listOf(
            findView<TextView>(HomeR.id.tvTodayMore),
            findView<TextView>(HomeR.id.tvRankMore),
            findView<TextView>(HomeR.id.tvGamesMore),
            findView<TextView>(HomeR.id.tvReservationMore),
            binding.tvActivityMore,
        ).forEach { more -> more.setOnClickListener { navigator.openSearch() } }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvHomeSubtitle.text = when (val screenState = state.screenState) {
                        HomeScreenState.Loading -> getString(CommonR.string.loading)
                        HomeScreenState.Content -> getString(CommonR.string.screen_home_recommend_count, state.apps.size)
                        HomeScreenState.Empty -> getString(CommonR.string.screen_home_empty_apps)
                        is HomeScreenState.Error -> screenState.message.ifBlank {
                            getString(CommonR.string.screen_home_error_hint)
                        }
                    }
                    binding.tvPolicyPrompt.text = state.policyPrompt
                    binding.tvPolicyPrompt.visibility = if (state.policyPrompt.isBlank()) View.GONE else View.VISIBLE
                    renderHomeSections(state.apps)
                }
            }
        }
    }

    private fun renderHomeSections(apps: List<AppViewData>) {
        renderVerticalApps(findView(HomeR.id.listTodayRecommend), pickApps(apps, start = INDEX_TODAY_START, count = COUNT_TODAY), showIndex = false)
        renderVerticalApps(findView(HomeR.id.listHotRank), pickApps(apps, start = INDEX_RANK_START, count = COUNT_RANK), showIndex = true)
        renderHorizontalApps(findView(HomeR.id.rowHotGames), pickApps(apps, start = INDEX_GAMES_START, count = COUNT_GAMES))
        renderHorizontalApps(findView(HomeR.id.rowNewGames), pickApps(apps, start = INDEX_NEW_GAMES_START, count = COUNT_GAMES))
    }

    // Fragment 同时支持 mobile(sw600) 和 desktop(sw900dp-land) 两套 layout；
    // mobile 用 include 复用 4 个 section，desktop 直接展开。统一通过 binding.root.findViewById
    // 访问 4 个 section 内的容器是处理双布局差异的最小侵入方式。
    private fun <T : View> findView(id: Int): T = binding.root.findViewById(id)

    private fun pickApps(apps: List<AppViewData>, start: Int, count: Int): List<AppViewData> {
        if (apps.isEmpty()) {
            return emptyList()
        }
        return (0 until count).map { offset -> apps[(start + offset) % apps.size] }
    }

    private fun renderVerticalApps(container: LinearLayout, apps: List<AppViewData>, showIndex: Boolean) {
        container.removeAllViews()
        apps.forEachIndexed { index, app ->
            container.addView(createListRow(app, if (showIndex) index + 1 else null))
        }
    }

    private fun createListRow(app: AppViewData, index: Int?): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(CommonR.drawable.bg_home_app_card)
            setPadding(dp(PADDING_LIST_HORIZONTAL), dp(PADDING_LIST_VERTICAL), dp(PADDING_LIST_HORIZONTAL), dp(PADDING_LIST_VERTICAL))
            setOnClickListener { navigator.openDetail(app.appId) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(MARGIN_LIST_BOTTOM) }
        }
        if (index != null) row.addView(createRankIndex(index))
        row.addView(createIcon(app, sizeDp = ICON_LIST_SIZE))
        row.addView(createListRowText(app))
        row.addView(createActionButton(app, widthDp = ACTION_WIDTH, heightDp = ACTION_HEIGHT_LIST))
        return row
    }

    private fun createRankIndex(index: Int): TextView = TextView(requireContext()).apply {
        text = index.toString()
        gravity = android.view.Gravity.CENTER
        setTextColor(resources.getColor(CommonR.color.car_accent, null))
        textSize = TEXT_SIZE_RANK_INDEX
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(dp(RANK_INDEX_WIDTH), dp(RANK_INDEX_HEIGHT)).apply { rightMargin = dp(MARGIN_RIGHT_INDEX) }
    }

    private fun createListRowText(app: AppViewData): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, WEIGHT_TEXT).apply {
            leftMargin = dp(MARGIN_LIST_LEFT)
            rightMargin = dp(MARGIN_LIST_RIGHT)
        }
        addView(
            TextView(requireContext()).apply {
                text = app.name
                setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                textSize = TEXT_SIZE_LIST_TITLE
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
            },
        )
        addView(
            TextView(requireContext()).apply {
                text = app.description
                setTextColor(resources.getColor(CommonR.color.car_text_secondary, null))
                textSize = TEXT_SIZE_LIST_DESC
                maxLines = 1
            },
        )
    }

    private fun renderHorizontalApps(container: LinearLayout, apps: List<AppViewData>) {
        container.removeAllViews()
        apps.forEach { app -> container.addView(createMiniAppCard(app)) }
    }

    private fun createMiniAppCard(app: AppViewData): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        setBackgroundResource(CommonR.drawable.bg_home_app_card)
        setPadding(dp(PADDING_MINI), dp(PADDING_MINI), dp(PADDING_MINI), dp(PADDING_MINI))
        setOnClickListener { navigator.openDetail(app.appId) }
        layoutParams = LinearLayout.LayoutParams(dp(MINI_CARD_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(PADDING_MINI)
        }
        addView(createIcon(app, sizeDp = ICON_MINI_SIZE))
        addView(createMiniAppTitle(app))
        addView(
            createActionButton(app, widthDp = ACTION_WIDTH, heightDp = ACTION_HEIGHT_MINI).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ACTION_WIDTH), dp(ACTION_HEIGHT_MINI)).apply { topMargin = dp(MARGIN_TOP_TEXT) }
            },
        )
    }

    private fun createMiniAppTitle(app: AppViewData): TextView = TextView(requireContext()).apply {
        text = app.name
        gravity = android.view.Gravity.CENTER
        setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
        textSize = TEXT_SIZE_MINI_TITLE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(MARGIN_TOP_TEXT) }
    }

    private fun createIcon(app: AppViewData, sizeDp: Int): View = FrameLayout(requireContext()).apply {
        setBackgroundResource(CommonR.drawable.bg_home_app_icon)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        val image = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val fallback = TextView(requireContext()).apply {
            text = app.iconText.ifBlank {
                app.name.firstOrNull()?.toString().orEmpty()
            }
            gravity = android.view.Gravity.CENTER
            setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
            textSize = if (sizeDp >= ICON_MINI_SIZE) TEXT_SIZE_LARGE_ICON else TEXT_SIZE_SMALL_ICON
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        addView(image)
        addView(fallback)
        AppImageLoader.load(image, app.iconUrl, fallback)
    }

    private fun createActionButton(app: AppViewData, widthDp: Int, heightDp: Int): TextView = TextView(requireContext()).apply {
        gravity = android.view.Gravity.CENTER
        textSize = TEXT_SIZE_ACTION
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        applyActionStyle(CarUiStyle.actionStyle(app.primaryAction))
        setOnClickListener { viewModel.onPrimaryClick(app) }
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()

        // 首页推荐位编排：4 个 section 各自的起始下标和数量。
        private const val INDEX_TODAY_START = 0
        private const val INDEX_RANK_START = 3
        private const val INDEX_GAMES_START = 1
        private const val INDEX_NEW_GAMES_START = 4
        private const val COUNT_TODAY = 3
        private const val COUNT_RANK = 4
        private const val COUNT_GAMES = 5

        // 列表行尺寸（dp 单位，运行时按 density 换算）。
        private const val PADDING_LIST_HORIZONTAL = 12
        private const val PADDING_LIST_VERTICAL = 10
        private const val MARGIN_LIST_BOTTOM = 10
        private const val MARGIN_LIST_LEFT = 12
        private const val MARGIN_LIST_RIGHT = 10
        private const val RANK_INDEX_WIDTH = 28
        private const val RANK_INDEX_HEIGHT = 48
        private const val MARGIN_RIGHT_INDEX = 8
        private const val ICON_LIST_SIZE = 48

        // 迷你卡片尺寸。
        private const val PADDING_MINI = 10
        private const val MINI_CARD_WIDTH = 104
        private const val ICON_MINI_SIZE = 54
        private const val MARGIN_TOP_TEXT = 8

        // 主动作按钮尺寸。车机场景下触控目标高度需 ≥ 48dp，避免颠簸中误触。
        private const val ACTION_WIDTH = 72
        private const val ACTION_HEIGHT_LIST = 48
        private const val ACTION_HEIGHT_MINI = 48

        // 文字字号（sp）。
        private const val TEXT_SIZE_LIST_TITLE = 16f
        private const val TEXT_SIZE_LIST_DESC = 12f
        private const val TEXT_SIZE_MINI_TITLE = 12f
        private const val TEXT_SIZE_RANK_INDEX = 16f
        private const val TEXT_SIZE_LARGE_ICON = 18f
        private const val TEXT_SIZE_SMALL_ICON = 16f
        private const val TEXT_SIZE_ACTION = 12f

        // LinearLayout 子 view 权重。
        private const val WEIGHT_TEXT = 1f
    }
}
