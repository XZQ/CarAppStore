# Android 开发最佳实践规范（Codex Agent Instructions）

> 本文件为 Codex Agent 的行为约束文件。生成任何 Android 代码时，必须严格遵守以下所有规范。
> 项目背景、模块说明与当前落地约束请结合 `README.md` 一起阅读；若两者出现冲突，以本文件的代码生成规范为准。

---

## 一、项目架构规范

### 1.1 架构模式
- 强制使用 **MVVM** 架构（Model - ViewModel - View）
- 禁止在 Activity / Fragment 中写业务逻辑，所有业务逻辑放入 ViewModel
- 数据层使用 **Repository 模式**，ViewModel 只与 Repository 交互，不直接访问数据库或网络
- 单向数据流：View → Event → ViewModel → State → View

### 1.2 包结构规范
```
com.example.app/
├── data/
│   ├── local/          # Room数据库、DAO、Entity
│   ├── remote/         # Retrofit接口、Response数据类
│   ├── repository/     # Repository实现类
│   └── model/          # 数据模型
├── domain/
│   ├── model/          # 业务模型（与数据模型分离）
│   ├── repository/     # Repository接口
│   └── usecase/        # 用例（可选，复杂业务时使用）
├── ui/
│   ├── base/           # BaseActivity、BaseFragment、BaseViewModel
│   ├── feature_name/   # 按功能模块划分，每个模块含Activity/Fragment/ViewModel/Adapter
│   └── common/         # 公共UI组件
├── di/                 # Hilt依赖注入模块
├── util/               # 工具类、扩展函数
└── MyApplication.kt
```

---

## 二、文件命名规范

### 2.1 Kotlin 文件命名
| 类型 | 命名规则 | 示例 |
|------|----------|------|
| Activity | `XxxActivity` | `LoginActivity` |
| Fragment | `XxxFragment` | `HomeFragment` |
| Dialog | `XxxDialog` 或 `XxxDialogFragment` | `ConfirmDialog` |
| BottomSheet | `XxxBottomSheet` | `ShareBottomSheet` |
| ViewModel | `XxxViewModel` | `LoginViewModel` |
| Repository | `XxxRepository` | `UserRepository` |
| Adapter | `XxxAdapter` | `ArticleListAdapter` |
| ViewHolder | `XxxViewHolder` | `ArticleViewHolder` |
| UseCase | `XxxUseCase` | `GetUserInfoUseCase` |
| Entity（Room） | `XxxEntity` | `UserEntity` |
| Response | `XxxResponse` | `LoginResponse` |
| Request | `XxxRequest` | `LoginRequest` |
| 扩展函数文件 | `XxxExtensions.kt` | `ViewExtensions.kt` |
| 工具类 | `XxxUtils.kt` | `DateUtils.kt` |

### 2.2 XML 布局文件命名
| 对应类型 | 命名前缀 | 示例 |
|----------|----------|------|
| Activity | `activity_` | `activity_login.xml` |
| Fragment | `fragment_` | `fragment_home.xml` |
| Dialog / DialogFragment | `dialog_` | `dialog_confirm.xml` |
| BottomSheet | `bottom_sheet_` | `bottom_sheet_share.xml` |
| RecyclerView Item | `item_` | `item_article.xml` |
| 自定义 View | `view_` | `view_rating_bar.xml` |
| 通用 include 布局 | `layout_` | `layout_empty_state.xml` |
| PopupWindow | `popup_` | `popup_menu.xml` |

### 2.3 资源文件命名
| 资源类型 | 命名规则 | 示例 |
|----------|----------|------|
| drawable（图片） | `ic_`（图标）、`bg_`（背景）、`img_`（插图） | `ic_arrow_right.xml` |
| color | 语义化命名 | `color_primary`、`color_text_secondary` |
| string | 模块_描述 | `login_btn_submit`、`home_title` |
| dimen | 用途_尺寸 | `margin_default`、`text_size_title` |
| style | 组件类型.描述 | `Widget.App.Button.Primary` |
| anim | 动作_方向 | `slide_in_right`、`fade_out` |

---

## 三、XML 布局规范

### 3.1 根布局强制要求
- **所有布局文件的根节点必须使用 `androidx.constraintlayout.widget.ConstraintLayout`**
- 禁止将 `LinearLayout`、`RelativeLayout`、`FrameLayout` 作为根节点
- 嵌套层级不得超过 **5 层**，超过时拆分为自定义 View 或使用 `<include>`

```xml
<!-- ✅ 正确 -->
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

</androidx.constraintlayout.widget.ConstraintLayout>

<!-- ❌ 禁止 -->
<LinearLayout ...>
<RelativeLayout ...>
```

### 3.2 View ID 命名规范
- 格式：`组件类型缩写_语义描述`（camelCase）
- 必须使用 ViewBinding，禁止 `findViewById`，禁止 Kotlin synthetics

```xml
<!-- 命名示例 -->
android:id="@+id/tvTitle"          <!-- TextView -->
android:id="@+id/etEmail"          <!-- EditText -->
android:id="@+id/btnSubmit"        <!-- Button -->
android:id="@+id/ivAvatar"         <!-- ImageView -->
android:id="@+id/rvArticleList"    <!-- RecyclerView -->
android:id="@+id/clContainer"      <!-- ConstraintLayout -->
android:id="@+id/pbLoading"        <!-- ProgressBar -->
android:id="@+id/cbRememberMe"     <!-- CheckBox -->
android:id="@+id/switchNotify"     <!-- Switch -->
```

### 3.3 尺寸与颜色规范
- 禁止在 XML 中硬编码 dp/sp 数值，必须引用 `@dimen/`
- 禁止在 XML 中硬编码颜色值（如 `#FF0000`），必须引用 `@color/`
- 禁止在 XML 中硬编码字符串，必须引用 `@string/`

```xml
<!-- ✅ 正确 -->
android:textSize="@dimen/text_size_body"
android:textColor="@color/color_text_primary"
android:text="@string/login_btn_submit"
android:margin="@dimen/margin_default"

<!-- ❌ 禁止 -->
android:textSize="16sp"
android:textColor="#333333"
android:text="提交"
android:margin="16dp"
```

---

## 四、Kotlin 代码规范

### 4.1 禁止魔法数字与魔法字符串
- 代码中不允许出现任何未命名的数字字面量（`0` 和 `1` 作为循环边界除外）
- 不允许出现未命名的字符串字面量（空字符串 `""` 除外）
- 所有常量必须定义在 `companion object` 或顶级 `const val` 中

```kotlin
// ✅ 正确
companion object {
    /** 请求超时时间（秒） */
    private const val TIMEOUT_SECONDS = 30L

    /** 列表每页加载数量 */
    private const val PAGE_SIZE = 20

    /** 用户头像最大尺寸（px） */
    private const val AVATAR_MAX_SIZE = 512

    /** 跳转详情页携带的文章ID键名 */
    const val EXTRA_ARTICLE_ID = "extra_article_id"
}

// ❌ 禁止
val timeout = 30
val size = 20
startActivity(intent.putExtra("extra_article_id", id))
```

### 4.2 变量与属性注释规范
- **所有成员变量必须添加注释**，说明用途（不是类型说明，是业务含义）
- 局部变量若含义不明显，也必须添加行内注释
- ViewModel 中的 LiveData / StateFlow 必须注释说明数据含义和触发时机

```kotlin
class LoginViewModel : ViewModel() {

    /** 登录页面UI状态，包含加载中、成功、错误三种状态 */
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    /** 用户输入的邮箱，实时同步自EditText */
    private val _email = MutableStateFlow("")

    /** 用户输入的密码，实时同步自EditText */
    private val _password = MutableStateFlow("")

    /** 表单是否可提交：邮箱和密码均不为空时为true */
    val isSubmitEnabled: StateFlow<Boolean> = combine(_email, _password) { email, pwd ->
        email.isNotBlank() && pwd.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}
```

### 4.3 函数规范
- 函数长度不超过 **40 行**，超过则拆分
- 函数只做一件事（单一职责）
- 所有 `public` 函数必须添加 KDoc 注释
- 参数超过 3 个时，使用数据类封装

```kotlin
/**
 * 提交登录请求
 *
 * @param email 用户邮箱
 * @param password 用户密码（明文，内部加密处理）
 */
fun submitLogin(email: String, password: String) {
    // ...
}

// 参数过多时，封装为数据类
data class LoginParams(
    /** 用户邮箱 */
    val email: String,
    /** 用户密码（明文） */
    val password: String,
    /** 是否记住登录状态 */
    val rememberMe: Boolean
)
```

### 4.4 类规范
- 禁止使用 `!!` 强制非空断言，必须用 `?.let`、`?: return`、`requireNotNull` 等安全方式处理
- 禁止在主线程执行网络请求、数据库查询、文件IO等耗时操作
- 所有协程必须在 `viewModelScope` 或 `lifecycleScope` 中启动，禁止裸用 `GlobalScope`
- 捕获异常时不得使用空 catch 块，至少记录日志

```kotlin
// ✅ 正确
val user = viewModel.user ?: return
binding.tvName.text = user.name

// ❌ 禁止
val user = viewModel.user!!
```

---

## 五、Activity / Fragment 规范

### 5.1 BaseActivity 模板
每个 Activity 必须继承 BaseActivity，使用 ViewBinding：

```kotlin
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    /** 当前页面的ViewBinding实例 */
    private var _binding: VB? = null
    protected val binding get() = requireNotNull(_binding) { "Binding is null" }

    /** 子类提供ViewBinding的inflate方法 */
    abstract fun inflateBinding(inflater: LayoutInflater): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        initView()
        initData()
        observeViewModel()
    }

    /** 初始化View，设置点击事件等 */
    protected open fun initView() {}

    /** 初始化数据，触发首次加载 */
    protected open fun initData() {}

    /** 订阅ViewModel的数据变化 */
    protected open fun observeViewModel() {}

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
```

### 5.2 Activity 跳转规范
- 禁止在外部直接 `new Intent(context, XxxActivity::class.java)`
- 所有跳转参数封装在目标 Activity 的 `companion object` 的 `start()` 静态方法中

```kotlin
class ArticleDetailActivity : BaseActivity<ActivityArticleDetailBinding>() {

    companion object {
        /** 文章ID的Intent键名 */
        private const val EXTRA_ARTICLE_ID = "extra_article_id"

        /** 文章标题的Intent键名（用于过渡动画前预显示） */
        private const val EXTRA_ARTICLE_TITLE = "extra_article_title"

        /**
         * 启动文章详情页
         *
         * @param context 上下文
         * @param articleId 文章唯一ID
         * @param articleTitle 文章标题
         */
        fun start(context: Context, articleId: Long, articleTitle: String) {
            val intent = Intent(context, ArticleDetailActivity::class.java).apply {
                putExtra(EXTRA_ARTICLE_ID, articleId)
                putExtra(EXTRA_ARTICLE_TITLE, articleTitle)
            }
            context.startActivity(intent)
        }
    }
}
```

### 5.3 Fragment 规范
- 禁止直接 `new XxxFragment(params)` 传参，必须通过 `newInstance()` + `arguments` 传参
- Fragment 通信禁止直接持有另一个 Fragment 的引用，通过共享 ViewModel 通信

```kotlin
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    companion object {
        /** 首页类型：推荐 */
        const val TYPE_RECOMMEND = 1

        /** 首页类型：最新 */
        const val TYPE_LATEST = 2

        /** Bundle中首页类型的键名 */
        private const val ARG_TYPE = "arg_type"

        /**
         * 创建首页Fragment实例
         *
         * @param type 首页类型，见 TYPE_RECOMMEND / TYPE_LATEST
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

---

## 六、ViewModel 规范

### 6.1 UI State 建模
- 每个页面的状态用 **sealed class** 建模，禁止用多个 Boolean 标志位表示状态

```kotlin
/** 登录页面UI状态 */
sealed class LoginUiState {
    /** 初始状态，无操作 */
    object Idle : LoginUiState()

    /** 登录请求中，展示Loading */
    object Loading : LoginUiState()

    /** 登录成功，携带用户信息 */
    data class Success(val user: User) : LoginUiState()

    /** 登录失败，携带错误信息 */
    data class Error(val message: String) : LoginUiState()
}
```

### 6.2 网络请求封装
- 统一用 Result / 自定义 Resource 封装网络结果，不在 ViewModel 裸 try-catch

```kotlin
/** 网络请求结果封装 */
sealed class Resource<out T> {
    /** 请求中 */
    object Loading : Resource<Nothing>()

    /** 请求成功 */
    data class Success<T>(val data: T) : Resource<T>()

    /** 请求失败 */
    data class Error(val message: String, val code: Int = -1) : Resource<Nothing>()
}
```

---

## 七、RecyclerView / Adapter 规范

### 7.1 强制使用 ListAdapter + DiffUtil
- 禁止使用普通 `RecyclerView.Adapter` + `notifyDataSetChanged()`
- 必须使用 `ListAdapter<T, VH>` 配合 `DiffUtil.ItemCallback`

```kotlin
class ArticleAdapter : ListAdapter<Article, ArticleAdapter.ViewHolder>(DiffCallback()) {

    /** 列表项点击事件回调 */
    var onItemClick: ((Article) -> Unit)? = null

    inner class ViewHolder(
        /** 当前列表项的ViewBinding */
        private val binding: ItemArticleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_ID.toInt()) {
                    onItemClick?.invoke(getItem(position))
                }
            }
        }

        fun bind(article: Article) {
            binding.tvTitle.text = article.title
            binding.tvDate.text = article.publishDate
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** 文章列表项差异比较回调 */
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

## 九、日志规范

- 禁止使用系统 `Log` 直接打印，必须使用统一封装的 `AppLogger`
- Release 包禁止输出任何日志
- 禁止打印用户密码、Token 等敏感信息

```kotlin
object AppLogger {
    /** 是否启用日志，仅Debug模式开启 */
    private val isEnabled = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (isEnabled) Log.d(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) Log.e(tag, message, throwable)
    }
}
```

---

## 十、禁止事项速查表

| 禁止行为 | 正确做法 |
|----------|----------|
| `findViewById` | ViewBinding |
| Kotlin synthetics（`import kotlinx.android.synthetic`） | ViewBinding |
| `!!` 强制非空 | `?.let`、`?: return`、`requireNotNull` |
| 魔法数字（如 `delay(3000)`） | `const val DELAY_MS = 3000L` |
| 硬编码字符串在代码中 | `strings.xml` + `R.string.xxx` |
| 硬编码颜色/尺寸在XML中 | `colors.xml` / `dimens.xml` |
| `GlobalScope.launch` | `viewModelScope.launch` / `lifecycleScope.launch` |
| `notifyDataSetChanged()` | `ListAdapter` + `DiffUtil` |
| Activity 构造函数传参 | `companion object start()` 方法 |
| Fragment 构造函数传参 | `newInstance()` + `arguments` |
| 空 catch 块 | 至少 `AppLogger.e(...)` 记录异常 |
| 非根布局以外的全屏 ConstraintLayout 替代 include | 用 `<include>` 拆分复用 |
| 在 Activity/Fragment 写业务逻辑 | 放入 ViewModel |
| 直接在 ViewModel 访问数据库/网络 | 通过 Repository |
| 主线程执行耗时操作 | `Dispatchers.IO` 协程 |

---

*最后更新：2026年 · 适用技术栈：Kotlin + MVVM + Hilt + Coroutines + ViewBinding + Room + Retrofit*
