# 2026-09-30 建。
#
# 为什么突然开 R8：微信发文件有大小限制 —— 11MB 的 apk / 10MB 的 zip **发不出去**
# （网关日志里是「CDN upload HTTP 500」），456 字节的小包能发。
# 所以把没用的代码和资源剔掉，让包小下来（主要是 material-icons-extended 那几千个图标）。
#
# 这个 App 没有反射、没有注解处理、没有序列化框架 —— 理论上不需要额外保什么。
# 下面两条纯粹是保险。

# 保留注解等元信息（Compose / Kotlin 有些地方会看）
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# androidx.biometric 在部分机型上会反射取 Fragment 里的回调 —— 锁屏那个功能不能出岔子
-keep class androidx.biometric.** { *; }
