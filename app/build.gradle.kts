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
        versionCode = 72
        versionName = "3.6.5"
        // ⚠️ 2026-10-03 多语言：这行原来是 `listOf("zh")` ——
        //    意思是「只打包中文资源，其它语言全砍掉」。
        //    留着它的话，values-en / values-zh-rTW 会被 aapt 直接剔出包，
        //    界面上切了语言也还是中文，而且查不出原因（资源压根不在包里）。
        resourceConfigurations += listOf("zh", "en", "zh-rHK", "zh-rTW")
    }

    buildTypes {
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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
