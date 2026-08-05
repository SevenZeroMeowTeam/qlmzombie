# -*- coding: utf-8 -*-
"""
提取 src/libs/ 目录下所有 JAR 文件的模组名，输出到 list.txt
数据来源优先级：META-INF/mods.toml (displayName) > MANIFEST.MF (Implementation-Title) > 文件名
"""
import zipfile
import os
import re
import glob

LIBS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src", "libs")
OUTPUT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "list.txt")


def extract_mod_name(jar_path):
    """从 JAR 中提取模组名"""
    filename = os.path.basename(jar_path)
    try:
        with zipfile.ZipFile(jar_path, "r") as zf:
            # 1. 尝试读取 META-INF/mods.toml
            try:
                tom = zf.read("META-INF/mods.toml").decode("utf-8", errors="replace")
                # 匹配 displayName = "..." 或 modId = "..."
                m = re.search(r'displayName\s*=\s*"([^"]+)"', tom)
                if m:
                    return m.group(1)
                m = re.search(r'modId\s*=\s*"([^"]+)"', tom)
                if m:
                    return m.group(1)
            except KeyError:
                pass

            # 2. 尝试读取 META-INF/MANIFEST.MF
            try:
                mf = zf.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
                m = re.search(r'Implementation-Title\s*:\s*(.+)', mf)
                if m:
                    return m.group(1).strip()
            except KeyError:
                pass

            # 3. 尝试读取 fabric.mod.json (Fabric mod)
            try:
                fmj = zf.read("fabric.mod.json").decode("utf-8", errors="replace")
                m = re.search(r'"name"\s*:\s*"([^"]+)"', fmj)
                if m:
                    return m.group(1)
            except KeyError:
                pass

    except Exception as e:
        return f"[读取失败] {filename} ({e})"

    # 回退：使用文件名
    return filename


def main():
    jars = sorted(glob.glob(os.path.join(LIBS_DIR, "*.jar")))
    if not jars:
        print(f"未找到 JAR 文件: {LIBS_DIR}")
        return

    lines = []
    lines.append(f"模组名列表（共 {len(jars)} 个）")
    lines.append(f"目录: {LIBS_DIR}")
    lines.append("=" * 80)

    for i, jar in enumerate(jars, 1):
        name = extract_mod_name(jar)
        filename = os.path.basename(jar)
        size_mb = os.path.getsize(jar) / (1024 * 1024)
        lines.append(f"{i:3d}. {name}")
        lines.append(f"     文件: {filename}")
        lines.append(f"     大小: {size_mb:.1f} MB")
        lines.append("")

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"已生成: {OUTPUT_FILE}")
    print(f"共 {len(jars)} 个模组")


if __name__ == "__main__":
    main()
