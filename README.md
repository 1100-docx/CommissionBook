# 约稿账本 · CommissionBook（安卓版）

记录约稿/委托的小账本 —— 画师、内容、总价与已付、进度、截止日、归档，全都存在手机本地。
原生 Android：**Kotlin + Jetpack Compose（Material 3）**，无第三方 UI 库、不联网。

> 这是 Android 版；iOS 版（SwiftUI）是另一套独立工程，不共用代码，但**备份文件两边通用**（纯 JSON）。

## 功能

| 页面 | 内容 |
|---|---|
| **账本** | 进行中合计 / 已付 / 未付；按进度筛选、排序（下单日 / 金额 / 截止日）；搜索画师、内容、备注；新增 / 编辑 / 删除 / 归档 |
| **画师** | 按画师归堆，看每人多少单、花了多少、未结多少；可记备注和联系方式 |
| **统计** | 全部金额（含已归档）、按进度分布、近 6 个月柱状图、花得最多的画师 |
| **设置** | 生物识别锁（指纹 / 面容 / 设备密码）、截止日提醒、备份导出 / 导入 / 分享 |

- **四档进度**：待报价 → 已付定金 → 绘制中 → 已交付
- **截止日提醒**：到期前 3 天、1 天各提醒一次（本地通知，不联网）
- **归档不算进合计**：归档 = 收进档案，但统计页里仍在（历史不该凭空变小）
- **数据全在本机**：不上传、不联网，只有一个 JSON 备份文件是你自己手动导出的

## 备份格式（跨平台通用）

```json
{ "commissions": [ ... ], "artistNotes": { "画师名": "备注" } }
```

iOS 版导出的能在这边恢复，反之亦然。

## 界面

- 浅色 / 深色跟随系统
- 切页是**整页滑过去**（也能手指直接左右划）
- 底部悬浮胶囊导航栏，选中的那枚指示器**跟手滑动**
- 列表、卡片、进度条、柱状图都有过渡动画，不硬切

## 自己编译

需要 JDK 17+ 和 Android SDK（`compileSdk 36`，`minSdk 26`）。

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew assembleRelease     # 产物在 app/build/outputs/apk/release/
```

> `local.properties` 里要写一行 `sdk.dir=<你的 Android SDK 路径>`（这个文件不进仓库）。

## 用到的技术点

- `HorizontalPager` —— 切页滑动 + 手势换页
- 自建底部导航栏 —— 系统的 `NavigationBar` 做不到「一枚指示器滑过去」，
  自己搭了一个（形状配色照 M3），并用 `windowInsetsPadding(WindowInsets.navigationBars)`
  让开系统导航栏（三大金刚键 / 手势条）
- `ModalBottomSheet` 编辑表单、`AnimatedVisibility` 展开收起、`animateItem()` 列表增删、
  `animateFloatAsState` 柱状图生长
- `BiometricPrompt` —— 安卓 11 起才支持「生物识别 + 设备密码」组合，低版本按版本分路
- SAF（`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`）读写备份，不写绝对路径

## 隐私

**不联网、不要账号、不收集任何信息。** 连「网络访问」权限都没申请 —— 不是承诺不上传，是系统层面它就没有联网的能力。

完整说明见 [PRIVACY.md](PRIVACY.md)。

## 说明

个人自用的小工具，代码随手写的，注释是中文。欢迎看，但没打算做成通用产品。
