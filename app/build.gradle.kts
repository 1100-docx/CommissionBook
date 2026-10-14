plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yifeng.commissionbook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yifeng.commissionbook"
        minSdk = 26
        targetSdk = 36
        // 2026-10-08：二维码传输（局域网扫码，见 QrTransfer.kt / QrScanActivity.kt）。
        // 逸风定的「这算一次大版本」——它第一次给 App 带来**相机权限**，
        // 隐私政策也跟着升到第六版，所以是大版本。
        // 3.9.1：当天第二版 —— 出码 Sheet 改**弹全屏**、底下那行去掉红字。
        // 3.9.3：当天第四版 —— 真凶抓到：安卓 targetSdk 36 **默认禁止明文 HTTP**，
        //        而拉备份用的是 HttpURLConnection → 请求还没出手机就被系统拦掉，
        //        还伪装成「不在同一个 Wi-Fi」（iOS 扫安卓通、反方向不通就是这个原因）。
        //        改成裸 TCP socket 手拼 GET，不碰明文策略；失败原因也一并带到弹窗里。
        // 3.9.2：当天第三版 —— 逸风实测「五分钟倒计时内码也在一直变，这不应该」，
        //        把「口令每 10 秒轮换」撤掉：整场 5 分钟就一张码，不变，到点才作废。
        // 3.9.3：当天第四版 —— 修「安卓扫 iOS 报不在同一个 Wi-Fi」的真凶：
        //        安卓 9 起 targetSdk≥28 默认禁明文 http，HttpURLConnection 直接被系统拦。
        //        拉数据改走**裸 TCP socket 手拼 GET**（明文策略管不到裸 socket），
        //        顺带没给 App 开任何明文口子；失败时把原始报错小字附在弹窗里。
        // 3.9.4：当天第五版 —— 修 3.9.3 带出来的回归：拉数据原来写在主线程，
        //        主线程被占住 → 取景页退不回来，用户看到的是「对着码一直没反应」。
        //        挪到独立线程 + 中间加「正在传输…」。
        // 3.9.5：当天第五版（**这一版推通道**）。四件事一起：
        //        ① 连接超时 4s → 8s；超时弹窗补上「网络禁止设备互连（AP 隔离）/ 建议开热点」这条出路；
        //        ② 扫码页埋面包屑（崩溃记录里能看到「二维码 · 扫码页」）；
        //        ③ 两句**已经不实**的旧文案改准（崩溃记录页那句「不联网、不要权限」+
        //           网络那条补上「二维码传输只在同一个 Wi-Fi 内通信」）；
        //        ④ 出码页去掉红字、Sheet 全屏、码不再每 10 秒轮换。
        // 3.9.6：当天第六版 —— **纯为绕开镜像缓存而升的号**。
        //        起因：3.9.5 推上去之后，四个源里 ghproxy.net 那份 version.json 还是旧的
        //        （size 2441952 ≠ 本次的 2442584），但 versionCode 同样是 92 ——
        //        App「取版本号最大」时可能挑中这份旧清单，拿旧 sha 去校新包会装不上，
        //        就算装上也是没带 AP 隔离提示的那一版，而版本号一样、以后不再提示更新。
        //        升到 93 之后，新鲜的那些源版本号更大，一定赢。**行为与 3.9.5 完全一致。**
        // 3.9.7：政策正文补一条「二维码传输」（跟 iOS 那边对齐）—— **纯文案，行为一行没动**。
        // 3.9.12（2026-10-11）：**回滚版** —— 逸风「算了，全部回滚到 v3.9.10」。
        //        3.9.11 那版（设置页「收进」+「更多」二级页）试完不要了，代码已整份退回
        //        3.9.10 的状态（`git checkout -- app/`，见 commit 3687e62）。
        //        ⚠️ 版本号仍是 3.9.10（逸风要的就是「回到 3.9.10」），
        //           但 versionCode 必须给 99：他手机上装的是 3.9.11（code 98），
        //           安卓**不允许装版本号更低的包**（INSTALL_FAILED_VERSION_DOWNGRADE），
        //           给 97 他会看到「应用未安装」。给 99 就能直接覆盖装、数据一条不丢。
        //           通道那边仍是 code 97 / 3.9.10 → 本地 99 比它大，不会弹更新（这正是我们要的）。
        //           下次真发新版从 100 起，照样是递增的。
        // 3.9.12（2026-10-11 第二次）：**正式发版** —— Codex 接手的首轮界面打磨。
        //        ① 大标题右「+」改实心主色（原来跟搜索两颗长得一模一样、一眼分不出主操作）
        //        ② 进度卡推进/退回箭头视觉减重（原「实心淡底+圈」比进度条还抢眼）→ 无底轻箭头、
        //           按下才浮一层淡底；退回压一档（推进才是主操作）；
        //           ⚠️ **可点区域仍 34dp、两侧位置没变**（「不会点错」是逸风明确要求，不能丢）
        //        ③ 年度报告卡补一句描述（4 语种）；④ 补 debug 的 BuildConfig.LEGACY_COMPAT
        //           （原来 assembleDebug 从来编不过，代码里六处引用它）
        //        ⚠️ versionName 必须离开 3.9.10 —— 否则用户更新完在「关于」里看不出成没成。
        // 3.9.13（2026-10-11 第三次）：**画师图片备注删图补二次确认**。
        //        原来点缩略图右上角 ✕ 是一点就删（`onRemove = { state.removeArtistPhoto(...) }`），
        //        现改成：点 ✕ 先弹系统确认框（Material3 AlertDialog，「删除」红 /「取消」退路），
        //        确认后才真删。删图不可逆，跟约稿条目那边「删除先弹确认」一个规矩
        //        （归档 / 放回那种可逆的才不弹）。⚠️ 只动画师这一侧的**调用点**，
        //        共用的 PhotoStrip 组件一行没改 → 约稿那边「参考图」删除行为不受影响。
        // 3.9.14（2026-10-12）：**画师页加排序**。照账本页那套（圆形按钮 + 下拉、选中打勾）
        //        抄了一份 ArtistSort：按约稿次数（默认，跟原来写死的顺序一致）/ 按最近约稿 / 按名字。
        //        只改列表显示顺序，不碰数据；画师卡片先后 + 卡内那摞**两处**都过排序。
        //        新增 artist_sort_by_count / _recent / _name（4 语种齐）。
        // 3.9.16（2026-10-12）：**画师图片大图页**两处（逸风要的，**只动画师这一侧**）：
        //        ① 左上角加垃圾桶「删除此照片」→ 走原有的二次确认（跟缩略图 ✕ 同一条路）；
        //        ② 右上角分享改成**直接发磁盘上的原图文件**（FileProvider + ACTION_SEND + EXTRA_STREAM，
        //           不烧水印、不重编码）；文件不在就不显示这颗按钮。
        //        ⚠️ 关页方式一字未动（系统小托柄往下拖 / 点旁边黑边 / 系统返回），这页本来就没有 ✕。
        //        ⚠️ 共用的 PhotoViewer 只加了两个**带默认值**的参数（onDelete / shareOriginal），
        //           默认 = 老行为 → 约稿「参考图」那边的调用点一行没改、画面一模一样。
        //        没加新词条：分享复用 common_share、删除复用 common_delete_2（4 语种都现成有）。
        // 3.9.20（2026-10-13）：**画师页也加上分类**（逸风：「在画师页面也加上分类，不只是详情页」）：
        //        ① 买家模式**按分类分段显示**：一档一段，段标题 = 分类名（`SectionLabel`），
        //           顺序 = 分类清单的顺序（常稿在最前，他自己加的在后面）。
        //        ② **空档不画** —— 一个光标题下面啥也没有，比不显示更乱。
        //        ③ 卡片上那个分类小标签**撤了**：段标题已经说清分类，卡片再挂一遍是同一句话说两遍
        //           （`state.showsCategoryBadge` 留着，要恢复把它接回去就行）。
        //        ⚠️ 与 iOS 同日同形状：先在「条目/分组」那层切段，**不动数据**；段里顺序还归排序管。
        //        ⚠️ 段标题 `item` 的 key 用**序号**：分类名是他自己打的、可能重名，
        //           重名 → 抢同一个 key → LazyColumn 直接崩。
        //        ⚠️ `categoryDefs` 加了「REGULAR 必须在」的保险（见 AppState）；
        //           `bySection` 末尾再垫一个 `ifEmpty` —— 宁可不分段，也绝不摆空列表。
        //        ⚠️ 第一版（3.9.19/code107，未发）是「顶上那排筛选胶囊」，逸风看了说「不是这样」
        //           → 当场改成分段。`artist_sort_by_category` / `artist_category_empty`
        //           两条词条暂留未用（4 语种都在），要回到筛选/按分类排序时能直接用。
        // 3.9.21（2026-10-13 中午）：**段标题调大**（逸风：「Android版的分段小标题是不是太小了」）。
        //        第一版直接复用了设置页那个 `SectionLabel`（12sp 灰）——那是「分组说明」的尺寸；
        //        段标题是**一整段的主标题**，改成 **14sp / 半粗 / onSurface**，
        //        非第一段再垫 8dp 上间距（卡片名 16sp 半粗，层次仍压得住）。
        //        ⚠️ 只有安卓这一版；**iOS 那边不动** —— iOS 用的是系统 `Section` 表头，
        //           那是平台自己的样子（他这次指的也是安卓）。
        // 3.9.22（2026-10-13 傍晚）：**画师页的分类段能折叠**（逸风看了 TestFlight 的
        //        「以前测试过」那一栏，说「可以把小标题下的东西折叠起来」）：
        //        ① 点段标题（分类名那一行）→ 收起 / 展开；收起来就只剩标题那一行。
        //        ② 标题右边挂**人数** + 一个小箭头（90°=展开 / 0°=收起，转着走不硬切）。
        //        ③ 折叠状态是 `remember` **不落盘**，重新打开 App 一律**全展开** ——
        //           老用户升级上来看到的东西跟以前一模一样。
        //        ⚠️ 跟 iOS 同日同形状；`ArtistSection`（id/title/items）那个小结构就是为它加的。
        //        ⚠️ 段标题仍是 **14sp / 半粗 / 主文字色**（3.9.21 他拍过「太小了」），
        //           现在包在 `SectionHeaderRow` 里，顺手把「整行可点 + 箭头转」一起做了。
        // 3.9.23（2026-10-13 晚）：段标题那一行的**箭头和数字**照 TestFlight 对齐。
        //        逸风看了真机截图的反馈原话：「我要求尽量使用原生的组件，原生组件是蓝色箭头的，
        //        而且展开的时候没有数字，只有收起的时候会显示一个蓝色箭头跟数字」。
        //        ⇒ ① 数字**只在收起时**出现；② 数字和箭头都改用**主题色**（冰蓝），不再是灰的。
        //        ⚠️ 排序**没动** —— 安卓的 `sortedByDescending` / `groupBy` 天生保序，
        //           不会像 iOS 那样「同次数的人来回跳」（那个是 Swift 不保序 + 字典哈希顺序闹的）。
        // 3.9.24（2026-10-13 晚）：段标题右边那个数字跟标题**一般大**。
        //        逸风原话：「iOS 版箭头跟数字可以跟小标题字号一样大」→ 安卓一起跟（不然两端不一样）。
        //        数字 12sp → **14sp**（=段标题的字号）；箭头图标仍是 18dp（Material 里 18dp 的
        //        chevron 跟 14sp 的字放一起，视觉高度本来就相当，不用动）。
        // 3.9.25（2026-10-13 晚）：**扫码识别提速**。逸风报「现在 Android 版扫码识别很慢」。
        //        ① 解码格式限定成 **QR_CODE** —— 库的默认是「所有格式」：
        //           `CaptureManager.initializeFromIntent` 在 Intent 没带 `SCAN_FORMATS` 时
        //           把 `decodeFormats` 设成 null → `MultiFormatReader` 每帧连一维码
        //           那一堆 reader 也全试一遍。这是慢的头号原因。
        //           ⚠️ 必须写在 `super.onCreate` **之后**，否则会被 `initializeFromIntent` 冲掉。
        //        ② 相机对焦改成**连续对焦**（`continuousFocusEnabled = true` + `FocusMode.CONTINUOUS`）——
        //           出厂值是 false/AUTO，对不上焦要等 2 秒才重对一次，手感就是「举着半天不认」。
        //        ③ 相册那条解码路（`decodeFromImage`）同样补上「只认二维码」的 hints。
        // 3.9.26（2026-10-14）：**缩略图加了内存缓存**（纯优化，不改任何行为/数据）。
        //        原来 `Photos.thumbnail` 每次都从磁盘重解（当初的理由是「一条单最多 6 张、
        //        几毫秒，缓存还得自己管内存」）—— 但画师详情页的图片格子也走它，
        //        看大图（2200）左右翻的时候更是每翻回一页就重解一张。
        //        现在用 `LruCache`（64MB，按位图字节数算），超限自己丢；删图/清孤儿图时 `clearCache()`。
        //        ⚠️ 跟 iOS 3.9.27 同一轮（iOS 那边用 `NSCache`）—— 两端对齐。
        //        ⚠️ 动画那条**安卓不用改**：`.animateItem()` 早就在账本卡片和画师卡片上了。
        versionCode = 114
        versionName = "3.9.26"
        // ⚠️ 2026-10-03 多语言：这行原来是 `listOf("zh")` ——
        //    意思是「只打包中文资源，其它语言全砍掉」。
        //    留着它的话，values-en / values-zh-rTW 会被 aapt 直接剔出包，
        //    界面上切了语言也还是中文，而且查不出原因（资源压根不在包里）。
        resourceConfigurations += listOf("zh", "en", "zh-rHK", "zh-rTW")
    }

    buildTypes {
        // ⚠️ 2026-10-11 补：debug 一直缺 BuildConfig.LEGACY_COMPAT 这个字段，
        //    所以 `assembleDebug` 从来编不过（代码里六处引用它，只有 release/compat 定义了）。
        //    平时都是直接打 release 包所以没暴露。给 debug 也补上（值同通用版 = false），
        //    这样装到模拟器上跑调试包时行为跟正式版一致。
        debug {
            buildConfigField("boolean", "LEGACY_COMPAT", "false")
        }
        release {
            // 2026-09-30 打开 R8：微信发文件有大小限制，11MB 的包发不出去（CDN 500）。
            // minify = 剔掉没用到的代码（material-icons-extended 那几千个图标是重头）
            // shrinkResources = 顺带剔掉没人用的资源
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
            // 通用版：所有兼容开关都关着
            buildConfigField("boolean", "LEGACY_COMPAT", "false")
        }

        // ⚠️ 老机兼容版（2026-10-07 加）——**只给特定用户**的单独一份包。
        //
        //    起因：一位用户的华为 nova 3（鸿蒙 2.0 / 麒麟 970）打开「年度报告」会闪退。
        //    逸风的决定是「通用版别动，专门给她做一版」——所以这儿不是「降级版」，
        //    而是**同一份代码、另一套开关**，产物跟她手机上的旧版是同一个包名，
        //    装上去是覆盖升级（数据一定在）。
        //
        //    这一版比通用版多的两样：
        //      ① 更新功能整块锁死（灰掉 + 弹窗说明）—— 免得她升回通用版又崩；
        //      ② 年度报告那张卡不再「一进页面就录图」（见 Extras.kt）。
        //
        //    ⚠️ 跟 release 走同一套 R8 + 同一个签名，只有 BuildConfig.LEGACY_COMPAT 是 true。
        //    ⚠️ `assembleRelease` 不受影响 —— 通用版的打包流程一行没改。
        create("compat") {
            initWith(getByName("release"))
            versionNameSuffix = "-compat"
            buildConfigField("boolean", "LEGACY_COMPAT", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // FlowRow（报价计算器里那排可变的加价项胶囊）还是试验 API，
        // 统一在这里开一次，省得每个用到的 composable 都挂 @OptIn
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
    }

    buildFeatures {
        compose = true
        // 2026-10-03 打开：隐私政策里那行「适用版本：安卓版 3.5.x（versionCode N）」
        // 以前是**写死**在四份语种资源里的，一升版本就悄悄过期
        // （刚就踩了：包里是 3.5.9、政策上还写着 3.5.8）。
        // 改成从 BuildConfig 现取，以后升版本不必再手动改四份语言包。
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 二维码传输（2026-10-08 加，见 QrTransfer.kt / QrScanActivity.kt）
    //   core     = 造二维码（出码那端）+ 从一张图片里解二维码（扫码页的「相册」那颗）
    //   embedded = 相机预览、对焦、连续解码、相机权限申请全包了；
    //              我们的取景页只是**换掉它的布局**（见 QrScanActivity），
    //              比自己拉 CameraX 重写一遍快得多，也少一堆型号坑。
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
