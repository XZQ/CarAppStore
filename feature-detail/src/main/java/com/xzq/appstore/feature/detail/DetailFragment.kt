package com.xzq.appstore.feature.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xzq.appstore.common.R
import com.xzq.appstore.common.base.BaseFragment
import com.xzq.appstore.common.ui.AppImageLoader
import com.xzq.appstore.common.ui.CarUiStyle
import com.xzq.appstore.common.ui.applyActionStyle
import com.xzq.appstore.common.ui.applyTagStyle
import com.xzq.appstore.feature.detail.databinding.FragmentDetailBinding
import kotlinx.coroutines.launch

class DetailFragment : BaseFragment() {
    private var _binding: FragmentDetailBinding? = null

    private val binding get() = requireNotNull(_binding) { "Binding 已销毁" }

    /** 当前详情页对应的应用标识。 */
    private val appId: String by lazy {
        requireArguments().getString(ARG_APP_ID).orEmpty()
    }

    /** 详情页 ViewModel。 */
    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory(
            appManager = appServices.appManager,
            downloadManager = appServices.downloadManager,
            installManager = appServices.installManager,
            upgradeManager = appServices.upgradeManager,
            stateCenter = appServices.stateCenter,
            policyCenter = appServices.policyCenter,
            eventTracker = appServices.eventTracker,
        )
    }

    /** 创建详情页视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化详情页标题、状态订阅和按钮事件。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigator.updateTitle(getString(R.string.screen_detail_title))
        observeState()
        viewModel.load(appId)

        binding.btnPrimaryAction.setOnClickListener { viewModel.onPrimaryClick() }
        binding.btnGoMyApps.setOnClickListener { navigator.openMyApps() }
        binding.btnBackHome.setOnClickListener { navigator.openHome() }
        binding.includeDetailAppInfo.policyIntercept.btnInterceptAction.setOnClickListener {
            appServices.eventTracker.track("policy_intercept_open_settings")
            navigator.openDeveloperSettings()
        }
    }

    /** 订阅详情页 UI 状态，并刷新详情信息与主动作。 */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val appInfoBinding = binding.includeDetailAppInfo
                    val taskStatusBinding = binding.includeDetailTaskStatus
                    appInfoBinding.tvDetailName.text = state.appDetail?.name ?: getString(R.string.screen_detail_empty_name)
                    appInfoBinding.tvDetailHero.text = buildHeroText(state)
                    AppImageLoader.load(appInfoBinding.ivDetailHero, state.appDetail?.bannerUrl.orEmpty(), appInfoBinding.tvDetailHero)
                    appInfoBinding.tvDetailHero.visibility = if (state.screenState == DetailScreenState.Content) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    appInfoBinding.tvDetailInitial.text = state.appDetail?.iconText?.ifBlank {
                        state.appDetail?.name.firstDisplayChar()
                    }.orEmpty()
                    AppImageLoader.load(appInfoBinding.ivDetailIcon, state.appDetail?.iconUrl.orEmpty(), appInfoBinding.tvDetailInitial)
                    appInfoBinding.tvDetailVersion.text = buildVersionText(state)
                    appInfoBinding.tvDetailDesc.text = buildDescriptionText(state)
                    bindScreenshots(state)
                    appInfoBinding.tvDetailMeta.text = buildMetaText(state)
                    appInfoBinding.tvDetailMeta.visibility = if (state.screenState == DetailScreenState.Content) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    taskStatusBinding.tvState.applyTagStyle(CarUiStyle.tagStyle(state.stateText, state.statusTone))
                    // 进度和主动作始终跟随状态中心与业务编排结果刷新。
                    taskStatusBinding.progressDownload.progress = state.progress
                    taskStatusBinding.tvProgress.text = if (state.progress > 0) {
                        getString(R.string.screen_detail_progress_format, state.progress)
                    } else {
                        getString(R.string.screen_detail_no_progress)
                    }
                    appInfoBinding.tvPolicyPrompt.text = state.policyPrompt
                    appInfoBinding.tvPolicyPrompt.visibility = if (state.policyPrompt.isBlank()) View.GONE else View.VISIBLE
                    val interceptBinding = appInfoBinding.policyIntercept
                    interceptBinding.tvInterceptReason.text = state.interceptReason
                    interceptBinding.root.visibility = if (state.interceptReason.isBlank()) View.GONE else View.VISIBLE
                    binding.btnPrimaryAction.applyActionStyle(CarUiStyle.actionStyle(state.primaryAction))
                }
            }
        }
    }

    /** 组装版本、副标题与状态文案。 */
    private fun buildVersionText(state: DetailUiState): String = when (val screenState = state.screenState) {
        DetailScreenState.Loading -> getString(R.string.loading)
        is DetailScreenState.Error -> screenState.message.ifBlank {
            getString(R.string.screen_detail_error_hint)
        }

        DetailScreenState.Content -> {
            val detail = state.appDetail
            getString(R.string.screen_detail_version_meta_format, detail?.versionName.orEmpty(), detail?.sizeText.orEmpty(), detail?.lastUpdatedText.orEmpty())
        }
    }

    /** 组装详情页描述。 */
    private fun buildDescriptionText(state: DetailUiState): String = when (state.screenState) {
        DetailScreenState.Loading -> getString(R.string.screen_detail_loading_hint)
        is DetailScreenState.Error -> getString(R.string.screen_detail_error_hint)
        DetailScreenState.Content -> state.appDetail?.description.orEmpty()
    }

    private fun buildHeroText(state: DetailUiState): String {
        val detail = state.appDetail ?: return ""
        return detail.heroText.ifBlank { detail.description }
    }

    private fun bindScreenshots(state: DetailUiState) {
        val screenshots = state.appDetail?.screenshotUrls.orEmpty().take(3)
        val infoBinding = binding.includeDetailAppInfo
        val isContent = state.screenState == DetailScreenState.Content
        infoBinding.layoutScreenshots.visibility = if (isContent && screenshots.isNotEmpty()) View.VISIBLE else View.GONE
        val imageViews = listOf(infoBinding.ivScreenshot1, infoBinding.ivScreenshot2, infoBinding.ivScreenshot3)
        imageViews.forEachIndexed { index, imageView ->
            val source = screenshots.getOrNull(index).orEmpty()
            imageView.visibility = if (source.isBlank()) View.GONE else View.VISIBLE
            AppImageLoader.load(imageView, source)
        }
    }

    /** 组装详情页信任信息。 */
    private fun buildMetaText(state: DetailUiState): String {
        val detail = state.appDetail ?: return ""
        return listOf(
            getString(R.string.screen_detail_developer_format, detail.developerName),
            getString(R.string.screen_detail_category_format, detail.category),
            getString(R.string.screen_detail_rating_format, detail.ratingText),
            getString(R.string.screen_detail_compatibility_format, detail.compatibilitySummary),
            getString(R.string.screen_detail_permissions_format, detail.permissionsSummary),
            getString(R.string.screen_detail_update_summary_format, detail.updateSummary),
        ).filterNot { it.substringAfter('：').isBlank() }.joinToString("\n")
    }

    /** 提取应用名称首个可见字符作为轻量图标。 */
    private fun String?.firstDisplayChar(): String = this?.trim()?.firstOrNull()?.toString().orEmpty()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Bundle 中应用标识的键名。 */
        private const val ARG_APP_ID = "arg_app_id"

        /** 创建指定应用的详情页实例。 */
        fun newInstance(appId: String) = DetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_APP_ID, appId)
            }
        }
    }
}
