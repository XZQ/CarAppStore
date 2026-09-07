package com.xzq.appstore.feature.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.common.ui.AppImageLoader
import com.xzq.appstore.common.ui.CarUiStyle
import com.xzq.appstore.common.ui.applyActionStyle
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.feature.search.databinding.FragmentSearchBinding
import kotlinx.coroutines.launch
import com.xzq.appstore.common.R as CommonR

class SearchFragment : BaseFragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding 已销毁" }
    private val page: CatalogPage by lazy { CatalogPage.from(arguments?.getString(ARG_PAGE)) }

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(
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
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(page.title)
        binding.tvCatalogTitle.text = page.title
        binding.etSearch.hint = page.searchHint
        binding.tvHeroTitle.text = page.heroTitle
        binding.tvSearchSubtitle.text = page.heroSubtitle
        binding.tvResultTitle.text = page.firstSectionTitle
        binding.tvHistoryTitle.visibility = View.GONE
        binding.historyChips.visibility = View.GONE
        binding.etSearch.setText(viewModel.uiState.value.keyword)
        binding.etSearch.doAfterTextChanged { text -> viewModel.search(text?.toString().orEmpty()) }
        observeState()
        viewModel.load(page)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvPolicyPrompt.text = state.policyPrompt
                    binding.tvPolicyPrompt.visibility = if (state.policyPrompt.isBlank()) View.GONE else View.VISIBLE
                    renderResults(state.apps)
                    val message = when (val screen = state.screenState) {
                        SearchScreenState.Loading -> getString(CommonR.string.loading)
                        SearchScreenState.Empty, SearchScreenState.Idle -> getString(R.string.catalog_empty)
                        is SearchScreenState.Error -> getString(R.string.catalog_error_retry, screen.message)
                        SearchScreenState.Content -> getString(R.string.catalog_result_count, state.apps.size)
                    }
                    binding.tvResultTitle.text = message
                    binding.tvResultTitle.setOnClickListener { if (state.screenState is SearchScreenState.Error) viewModel.retry() }
                    binding.listCatalogResults.visibility = if (state.screenState == SearchScreenState.Content) View.VISIBLE else View.GONE
                    if (renderedCategories != state.categories) {
                        renderedCategories = state.categories
                        renderChipRows(binding.hotSearchChips, listOf(getString(R.string.catalog_all)) + state.categories)
                    }
                    renderSuggestions(state.suggestions, state.keyword)
                }
            }
        }
    }

    /** 最近一次已渲染的结果列表；内容未变化时跳过全量重建，视图销毁后重置避免新容器漏渲染。 */
    private var renderedResults: List<AppViewData>? = null
    private var renderedCategories: List<String>? = null

    /** 最近一次已渲染的联想候选（含关键词）；内容未变化时跳过重建。 */
    private var renderedSuggestionKey: Pair<List<AppViewData>, String>? = null

    private fun renderResults(apps: List<AppViewData>) {
        if (apps == renderedResults) {
            return
        }
        renderedResults = apps
        binding.listCatalogResults.removeAllViews()
        apps.forEachIndexed { index, app ->
            binding.listCatalogResults.addView(createAppRow(app, showIndex = page == CatalogPage.Rank, index = index + 1))
        }
    }

    /** 渲染搜索联想候选下拉：仅在有关键词且候选非空时显示，点击即填充并搜索。 */
    private fun renderSuggestions(suggestions: List<AppViewData>, keyword: String) {
        val suggestionKey = suggestions to keyword
        if (suggestionKey == renderedSuggestionKey) {
            return
        }
        renderedSuggestionKey = suggestionKey
        binding.suggestionPanel.removeAllViews()
        if (keyword.isBlank() || suggestions.isEmpty()) {
            binding.suggestionPanel.visibility = View.GONE
            return
        }
        suggestions.forEach { app ->
            val row = TextView(requireContext()).apply {
                text = app.name
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                textSize = 14f
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener {
                    binding.etSearch.setText(app.name)
                    binding.etSearch.setSelection(app.name.length)
                    viewModel.search(app.name)
                }
            }
            binding.suggestionPanel.addView(row)
        }
        binding.suggestionPanel.visibility = View.VISIBLE
    }

    private fun renderChipRows(container: LinearLayout, chips: List<String>) {
        container.removeAllViews()
        chips.chunked(3).forEach { rowChips ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(8) }
            }
            rowChips.forEach { label ->
                row.addView(
                    createChip(label).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(8) }
                        setOnClickListener { viewModel.selectCategory(label.takeUnless { it == getString(R.string.catalog_all) }) }
                    },
                )
            }
            container.addView(row)
        }
    }

    private fun createChip(label: String): TextView = TextView(requireContext()).apply {
        text = label
        gravity = android.view.Gravity.CENTER
        setBackgroundResource(CommonR.drawable.bg_home_chip)
        setTextColor(resources.getColor(CommonR.color.car_text_secondary, null))
        textSize = 12f
    }

    private fun createAppRow(app: AppViewData, showIndex: Boolean, index: Int): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundResource(CommonR.drawable.bg_home_app_card)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { navigator.openDetail(app.appId) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10) }
        if (showIndex) {
            addView(
                TextView(requireContext()).apply {
                    text = getString(CommonR.string.rank_index_format, index)
                    gravity = android.view.Gravity.CENTER
                    setTextColor(resources.getColor(CommonR.color.car_accent, null))
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(48)).apply { rightMargin = dp(8) }
                },
            )
        }
        addView(createIcon(app))
        addView(
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(10)
                }
                addView(
                    TextView(requireContext()).apply {
                        text = app.name
                        setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                        textSize = 16f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        maxLines = 1
                    },
                )
                addView(
                    TextView(requireContext()).apply {
                        text = getString(CommonR.string.app_description_version_format, app.description, app.versionName)
                        setTextColor(resources.getColor(CommonR.color.car_text_secondary, null))
                        textSize = 12f
                        maxLines = 1
                    },
                )
            },
        )
        addView(
            TextView(requireContext()).apply {
                gravity = android.view.Gravity.CENTER
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                applyActionStyle(CarUiStyle.actionStyle(app.primaryAction))
                setOnClickListener { viewModel.onPrimaryClick(app) }
                layoutParams = LinearLayout.LayoutParams(dp(76), dp(36))
            },
        )
    }

    private fun createIcon(app: AppViewData): View = FrameLayout(requireContext()).apply {
        setBackgroundResource(CommonR.drawable.bg_home_app_icon)
        layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
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
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        addView(image)
        addView(fallback)
        AppImageLoader.load(image, app.iconUrl, fallback)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        renderedResults = null
        renderedSuggestionKey = null
        renderedCategories = null
        _binding = null
    }

    companion object {
        private const val ARG_PAGE = "catalog_page"

        fun newInstance(page: CatalogPage = CatalogPage.Software) = SearchFragment().apply {
            arguments = Bundle().apply { putString(ARG_PAGE, page.argument) }
        }
    }
}
