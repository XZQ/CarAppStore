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
import com.xzq.appstore.common.R as CommonR
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.common.ui.AppImageLoader
import com.xzq.appstore.common.ui.CarUiStyle
import com.xzq.appstore.common.ui.applyActionStyle
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.feature.home.R as HomeR
import com.xzq.appstore.feature.home.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        renderVerticalApps(findView(HomeR.id.listTodayRecommend), pickApps(apps, start = 0, count = 3), showIndex = false)
        renderVerticalApps(findView(HomeR.id.listHotRank), pickApps(apps, start = 3, count = 4), showIndex = true)
        renderHorizontalApps(findView(HomeR.id.rowHotGames), pickApps(apps, start = 1, count = 5))
        renderHorizontalApps(findView(HomeR.id.rowNewGames), pickApps(apps, start = 4, count = 5))
    }

    private fun <T : View> findView(id: Int): T = binding.root.findViewById(id)

    private fun pickApps(apps: List<AppViewData>, start: Int, count: Int): List<AppViewData> {
        if (apps.isEmpty()) return emptyList()
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
            setBackgroundResource(HomeR.drawable.bg_home_app_card)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { navigator.openDetail(app.appId) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }

        if (index != null) {
            row.addView(TextView(requireContext()).apply {
                text = index.toString()
                gravity = android.view.Gravity.CENTER
                setTextColor(resources.getColor(CommonR.color.car_accent, null))
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(48)).apply { rightMargin = dp(8) }
            })
        }

        row.addView(createIcon(app, sizeDp = 48))
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
                rightMargin = dp(10)
            }
            addView(TextView(requireContext()).apply {
                text = app.name
                setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            addView(TextView(requireContext()).apply {
                text = app.description
                setTextColor(resources.getColor(CommonR.color.car_text_secondary, null))
                textSize = 12f
                maxLines = 1
            })
        })
        row.addView(createActionButton(app, widthDp = 72, heightDp = 36))
        return row
    }

    private fun renderHorizontalApps(container: LinearLayout, apps: List<AppViewData>) {
        container.removeAllViews()
        apps.forEach { app ->
            container.addView(createMiniAppCard(app))
        }
    }

    private fun createMiniAppCard(app: AppViewData): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setBackgroundResource(HomeR.drawable.bg_home_app_card)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { navigator.openDetail(app.appId) }
            layoutParams = LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(10)
            }
            addView(createIcon(app, sizeDp = 54))
            addView(TextView(requireContext()).apply {
                text = app.name
                gravity = android.view.Gravity.CENTER
                setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) }
            })
            addView(createActionButton(app, widthDp = 72, heightDp = 32).apply {
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(32)).apply { topMargin = dp(8) }
            })
        }
    }

    private fun createIcon(app: AppViewData, sizeDp: Int): View {
        return FrameLayout(requireContext()).apply {
            setBackgroundResource(HomeR.drawable.bg_home_app_icon)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            val image = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            val fallback = TextView(requireContext()).apply {
                text = app.iconText.ifBlank { app.name.firstOrNull()?.toString().orEmpty() }
                gravity = android.view.Gravity.CENTER
                setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                textSize = if (sizeDp >= 54) 18f else 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            addView(image)
            addView(fallback)
            AppImageLoader.load(image, app.iconUrl, fallback)
        }
    }

    private fun createActionButton(app: AppViewData, widthDp: Int, heightDp: Int): TextView {
        return TextView(requireContext()).apply {
            gravity = android.view.Gravity.CENTER
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            applyActionStyle(CarUiStyle.actionStyle(app.primaryAction))
            setOnClickListener { viewModel.onPrimaryClick(app) }
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
