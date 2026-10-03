#!/bin/bash
# 在 iCloud 外面编译，编好把成品拿回来。
#
# 为什么必须这样（2026-10-03 连栽四次）：
#   工程放在 ~/Desktop，而桌面开着 iCloud 同步。构建过程中 Gradle 不停地写中间产物，
#   iCloud 一边同步一边判定「冲突」，于是到处生出「文件名 + 空格 + 2」的副本
#   （strings 2.xml / mergeReleaseResources 2.json / .dm 2 …）。
#   Gradle 去算这些副本的 MD5 时直接报 "Accessing unreadable inputs or outputs"，
#   构建失败。最坑的是失败点每次都换，清一批又冒一批 —— 在源头上躲开才治得好。
#
# 用法：tools/build_release.sh            （编正式包）
set -e
SRC="$HOME/Desktop/AndroidLearning/CommissionBook"
WORK="$HOME/cb-build/CommissionBook"          # ← 这个路径不在任何 iCloud 同步目录下

echo "① 同步源码到 $WORK"
mkdir -p "$(dirname "$WORK")"
# ⚠️ 排除写法别改回「build/」：那样排除不掉 app/build，会把一堆旧中间产物一起搬过去，
#    于是这边走增量编译（4 秒就「成功」），看着快，其实埋雷。
rsync -a --delete \
  --exclude=build --exclude=.gradle --exclude=.kotlin --exclude=.git \
  "$SRC/" "$WORK/"

echo "② 清掉可能已经被同步出来的冲突副本"
find "$WORK" -name "* [0-9].*" -delete 2>/dev/null || true

echo "③ 编译"
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd "$WORK"
./gradlew assembleRelease --console=plain 2>&1 | grep -E "BUILD|error:" | head -5

APK="$WORK/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "!! 没有产物，编译没成功"; exit 1; }

echo "④ 把成品拿回来"
mkdir -p "$SRC/app/build/outputs/apk/release"
cp "$APK" "$SRC/app/build/outputs/apk/release/app-release.apk"
ls -l "$SRC/app/build/outputs/apk/release/app-release.apk"
