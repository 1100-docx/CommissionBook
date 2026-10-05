#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发版打包：把 APK 复制进 dist/，并重建仓库根目录的 version.json（App 检查更新就读它）。"""
import hashlib, json, pathlib, shutil, sys

repo = pathlib.Path.home() / "Developer/AndroidLearning/CommissionBook"
apk = repo / "app/build/outputs/apk/release/app-release.apk"
dist = repo / "dist"
dist.mkdir(exist_ok=True)

version_name = sys.argv[1] if len(sys.argv) > 1 else "3.5.7"
version_code = int(sys.argv[2]) if len(sys.argv) > 2 else 31

# 更新说明（App 的「检查更新」弹框里那一块「更新内容」显示的就是它）。
#
# ⚠️ 2026-10-06 改：**这段字以后归逸风自己写**。
#    他原话「我还在想Android版本要不要显示更新的时候显示我写的更新说明」——
#    要，而且本来就显示。所以做法是：说明写在 tools/更新说明.txt 里，
#    这个脚本自动读进来。想改措辞就改那个 txt，不用重新编译（version.json
#    是运行时联网取的，改完推上去，别人下次检查更新就看到新的了）。
#
#    优先级：命令行第 3 个参数 > tools/更新说明.txt > 留空。
#    留空的话 App 那边会显示「（这一版没写更新说明）」。
notes_file = repo / "tools/更新说明.txt"
if len(sys.argv) > 3:
    notes = sys.argv[3]
elif notes_file.is_file():
    notes = notes_file.read_text(encoding="utf-8").strip()
else:
    notes = ""

h = hashlib.sha256()
with apk.open("rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        h.update(chunk)
digest = h.hexdigest()
size = apk.stat().st_size

name = f"CommissionBook_{version_name}.apk"
shutil.copy2(apk, dist / name)

# 只留最新那份，别让仓库一直堆二进制
for old in dist.glob("CommissionBook_*.apk"):
    if old.name != name:
        old.unlink()

manifest = {
    "versionCode": version_code,
    "versionName": version_name,
    "url": f"https://raw.githubusercontent.com/1100-docx/CommissionBook/main/dist/{name}",
    "size": size,
    "sha256": digest,
    "notes": notes,
}
(repo / "version.json").write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)

print(f"size   = {size:,} bytes ({size / 1024 / 1024:.2f} MB)")
print(f"sha256 = {digest}")
print()
print((repo / "version.json").read_text(encoding="utf-8"))
print("dist/ :", sorted(p.name for p in dist.iterdir()))
