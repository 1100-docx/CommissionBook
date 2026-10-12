# AGENTS.md —— 安卓版「约稿账本」工程说明（给 Codex 读）

> 你每次开跑会自动读这个文件。里面是这个工程用**真金白银和返工**换来的规矩。
> ⚠️ 违反其中任何一条，通常**不会在编译期报错**，而是**装到真机上才炸**。
> 最后更新：2026-10-11（逸晨风 写）

---

## 一、你是谁、边界在哪

- 你（Codex）负责**本工程 = 安卓版**。
- **iOS 版归另一个 AI（逸晨风），不要碰** `~/Developer/SwiftLearning/CommissionBook/`。
- ⛔ **推更新通道必须经逸风明确同意** —— 真实用户会收到更新，不许自己推。
- 产品决定权在逸风（16 岁，非专业开发者）。方案你可以提，别自作主张改产品行为。
- 一个工程一次只让一个 AI 改 —— 你干活时逸晨风不会进来，反之亦然。

## 二、工程事实

- 包名 `com.yifeng.commissionbook`；minSdk 26 / targetSdk 36 / compileSdk 36
- 构建环境：`JAVA_HOME=/opt/homebrew/opt/openjdk@21`、`ANDROID_HOME=$HOME/Library/Android/sdk`
- ⛔ 打正式包走 **`tools/build_release.sh`**，别绕过。它会：
  1. 编译前扫 7 个危险方法名 —— `.reversed .getFirst .getLast .addFirst .addLast .removeFirst .removeLast`
  2. 编译后**拆 dex 再扫一遍**
  **原因**：华为 nova 3（Android 9 / HUAWEI PAR-AL00 / kirin970）上就因为
  `SortedSet.reversed()` 报 `NoSuchMethodError` 崩过 —— 这是真用户报的闪退。
- 产物：`app/build/outputs/apk/release/app-release.apk`（另 `compat/` 给老机型）
- ⚠️ **签名用的是 debug 证书（`CN=Android Debug`）**，老用户装的也是这个。
  **绝对不要改签名配置** —— 换了 key 老用户就装不上新版（签名不一致）。
- 版本线：`code97`(3.9.10，通道最新) → `code98`(3.9.11，已撤) → **`code99`(3.9.10 回滚包，装在他机上)**
  → **下次真发版 versionCode 从 100 起**
- 发版：`tools/publish_android.py`；**更新通道 = GitHub 上的 `version.json`（不是上传文件）**
- 真实用户：小红书 2 / QQ 3 / 微信 1 = **6 位。推坏了有人会装。**

## 三、🚨 硬规矩

### 1. i18n（踩过多次）
- 界面文字用 `stringResource()`（**仅 `@Composable` 内**）；非界面用 `AppCtx.s(R.string.x)`
- ⛔ **枚举的构造参数里绝对不要调用 `AppCtx.s(...)`** → 一律「资源 ID + 计算属性」
- ⛔ **切语言后字符串会「钉死」**：页面第一次打开时取一次就固定了 → **每次重组必须重新取**
- 语言：`values`(简中) / `values-en` / `values-zh-rTW` / `values-zh-rHK` —— **加词条要四个都加**
- XML：`'` 要转义成 `\'`；**注释里不能有连续两个减号**
- Kotlin **块注释会嵌套**，别在 `/* */` 里再写 `/*`

### 2. 钱的方向必须跟着模式走
- 买家模式 →「付清 / 总支出」；画师模式 →「收齐 / 总稿酬」
- 用 `Terms` / `AppMode`，**不许写死**。同类旧坑：约稿记录 / 接稿记录
- 这类「站错队」的文案 bug **已经犯过至少两次**

### 3. 隐私政策
- ⛔ **条款序号写死在标题里** → 中间插一条，后面**所有**号必须顺延
- `POLICY_VERSION` 当前 = **第六版**；改正文要同步升版
- 首启顺序：隐私政策 → 必要授权 → 使用模式 → 引导。**必留「不同意（退出）」**

### 4. 数据 / 备份格式
- 备份 JSON：`{"commissions":[…], "artistNotes":{…}, "artistAvatars":{…}}`
- ⛔ **这三个键和字段语义不能动** —— 要和 iOS 版**互相认得**
- `mode` 字段（买家/画师）；**没有 `mode` 的旧数据一律归买家**

### 5. 通知隐私
- ⛔ 通知里**不许出现**画师名 / 稿件标题 / 状态 / 金额
- 文案：「您有一单约稿即将到期 / 还有 3 天到期」（前一天「明天到期」）；默认 19:00、**默认关闭**
- **不为提醒索要自启动权限**（ColorOS 会静默降级精确闹钟，别指望）

### 6. 交互一致性
- 删除 → 弹系统确认；**归档 / 放回不弹**
- 触感四档（关/轻/中/重）；水印方案 A（每单独立存 开关/文字/颜色/不透明度 四字段）
- 大图查看走 **Sheet**，不是全屏页
- ⛔ **界面出现/消失别硬切** —— 逸风原话「刷的一下…不太美观」，要有过渡
- 设置页行排布：带胶囊/按钮的行统一「图标+标题在左、胶囊靠右垂直居中，同一行」

### 7. 功能取舍
- 逸风提的 → 做；**自己觉得酷的 → 先放三个月再看**，别直接加

## 四、知识库（全库可读可写）

- 位置 `~/Documents/Knowledge/`（Obsidian 库），**完整规范见库内 `CLAUDE.md`**
- ⚠️ **三个 AI 同时在写这个库**（逸晨风 / 小逸晨风 / 你）
- ⛔ **动任何已存在的页面前，必须先 `cat` / `grep` 读一遍真实内容** ——
  别人可能刚改过，凭记忆或凭旧摘要写会**整段覆盖别人的改动**
- ⛔ 改现有页面一律用 **`apply_patch`**；**不许整份覆盖重写**；**不许删文件**（重了只合并）
- 库是 **git 仓库（纯本地，无远端）**。改完请提交，方便回退：
  ```
  git -C ~/Documents/Knowledge add -A
  git -C ~/Documents/Knowledge -c user.name=Codex -c user.email=codex@local \
      commit -m "Codex: <一句话说清改了什么>"
  ```
- ⚠️ **写权限**：知识库在工程目录**外面**，沙箱默认会拦（你会看到「只有读权限」）。
  已在 `~/.codex/config.toml` 里加了这一段，2026-10-11 实测可写：
  ```toml
  [sandbox_workspace_write]
  writable_roots = ["/Users/liorvyn/Documents/Knowledge"]
  ```
  **如果哪天又提示没写权限 → 先看这一段还在不在**（CC Switch 换 provider 时可能顺手重写 config）。
- 跟本工程最相关的页：`projects/约稿账本-画师版.md`（工程全史，很长）、`index.md`
- 干完活 → 在那一页**追加**记录（写到对应版本段或末尾，**不要改别人写的段**）

## 五、干活习惯

- 改之前先备份到 `tools/改前备份-<改动名>/`（现有先例：`改前备份-收进/`、`*.改前备份`）
- **改完自己编译验证**，别只改不编
- **干完一轮就在本工程 `git commit` 一次** —— 要的是**回退锚点**，不是好看的 commit message
- 他怕费钱：DeepSeek 余额有限，别做无关的重复探索
- 汇报要具体：**改了哪个文件、为什么、怎么验证的**
