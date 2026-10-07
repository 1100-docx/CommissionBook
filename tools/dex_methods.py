#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DEX 方法引用扫描：直接列出包里对 SortedSet / SequencedCollection 那套方法的引用。

为什么值得写：这次闪退的真凶是**编译期 API 泄漏**（源码里看着没毛病），
所以「我改了源码」不算证据 —— 得看编译产物里那条方法引用还在不在。
用真实的 method_id 表来查，而不是在 dex 里 grep 字符串（字符串表里
`reversed` 这个名字被别处引用过、proto 又不以合并字符串形式存，grep 会骗人）。

用法：python3 tools/dex_methods.py <apk> [关键字...]
"""
import struct
import sys
import zipfile

WATCH_TYPES = ("java.util.SortedSet", "java.util.SortedMap", "java.util.SequencedSet",
               "java.util.SequencedCollection", "java.util.SequencedMap",
               "java.util.List", "java.util.LinkedHashSet", "java.util.TreeSet",
               "java.util.TreeMap")
# ⚠️ `ArrayDeque` 故意**不**放进来：它的 addFirst/addLast/removeFirst/... 是 Java 1.6
#    就有的老 API（Deque 自带），在 API 26 上活得好好的。放进来只会天天报假警报
#    （Compose 自己就在用 `ArrayDeque.addLast`）。假警报多了就没人看真警报了。
WATCH_NAMES = ("reversed", "getFirst", "getLast", "addFirst", "addLast",
               "removeFirst", "removeLast")


def uleb128(b, off):
    result = shift = 0
    while True:
        byte = b[off]
        off += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, off
        shift += 7


def dex_strings(b):
    size, off = struct.unpack_from("<II", b, 0x38)
    out = []
    for i in range(size):
        data_off = struct.unpack_from("<I", b, off + i * 4)[0]
        n, p = uleb128(b, data_off)
        out.append(b[p:p + n].decode("utf-8", "replace"))
    return out


def dex_types(b, strs):
    """type_ids 表：每项 4 字节 = **指向 string_ids 的下标**。
    ⚠️ `method_id_item.class_idx` 是这张表的下标、**不是字符串下标** ——
    我头两版都直接拿它当字符串下标用，于是查出来的「类名」全是别的字符串，
    结果把有 bug 的包报成干净。这种「验证脚本自己错了」最坑：它会让你以为修好了。"""
    size, off = struct.unpack_from("<II", b, 0x40)
    return [strs[struct.unpack_from("<I", b, off + i * 4)[0]] for i in range(size)]


def dex_methods(b, strs):
    """method_id_item = class_idx(u2) + proto_idx(u2) + name_idx(u4) —— **8 字节**。
    （我第一版写成 `<III` / 12 字节，索引全错，解析直接炸 —— 记在这儿免得再犯。）"""
    types = dex_types(b, strs)
    size, off = struct.unpack_from("<II", b, 0x58)
    rows = []
    for i in range(size):
        cls, _proto, name = struct.unpack_from("<HHI", b, off + i * 8)
        if cls < len(types) and name < len(strs):
            rows.append((types[cls], strs[name]))
    return rows


def norm_cls(c):
    """dex 字符串表里的类名是 **`Ljava/util/SortedSet;`** 这种带 `L`…`;` 的描述符，
    不是 `java.util.SortedSet` —— 我第一版直接拿后者比，结果**全部漏判**，
    把有 bug 的旧包也报成「干净」。这里统一剥壳。"""
    return c[1:-1].replace("/", ".") if c.startswith("L") and c.endswith(";") else c


def main():
    apk = sys.argv[1]
    hits = {}
    total = 0
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            if not n.endswith(".dex"):
                continue
            b = z.read(n)
            try:
                strs = dex_strings(b)
                for cls, name in dex_methods(b, strs):
                    total += 1
                    c = norm_cls(cls)
                    if c in WATCH_TYPES and name in WATCH_NAMES:
                        hits[(c, name)] = hits.get((c, name), 0) + 1
                    elif c in WATCH_TYPES:
                        hits.setdefault((c, "<" + name + ">"), 0)
                        hits[(c, "<" + name + ">")] += 1
            except Exception as e:                      # noqa: BLE001
                print("  !!", n, "解析失败:", e)
    label = apk.split("/")[-1]
    print("===", label, "===")
    print("   扫了", total, "条方法引用")
    danger = [(k, v) for k, v in hits.items() if not k[1].startswith("<")]
    other = [(k, v) for k, v in hits.items() if k[1].startswith("<")]
    if not danger:
        print("   ✅ 没有任何对「Java 21 集合方法」的调用")
    for (cls, name), c in sorted(danger):
        print(f"   ❌ 危险：{cls}.{name}() ×{c}  ← 老手机上会 NoSuchMethodError")
    for (cls, name), c in sorted(other):
        print(f"   （只有类型引用）{cls}{name} ×{c}")


if __name__ == "__main__":
    main()
