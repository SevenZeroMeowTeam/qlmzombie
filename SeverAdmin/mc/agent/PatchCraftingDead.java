import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.analysis.*;
import javassist.expr.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class PatchCraftingDead {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java PatchCraftingDead <craftingdead.jar>");
            System.exit(1);
        }

        String jarPath = args[0];
        String backupPath = jarPath + ".bak";

        // Backup
        File jarFile = new File(jarPath);
        File backupFile = new File(backupPath);
        if (!backupFile.exists()) {
            try (InputStream is = new FileInputStream(jarFile);
                 OutputStream os = new FileOutputStream(backupFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            }
            System.out.println("[Patch] Backup created: " + backupPath);
        }

        // Open JAR
        JarInputStream jis = new JarInputStream(new FileInputStream(jarPath));
        Map<String, byte[]> entries = new LinkedHashMap<>();

        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.isDirectory()) continue;
            byte[] data = jis.readAllBytes();
            entries.put(entry.getName(), data);
        }
        jis.close();

        System.out.println("[Patch] Loaded " + entries.size() + " entries from JAR");

        // Find and patch GunConfigurations.class
        String targetClass = "com/craftingdead/core/world/item/gun/GunConfigurations.class";
        byte[] classBytes = entries.get(targetClass);

        if (classBytes == null) {
            System.out.println("[Patch] ERROR: " + targetClass + " not found in JAR");
            System.exit(1);
        }

        System.out.println("[Patch] Found " + targetClass + " (" + classBytes.length + " bytes)");

        // Use javassist to modify the class
        ClassPool pool = new ClassPool(true);
        pool.appendSystemPath();

        // We need to use a CodeEditor to modify the bytecode
        // Strategy: find the method that calls disableSaving() and remove it

        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));

        // Find the method that contains disableSaving() call
        CtMethod[] methods = ctClass.getDeclaredMethods();
        for (CtMethod method : methods) {
            String body = method.getBody();
            if (body != null && body.contains("disableSaving")) {
                System.out.println("[Patch] Found disableSaving() in method: " + method.getName());
                // Log the method body
                System.out.println("[Patch]   Body: " + body.substring(0, Math.min(body.length(), 200)));
            }
        }

        // Try to make a CtBehavior that removes the disableSaving call
        // Alternative: use bytecode-level manipulation

        // Simple approach: use MethodEditor to replace the disableSaving() call
        // with no-op

        // Actually, let's use the ConstPool and modify the bytecode directly
        ClassFile classFile = new ClassFile(classBytes);
        ConstPool constPool = classFile.getConstPool();

        // Find all occurrences of "disableSaving" in the constant pool
        int cpIndex;
        List<Integer> disableSavingIndices = new ArrayList<>();
        for (int i = 1; i < constPool.getSize(); i++) {
            int tag = constPool.getTag(i);
            if (tag == ConstPool.Utf8) {
                String s = constPool.getString(i);
                if (s != null && s.equals("disableSaving")) {
                    disableSavingIndices.add(i);
                }
            }
        }

        System.out.println("[Patch] Found 'disableSaving' at constant pool indices: " + disableSavingIndices);

        if (disableSavingIndices.isEmpty()) {
            System.out.println("[Patch] No disableSaving references found - JAR might already be patched");
            System.exit(0);
        }

        // Find the method that references disableSaving
        // and modify the bytecode to skip the call

        // Get all methods
        MethodInfo[] methodInfos = classFile.getMethods();
        boolean patched = false;

        for (MethodInfo methodInfo : methodInfos) {
            CodeAttribute codeAttr = methodInfo.getCodeAttribute();
            if (codeAttr == null) continue;

            byte[] code = codeAttr.getCode();
            int maxLen = code.length;

            // Scan for invokevirtual (0xB6) + 2-byte index
            // Check if the index references disableSaving
            for (int i = 0; i < maxLen - 2; i++) {
                if (code[i] == 0xB6) { // invokevirtual
                    int cpIndexRef = (code[i+1] << 8) | (code[i+2] & 0xFF);
                    // Check if this index or its chain references disableSaving
                    if (isDisableSavingRef(constPool, cpIndexRef, disableSavingIndices)) {
                        System.out.println("[Patch] Found invokevirtual disableSaving at offset " + i
                            + " in method " + methodInfo.getName());
                        // Replace with aconst_null + pop (3 bytes: 0x01 + 0x00 + 0x57)
                        // But this changes the code length and breaks everything
                        // Instead, let's find a different approach
                    }
                }
            }
        }

        // Alternative approach: modify the CONSTANT POOL to change the
        // disableSaving method reference to point to a different method
        // that does nothing (like getClass() or hashCode())

        // Simplest approach: find the methodref in the constant pool
        // that points to disableSaving, and change it to point to getClass
        // or another harmless method

        // Actually, the SIMPLEST approach is to just modify the
        // RegistryBuilder.disableSaving() method to be a no-op
        // But we don't have access to that class in the JAR

        // Let's try a different approach: modify the GunConfigurations
        // class to NOT call disableSaving at all

        // The disableSaving call is in a lambda that creates the registry
        // We need to find the lambda method and remove the call

        // Use javassist's ExprEditor to find and replace the call
        CtClass ctClass2 = pool.makeClass(new ByteArrayInputStream(classBytes));
        CtMethod[] methods2 = ctClass2.getDeclaredMethods();

        for (CtMethod method : methods2) {
            try {
                method.instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("disableSaving")) {
                            System.out.println("[Patch] Replacing disableSaving() call in " + m.where());
                            // Replace with: $_ = $0 (just return the receiver)
                            m.replace("$_ = $0;");
                        }
                    }
                });
                System.out.println("[Patch] Instrumented method: " + method.getName());
            } catch (CannotCompileException e) {
                System.out.println("[Patch] Warning: Could not instrument " + method.getName() + ": " + e.getMessage());
            }
        }

        // Get the modified bytecode
        byte[] patchedBytes = ctClass2.toBytecode();
        entries.put(targetClass, patchedBytes);
        System.out.println("[Patch] Patched class: " + patchedBytes.length + " bytes (was " + classBytes.length + ")");

        // Write patched JAR
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath))) {
            for (Map.Entry<String, byte[]> entry2 : entries.entrySet()) {
                JarEntry je = new JarEntry(entry2.getKey());
                je.setTime(System.currentTimeMillis());
                jos.putNextEntry(je);
                jos.write(entry2.getValue());
                jos.closeEntry();
            }
        }

        System.out.println("[Patch] ✅ Patched JAR written: " + jarPath);
        System.out.println("[Patch] Original backup: " + backupPath);
    }

    private static boolean isDisableSavingRef(ConstPool constPool, int cpIndex, List<Integer> targetIndices) {
        // Follow the constant pool chain to find if cpIndex references disableSaving
        int tag = constPool.getTag(cpIndex);

        // Methodref (0x0a) or InterfaceMethodref (0x09)
        if (tag == ConstPool.Methodref || tag == ConstPool.InterfaceMethodref) {
            // Methodref has: class_index (2) + name_and_type_index (2)
            int nameAndTypeIndex = constPool.getNameAndTypeIndex(cpIndex);
            int natTag = constPool.getTag(nameAndTypeIndex);
            if (natTag == ConstPool.NameAndType) {
                int nameIndex = constPool.getNameAndTypeName(nameAndTypeIndex);
                if (targetIndices.contains(nameIndex)) {
                    return true;
                }
            }
        }

        // NameAndType (0x0c)
        if (tag == ConstPool.NameAndType) {
            int nameIndex = constPool.getNameAndTypeName(cpIndex);
            if (targetIndices.contains(nameIndex)) {
                return true;
            }
        }

        return false;
    }
}
