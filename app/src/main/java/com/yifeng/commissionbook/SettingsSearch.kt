package com.yifeng.commissionbook

/**
 * 设置页的搜索（2026-11-19 加，跟 iOS 版 `SettingsSearch.swift` 同一张关键词表）。
 *
 * 逸风要的「B」：**标题 / 说明 / 别名**都能搜。
 *
 * ⚠️ 为什么按**整块**筛、不按行筛：设置页那一行一行都是手写的 `GroupRow`，
 *    按行摘出来得把 900 多行拆开重拼，风险大；以后每加一条还得记得回来登记，
 *    漏了就「搜不到」——那是排查起来最烦的一类问题。
 *    一块通常两三行，留下了点得到。
 *
 * ⚠️ 关键词**中英并列、不跟界面语言走**：他在中文界面也可能敲 "face id"，
 *    英语用户也可能敲拼音；多写几个词几乎零成本，漏一个就搜不到。
 */
enum class SettingSec {
    Display, Mode, Appearance, Haptic, Language, Security,
    Reminders, Backup, Tools, Phrases, Help, Update, About, Support;

    /** 这一块能被哪些词搜到：分区标题 + 这块里每一项的名字 + 说明里的关键词 + 别名 */
    fun keywords(): List<String> = when (this) {
        Display -> listOf("显示", "不显示功能描述", "描述", "说明", "收起", "description", "hide")
        Mode -> listOf("使用模式", "买家", "画师", "切换", "模式", "mode", "buyer", "artist")
        Appearance -> listOf("外观", "深色", "浅色", "暗色", "跟随系统", "主题", "appearance", "dark", "light", "theme")
        Haptic -> listOf("触感", "震动", "振动", "震", "反馈", "haptic", "vibration")
        Language -> listOf("语言", "中文", "简体", "繁體", "英文", "language", "english", "chinese")
        // 「保护」这一块搜得最多 —— 别名往全了写
        Security -> listOf(
            "隐私", "保护", "面容", "指纹", "人脸", "密码", "锁", "后台", "模糊",
            "遮挡", "验身", "验证", "privacy", "lock", "face", "touch", "blur", "protect",
        )
        Reminders -> listOf("提醒", "通知", "提醒时间", "几点", "到点", "截止", "到期", "reminder", "notification", "alarm")
        Backup -> listOf(
            "数据", "备份", "恢复", "导入", "导出", "分享", "自动备份", "快照", "小工具",
            "backup", "restore", "import", "export", "share", "json",
        )
        Tools -> listOf("小工具", "报价", "计算器", "算价", "tools", "calculator", "quote")
        Phrases -> listOf("常用语", "短语", "模板", "话术", "phrase", "template")
        Help -> listOf("帮助", "怎么用", "功能一览", "教程", "help", "guide", "tutorial")
        Update -> listOf("更新", "新版本", "检查更新", "升级", "update", "version check")
        About -> listOf(
            "关于", "版本", "作者", "崩溃记录", "反馈", "建议", "许可证",
            "about", "version", "crash", "feedback",
        )
        Support -> listOf("支持", "赞赏", "自愿支持", "打赏", "support", "tip", "donate")
    }

    /** 这次搜索有没有命中这一块 */
    fun matches(q: String): Boolean {
        val needle = q.trim()
        if (needle.isEmpty()) return true
        return keywords().any { it.contains(needle, ignoreCase = true) }
    }
}
