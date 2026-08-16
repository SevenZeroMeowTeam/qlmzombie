import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * CraftingDeadAgent - Java Agent to patch disableSaving() at runtime.
 * 
 * This agent intercepts the RegistryBuilder.disableSaving() method
 * and makes it a no-op, preventing the DFU codec registration issue.
 * 
 * Usage: -javaagent:craftingdead-agent.jar
 */
public class CraftingDeadAgent {
    
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[CraftingDeadAgent] Agent loaded - patching disableSaving()...");
        inst.addTransformer(new DisableSavingTransformer());
    }
    
    public static class DisableSavingTransformer implements ClassFileTransformer {
        
        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) {
            
            // Target: net.minecraftforge.registries.RegistryBuilder
            if ("net/minecraftforge/registries/RegistryBuilder".equals(className)) {
                System.out.println("[CraftingDeadAgent] Found RegistryBuilder class, patching...");
                return patchDisableSaving(classfileBuffer);
            }
            
            return null;
        }
        
        /**
         * Patch the RegistryBuilder class to make disableSaving() a no-op.
         * 
         * The disableSaving() method is typically:
         *   public RegistryBuilder<T> disableSaving() {
         *       this.disableSaving = true;
         *       return this;
         *   }
         * 
         * We need to replace the method body with just 'return this;'
         * to keep the method chaining working but skip the disable flag.
         */
        private byte[] patchDisableSaving(byte[] classBytes) {
            try {
                // Use simple bytecode manipulation
                // We need to find the disableSaving() method and modify it
                
                // The method: public RegistryBuilder<T> disableSaving() { ... }
                // Returns: RegistryBuilder (same as 'this')
                
                // Strategy: Find the method and replace its body
                // Since we can't easily parse Java bytecode without ASM,
                // let's use a simpler approach: find the method in the
                // constant pool and modify the bytecode
                
                System.out.println("[CraftingDeadAgent] Patching RegistryBuilder.disableSaving()...");
                
                // Find "disableSaving" in the constant pool
                int cpOffset = 8; // After magic(4) + minor(2) + major(2)
                int cpCount = readUnsignedShort(classBytes, 8);
                
                // Build constant pool string table
                java.util.HashMap<Integer, String> stringTable = new java.util.HashMap<>();
                int[] stringOffsets = new int[cpCount];
                
                int pos = cpOffset;
                for (int i = 1; i < cpCount; i++) {
                    int tag = classBytes[pos] & 0xFF;
                    stringOffsets[i] = pos;
                    
                    switch (tag) {
                        case 1: // Utf8
                            int len = readUnsignedShort(classBytes, pos + 1);
                            String s = new String(classBytes, pos + 3, len, "UTF-8");
                            stringTable.put(i, s);
                            pos += 3 + len;
                            break;
                        case 7: case 8: case 16: case 19: case 20:
                            pos += 3; break;
                        case 9: case 10: case 11: case 12:
                        case 18: case 19: // Note: 19 is Module, handled above
                            pos += 5; break;
                        case 3: case 4:
                            pos += 5; break;
                        case 5: case 6:
                            pos += 9; i++; break;
                        case 13: case 14:
                            pos += 4; break;
                        case 15: case 17:
                            pos += 5; break;
                        default:
                            System.out.println("[CraftingDeadAgent] Unknown constant pool tag: " + tag + " at index " + i);
                            return classBytes; // Don't modify if we can't parse
                    }
                }
                
                // Find the disableSaving method
                // Look for methods that reference "disableSaving"
                boolean patched = false;
                
                // We need to scan the methods section
                pos = findMethodsSection(classBytes, stringOffsets, cpCount);
                if (pos < 0) {
                    System.out.println("[CraftingDeadAgent] Could not find methods section");
                    return classBytes;
                }
                
                int methodCount = readUnsignedShort(classBytes, pos);
                pos += 2;
                
                for (int m = 0; m < methodCount; m++) {
                    int methodStart = pos;
                    int accessFlags = readUnsignedShort(classBytes, pos);
                    pos += 2;
                    int nameIndex = readUnsignedShort(classBytes, pos);
                    pos += 2;
                    int descriptorIndex = readUnsignedShort(classBytes, pos);
                    pos += 2;
                    
                    String methodName = stringTable.get(nameIndex);
                    
                    // Skip attributes
                    int attrCount = readUnsignedShort(classBytes, pos);
                    pos += 2;
                    for (int a = 0; a < attrCount; a++) {
                        int attrNameIndex = readUnsignedShort(classBytes, pos);
                        long attrLen = readUnsignedInt(classBytes, pos + 2);
                        pos += 6 + attrLen;
                    }
                    
                    if ("disableSaving".equals(methodName)) {
                        System.out.println("[CraftingDeadAgent] Found disableSaving method at offset " + methodStart);
                        
                        // To be safe, we need to modify the method body
                        // But without ASM, we'll use a different strategy:
                        // We'll modify the constant pool to change the behavior
                        
                        // Actually, the safest approach is to modify the method's Code attribute
                        // to just return 'this' (aload_0 + areturn)
                        
                        // Let's find the Code attribute and replace the bytecode
                        pos = methodStart + 6; // Skip access_flags + name + descriptor
                        attrCount = readUnsignedShort(classBytes, pos);
                        pos += 2;
                        
                        for (int a = 0; a < attrCount; a++) {
                            int attrNameIndex = readUnsignedShort(classBytes, pos);
                            String attrName = stringTable.get(attrNameIndex);
                            long attrLen = readUnsignedInt(classBytes, pos + 2);
                            
                            if ("Code".equals(attrName)) {
                                // Found Code attribute - replace its bytecode
                                int codeStart = pos + 6; // After name_index + length + max_stack + max_locals
                                int newCodeLen = 2; // aload_0 (0x2A) + areturn (0xB0)
                                
                                // Modify the code_length field
                                writeUnsignedInt(classBytes, codeStart - 2, newCodeLen);
                                
                                // Write new bytecode: aload_0 + areturn
                                classBytes[codeStart] = 0x2A; // aload_0
                                classBytes[codeStart + 1] = 0xB0; // areturn
                                
                                patched = true;
                                System.out.println("[CraftingDeadAgent] ✅ Patched disableSaving() to aload_0 + areturn");
                                
                                // Need to update code attribute length in the stack map table
                                // This is complex without ASM, so we just fix the basic case
                                break;
                            }
                            
                            pos += 6 + attrLen;
                        }
                        
                        break;
                    }
                }
                
                if (!patched) {
                    System.out.println("[CraftingDeadAgent] ⚠️  disableSaving method not found or already patched");
                }
                
                return classBytes;
                
            } catch (Exception e) {
                System.out.println("[CraftingDeadAgent] ERROR: " + e.getMessage());
                e.printStackTrace();
                return classBytes;
            }
        }
        
        private int findMethodsSection(byte[] classBytes, int[] stringOffsets, int cpCount) {
            try {
                int pos = 8; // After magic + version
                int cpCountLocal = readUnsignedShort(classBytes, 8);
                
                // Skip constant pool
                pos = skipConstantPool(classBytes, pos, cpCountLocal);
                
                // Skip access_flags, this_class, super_class
                pos += 6;
                
                // Skip interfaces
                int ifaceCount = readUnsignedShort(classBytes, pos);
                pos += 2 + ifaceCount * 2;
                
                // Skip fields
                int fieldCount = readUnsignedShort(classBytes, pos);
                pos += 2;
                for (int f = 0; f < fieldCount; f++) {
                    pos += 6; // access_flags + name + descriptor
                    int attrCount = readUnsignedShort(classBytes, pos);
                    pos += 2;
                    for (int a = 0; a < attrCount; a++) {
                        long attrLen = readUnsignedInt(classBytes, pos + 2);
                        pos += 6 + attrLen;
                    }
                }
                
                return pos;
            } catch (Exception e) {
                return -1;
            }
        }
        
        private int skipConstantPool(byte[] classBytes, int start, int count) {
            int pos = start;
            for (int i = 1; i < count; i++) {
                int tag = classBytes[pos] & 0xFF;
                switch (tag) {
                    case 1:
                        int len = readUnsignedShort(classBytes, pos + 1);
                        pos += 3 + len;
                        break;
                    case 7: case 8: case 16: case 19: case 20:
                        pos += 3; break;
                    case 9: case 10: case 11: case 12:
                    case 18:
                        pos += 5; break;
                    case 3: case 4:
                        pos += 5; break;
                    case 5: case 6:
                        pos += 9; i++; break;
                    case 13: case 14:
                        pos += 4; break;
                    case 15: case 17:
                        pos += 5; break;
                    default:
                        pos += 3; // Assume simple entry
                }
            }
            return pos;
        }
        
        private int readUnsignedShort(byte[] data, int offset) {
            return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        }
        
        private long readUnsignedInt(byte[] data, int offset) {
            return ((long)(data[offset] & 0xFF) << 24) |
                   ((long)(data[offset + 1] & 0xFF) << 16) |
                   ((long)(data[offset + 2] & 0xFF) << 8) |
                   ((long)(data[offset + 3] & 0xFF));
        }
        
        private void writeUnsignedInt(byte[] data, int offset, int value) {
            data[offset] = (byte)((value >> 24) & 0xFF);
            data[offset + 1] = (byte)((value >> 16) & 0xFF);
            data[offset + 2] = (byte)((value >> 8) & 0xFF);
            data[offset + 3] = (byte)(value & 0xFF);
        }
    }
}
