#!/usr/bin/env python3
"""
Patch KubeJS JAR - make KubeJSPlugins.LIST thread-safe.

Fixes server startup failure:
  KubeJS (kubejs) has failed to load correctly
  java.util.ConcurrentModificationException: null
      at dev.latvian.mods.kubejs.script.ScriptManager.load(ScriptManager.java:192)

Root cause: KubeJSPlugins.LIST is a static plain ArrayList that is populated
while Forge loads mods in parallel. ScriptManager.load() iterates over it while
another mod's constructor adds a KubeJS plugin (LIST.add), which throws
ConcurrentModificationException and aborts server startup.

Fix: change the LIST initializer from `new ArrayList` to
`new CopyOnWriteArrayList` (java.util.concurrent). CopyOnWriteArrayList's
iterators never throw ConcurrentModificationException and concurrent adds are
safe. The field type stays `java.util.List`, so no other bytecode changes are
required.

This patches only the first `new ArrayList` + `invokespecial ArrayList.<init>`
pair inside the static initializer (<clinit>), which is the one that
initializes LIST (GLOBAL_CLASS_FILTER keeps ArrayList).
"""

import sys
import os
import zipfile
import struct
import shutil

# 兼容 Windows GBK 控制台：输出统一 ASCII，避免 UnicodeEncodeError
NEW_CLASS = 'java/util/concurrent/CopyOnWriteArrayList'


def parse_cp(data):
    """Parse constant pool. Returns (cp_count, cp_tags, cp_strings, cp_positions, cp_end)."""
    cp_count = struct.unpack('>H', data[8:10])[0]
    cp_strings = {}
    cp_tags = {}
    cp_positions = {}
    i = 10
    idx = 1
    while idx < cp_count and i < len(data):
        cp_positions[idx] = i
        tag = data[i]
        cp_tags[idx] = tag
        if tag == 1:  # Utf8
            length = struct.unpack('>H', data[i+1:i+3])[0]
            s = data[i+3:i+3+length].decode('utf-8', errors='replace')
            cp_strings[idx] = s
            i += 3 + length
            idx += 1
        elif tag == 7:   # Class: name_index(2)
            i += 3
            idx += 1
        elif tag in (9, 10, 11):  # Fieldref/Methodref/InterfaceMethodref: class(2)+nat(2)
            i += 5
            idx += 1
        elif tag == 8:   # String: string_index(2)
            i += 3
            idx += 1
        elif tag in (3, 4):  # Integer/Float
            i += 5
            idx += 1
        elif tag in (5, 6):  # Long/Double (2 slots)
            i += 9
            idx += 2
        elif tag == 12:  # NameAndType: name(2)+descriptor(2)
            i += 5
            idx += 1
        elif tag in (13, 15):  # MethodHandle: reference_kind(1) + reference_index(2)
            i += 4
            idx += 1
        elif tag in (14, 16):  # MethodType: descriptor_index(2)
            i += 3
            idx += 1
        elif tag in (17, 18):  # Dynamic/InvokeDynamic: bootstrap(2)+nat(2)
            i += 5
            idx += 1
        elif tag in (19, 20):  # Module/Package: name_index(2)
            i += 3
            idx += 1
        else:
            print(f"[Patch] Unknown CP tag {tag} at index {idx}, stopping")
            break
    return cp_count, cp_tags, cp_strings, cp_positions, i


def find_cp_index(cp_tags, cp_positions, tag, check_func):
    """Find first cp index with given tag whose bytes satisfy check_func."""
    for idx in sorted(cp_tags):
        if cp_tags[idx] == tag:
            pos = cp_positions[idx]
            if check_func(idx, pos):
                return idx
    return None


def find_utf8_index(cp_strings, value):
    for idx, s in cp_strings.items():
        if s == value:
            return idx
    return None


def parse_methods(data, cp_end, cp_strings):
    """Return list of (name, descriptor, code_start_abs) for each method."""
    pos = cp_end
    pos += 6  # access_flags(2) + this_class(2) + super_class(2)
    iface_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2 + iface_count * 2
    field_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    for _ in range(field_count):
        pos += 6
        attr_count = struct.unpack('>H', data[pos:pos+2])[0]
        pos += 2
        for _ in range(attr_count):
            pos += 2
            attr_len = struct.unpack('>I', data[pos:pos+4])[0]
            pos += 4 + attr_len
    method_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    methods = []
    for _ in range(method_count):
        access, name_idx, desc_idx, attr_count = struct.unpack('>HHHH', data[pos:pos+8])
        pos += 8
        for _ in range(attr_count):
            aname_idx = struct.unpack('>H', data[pos:pos+2])[0]
            attr_len = struct.unpack('>I', data[pos+2:pos+6])[0]
            attr_start = pos + 6
            if aname_idx in cp_strings and cp_strings[aname_idx] == 'Code':
                # Code attribute: max_stack(2)+max_locals(2)+code_length(4)+[code]
                code_length = struct.unpack('>I', data[attr_start+4:attr_start+8])[0]
                code_start = attr_start + 8
                methods.append((name_idx, desc_idx, code_start, code_length))
            pos = attr_start + attr_len
    return methods


def patch_jar(jar_path):
    print(f"[Patch] Processing: {jar_path}")
    backup = jar_path + ".bak"
    if not os.path.exists(backup):
        shutil.copy2(jar_path, backup)
        print(f"[Patch] Backup created: {backup}")

    entries = {}
    with zipfile.ZipFile(jar_path, 'r') as zf:
        for name in zf.namelist():
            if not name.endswith('/'):
                entries[name] = zf.read(name)

    target = 'dev/latvian/mods/kubejs/util/KubeJSPlugins.class'
    if target not in entries:
        print(f"[Patch] ERROR: {target} not found in JAR")
        return False

    data = bytearray(entries[target])
    cp_count, cp_tags, cp_strings, cp_positions, cp_end = parse_cp(data)

    # ---- Step 1: find existing ArrayList Class & <init> Methodref ----
    # Find Utf8 'java/util/ArrayList'
    arr_utf8 = find_utf8_index(cp_strings, 'java/util/ArrayList')
    arr_class = None
    arr_init_mr = None
    if arr_utf8:
        # Class entries referencing this Utf8
        arr_class = find_cp_index(cp_tags, cp_positions, 7,
                                  lambda idx, pos: struct.unpack('>H', data[pos+1:pos+3])[0] == arr_utf8)
        # Methodref with NameAndType '<init>:()V' on ArrayList class
        init_utf8 = find_utf8_index(cp_strings, '<init>')
        void_utf8 = find_utf8_index(cp_strings, '()V')
        if arr_class and init_utf8 and void_utf8:
            nat = find_cp_index(cp_tags, cp_positions, 12, lambda idx, pos:
                                struct.unpack('>H', data[pos+1:pos+3])[0] == init_utf8 and
                                struct.unpack('>H', data[pos+3:pos+5])[0] == void_utf8)
            if nat:
                arr_init_mr = find_cp_index(cp_tags, cp_positions, 10, lambda idx, pos:
                                            struct.unpack('>H', data[pos+1:pos+3])[0] == arr_class and
                                            struct.unpack('>H', data[pos+3:pos+5])[0] == nat)
    if not arr_class or not arr_init_mr:
        print("[Patch] ERROR: could not locate ArrayList class/methodref in constant pool")
        return False
    print(f"[Patch] ArrayList class cp=#{arr_class}, <init> methodref cp=#{arr_init_mr}")

    # ---- Step 2: add CopyOnWriteArrayList Class + Methodref to CP ----
    new_utf8 = find_utf8_index(cp_strings, NEW_CLASS)
    if new_utf8 is None:
        # append Utf8
        b = NEW_CLASS.encode('utf-8')
        new_entry = b'\x01' + struct.pack('>H', len(b)) + b
        data[cp_end:cp_end] = new_entry
        cp_end += len(new_entry)
        new_utf8 = cp_count
        cp_count += 1
        print(f"[Patch] Added Utf8 #{new_utf8}: {NEW_CLASS}")
    else:
        print(f"[Patch] Reuse Utf8 #{new_utf8}: {NEW_CLASS}")

    # Class entry
    new_class = None
    for idx in sorted(cp_tags):
        if cp_tags[idx] == 7 and struct.unpack('>H', data[cp_positions[idx]+1:cp_positions[idx]+3])[0] == new_utf8:
            new_class = idx
            break
    if new_class is None:
        new_entry = b'\x07' + struct.pack('>H', new_utf8)
        data[cp_end:cp_end] = new_entry
        cp_end += len(new_entry)
        new_class = cp_count
        cp_count += 1
        print(f"[Patch] Added Class #{new_class}: {NEW_CLASS}")

    # '<init>' and '()V' Utf8 - reuse existing
    init_utf8 = find_utf8_index(cp_strings, '<init>')
    void_utf8 = find_utf8_index(cp_strings, '()V')
    if init_utf8 is None or void_utf8 is None:
        print("[Patch] ERROR: <init> or ()V Utf8 missing")
        return False

    # NameAndType '<init>:()V'
    new_nat = None
    for idx in sorted(cp_tags):
        if cp_tags[idx] == 12:
            pos = cp_positions[idx]
            if struct.unpack('>H', data[pos+1:pos+3])[0] == init_utf8 and \
               struct.unpack('>H', data[pos+3:pos+5])[0] == void_utf8:
                new_nat = idx
                break
    if new_nat is None:
        new_entry = b'\x0c' + struct.pack('>H', init_utf8) + struct.pack('>H', void_utf8)
        data[cp_end:cp_end] = new_entry
        cp_end += len(new_entry)
        new_nat = cp_count
        cp_count += 1
        print(f"[Patch] Added NameAndType #{new_nat}: <init>:()V")

    # Methodref CopyOnWriteArrayList.<init>:()V
    new_mr = None
    for idx in sorted(cp_tags):
        if cp_tags[idx] == 10:
            pos = cp_positions[idx]
            if struct.unpack('>H', data[pos+1:pos+3])[0] == new_class and \
               struct.unpack('>H', data[pos+3:pos+5])[0] == new_nat:
                new_mr = idx
                break
    if new_mr is None:
        new_entry = b'\x0a' + struct.pack('>H', new_class) + struct.pack('>H', new_nat)
        data[cp_end:cp_end] = new_entry
        cp_end += len(new_entry)
        new_mr = cp_count
        cp_count += 1
        print(f"[Patch] Added Methodref #{new_mr}: CopyOnWriteArrayList.<init>")

    # Update constant_pool_count
    data[8:10] = struct.pack('>H', cp_count)

    # ---- Step 3: locate <clinit> and patch first new+invokespecial ----
    methods = parse_methods(data, cp_end, cp_strings)
    clinit_name_idx = find_utf8_index(cp_strings, '<clinit>')
    clinit = None
    for name_idx, desc_idx, code_start, code_length in methods:
        if name_idx == clinit_name_idx:
            clinit = (code_start, code_length)
            break
    if clinit is None:
        print("[Patch] ERROR: <clinit> not found")
        return False
    code_start, code_length = clinit
    print(f"[Patch] <clinit> code at {code_start}, len {code_length}")

    code = data[code_start:code_start+code_length]
    # Idempotency check: if the first `new` in <clinit> is no longer ArrayList,
    # the patch was already applied - exit cleanly.
    first_new_idx = None
    for k in range(0, code_length - 2):
        if code[k] == 0xBB:
            first_new_idx = struct.unpack('>H', code[k+1:k+3])[0]
            break
    if first_new_idx is None:
        print("[Patch] ERROR: no `new` instruction in <clinit>")
        return False
    if first_new_idx != arr_class:
        print(f"[Patch] Already patched (first new cp=#{first_new_idx} != ArrayList #{arr_class}), skip")
        return True

    # find first `new` (0xBB) with index == arr_class
    patched_new = False
    patched_init = False
    i = 0
    while i < code_length - 2:
        op = code[i]
        if op == 0xBB and not patched_new:
            idx = struct.unpack('>H', code[i+1:i+3])[0]
            if idx == arr_class:
                data[code_start+i+1:code_start+i+3] = struct.pack('>H', new_class)
                print(f"[Patch] new ArrayList -> new CopyOnWriteArrayList (offset {i})")
                patched_new = True
                i += 3
                continue
        elif op == 0xB7 and not patched_init:
            idx = struct.unpack('>H', code[i+1:i+3])[0]
            if idx == arr_init_mr:
                data[code_start+i+1:code_start+i+3] = struct.pack('>H', new_mr)
                print(f"[Patch] invokespecial ArrayList.<init> -> CopyOnWriteArrayList.<init> (offset {i})")
                patched_init = True
                i += 3
                continue
        # skip instruction
        if op >= 0x00 and op <= 0x0f:  # nop..aconst_null, ldc is 0x12
            i += 1
        elif op == 0x10 or op == 0x11:  # bipush, sipush
            i += 2 if op == 0x10 else 3
        elif op == 0x12:  # ldc
            i += 2
        elif op == 0x13 or op == 0x14:  # ldc_w, ldc2_w
            i += 3
        elif op == 0x15 or op == 0x16 or op == 0x17 or op == 0x18:  # iload..aload
            i += 2
        elif op >= 0x19 and op <= 0x2a:  # iload_0..aload_3 (0-args)
            i += 1
        elif op == 0x32 or op == 0x33 or op == 0x34 or op == 0x35 or op == 0x36:  # iastore..aastore
            i += 1
        elif op >= 0x3b and op <= 0x51:  # istore..astore (with index) 0x3b-0x43, and xstore_0..3 0x43-0x4f... keep simple
            i += 2 if op <= 0x42 else 1
        elif op == 0x84:  # iinc
            i += 3
        elif op >= 0x99 and op <= 0xa8:  # if<cond>
            i += 3
        elif op == 0xa7 or op == 0xa8:  # goto, jsr
            i += 3
        elif op == 0xa9:  # ret
            i += 2
        elif op >= 0xac and op <= 0xb1:  # ireturn..return
            i += 1
        elif op == 0xb2 or op == 0xb3:  # getstatic/putstatic
            i += 3
        elif op == 0xb4 or op == 0xb5:  # getfield/putfield
            i += 3
        elif op == 0xb6 or op == 0xb7 or op == 0xb8:  # invokevirtual/invokespecial/invokestatic
            i += 3
        elif op == 0xb9:  # invokeinterface
            i += 5
        elif op == 0xba:  # invokedynamic
            i += 5
        elif op == 0xbb:  # new
            i += 3
        elif op == 0xbc:  # newarray
            i += 2
        elif op == 0xbd:  # anewarray
            i += 3
        elif op == 0xbe or op == 0xbf or op == 0xc0 or op == 0xc1:  # arraylength/athrow/checkcast/instanceof
            i += 2 if op == 0xc0 or op == 0xc1 else 1
        elif op == 0xc2 or op == 0xc3:  # monitorenter/monitorexit
            i += 1
        elif op == 0xc4:  # wide
            i += 4
        elif op == 0xc5:  # multianewarray
            i += 4
        elif op >= 0xc6 and op <= 0xc9:  # ifnull..if_acmpne
            i += 3
        elif op == 0xca:  # breakpoint
            i += 1
        else:
            # fallback: assume 1 byte; if patched both, stop scanning
            i += 1
        if patched_new and patched_init:
            break

    if not (patched_new and patched_init):
        print(f"[Patch] ERROR: incomplete patch (new={patched_new}, init={patched_init})")
        return False

    # ---- Step 4: write back ----
    entries[target] = bytes(data)
    with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for name, content in entries.items():
            zf.writestr(name, content)
    print(f"[Patch] OK - KubeJSPlugins.LIST now uses CopyOnWriteArrayList ({target})")
    return True


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python patch_kubejs_jar.py <kubejs-forge-*.jar>")
        sys.exit(1)
    ok = patch_jar(sys.argv[1])
    sys.exit(0 if ok else 1)
