#!/bin/bash
# patch_cd_jar.sh - Patch CraftingDead JAR to remove disableSaving() call
# Uses javassist for reliable bytecode manipulation
#
# Usage: patch_cd_jar.sh <craftingdead.jar>
# Run in Docker container during entrypoint

set -e

JAR_PATH="$1"
if [ -z "$JAR_PATH" ]; then
  echo "[Patch] Usage: $0 <craftingdead.jar>"
  exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
  echo "[Patch] ERROR: $JAR_PATH not found"
  exit 1
fi

# Backup
BACKUP="${JAR_PATH}.bak"
if [ ! -f "$BACKUP" ]; then
  cp "$JAR_PATH" "$BACKUP"
  echo "[Patch] Backup: $BACKUP"
fi

# Download javassist if not present
JAVASSIST_JAR="/tmp/javassist.jar"
JAVASSIST_URL="https://repo1.maven.org/maven2/org/javassist/javassist/3.30.2-GA/javassist-3.30.2-GA.jar"

if [ ! -f "$JAVASSIST_JAR" ]; then
  echo "[Patch] Downloading javassist..."
  if command -v curl >/dev/null 2>&1; then
    curl -sL -o "$JAVASSIST_JAR" "$JAVASSIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$JAVASSIST_JAR" "$JAVASSIST_URL"
  else
    echo "[Patch] ERROR: Neither curl nor wget available"
    exit 1
  fi
  echo "[Patch] javassist downloaded: $(wc -c < "$JAVASSIST_JAR") bytes"
fi

# Create patcher Java source
PATCHER_SRC="/tmp/PatchCraftingDead.java"
cat > "$PATCHER_SRC" << 'JAVAEOF'
import javassist.*;
import javassist.expr.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class PatchCraftingDead {
    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        System.out.println("[Patch] Processing: " + jarPath);

        // Read JAR
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarPath))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), jis.readAllBytes());
                }
            }
        }

        String target = "com/craftingdead/core/world/item/gun/GunConfigurations.class";
        byte[] classBytes = entries.get(target);
        if (classBytes == null) {
            System.out.println("[Patch] ERROR: " + target + " not found");
            System.exit(1);
        }

        System.out.println("[Patch] Found " + target + " (" + classBytes.length + " bytes)");

        // Use javassist to patch
        ClassPool pool = new ClassPool(false);
        pool.appendSystemPath();

        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classBytes));
        boolean patched = false;

        CtMethod[] methods = ctClass.getDeclaredMethods();
        for (CtMethod method : methods) {
            try {
                method.instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("disableSaving")) {
                            System.out.println("[Patch]   Replacing disableSaving() in " + m.where());
                            m.replace("$_ = $0;");
                        }
                    }
                });
            } catch (CannotCompileException e) {
                System.out.println("[Patch]   Warning: " + e.getMessage());
            }
        }

        // Also check constructors
        CtConstructor[] constructors = ctClass.getDeclaredConstructors();
        for (CtConstructor constructor : constructors) {
            try {
                constructor.instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("disableSaving")) {
                            System.out.println("[Patch]   Replacing disableSaving() in constructor " + m.where());
                            m.replace("$_ = $0;");
                        }
                    }
                });
            } catch (CannotCompileException e) {
                System.out.println("[Patch]   Warning: " + e.getMessage());
            }
        }

        byte[] patchedBytes = ctClass.toBytecode();
        entries.put(target, patchedBytes);
        System.out.println("[Patch] Patched: " + patchedBytes.length + " bytes (was " + classBytes.length + ")");

        // Write patched JAR
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                JarEntry je = new JarEntry(e.getKey());
                je.setTime(System.currentTimeMillis());
                jos.putNextEntry(je);
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }

        System.out.println("[Patch] ✅ Successfully patched and repackaged: " + jarPath);
    }
}
JAVAEOF

# Compile patcher
PATCHER_CLASS="/tmp/PatchCraftingDead.class"
echo "[Patch] Compiling patcher..."
javac -cp "$JAVASSIST_JAR" -d /tmp "$PATCHER_SRC" 2>&1

# Run patcher
echo "[Patch] Running patcher..."
java -cp "/tmp:$JAVASSIST_JAR" PatchCraftingDead "$JAR_PATH" 2>&1

echo "[Patch] Done."
