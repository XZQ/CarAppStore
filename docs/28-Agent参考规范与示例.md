# 28. Agent 参考规范与示例

## 1. 文档目的

这份文档用于承接 `AGENTS.md` 中不适合每次都注入上下文的详细示例、模板和解释性说明。  
原则很简单：

- `AGENTS.md` 只保留硬规则
- 本文档提供按需查阅的参考内容
- 若本文档与 `AGENTS.md` 冲突，以 `AGENTS.md` 为准

## 2. 适合什么时候看

以下情况适合查看本文档：

- 需要回忆某类文件的命名方式
- 需要参考标准的 `Activity` / `Fragment` 传参写法
- 需要参考 `ViewModel` 状态建模方式
- 需要参考 `ListAdapter`、`Resource`、`AppLogger` 这类模板
- 需要补充注释风格和代码组织示例

## 3. 命名参考

### 3.1 Kotlin 文件命名

| 类型 | 命名示例 |
|------|----------|
| Activity | `LoginActivity` |
| Fragment | `HomeFragment` |
| Dialog | `ConfirmDialog` / `ConfirmDialogFragment` |
| BottomSheet | `ShareBottomSheet` |
| ViewModel | `LoginViewModel` |
| Repository | `UserRepository` |
| Adapter | `ArticleAdapter` |
| ViewHolder | `ArticleViewHolder` |
| UseCase | `GetUserInfoUseCase` |
| Entity | `UserEntity` |
| Response | `LoginResponse` |
| Request | `LoginRequest` |
| 扩展函数文件 | `ViewExtensions.kt` |
| 工具类 | `DateUtils.kt` |

### 3.2 XML 布局命名

| 场景 | 命名前缀 | 示例 |
|------|----------|------|
| Activity | `activity_` | `activity_login.xml` |
| Fragment | `fragment_` | `fragment_home.xml` |
| Dialog | `dialog_` | `dialog_confirm.xml` |
| BottomSheet | `bottom_sheet_` | `bottom_sheet_share.xml` |
| RecyclerView Item | `item_` | `item_article.xml` |
| 自定义 View | `view_` | `view_rating_bar.xml` |
| include 布局 | `layout_` | `layout_empty_state.xml` |
| Popup | `popup_` | `popup_menu.xml` |

### 3.3 资源命名参考

| 资源类型 | 命名建议 |
|----------|----------|
| drawable | `ic_` / `bg_` / `img_` |
| color | 使用业务语义名，如 `color_primary` |
| string | 使用 `模块_描述` |
| dimen | 使用 `用途_尺寸` |
| style | 使用 `组件类型.描述` |
| anim | 使用 `动作_方向` |

## 4. XML 参考示例

### 4.1 根布局与属性引用

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/home_title"
        android:textColor="@color/color_text_primary"
        android:textSize="@dimen/text_size_title"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        tools:text="应用商店" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 4.2 View ID 示例

```xml
android:id="@+id/tvTitle"
android:id="@+id/etKeyword"
android:id="@+id/btnSubmit"
android:id="@+id/ivAvatar"
android:id="@+id/rvArticleList"
android:id="@+id/pbLoading"
```

## 5. Kotlin 参考示例

### 5.1 常量提取

```kotlin
companion object {
    /** 请求超时时间（秒） */
    private const val TIMEOUT_SECONDS = 30L

    /** 列表每页加载数量 */
    private const val PAGE_SIZE = 20

    /** 详情页应用ID参数键名 */
    private const val EXTRA_APP_ID = "extra_app_id"
}
```

### 5.2 成员变量注释

```kotlin
class LoginViewModel : ViewModel() {

    /** 登录页面UI状态，触发登录、成功或失败时更新 */
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    /** 用户输入的邮箱内容，实时同步输入框 */
    private val _email = MutableStateFlow("")

    /** 用户输入的密码内容，实时同步输入框 */
    private val _password = MutableStateFlow("")
}
```

### 5.3 方法注释

```kotlin
/**
 * 提交登录请求
 *
 * @param email 用户邮箱
 * @param password 用户密码明文，内部负责加密与校验
 */
fun submitLogin(email: String, password: String) {
    // 先校验入参，避免无效请求继续向下执行
    // 再切换加载态，通知页面展示 loading
    // 最后调用 repository 发起请求并回写结果
}
```

## 6. 页面模板参考

### 6.1 BaseActivity 写法

```kotlin
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    /** 当前页面的 ViewBinding 实例 */
    private var _binding: VB? = null
    protected val binding: VB
        get() = requireNotNull(_binding) { "Binding is null" }

    /** 子类提供对应布局的 inflate 方法 */
    abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        initView()
        initData()
        observeViewModel()
    }

    /** 初始化 View 与事件 */
    protected open fun initView() = Unit

    /** 初始化数据与首次加载 */
    protected open fun initData() = Unit

    /** 订阅 ViewModel 输出 */
    protected open fun observeViewModel() = Unit

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
```

### 6.2 Activity 启动方式

```kotlin
class ArticleDetailActivity : BaseActivity<ActivityArticleDetailBinding>() {

    companion object {
        /** 应用ID参数键名 */
        private const val EXTRA_APP_ID = "extra_app_id"

        /**
         * 启动详情页
         *
         * @param context 上下文
         * @param appId 应用唯一ID
         */
        fun start(context: Context, appId: Long) {
            val intent = Intent(context, ArticleDetailActivity::class.java).apply {
                putExtra(EXTRA_APP_ID, appId)
            }
            context.startActivity(intent)
        }
    }
}
```

### 6.3 Fragment 传参方式

```kotlin
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    companion object {
        /** Bundle 中首页类型参数键名 */
        private const val ARG_TYPE = "arg_type"

        /**
         * 创建首页 Fragment
         *
         * @param type 首页类型
         */
        fun newInstance(type: Int): HomeFragment {
            return HomeFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TYPE, type)
                }
            }
        }
    }
}
```

## 7. 状态建模参考

### 7.1 页面状态

```kotlin
/** 登录页面 UI 状态 */
sealed class LoginUiState {
    /** 初始状态，无操作 */
    object Idle : LoginUiState()

    /** 登录中，页面应展示加载态 */
    object Loading : LoginUiState()

    /** 登录成功，携带用户信息 */
    data class Success(val user: User) : LoginUiState()

    /** 登录失败，携带错误原因 */
    data class Error(val message: String) : LoginUiState()
}
```

### 7.2 通用结果封装

```kotlin
/** 通用异步结果封装 */
sealed class Resource<out T> {
    /** 请求中 */
    object Loading : Resource<Nothing>()

    /** 请求成功 */
    data class Success<T>(val data: T) : Resource<T>()

    /** 请求失败 */
    data class Error(val message: String, val code: Int = -1) : Resource<Nothing>()
}
```

## 8. 列表组件参考

```kotlin
class ArticleAdapter : ListAdapter<Article, ArticleAdapter.ArticleViewHolder>(DiffCallback()) {

    /** 列表项点击回调 */
    var onItemClick: ((Article) -> Unit)? = null

    inner class ArticleViewHolder(
        /** 当前列表项 binding */
        private val binding: ItemArticleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /** 绑定单项数据 */
        fun bind(article: Article) {
            binding.tvTitle.text = article.title
            binding.tvDate.text = article.publishDate
            binding.root.setOnClickListener {
                onItemClick?.invoke(article)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem == newItem
        }
    }
}
```

## 9. 日志参考

```kotlin
object AppLogger {

    /** 是否启用日志，仅 Debug 构建打开 */
    private val isEnabled = BuildConfig.DEBUG

    /** 输出调试日志 */
    fun d(tag: String, message: String) {
        if (isEnabled) Log.d(tag, message)
    }

    /** 输出错误日志 */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) Log.e(tag, message, throwable)
    }
}
```

## 10. 使用建议

- 想知道“必须遵守什么”，先看 `AGENTS.md`
- 想知道“具体怎么写”，再看本文档
- 想知道“当前项目做到哪了”，看 `docs/25-当前项目状态与接手指南.md`
- 想知道“下一步优先做什么”，看 `docs/08-开发顺序与落地清单.md`

## 11. 一句话总结

`AGENTS.md` 负责约束，本文档负责示例；一个要短，一个可以详细。
