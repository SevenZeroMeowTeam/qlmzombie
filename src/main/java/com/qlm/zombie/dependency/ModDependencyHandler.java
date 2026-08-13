package com.qlm.zombie.dependency;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModDependencyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "qlmzombie";
    public static final String DISABLED_MARKER = ".disabled";
    private static final String TRACKING_FILE = "qlmzombie_disabled_tracker.txt";

    private static final List<String> DEFAULT_DISABLED_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "thirstmod", "thirstcanteen", "thirstwastaken"
    ));

    private static final List<String> CONFLICT_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "thirstmod", "thirstcanteen", "thirstwastaken",
            "thirst", "drink", "hydration",
            "dayphase", "day-night", "day/night",
            "zombie-overhaul", "zombieapocalypse",
            "craftingdead", "crafting-dead"
    ));

    private static final List<String> KEEP_ALWAYS_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
            "kotlin", "cloth", "kubejs", "forge", "minecraft",
            "qlmzombie", "craftingdead", "dayphase",
            "lib", "library", "api", "core", "common",
            "fabric", "architectury", "puzzleslib",
            "jython", "graal", "polyglot"
    ));

    private static int releasedCount;
    private static int skippedCount;
    private static int failedCount;
    private static boolean hasConflicts;
    private static boolean hasDuplicates;
    private static List<String> disabledMods = new ArrayList<>();
    private static List<String> releasedMods = new ArrayList<>();
    private static List<String> failedMods = new ArrayList<>();
    private static List<String> skippedMods = new ArrayList<>();
    private static List<String> detectedConflicts = new ArrayList<>();
    private static List<String> deletedDuplicates = new ArrayList<>();
    private static int totalLibsCount;
    private static boolean initialized;

    private ModDependencyHandler() {
    }

    public static synchronized void initializeFromLibs() {
        if (initialized) {
            LOGGER.debug("[QLM Zombie] ModDependencyHandler 已初始化，跳过重复调用");
            return;
        }
        initialized = true;

        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path modsDir = gameDir.resolve("mods");

            LOGGER.info("[QLM Zombie] ====== 开始自动释放内部依赖模组 ======");
            LOGGER.info("[QLM Zombie] 游戏目录: {}", gameDir);

            if (!Files.exists(modsDir)) {
                Files.createDirectories(modsDir);
                LOGGER.info("[QLM Zombie] 创建 mods 目录: {}", modsDir);
            }

            Set<String> trackedDisabled = loadTrackedDisabled(gameDir);
            LOGGER.debug("[QLM Zombie] 加载已追踪禁用模组: {} 个", trackedDisabled.size());

            Path modJarPath = getModJarPath();
            if (modJarPath == null) {
                LOGGER.warn("[QLM Zombie] 无法获取 mod JAR 路径，跳过依赖释放（开发环境？）");
                scanAndHandleConflicts(modsDir, trackedDisabled);
                saveTrackedDisabled(gameDir, trackedDisabled);
                logSummary();
                return;
            }

            LOGGER.info("[QLM Zombie] Mod JAR 路径: {}", modJarPath);

            List<EmbeddedJar> embeddedJars = readEmbeddedJars(modJarPath);
            totalLibsCount = embeddedJars.size();
            LOGGER.info("[QLM Zombie] 发现 {} 个内部嵌入 JAR", totalLibsCount);

            for (EmbeddedJar embeddedJar : embeddedJars) {
                releaseJar(embeddedJar, modsDir, trackedDisabled);
            }

            scanAndHandleConflicts(modsDir, trackedDisabled);
            saveTrackedDisabled(gameDir, trackedDisabled);
            logSummary();

        } catch (Exception e) {
            LOGGER.error("[QLM Zombie] 初始化依赖模组时发生严重错误", e);
        }
    }

    private static Path getModJarPath() {
        // 优先使用 Forge ModList API 获取 mod JAR 路径
        // protectionDomain.getCodeSource() 在 Forge 中返回 union URI (如 union:/.../jar#174!/)
        // Paths.get() 无法解析这种 URI，会导致依赖释放失败
        try {
            var modFile = ModList.get().getModFileById(MOD_ID);
            if (modFile != null) {
                Path path = modFile.getFile().getFilePath();
                if (path != null && Files.exists(path) && !Files.isDirectory(path)) {
                    return path;
                }
                if (path != null) {
                    LOGGER.debug("[QLM Zombie] ModList 返回路径为目录或不存在: {}", path);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[QLM Zombie] ModList API 获取路径失败，尝试回退方式: {}", e.getMessage());
        }

        // 回退：尝试 protectionDomain（开发环境或某些 Forge 版本可能有效）
        try {
            URI uri = ModDependencyHandler.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            String uriStr = uri.toString();

            // 处理 union URI 格式: union:/C:/path/to/jar#174!/
            if (uriStr.startsWith("union:") || uriStr.contains("#")) {
                String filePath = uriStr
                    .replace("union:/", "")
                    .replace("union:", "");
                int hashIdx = filePath.indexOf('#');
                if (hashIdx > 0) {
                    filePath = filePath.substring(0, hashIdx);
                }
                if (filePath.endsWith("!/")) {
                    filePath = filePath.substring(0, filePath.length() - 2);
                }
                Path path = Paths.get(filePath);
                if (Files.exists(path) && !Files.isDirectory(path)) {
                    return path;
                }
                LOGGER.warn("[QLM Zombie] union URI 解析路径不存在: {}", path);
            } else {
                Path path = Paths.get(uri);
                if (Files.isDirectory(path)) {
                    LOGGER.debug("[QLM Zombie] 检测到开发环境 (目录模式): {}", path);
                    return null;
                }
                if (Files.exists(path)) {
                    return path;
                }
            }
        } catch (Exception e) {
            LOGGER.error("[QLM Zombie] 获取 mod JAR 路径失败", e);
        }

        LOGGER.warn("[QLM Zombie] 无法获取 mod JAR 路径，跳过依赖释放");
        return null;
    }

    private static List<EmbeddedJar> readEmbeddedJars(Path modJarPath) {
        List<EmbeddedJar> result = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(modJarPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("libs/") && name.endsWith(".jar") && !entry.isDirectory()) {
                    String fileName = name.substring(name.lastIndexOf('/') + 1);

                    if (shouldSkipEmbeddedJar(fileName)) {
                        LOGGER.debug("[QLM Zombie] 跳过被排除的 JAR: {}", fileName);
                        zis.closeEntry();
                        continue;
                    }

                    byte[] content = zis.readAllBytes();
                    result.add(new EmbeddedJar(fileName, content));
                    LOGGER.debug("[QLM Zombie] 发现嵌入 JAR: {} ({} 字节)", fileName, content.length);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 读取嵌入 JAR 失败", e);
        }

        return result;
    }

    private static boolean shouldSkipEmbeddedJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("qlmzombie")) return true;
        if (lower.startsWith("serveradmin")) return true;
        if (lower.startsWith("player2-")) return true;
        if (lower.startsWith("vanilla_server")) return true;
        if (lower.contains("jython")) return true;
        if (lower.contains("graal")) return true;
        if (lower.contains("polyglot")) return true;
        if (lower.contains("[python]")) return true;
        return false;
    }

    private static void releaseJar(EmbeddedJar embeddedJar, Path modsDir, Set<String> trackedDisabled) {
        try {
            String fileName = embeddedJar.fileName;
            Path targetPath = modsDir.resolve(fileName);
            String lowerName = fileName.toLowerCase(Locale.ROOT);

            if (containsAny(lowerName, KEEP_ALWAYS_KEYWORDS)) {
                LOGGER.debug("[QLM Zombie] 核心库模组，跳过禁用检查: {}", fileName);
            }

            if (Files.exists(targetPath)) {
                Path disabledPath = modsDir.resolve(fileName + DISABLED_MARKER);
                if (Files.exists(disabledPath)) {
                    LOGGER.debug("[QLM Zombie] 模组已存在且被禁用 (.disabled): {}", fileName);
                    skippedCount++;
                    skippedMods.add(fileName);
                    return;
                }
                LOGGER.debug("[QLM Zombie] 模组已存在，跳过释放: {}", fileName);
                skippedCount++;
                skippedMods.add(fileName);
                return;
            }

            Files.write(targetPath, embeddedJar.content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            releasedMods.add(fileName);
            releasedCount++;
            LOGGER.info("[QLM Zombie] 释放模组: {} ({} 字节)", fileName, embeddedJar.content.length);

            if (containsAny(lowerName, DEFAULT_DISABLED_KEYWORDS)
                    && !containsAny(lowerName, KEEP_ALWAYS_KEYWORDS)) {
                if (!trackedDisabled.contains(fileName)) {
                    disableMod(targetPath, "DEFAULT_DISABLED: " + matchKeyword(lowerName, DEFAULT_DISABLED_KEYWORDS));
                    trackedDisabled.add(fileName);
                } else {
                    LOGGER.debug("[QLM Zombie] 用户已手动启用模组，跳过默认禁用: {}", fileName);
                }
            }

        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 释放模组失败: {}", embeddedJar.fileName, e);
            failedMods.add(embeddedJar.fileName);
            failedCount++;
        }
    }

    private static void scanAndHandleConflicts(Path modsDir, Set<String> trackedDisabled) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path modFile : stream) {
                String fileName = modFile.getFileName().toString();
                String lowerName = fileName.toLowerCase(Locale.ROOT);

                if (containsAny(lowerName, KEEP_ALWAYS_KEYWORDS)) {
                    continue;
                }

                if (!containsAny(lowerName, CONFLICT_KEYWORDS)) {
                    continue;
                }

                if (trackedDisabled.contains(fileName)) {
                    LOGGER.debug("[QLM Zombie] 用户已手动启用模组，跳过冲突禁用: {}", fileName);
                    continue;
                }

                disableMod(modFile, "CONFLICT: " + matchKeyword(lowerName, CONFLICT_KEYWORDS));
                trackedDisabled.add(fileName);
            }
        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 扫描冲突模组时出错", e);
        }
    }

    private static void disableMod(Path modFile, String reason) {
        try {
            String fileName = modFile.getFileName().toString();
            String disabledName = fileName + DISABLED_MARKER;
            Path disabledPath = modFile.resolveSibling(disabledName);

            if (Files.exists(disabledPath)) {
                LOGGER.debug("[QLM Zombie] 模组已是禁用状态: {}", fileName);
                if (!disabledMods.contains(fileName)) {
                    disabledMods.add(fileName);
                }
                return;
            }

            Files.move(modFile, disabledPath, StandardCopyOption.REPLACE_EXISTING);
            disabledMods.add(fileName);
            LOGGER.info("[QLM Zombie] 禁用模组 ({}): {}", reason, fileName);

        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 禁用模组失败: {}", modFile.getFileName(), e);
        }
    }

    private static Set<String> loadTrackedDisabled(Path gameDir) {
        Set<String> result = new HashSet<>();
        Path trackingPath = gameDir.resolve(TRACKING_FILE);
        if (Files.exists(trackingPath)) {
            try {
                List<String> lines = Files.readAllLines(trackingPath);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        result.add(trimmed);
                    }
                }
                LOGGER.debug("[QLM Zombie] 加载追踪文件: {} 条记录", result.size());
            } catch (IOException e) {
                LOGGER.warn("[QLM Zombie] 读取禁用追踪文件失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private static void saveTrackedDisabled(Path gameDir, Set<String> trackedDisabled) {
        Path trackingPath = gameDir.resolve(TRACKING_FILE);
        try {
            List<String> lines = new ArrayList<>(trackedDisabled);
            lines.add(0, "# QLM Zombie Dependency Handler - Disabled Mod Tracking");
            lines.add(1, "# This file tracks mods that were automatically disabled.");
            lines.add(2, "# If you manually enable a mod, it will NOT be re-disabled.");
            Files.write(trackingPath, lines,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.debug("[QLM Zombie] 保存追踪文件: {} 条记录", trackedDisabled.size());
        } catch (IOException e) {
            LOGGER.warn("[QLM Zombie] 保存禁用追踪文件失败: {}", e.getMessage());
        }
    }

    private static boolean containsAny(String source, List<String> keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String matchKeyword(String source, List<String> keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return keyword;
            }
        }
        return "unknown";
    }

    private static void logSummary() {
        hasConflicts = !disabledMods.isEmpty();
        LOGGER.info("[QLM Zombie] ====== 依赖释放汇总 ======");
        LOGGER.info("[QLM Zombie] 嵌入 JAR 总数: {}", totalLibsCount);
        LOGGER.info("[QLM Zombie] 成功释放: {}", releasedCount);
        LOGGER.info("[QLM Zombie] 跳过: {}", skippedCount);
        LOGGER.info("[QLM Zombie] 失败: {}", failedCount);
        LOGGER.info("[QLM Zombie] 自动禁用: {}", disabledMods.size());
        if (!disabledMods.isEmpty()) {
            LOGGER.info("[QLM Zombie] 禁用列表: {}", String.join(", ", disabledMods));
        }
        LOGGER.info("[QLM Zombie] 冲突检测: {}", hasConflicts ? "检测到冲突" : "无冲突");
        LOGGER.info("[QLM Zombie] =============================");
    }

    public static int getTotalLibsCount() {
        return totalLibsCount;
    }

    public static int getReleasedCount() {
        return releasedCount;
    }

    public static int getSkippedCount() {
        return skippedCount;
    }

    public static int getFailedCount() {
        return failedCount;
    }

    public static List<String> getDisabledMods() {
        return Collections.unmodifiableList(disabledMods);
    }

    public static List<String> getReleasedMods() {
        return Collections.unmodifiableList(releasedMods);
    }

    public static List<String> getFailedMods() {
        return Collections.unmodifiableList(failedMods);
    }

    public static List<String> getSkippedMods() {
        return Collections.unmodifiableList(skippedMods);
    }

    public static boolean hasConflicts() {
        return hasConflicts;
    }

    public static boolean hasDuplicates() {
        return hasDuplicates;
    }

    public static List<String> getDeletedDuplicates() {
        return Collections.unmodifiableList(deletedDuplicates);
    }

    public static List<String> getDetectedConflicts() {
        return Collections.unmodifiableList(detectedConflicts);
    }

    /**
     * Scan mod JAR for embedded libs and return their file names.
     */
    public static List<String> scanInternalLibs() {
        List<String> libs = new ArrayList<>();
        try {
            Path modJarPath = getModJarPath();
            if (modJarPath == null) {
                return libs;
            }
            List<EmbeddedJar> embeddedJars = readEmbeddedJars(modJarPath);
            for (EmbeddedJar jar : embeddedJars) {
                libs.add(jar.fileName);
            }
        } catch (Exception e) {
            LOGGER.warn("[QLM Zombie] 扫描内部库失败", e);
        }
        return libs;
    }

    /**
     * Get a set of mod file names that exist in the given directory.
     */
    public static Set<String> getExistingModsIn(Path modsDir) {
        Set<String> existing = new HashSet<>();
        if (!Files.exists(modsDir) || !Files.isDirectory(modsDir)) {
            return existing;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path file : stream) {
                existing.add(file.getFileName().toString());
            }
        } catch (IOException e) {
            LOGGER.warn("[QLM Zombie] 读取 mods 目录失败: {}", modsDir, e);
        }
        return existing;
    }

    /**
     * Extract a specific embedded lib from the mod JAR to the mods directory.
     */
    public static boolean extractModFromJar(String libFileName, Path modsDir) {
        try {
            Path modJarPath = getModJarPath();
            if (modJarPath == null) {
                return false;
            }
            List<EmbeddedJar> embeddedJars = readEmbeddedJars(modJarPath);
            for (EmbeddedJar jar : embeddedJars) {
                if (jar.fileName.equals(libFileName)) {
                    Path targetPath = modsDir.resolve(libFileName);
                    Files.write(targetPath, jar.content,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    LOGGER.info("[QLM Zombie] 手动释放: {} ({} 字节)", libFileName, jar.content.length);
                    return true;
                }
            }
            LOGGER.warn("[QLM Zombie] 未在 JAR 中找到内部库: {}", libFileName);
            return false;
        } catch (Exception e) {
            LOGGER.error("[QLM Zombie] 释放内部库失败: {}", libFileName, e);
            return false;
        }
    }

    /**
     * Detect and resolve conflicts among mods in the given directory.
     */
    public static void detectAndResolveConflicts(Path modsDir, List<String> internalLibs) {
        detectedConflicts.clear();
        disabledMods.clear();
        hasConflicts = false;
        hasDuplicates = false;
        deletedDuplicates.clear();

        if (!Files.exists(modsDir) || !Files.isDirectory(modsDir)) {
            return;
        }

        Set<String> trackedDisabled = new HashSet<>();
        try {
            // Detect duplicates: same mod file appearing multiple times with different versions
            java.util.Map<String, List<Path>> modBaseNames = new java.util.HashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path modFile : stream) {
                    String fileName = modFile.getFileName().toString();
                    String baseName = stripVersion(fileName);
                    modBaseNames.computeIfAbsent(baseName, k -> new ArrayList<>()).add(modFile);
                }
            }

            for (java.util.Map.Entry<String, List<Path>> entry : modBaseNames.entrySet()) {
                if (entry.getValue().size() > 1) {
                    hasDuplicates = true;
                    List<Path> dups = entry.getValue();
                    // Sort by file name to keep the first one deterministically
                    dups.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
                    Path keep = dups.get(0);
                    String conflictName = entry.getKey() + " (保留: " + keep.getFileName() + ")";
                    detectedConflicts.add(conflictName);
                    for (int i = 1; i < dups.size(); i++) {
                        Path toDelete = dups.get(i);
                        String fileName = toDelete.getFileName().toString();
                        try {
                            Files.deleteIfExists(toDelete);
                            deletedDuplicates.add(fileName);
                            LOGGER.info("[QLM Zombie] 删除重复 mod: {}", fileName);
                        } catch (IOException ex) {
                            LOGGER.warn("[QLM Zombie] 删除重复 mod 失败: {}", fileName, ex);
                        }
                    }
                }
            }

            // Detect conflicts by keyword
            scanAndHandleConflicts(modsDir, trackedDisabled);
            hasConflicts = !disabledMods.isEmpty();

            // Populate conflict descriptions
            for (String disabled : disabledMods) {
                String lower = disabled.toLowerCase(Locale.ROOT);
                String keyword = matchKeyword(lower, CONFLICT_KEYWORDS);
                if (!"unknown".equals(keyword)) {
                    detectedConflicts.add(disabled + " (冲突: " + keyword + ")");
                }
            }

        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 冲突检测失败", e);
        }
    }

    private static String stripVersion(String fileName) {
        // Strip version numbers and suffixes to identify the same mod
        String name = fileName.toLowerCase(Locale.ROOT);
        // Remove .jar extension
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        // Remove version patterns like -1.0.0, -1.20.1, etc.
        name = name.replaceAll("-\\d+.*$", "");
        name = name.replaceAll("_\\d+.*$", "");
        return name;
    }

    private static class EmbeddedJar {
        final String fileName;
        final byte[] content;

        EmbeddedJar(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }
    }
}