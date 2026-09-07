package com.xzq.appstore.feature.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.common.ui.CatalogAppAdapter
import com.xzq.appstore.common.ui.CatalogGridLayoutManager
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.feature.search.databinding.FragmentSearchBinding
import kotlinx.coroutines.launch
import com.xzq.appstore.common.R as CommonR

class SearchFragment : BaseFragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding 已销毁" }
    private val page by lazy { CatalogPage.from(arguments?.getString(ARG_PAGE)) }
    private var resultsAdapter: CatalogAppAdapter? = null
    private var renderedCategories: Pair<List<String>, String?>? = null
    private var renderedSuggestions: Pair<List<AppViewData>, String>? = null
    private var suggestionsEnabled = true
    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(appServices.appManager, appServices.stateCenter, appServices.downloadManager,
            appServices.installManager, appServices.upgradeManager, appServices.policyCenter, appServices.eventTracker)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onServicesReady(view: View, savedInstanceState: Bundle?) {
        navigator.updateTitle(page.title)
        binding.tvCatalogTitle.text = page.title
        binding.tvSearchSubtitle.text = page.heroSubtitle
        binding.etSearch.hint = page.searchHint
        resultsAdapter = CatalogAppAdapter(viewModel::onPrimaryClick, { navigator.openDetail(it.appId) }, showRank = page == CatalogPage.Rank)
        binding.listCatalogResults.apply {
            layoutManager = CatalogGridLayoutManager(context)
            adapter = resultsAdapter
            itemAnimator = null
        }
        binding.etSearch.setText(viewModel.uiState.value.keyword)
        binding.etSearch.doAfterTextChanged {
            suggestionsEnabled = true
            viewModel.search(it?.toString().orEmpty())
        }
        binding.etSearch.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) false else {
                finishInput()
                viewModel.retry()
                true
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
        viewModel.load(page)
    }

    private fun renderState(state: SearchUiState) {
        binding.tvPolicyPrompt.text = state.policyPrompt
        binding.tvPolicyPrompt.visibility = if (state.policyPrompt.isBlank()) View.GONE else View.VISIBLE
        resultsAdapter?.submitList(state.apps)
        binding.tvResultTitle.text = when (val screen = state.screenState) {
            SearchScreenState.Loading -> getString(CommonR.string.loading)
            SearchScreenState.Empty, SearchScreenState.Idle -> getString(R.string.catalog_empty)
            is SearchScreenState.Error -> getString(R.string.catalog_error_retry, screen.message)
            SearchScreenState.Content -> getString(R.string.catalog_result_count, state.apps.size)
        }
        binding.tvResultTitle.setOnClickListener(if (state.screenState is SearchScreenState.Error) View.OnClickListener { viewModel.retry() } else null)
        binding.listCatalogResults.visibility = if (state.screenState == SearchScreenState.Content) View.VISIBLE else View.INVISIBLE
        val categoryKey = state.categories to state.selectedCategory
        if (categoryKey != renderedCategories) {
            renderedCategories = categoryKey
            binding.hotSearchChips.removeAllViews()
            (listOf<String?>(null) + state.categories).forEach { category ->
                val chip = TextView(requireContext()).apply {
                    text = category ?: getString(R.string.catalog_all)
                    gravity = android.view.Gravity.CENTER
                    minWidth = dp(64)
                    setPadding(dp(12), 0, dp(12), 0)
                    isSelected = category == state.selectedCategory
                    setBackgroundResource(if (isSelected) CommonR.drawable.bg_primary_button else CommonR.drawable.bg_home_chip)
                    setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { marginEnd = dp(8) }
                    setOnClickListener { viewModel.selectCategory(category) }
                }
                binding.hotSearchChips.addView(chip)
            }
        }
        renderSuggestions(state.suggestions, state.keyword)
    }

    private fun renderSuggestions(apps: List<AppViewData>, keyword: String) {
        binding.suggestionScroll.visibility = if (suggestionsEnabled && keyword.isNotBlank() && apps.isNotEmpty()) View.VISIBLE else View.GONE
        if (renderedSuggestions == apps to keyword) return
        renderedSuggestions = apps to keyword
        binding.suggestionPanel.removeAllViews()
        apps.forEach { app ->
            binding.suggestionPanel.addView(TextView(requireContext()).apply {
                text = app.name
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(resources.getColor(CommonR.color.car_text_primary, null))
                setPadding(dp(12), 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
                setOnClickListener { binding.etSearch.setText(app.name); finishInput() }
            })
        }
    }

    private fun finishInput() {
        suggestionsEnabled = false
        binding.suggestionScroll.visibility = View.GONE
        binding.etSearch.clearFocus()
        requireContext().getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        binding.listCatalogResults.adapter = null
        resultsAdapter = null
        renderedCategories = null
        renderedSuggestions = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PAGE = "catalog_page"
        fun newInstance(page: CatalogPage = CatalogPage.Software) = SearchFragment().apply {
            arguments = Bundle().apply { putString(ARG_PAGE, page.argument) }
        }
    }
}
