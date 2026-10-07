#!/bin/bash
# 打安卓正式包（R8 压缩版）。
# 用法：tools/build_release.sh
#
# 2026-10-03：工程从 ~/Desktop 挪到 ~/Developer 之后，这个脚本就回归本职了 ——
#   原地编译即可。之前那套「复制到 iCloud 外面再编」的绕法是给桌面同步擦屁股用的，
#   现在不需要了（下面那段说明留着，免得以后有人把工程挪回桌面又踩一遍）。
#
# ⚠️ 2026-10-05 加了两个护栏，都是被坑出来的，**别删**：
#   ① `set -o pipefail` —— 编译那行是 `gradlew | grep | head`，
#      没有它的话退出码来自 head（永远是 0），`set -e` 形同虚设：
#      编译失败脚本照跑，把**上一次的旧 APK** 当新版本发出去。
#      当天就这么发过一次（3.5.15 那次，包里其实是 3.5.14，version.json 写着 code 39
#      → 用户会陷入「永远提示有更新、装完还是旧版」的死循环）。
#   ② 编译完**校对包里的 versionCode/versionName** 跟 build.gradle 写的是否一致，
#      不一致直接退，不许往下走。
set -e
set -o pipefail
cd "$(dirname "$0")/.."          # 工程根目录

export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="$HOME/Library/Android/sdk"

# ⚠️ 别把工程放回 ~/Desktop / ~/Documents —— 那两个目录开着 iCloud 同步。
#    现象：构建过程中 iCloud 会到处生成「文件名 + 空格 + 2」的副本
#    （strings 2.xml / mergeReleaseResources 2.json / .dm 2 …），
#    Gradle 去算这些副本的 MD5 就报 "Accessing unreadable inputs or outputs"，构建失败。
#    2026-10-03 一天之内栽了四次，失败点每次都不一样，清一批又冒一批。
echo "① 清掉可能存在的 iCloud 冲突副本"
find . -name "* [0-9].*" -not -path "./.git/*" -delete 2>/dev/null || true

# ⚠️ 2026-10-07 加的护栏，**别删** —— 这天栽了一个真闪退，代价是白折腾大半天：
#
#   `.reversed()` / `.getFirst()` / `.getLast()` / `.addFirst()` / `.addLast()` /
#   `.removeFirst()` / `.removeLast()` —— 这几个名字从 **Java 21** 起在 Java 集合
#   （List / Set / Map / Deque）上是**成员方法**（SequencedCollection 那套）。
#   Kotlin 里**成员优先于扩展**，所以在 `SortedSet` 之类 Java 类型上一叫，
#   绑到的不是 Kotlin 的扩展，而是新版 libcore 的成员。
#
#   而 `compileSdk = 36`（Android 16）的 libcore 已经是 **OpenJDK 21 底子** →
#   编译**看得见、编得过**；用户机（Android 9 / API 28、鸿蒙 2.0）**运行时没有** →
#   `java.lang.NoSuchMethodError: No interface method reversed()Ljava/util/SortedSet;`
#   → 一进「年度报告」就闪退。
#
#   真想倒序：用 Kotlin 独有名字 `asReversed()` / `sortedDescending()`；
#   ⚠️ 别想着 `toList().reversed()` —— `List.reversed()` 同样是 Java 21 成员，**一样崩**。
echo "①·5 扫 Java 21 才有的集合方法（编译期 API 泄漏）"
SRC=app/src/main/java
HITS=$(grep -rnE "\.(reversed|getFirst|getLast|addFirst|addLast|removeFirst|removeLast)\(" "$SRC" 2>/dev/null \
        | grep -vE "^[^:]+:[0-9]+:[[:space:]]*(//|\*)" || true)
if [ -n "$HITS" ]; then
  echo "❌ 用到 Java 21 才有的集合方法了 —— 老手机会 NoSuchMethodError 闪退："
  echo "$HITS"
  echo "   改法：倒序用 Kotlin 的 asReversed() / sortedDescending()（换个名字就不绑新版 API）"
  exit 1
fi

echo "② 编译"
# ⚠️ Kotlin 的编译错误前缀是 `e: `（不是 `error:`），grep 里必须带上，
#    否则「编译失败」这四个字看得见、真正的原因看不见。
./gradlew assembleRelease --console=plain 2>&1 | grep -E "BUILD|error:|^e: " | head -20

APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "!! 没有产物，编译没成功"; exit 1; }

echo "③ 校对包里的版本号（防发出旧包）"
WANT_NAME=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | sed 's/.*"\(.*\)"/\1/')
WANT_CODE=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | grep -o '[0-9]*')
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | tail -1)
if [ -n "$AAPT" ] && [ -x "$AAPT" ]; then
  GOT=$("$AAPT" dump badging "$APK" 2>/dev/null \
        | sed -n "s/.*versionCode='\([0-9]*\)'.*versionName='\([^']*\)'.*/\1 \2/p" | head -1)
  GOT_CODE=${GOT%% *}
  GOT_NAME=${GOT##* }
  if [ "$GOT_CODE" != "$WANT_CODE" ] || [ "$GOT_NAME" != "$WANT_NAME" ]; then
    echo "!! 包里是 ${GOT_NAME}（code ${GOT_CODE}），build.gradle 写的是 ${WANT_NAME}（code ${WANT_CODE}）"
    echo "!! 这是上一次的旧产物，别发 —— 先弄清这次编译为什么没产出新包。"
    exit 1
  fi
else
  echo "   （没找到 aapt2，这一守卫跳过了：$AAPT）"
fi

echo "④ 成品：${WANT_NAME}（code ${WANT_CODE}）"
ls -l "$APK"

# ⚠️ 2026-10-07 加：**对着产物再扫一遍**（不是对着源码）。
#    为什么两道都要：源码那道（①·5）防的是「以后再写出来」，
#    这道防的是「库里带进来 / R8 里面还留着」——
#    真凶那次就是源码里看着人畜无害的一行 `.reversed()`，
#    只有拆开 dex 的方法引用表才看得见它绑到了 Java 21 的成员上。
echo "⑤ 扫产物 dex：确认没有 Java 21 才有的集合方法"
if python3 tools/dex_methods.py "$APK" | grep -q "❌"; then
  python3 tools/dex_methods.py "$APK" | grep -A2 "❌"
  echo "!! 产物里还留着新版 API 调用 —— 老手机会 NoSuchMethodError，这包别发"
  exit 1
fi
python3 tools/dex_methods.py "$APK" | sed -n '2p'
echo "   ✅ 干净（compat 版另跑一次：python3 tools/dex_methods.py app/build/outputs/apk/compat/app-compat.apk）"
