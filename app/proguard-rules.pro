# 发布混淆保留规则
#
# 说明：本项目采用手动依赖注入（AppContainer），核心类均从 MainActivity 可达，
# R8 会按引用链保留它们。以下规则仅保留「框架按类名反射重建」或「跨进程/状态保存」所需的部分，
# 避免 release 包在低内存/进程重建时崩溃。

# Android 组件：进程重建时系统按类名重建 Activity/Service/Receiver/Provider
-keep public class * extends android.app.Activity { public <init>(); }
-keep public class * extends android.app.Service { public <init>(); }
-keep public class * extends android.content.BroadcastReceiver { public <init>(); }
-keep public class * extends android.content.ContentProvider { public <init>(); }

# Fragment：FragmentManager 在进程重建时通过 ClassLoader.loadClass 按类名重建 Fragment
-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }
-keep class com.xzq.appstore.common.base.** { *; }

# 自定义 View（如有自定义构造签名）
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Parcelable：跨进程与保存状态所需的 CREATOR
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 显式标注 @Keep 的类与成员
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <fields>; }

# 本地存储实体与数据模型：org.json 手动解析，字段名以字符串字面量访问，
# 这里保留类结构以防 R8 误判不可达而移除。
-keep class com.xzq.appstore.data.model.** { *; }
-keep class com.xzq.appstore.data.local.entity.** { *; }

# 保留协程/流相关内部类（常见 crash 防护）
-keepclassmembers class kotlin.Metadata { *; }
