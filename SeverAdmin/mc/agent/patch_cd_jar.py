#!/usr/bin/env python3
"""
Patch CraftingDead JAR - Direct binary bytecode patch.

Finds the invokevirtual call to RegistryBuilder.disableSaving()
in GunConfigurations.class and replaces it with a no-op sequence
that keeps the JVM stack balanced.

Java bytecode opcodes used:
  aconst_null = 0x01  (push null onto stack)
  pop         = 0x57  (pop top value from stack)
  nop         = 0x00  (no operation)
  
  invokevirtual = 0xB6 (3 bytes: opcode + 2-byte cp index)

The patch replaces:  [0xB6] [idx_hi] [idx_lo]  (3 bytes)
              with:  [0x01] [0x57] [0x00]  (3 bytes, stack-neutral)

This is safe because disableSaving() returns RegistryBuilder (this),
and the return value is used for method chaining. By replacing
the call with aconst_null+pop+nop, we discard the return value
(null) and break the chain - but since disableSaving() was the
last call in the chain (before makeRegistry()), this is fine.
"""

import sys
import os
import zipfile
import struct
import shutil

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
    
    target = 'com/craftingdead/core/world/item/gun/GunConfigurations.class'
    if target not in entries:
        print(f"[Patch] ERROR: {target} not found in JAR")
        return False
    
    data = bytearray(entries[target])
    original_len = len(data)
    print(f"[Patch] Found {target} ({original_len} bytes)")
    
    # Step 1: Parse constant pool to find "disableSaving" Utf8 entries
    cp_count = struct.unpack('>H', data[8:10])[0]
    
    # Build a map: cp_index -> string value (for Utf8 entries only)
    # Also build: cp_index -> tag (for all entries)
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
        elif tag == 7:   # Class
            i += 3
            idx += 1
        elif tag in (9, 10, 11):  # Fieldref, Methodref, InterfaceMethodref
            i += 5
            idx += 1  # 1 slot (只有 Long/Double 占 2 slots)
        elif tag == 8:   # String
            i += 3
            idx += 1
        elif tag in (3, 4):  # Integer, Float
            i += 5
            idx += 1
        elif tag in (5, 6):  # Long, Double — 唯一占 2 个 slot 的常量
            i += 9
            idx += 2
        elif tag == 12:  # NameAndType
            i += 5
            idx += 1  # 1 slot
        elif tag == 13:  # MethodHandle: reference_kind(1) + reference_index(2) = 4 bytes total
            i += 4
            idx += 1
        elif tag == 14:  # MethodType: descriptor_index(2) = 3 bytes total
            i += 3
            idx += 1
        elif tag in (15, 16):  # MethodHandle(15) fallback / MethodType(16) fallback
            # 15=MethodHandle(4 bytes), 16=MethodType(3 bytes)
            if tag == 15:
                i += 4
            else:
                i += 3
            idx += 1
        elif tag in (17, 18):  # Dynamic(17), InvokeDynamic(18): bootstrap_method_attr_index(2) + name_and_type_index(2) = 5 bytes
            i += 5
            idx += 1  # 1 slot (不是 2！ Long/Double 才占 2 个 slot)
        elif tag in (19, 20):  # Module, Package: name_index(2) = 3 bytes
            i += 3
            idx += 1
        else:
            print(f"[Patch] Unknown tag {tag} at index {idx}, stopping CP parse")
            break
    
    print(f"[Patch] Constant pool: {len(cp_strings)} Utf8 entries found, {len(cp_tags)} total entries")
    
    # Step 2: Find all Utf8 indices with value "disableSaving"
    ds_utf8_indices = [k for k, v in cp_strings.items() if v == 'disableSaving']
    print(f"[Patch] 'disableSaving' at Utf8 indices: {ds_utf8_indices}")
    
    # 如果找不到 disableSaving，搜索所有包含 "disable" 的字符串
    if not ds_utf8_indices:
        print("[Patch] ⚠️  未找到 'disableSaving' 字符串")
        print("[Patch] 搜索所有包含 'disable' 或 'saving' 的字符串...")
        for k, v in sorted(cp_strings.items()):
            if 'disable' in v.lower() or 'saving' in v.lower():
                print(f"  [{k}] Utf8: '{v}'")
        # 也搜索所有以大写字母开头的方法名
        print("[Patch] 搜索所有可能的方法名（以小写字母开头）...")
        method_names = set()
        for k, v in sorted(cp_strings.items()):
            if v and len(v) > 2 and v[0].islower() and any(c.isupper() for c in v):
                method_names.add(v)
        for name in sorted(method_names)[:50]:
            print(f"  - {name}")
        if len(method_names) > 50:
            print(f"  ... 还有 {len(method_names) - 50} 个")
        return False  # 返回 False 表示补丁失败
    
    # Step 3: Find NameAndType entries that reference these Utf8 indices
    # NameAndType (tag 12): name_index(2) + descriptor_index(2)
    ds_nat_indices = []
    for idx_k, pos in cp_positions.items():
        if idx_k not in cp_tags:
            continue
        if cp_tags[idx_k] == 12:  # NameAndType
            if pos + 5 > len(data):
                break
            name_index = struct.unpack('>H', data[pos+1:pos+3])[0]
            if name_index in ds_utf8_indices:
                ds_nat_indices.append(idx_k)
                print(f"[Patch] NameAndType {idx_k} references 'disableSaving' (via Utf8 {name_index})")
    
    if not ds_nat_indices:
        print("[Patch] No NameAndType references to 'disableSaving' found")
        return False
    
    # Step 4: Find Methodref entries that reference these NameAndType entries
    # Methodref (tag 10): class_index(2) + name_and_type_index(2)
    # InterfaceMethodref (tag 11): same structure
    ds_methodref_indices = []
    for idx_k, pos in cp_positions.items():
        if idx_k not in cp_tags:
            continue
        if cp_tags[idx_k] in (10, 11):  # Methodref or InterfaceMethodref
            if pos + 5 > len(data):
                break
            nat_index = struct.unpack('>H', data[pos+3:pos+5])[0]
            if nat_index in ds_nat_indices:
                ds_methodref_indices.append(idx_k)
                print(f"[Patch] Methodref {idx_k} references disableSaving (via NameAndType {nat_index})")
    
    if not ds_methodref_indices:
        print("[Patch] No Methodref to disableSaving found - bytecode pattern may differ")
        return False
    
    # Convert to set for fast lookup
    ds_mr_set = set(ds_methodref_indices)
    
    # Step 5: Find the Code attribute in the class file
    # Class file structure after constant pool:
    #   access_flags(2) + this_class(2) + super_class(2) +
    #   interfaces_count(2) + [interfaces] +
    #   fields_count(2) + [fields] +
    #   methods_count(2) + [methods]
    # Each method: access_flags(2) + name_index(2) + descriptor_index(2) +
    #   attributes_count(2) + [attributes]
    # Each attribute: attribute_name_index(2) + attribute_length(4) + [data]
    # Code attribute data: max_stack(2) + max_locals(2) + code_length(4) + [code] + ...
    
    # Find position after constant pool
    cp_end = i  # Set earlier during CP parsing
    
    # Skip class header
    pos = cp_end
    if pos + 8 > len(data):
        print("[Patch] Class file too short after constant pool")
        return False
    
    # Skip: access_flags(2) + this_class(2) + super_class(2)
    pos += 6
    
    # Skip interfaces
    if pos + 2 > len(data):
        return False
    iface_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2 + iface_count * 2
    
    # Skip fields
    if pos + 2 > len(data):
        return False
    field_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    for _ in range(field_count):
        if pos + 6 > len(data):
            break
        pos += 6  # access_flags + name_index + descriptor_index
        if pos + 2 > len(data):
            break
        attr_count = struct.unpack('>H', data[pos:pos+2])[0]
        pos += 2
        for __ in range(attr_count):
            if pos + 6 > len(data):
                break
            attr_len = struct.unpack('>I', data[pos+2:pos+6])[0]
            pos += 6 + attr_len
    
    # Now at methods section
    if pos + 2 > len(data):
        print("[Patch] Reached end of class before methods")
        return False
    
    method_count = struct.unpack('>H', data[pos:pos+2])[0]
    pos += 2
    
    print(f"[Patch] Scanning {method_count} methods for disableSaving call...")
    
    # Step 6: Scan all method Code attributes for invokevirtual to disableSaving
    patched = 0
    for _ in range(min(method_count, 1000)):  # Safety limit
        if pos + 8 > len(data):
            break
        
        method_access = struct.unpack('>H', data[pos:pos+2])[0]
        method_name_idx = struct.unpack('>H', data[pos+2:pos+4])[0]
        method_name = cp_strings.get(method_name_idx, f'idx_{method_name_idx}')
        
        pos += 6  # access_flags + name_index + descriptor_index
        
        if pos + 2 > len(data):
            break
        attr_count = struct.unpack('>H', data[pos:pos+2])[0]
        pos += 2
        
        for __ in range(attr_count):
            if pos + 6 > len(data):
                break
            
            attr_name_idx = struct.unpack('>H', data[pos:pos+2])[0]
            attr_name = cp_strings.get(attr_name_idx, f'idx_{attr_name_idx}')
            attr_len = struct.unpack('>I', data[pos+2:pos+6])[0]
            
            if attr_name == 'Code':
                # Found Code attribute
                code_data_start = pos + 6  # Start of max_stack
                if code_data_start + 8 > len(data):
                    pos += 6 + attr_len
                    continue
                
                max_stack = struct.unpack('>H', data[code_data_start:code_data_start+2])[0]
                code_start = code_data_start + 8  # After max_stack(2) + max_locals(2) + code_length(4)
                
                # Wait, the format is: max_stack(2) + max_locals(2) + code_length(4) + code(code_length)
                # So code starts at code_data_start + 2 + 2 + 4 = code_data_start + 8
                code_len = struct.unpack('>I', data[code_data_start+4:code_data_start+8])[0]
                code_end = code_start + code_len
                
                # Print method info if it has the disableSaving call
                found_in_method = False
                
                for ci in range(code_start, min(code_end, len(data) - 2)):
                    if data[ci] == 0xB6:  # invokevirtual
                        ref_idx = struct.unpack('>H', data[ci+1:ci+3])[0]
                        if ref_idx in ds_mr_set:
                            if not found_in_method:
                                print(f"[Patch] Found in method: {method_name}")
                                found_in_method = True
                            print(f"[Patch]   Patching invokevirtual #{ref_idx} at code offset {ci - code_start}")
                            
                            # Replace with stack-neutral no-op
                            # aconst_null(0x01) + pop(0x57) + nop(0x00)
                            data[ci] = 0x01
                            data[ci+1] = 0x57
                            data[ci+2] = 0x00
                            patched += 1
                
                pos = code_start + code_len
            else:
                pos += 6 + attr_len
    
    if patched == 0:
        print("[Patch] ⚠️  No invokevirtual disableSaving calls found - JAR might use a different code pattern")
        return False
    
    print(f"[Patch] ✅ Patched {patched} invokevirtual instruction(s)")
    
    # Step 7: Update the JAR
    entries[target] = bytes(data)
    with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for name, content in entries.items():
            zf.writestr(name, content)
    
    print(f"[Patch] ✅ JAR repackaged successfully: {jar_path}")
    return True


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 patch_cd_jar.py <craftingdead.jar> [craftingdead2.jar ...]")
        sys.exit(1)
    
    success = True
    for jar_path in sys.argv[1:]:
        if os.path.exists(jar_path):
            try:
                if not patch_jar(jar_path):
                    success = False
            except Exception as e:
                print(f"[Patch] ERROR processing {jar_path}: {e}")
                import traceback
                traceback.print_exc()
                success = False
        else:
            print(f"[Patch] File not found: {jar_path}")
            success = False
    
    sys.exit(0 if success else 1)
