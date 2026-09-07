package com.xzq.appstore.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.common.navigation.CatalogSection
import com.xzq.appstore.data.model.AppViewData
import com.xzq.appstore.data.model.CatalogQuery
import com.xzq.appstore.domain.appmanager.AppCatalogFilter
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

    override fun onServicesReady(view: View, savedInstanceState: Bundle?) {
        super.onServicesReady(view, savedInstanceState)
        navigator.updateTitle(getString(CommonR.string.screen_home_title))
        bindStaticClicks()
        setupLists()
        observeState()
        viewModel.load()
    }

    private fun bindStaticClicks() {
        binding.tvHomeSearch.setOnClickListener { navigator.openCatalog(CatalogSection.Category) }
        binding.entryCategory.setOnClickListener { navigator.openCatalog(CatalogSection.Category) }
        binding.entryRank.setOnClickListener { navigator.openCatalog(CatalogSection.Rank) }
        binding.entryEssential.setOnClickListener { navigator.openCatalog(CatalogSection.Essential) }
        binding.entryFactory.setText(CommonR.string.ui_activity)
        binding.entryFactory.setOnClickListener { navigator.openCatalog(CatalogSection.Activity) }
        findView<TextView>(HomeR.id.tvTodayMore).setOnClickListener { navigator.openCatalog(CatalogSection.Category) }
        findView<TextView>(HomeR.id.tvRankMore).setOnClickListener { navigator.openCatalog(CatalogSection.Rank) }
        findView<TextView>(HomeR.id.tvGamesMore).setOnClickListener { navigator.openCatalog(CatalogSection.Game) }
        findView<TextView>(HomeR.id.tvReservationMore).setOnClickListener { navigator.openCatalog(CatalogSection.Software) }
        binding.carouselPanel.setOnClickListener { navigator.openCatalog(CatalogSection.Category) }
        // 目录尚无促销权益数据，不展示无法兑现的入口。
        binding.limitedActivitySection.visibility = View.GONE
        binding.tvHomeNotice.visibility = View.GONE
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

    /** 最近一次已渲染的应用列表；内容未变化时跳过全量重建，视图销毁后重置避免新容器漏渲染。 */
    private var renderedApps: List<AppViewData>? = null

    private val adapters = mutableMapOf<Int, HomeAdapter>()

    private fun setupLists() {
        listOf(HomeR.id.listTodayRecommend, HomeR.id.listHotRank, HomeR.id.rowHotGames, HomeR.id.rowNewGames).forEach { id ->
            val horizontal = id == HomeR.id.rowHotGames || id == HomeR.id.rowNewGames
            val adapter = HomeAdapter(viewModel::onPrimaryClick, { navigator.openDetail(it.appId) }, showRank = id == HomeR.id.listHotRank, compact = horizontal)
            adapters[id] = adapter
            findView<RecyclerView>(id).apply {
                layoutManager = LinearLayoutManager(context, if (horizontal) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false)
                this.adapter = adapter
                isNestedScrollingEnabled = horizontal
                itemAnimator = null
            }
        }
    }

    private fun renderHomeSections(apps: List<AppViewData>) {
        if (apps == renderedApps) return
        renderedApps = apps
        adapters[HomeR.id.listTodayRecommend]?.submitList(apps.distinctBy { it.appId }.take(3))
        adapters[HomeR.id.listHotRank]?.submitList(AppCatalogFilter.select(apps, CatalogQuery(section = CatalogSection.Rank)).take(4))
        adapters[HomeR.id.rowHotGames]?.submitList(AppCatalogFilter.select(apps, CatalogQuery(section = CatalogSection.Game)).take(5))
        adapters[HomeR.id.rowNewGames]?.submitList(AppCatalogFilter.select(apps, CatalogQuery(section = CatalogSection.Software)).take(5))
    }

    private fun <T : View> findView(id: Int): T = binding.root.findViewById(id)

    override fun onDestroyView() {
        adapters.keys.forEach { findView<RecyclerView>(it).adapter = null }
        adapters.clear()
        renderedApps = null
        super.onDestroyView()
        _binding = null
    }

    companion object { fun newInstance() = HomeFragment() }
}
