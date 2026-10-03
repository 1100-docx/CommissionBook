#!/bin/bash
# 打安卓正式包（R8 压缩版）。
# 用法：tools/build_release.sh
#
# 2026-10-03：工程从 ~/Desktop 挪到 ~/Developer 之后，这个脚本就回归本职了 ——
#   原地编译即可。之前那套「复制到 iCloud 外面再编」的绕法是给桌面同步擦屁股用的，
#   现在不需要了（下面留着那段说明，免得以后有人把工程挪回桌面又踩一遍）。
set -e
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

echo "② 编译"
./gradlew assembleRelease --console=plain 2>&1 | grep -E "BUILD|error:" | head -5

APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "!! 没有产物，编译没成功"; exit 1; }
echo "③ 成品"
ls -l "$APK"
