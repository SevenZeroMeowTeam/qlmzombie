#!/usr/bin/env python3
"""
分析 CraftingDead JAR 中 GunConfigurations.class 的常量池
查找 disableSaving 字符串是否存在
"""
import struct
import sys
import zipfile
import os

def analyze_constant_pool(data):
    """解析 class 文件的常量池，返回所有字符串和它们的位置"""
    strings = {}
    cp_count = struct.unpack('>H', data[8:10])[0]
    
    i = 10
    idx = 1
    while idx < cp_count and i < len(data):
        tag = data[i]
        
        if tag == 1:  # Utf8
            length = struct.unpack('>H', data[i+1:i+3])[0]
            s = data[i+3:i+3+length].decode('utf-8', errors='replace')
            strings[idx] = s
            i += 3 + length
            idx += 1
        elif tag == 7:   # Class
            i += 3
            idx += 1
        elif tag in (9, 10, 11):  # Fieldref, Methodref, InterfaceMethodref
            i += 5
            idx += 1
        elif tag == 8:   # String
            i += 3
            idx += 1
        elif tag in (3, 4):  # Integer, Float
            i += 5
            idx += 1
        elif tag in (5, 6):  # Long, Double
            i += 9
            idx += 2
        elif tag == 12:  # NameAndType
            i += 5
            idx += 1
        elif tag == 13:  # MethodHandle
            i += 4
            idx += 1
        elif tag == 14:  # MethodType
            i += 3
            idx += 1
        elif tag in (15, 16):  # MethodHandle/MetaMethodHandle etc
            if tag == 15:
                i += 4
            else:
                i += 3
            idx += 1
        elif tag in (17, 18):  # Dynamic, InvokeDynamic
            i += 5
            idx += 1
        elif tag in (19, 20):  # Module, Package
            i += 3
            idx += 1
        elif tag == 21:  # Record
            i += 3
            idx += 1
        elif tag == 22:  # Annotation
            i += 4
            idx += 1
        else:
            print(f"  Unknown tag {tag} at index {idx}, stopping CP parse")
            break
    
    return strings

def find_disablesaving_in_jar(jar_path):
    """在 JAR 中查找 GunConfigurations.class 并分析其常量池"""
    target_class = 'com/craftingdead/core/world/item/gun/GunConfigurations.class'
    
    print(f"\n{'='*60}")
    print(f"分析 JAR: {os.path.basename(jar_path)}")
    print(f"{'='*60}")
    
    try:
        with zipfile.ZipFile(jar_path, 'r') as zf:
            # 列出所有匹配的 class 文件
            gun_config_entries = [e for e in zf.namelist() 
                                  if 'GunConfigurations' in e and e.endswith('.class')]
            print(f"找到 {len(gun_config_entries)} 个 GunConfigurations 类文件:")
            for entry in gun_config_entries:
                print(f"  - {entry}")
            
            if target_class not in zf.namelist():
                print(f"\n❌ 未找到目标类: {target_class}")
                return False
            
            data = zf.read(target_class)
            print(f"\n✅ 找到目标类: {target_class} ({len(data)} 字节)")
            
            strings = analyze_constant_pool(data)
            print(f"\n常量池中共 {len(strings)} 个字符串:")
            
            # 查找 disableSaving 相关字符串
            disable_saving_indices = []
            gun_config_indices = []
            
            for idx, s in strings.items():
                if 'disable' in s.lower() or 'saving' in s.lower():
                    print(f"  [{idx}] Utf8: '{s}'")
                    if 'disableSaving' in s or 'disable_saving' in s.lower():
                        disable_saving_indices.append(idx)
                if 'GunConfigurations' in s:
                    gun_config_indices.append(idx)
            
            if not disable_saving_indices:
                print("\n⚠️  未找到 'disableSaving' 相关字符串！")
                print("   这可能意味着:")
                print("   1. JAR 使用了不同的方法名 (如 obfuscated)")
                print("   2. JAR 已经被补丁过")
                print("   3. 方法来自父类或接口")
            
            # 查找 invokevirtual 指令引用的方法
            print(f"\n{'='*60}")
            print("查找字节码中的方法调用:")
            
            # 扫描整个 class 文件查找字节码模式
            # invokevirtual = 0xB6, 后跟 2 字节常量池索引
            invokevirtual_count = 0
            for i in range(len(data) - 2):
                if data[i] == 0xB6:  # invokevirtual
                    cp_index = struct.unpack('>H', data[i+1:i+3])[0]
                    invokevirtual_count += 1
                    if cp_index in strings:
                        method_name = strings[cp_index]
                        if 'disable' in method_name.lower() or 'saving' in method_name.lower():
                            print(f"  在偏移 {i}: invokevirtual -> [{cp_index}] '{method_name}'")
            
            print(f"\n共找到 {invokevirtual_count} 个 invokevirtual 指令")
            
            # 打印所有唯一的方法名（来自 invokevirtual 引用）
            referenced_methods = set()
            for i in range(len(data) - 2):
                if data[i] == 0xB6:
                    cp_index = struct.unpack('>H', data[i+1:i+3])[0]
                    if cp_index in strings:
                        referenced_methods.add(strings[cp_index])
            
            print(f"\ninvokevirtual 引用的所有唯一方法名:")
            for method in sorted(referenced_methods):
                marker = " ⚠️" if 'disable' in method.lower() else ""
                print(f"  - {method}{marker}")
            
            return len(disable_saving_indices) > 0 or invokevirtual_count > 0
            
    except Exception as e:
        print(f"❌ 分析失败: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == '__main__':
    mods_dir = sys.argv[1] if len(sys.argv) > 1 else '/data/mods'
    
    # 查找所有 crafting-dead JAR
    import glob
    pattern = os.path.join(mods_dir, 'crafting-dead*core*.jar')
    jars = glob.glob(pattern)
    
    if not jars:
        print(f"在 {mods_dir} 中未找到 crafting-dead JAR")
        sys.exit(1)
    
    for jar in jars:
        find_disablesaving_in_jar(jar)
