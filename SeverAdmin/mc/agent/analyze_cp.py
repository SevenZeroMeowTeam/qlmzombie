#!/usr/bin/env python3
"""精确解析 GunConfigurations.class 常量池，转储所有 Utf8 字符串与结构信息。"""
import struct
import sys
import zipfile

def parse_cp(data):
    cp_count = struct.unpack('>H', data[8:10])[0]
    i = 10
    idx = 1
    tags = {}
    strings = {}
    while idx < cp_count and i < len(data):
        tag = data[i]
        tags[idx] = tag
        if tag == 1:  # Utf8
            length = struct.unpack('>H', data[i+1:i+3])[0]
            s = data[i+3:i+3+length].decode('utf-8', errors='replace')
            strings[idx] = s
            i += 3 + length
            idx += 1
        elif tag == 7:   # Class
            i += 3; idx += 1
        elif tag in (9, 10, 11):  # Fieldref, Methodref, InterfaceMethodref
            i += 5; idx += 1
        elif tag == 8:   # String
            i += 3; idx += 1
        elif tag in (3, 4):  # Integer, Float
            i += 5; idx += 1
        elif tag in (5, 6):  # Long, Double
            i += 9; idx += 2
        elif tag == 12:  # NameAndType
            i += 5; idx += 1
        elif tag == 13:  # MethodHandle
            i += 4; idx += 1
        elif tag == 14:  # MethodType
            i += 3; idx += 1
        elif tag in (15, 16):
            i += (4 if tag == 15 else 3); idx += 1
        elif tag in (17, 18):  # Dynamic, InvokeDynamic
            i += 5; idx += 1
        elif tag in (19, 20):  # Module, Package
            i += 3; idx += 1
        else:
            print(f"Unknown tag {tag} at {idx}")
            break
    return tags, strings

def main(jar):
    target = 'com/craftingdead/core/world/item/gun/GunConfigurations.class'
    with zipfile.ZipFile(jar) as z:
        data = z.read(target)
    tags, strings = parse_cp(data)

    print(f"cp_count entries: {len(tags)}")
    print("\n=== 所有包含 'save' / 'disable' / 'saving' 的字符串 ===")
    for k, v in sorted(strings.items()):
        if 'save' in v.lower() or 'disable' in v.lower() or 'saving' in v.lower():
            print(f"  [{k}] {v!r}")

    print("\n=== 所有方法名（Methodref/InterfaceMethodref/NameAndType 指向的 Utf8）===")
    names = set(strings.values())
    for k, v in sorted(strings.items()):
        if v and (v[0].islower() or v.startswith('<')) and '(' in v:
            print(f"  [{k}] {v!r}")

    print("\n=== 所有以小写字母开头的字符串（候选方法名/字段名）===")
    for k, v in sorted(strings.items()):
        if v and v[0].islower():
            print(f"  [{k}] {v!r}")

if __name__ == '__main__':
    main(sys.argv[1])
