#!/bin/bash
# 打「老机兼容版」（productFlavor 无，buildType = compat）。
# 用法：tools/build_compat.sh
#
# ⚠️ 这份包**只给特定用户**（2026-10-07：一位用华为 nova 3 / 鸿蒙 2.0 的用户，
#    打开「年度报告」会闪退）。跟通用版的差别只有两处，全在 BuildConfig.LEGACY_COMPAT 里：
#      ① 设置里「检查更新 / 自动更新」两行灰掉，点了弹一句说明（升回通用版会把适配作废）
#      ② 年度报告彻底不碰 GraphicsLayer，分享改成发纯文字
#
# ⚠️ 包名跟通用版**完全一样**（com.yifeng.commissionbook），所以是覆盖升级、数据不丢。
#    在关于页看到的版本号是 `3.7.0-compat`，一眼能认出是哪份。
#
# ⚠️ 打通用版还是用 tools/build_release.sh（那条流程一行没改）。
set -e
set -o pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="$HOME/Library/Android/sdk"

echo "① 清掉可能存在的 iCloud 冲突副本"
find . -name "* [0-9].*" -not -path "./.git/*" -delete 2>/dev/null || true

echo "② 编译（compat）"
./gradlew assembleCompat --console=plain 2>&1 | grep -E "BUILD|error:|^e: " | head -20

APK=app/build/outputs/apk/compat/app-compat.apk
[ -f "$APK" ] || { echo "!! 没有产物，编译没成功"; exit 1; }

echo "③ 校对包里的版本号"
WANT_NAME=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | sed 's/.*"\(.*\)"/\1/')-compat
WANT_CODE=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | grep -o '[0-9]*')
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | tail -1)
if [ -n "$AAPT" ] && [ -x "$AAPT" ]; then
  GOT=$("$AAPT" dump badging "$APK" 2>/dev/null \
        | sed -n "s/.*versionCode='\([0-9]*\)'.*versionName='\([^']*\)'.*/\1 \2/p" | head -1)
  GOT_CODE=${GOT%% *}
  GOT_NAME=${GOT##* }
  if [ "$GOT_CODE" != "$WANT_CODE" ] || [ "$GOT_NAME" != "$WANT_NAME" ]; then
    echo "!! 包里是 ${GOT_NAME}（code ${GOT_CODE}），期望 ${WANT_NAME}（code ${WANT_CODE}）"
    exit 1
  fi
  echo "   包里确认：${GOT_NAME}（code ${GOT_CODE}）"
else
  echo "   （没找到 aapt2，这一守卫跳过了）"
fi

echo "④ 留一份到桌面（发给用户用的就是这个）"
cp "$APK" "$HOME/Desktop/约稿账本_${WANT_NAME}.apk"
ls -l "$HOME/Desktop/约稿账本_${WANT_NAME}.apk"
