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
notes = sys.argv[3] if len(sys.argv) > 3 else ""

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
