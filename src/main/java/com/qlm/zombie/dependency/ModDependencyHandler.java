package com.qlm.zombie.dependency;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
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

/**
 * 模组依赖自动释放处理器（重写版）。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>白名单源</b>：mod JAR 内 {@code libs/} 目录下所有 {@code *.jar}（由
 *       {@code build.gradle.kts} 从 {@code src/main/libs/} 打包而来），可选
 *       {@code libs/manifest.txt} 作为权威清单。白名单中的 JAR 永远不会被本处理器删除或禁用。</li>
 *   <li><b>禁用策略</b>：保守模式。只禁用 {@link #DEFAULT_DISABLED_PREFIXES}
 *       精确前缀匹配的"已知问题模组"（如 ToughAsNails/ThirstWasTaken，与项目"口渴"系统冲突），
 *       不再用模糊关键字扫描 mods 目录，避免误伤 create/refinedstorage/crafting-dead 等依赖。</li>
 *   <li><b>恢复策略</b>：如果 mods 目录中存在 {@code .disabled} 文件，且文件名在白名单中、
 *       不在 DEFAULT_DISABLED 列表中，则自动恢复（取消禁用）。这样即便外部脚本误禁用
 *       kotlinforforge/kubejs/cloth-config，下次启动也会自动恢复。</li>
 *   <li><b>重复处理</b>：白名单中的 JAR 优先保留，仅删除非白名单的重复版本。</li>
 * </ul>
 *
 * <h3>公共 API 兼容性</h3>
 * 所有原公共方法签名保持不变，仅新增 {@link #getRestoredCount()} / {@link #getRestoredMods()}。
 */
public class ModDependencyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "qlmzombie";
    public static final String DISABLED_MARKER = ".disabled";
    private static final String TRACKING_FILE = "qlmzombie_disabled_tracker.txt";
    private static final String MANIFEST_ENTRY = "libs/manifest.txt";
    private static final String LIBS_DIR_IN_JAR = "libs/";

    /**
     * 已知问题模组前缀（精确前缀匹配，非模糊关键字）。
     * 覆盖"口渴/生命管理"系列冲突模组，所有常见分隔符变体（连字符/下划线/无分隔/空格）均列出，
     * 避免 {@code [中文名] Tough As Nails-...} 等不规范命名绕过匹配。
     * 用户可手动启用（手动启用后会被加入 tracker，不再被自动禁用）。
     *
     * <p><b>双端通用</b>：无论 CLIENT 还是 DEDICATED_SERVER 都会匹配。
     */
    private static final List<String> DEFAULT_DISABLED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            // ToughAsNails 常见变体（含连字符、下划线、空格、缩写）
            "toughasnails",
            "tough-as-nails",
            "tough_as_nails",
            "tough as nails",
            "tough-as",
            // ThirstWasTaken 常见变体
            "thirstwastaken",
            "thirst-was-taken",
            "thirst_was_taken",
            "thirst was taken",
            // ThirstMod / ThirstCanteen 系列（全部变体）
            "thirstmod",
            "thirst-mod",
            "thirst_mod",
            "thirst mod",
            "thirstcanteen",
            "thirst-canteen",
            "thirst_canteen",
            "thirst canteen"
    ));

    /**
     * 服务端专属禁用前缀（仅在 {@code FMLEnvironment.dist == DEDICATED_SERVER} 时生效）。
     *
     * <p>禁用原因：Crafting Dead 模组以"枪/装饰/生存"为核心的大量客户端资源（动画、
     * 粒子、渲染器、GUI）在独立专用服务端上毫无意义，反而会触发类加载警告、
     * 延长启动时间、干扰服务端-only 集成测试。客户端（单人 / LAN 主机）仍然需要。
     *
     * <p>当前列出全部 4 个 Crafting Dead 子模组变体：core / decoration / survival / worldguard。
     * 以 {@code crafting-dead} 为主前缀，连字符、下划线、空格三种分隔符全部列出
     * （对付中文前缀、重命名、压缩包解压后各种奇怪变体）。
     */
    private static final List<String> SERVER_DISABLED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "crafting-dead",
            "crafting_dead",
            "crafting dead",
            "[crafting-dead]",
            "[craftingdead]",
            "craftingdead"
    ));

    /** @return 当前运行环境是否为独立专用服务端。 */
    private static boolean isDedicatedServerEnv() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    private static int releasedCount;
    private static int skippedCount;
    private static int failedCount;
    private static int restoredCount;
    private static boolean hasConflicts;
    private static boolean hasDuplicates;
    private static final List<String> disabledMods = new ArrayList<>();
    private static final List<String> releasedMods = new ArrayList<>();
    private static final List<String> failedMods = new ArrayList<>();
    private static final List<String> skippedMods = new ArrayList<>();
    private static final List<String> restoredMods = new ArrayList<>();
    private static final List<String> detectedConflicts = new ArrayList<>();
    private static final List<String> deletedDuplicates = new ArrayList<>();
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
                saveTrackedDisabled(gameDir, trackedDisabled);
                logSummary();
                return;
            }

            LOGGER.info("[QLM Zombie] Mod JAR 路径: {}", modJarPath);

            if (isDedicatedServerEnv()) {
                LOGGER.info("[QLM Zombie] 检测到 DEDICATED_SERVER 环境: 将自动禁用服务端不需要的模组 (crafting-dead* 等)");
            }

            List<EmbeddedJar> embeddedJars = readEmbeddedJars(modJarPath);
            totalLibsCount = embeddedJars.size();
            LOGGER.info("[QLM Zombie] 发现 {} 个内部嵌入 JAR（精确白名单源）", totalLibsCount);

            if (embeddedJars.isEmpty()) {
                LOGGER.warn("[QLM Zombie] 未发现任何嵌入 JAR！请检查 build.gradle.kts 是否将 src/main/libs/*.jar 打包到 libs/ 目录");
                saveTrackedDisabled(gameDir, trackedDisabled);
                logSummary();
                return;
            }

            Set<String> whiteList = buildWhiteList(embeddedJars);

            // 阶段 1：释放白名单 JAR 到 mods 目录
            for (EmbeddedJar embeddedJar : embeddedJars) {
                releaseJar(embeddedJar, modsDir, trackedDisabled);
            }

            // 阶段 2：恢复被误禁用的白名单 JAR（核心修复）
            restoreMistakenlyDisabled(whiteList, modsDir, trackedDisabled);

            // 阶段 3：禁用 DEFAULT_DISABLED_PREFIXES 中的已知问题模组
            disableKnownProblemMods(modsDir, trackedDisabled);

            // 阶段 4：检测重复 JAR（仅删除非白名单的重复项）
            detectAndRemoveDuplicates(modsDir, whiteList);

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
        Set<String> manifestNames = readManifest(modJarPath);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(modJarPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith(LIBS_DIR_IN_JAR) || !name.endsWith(".jar") || entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String fileName = name.substring(name.lastIndexOf('/') + 1);

                if (shouldSkipEmbeddedJar(fileName)) {
                    LOGGER.debug("[QLM Zombie] 跳过被排除的 JAR: {}", fileName);
                    zis.closeEntry();
                    continue;
                }

                // 如果存在 manifest，仅释放 manifest 中列出的 JAR
                if (!manifestNames.isEmpty() && !manifestNames.contains(fileName.toLowerCase(Locale.ROOT))) {
                    LOGGER.debug("[QLM Zombie] JAR 不在 manifest 白名单中，跳过: {}", fileName);
                    zis.closeEntry();
                    continue;
                }

                byte[] content = zis.readAllBytes();
                result.add(new EmbeddedJar(fileName, content));
                LOGGER.debug("[QLM Zombie] 发现嵌入 JAR: {} ({} 字节)", fileName, content.length);
                zis.closeEntry();
            }
        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 读取嵌入 JAR 失败", e);
        }

        return result;
    }

    /**
     * 读取 mod JAR 内的 {@code libs/manifest.txt}（构建时生成）。
     * 每行一个文件名，# 开头为注释。返回小写文件名集合。
     * 如果 manifest 不存在，返回空集合（回退到"扫描所有 libs/*.jar"模式）。
     */
    private static Set<String> readManifest(Path modJarPath) {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(modJarPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (MANIFEST_ENTRY.equals(entry.getName()) && !entry.isDirectory()) {
                    byte[] content = zis.readAllBytes();
                    String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : text.split("\\r?\\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        names.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                    LOGGER.info("[QLM Zombie] 已加载 libs/manifest.txt，共 {} 个白名单条目", names.size());
                    break;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            LOGGER.warn("[QLM Zombie] 读取 manifest 失败，将回退到扫描 libs/*.jar 模式: {}", e.getMessage());
        }
        return names;
    }

    private static boolean shouldSkipEmbeddedJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        String stripped = stripBracketPrefix(lower);
        if (stripped.startsWith("qlmzombie")) return true;
        if (stripped.startsWith("serveradmin")) return true;
        if (stripped.startsWith("player2-")) return true;
        if (stripped.startsWith("vanilla_server")) return true;
        if (lower.contains("jython")) return true;
        if (lower.contains("graal")) return true;
        if (lower.contains("polyglot")) return true;
        if (lower.contains("[python]")) return true;
        return false;
    }

    /**
     * 剥离文件名中的 {@code [中文名]} 前缀，返回纯净的文件名。
     * 例如 {@code "[意志坚定] ToughAsNails-1.20.1-9.2.0.171.jar"} → {@code "toughasnails-1.20.1-9.2.0.171.jar"}
     * 如果没有方括号前缀，返回原文件名的小写形式。
     */
    private static String stripBracketPrefix(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("[")) {
            int close = lower.indexOf(']');
            if (close > 0 && close < lower.length() - 1) {
                return lower.substring(close + 1).trim();
            }
        }
        return lower;
    }

    private static Set<String> buildWhiteList(List<EmbeddedJar> embeddedJars) {
        Set<String> whiteList = new HashSet<>();
        for (EmbeddedJar jar : embeddedJars) {
            whiteList.add(jar.fileName.toLowerCase(Locale.ROOT));
        }
        return whiteList;
    }

    private static void releaseJar(EmbeddedJar embeddedJar, Path modsDir, Set<String> trackedDisabled) {
        try {
            String fileName = embeddedJar.fileName;
            Path targetPath = modsDir.resolve(fileName);
            Path disabledPath = modsDir.resolve(fileName + DISABLED_MARKER);

            // 情况 1：用户已手动禁用（.disabled 存在且 .jar 不存在）
            if (Files.exists(disabledPath) && !Files.exists(targetPath)) {
                if (isUnifiedDisabled(fileName)) {
                    LOGGER.debug("[QLM Zombie] 命中统一禁用策略，保持禁用: {}", fileName);
                } else {
                    LOGGER.debug("[QLM Zombie] 用户已禁用此模组，跳过释放: {}", fileName);
                }
                skippedCount++;
                skippedMods.add(fileName);
                return;
            }

            // 情况 2：JAR 已存在且大小相同 → 跳过
            if (Files.exists(targetPath)) {
                long existingSize = Files.size(targetPath);
                if (existingSize == embeddedJar.content.length) {
                    LOGGER.debug("[QLM Zombie] 模组已存在且大小一致，跳过: {}", fileName);
                    skippedCount++;
                    skippedMods.add(fileName);
                } else {
                    // 大小不同 → 视为版本不同，覆盖
                    LOGGER.info("[QLM Zombie] 模组已存在但大小不同 ({} -> {}), 覆盖: {}",
                            existingSize, embeddedJar.content.length, fileName);
                    Files.write(targetPath, embeddedJar.content,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    releasedMods.add(fileName);
                    releasedCount++;
                }
            } else {
                // 情况 3：JAR 不存在 → 释放
                Files.write(targetPath, embeddedJar.content,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                releasedMods.add(fileName);
                releasedCount++;
                LOGGER.info("[QLM Zombie] 释放模组: {} ({} 字节)", fileName, embeddedJar.content.length);
            }

            // 如果命中统一禁用策略，禁用它（除非用户手动启用过）
            if (isUnifiedDisabled(fileName) && !trackedDisabled.contains(fileName)) {
                String reason = isServerDisabled(fileName) ? "SERVER_DISABLED" : "DEFAULT_DISABLED";
                disableMod(targetPath, reason);
                trackedDisabled.add(fileName);
            }

        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 释放模组失败: {}", embeddedJar.fileName, e);
            failedMods.add(embeddedJar.fileName);
            failedCount++;
        }
    }

    /**
     * 恢复被误禁用的白名单 JAR。
     * 如果 mods 目录中存在 .disabled 文件，且文件名在白名单中、不是 DEFAULT_DISABLED、
     * 未被用户主动追踪禁用，则自动恢复（取消禁用），保证项目依赖可用。
     *
     * 这是修复"kotlinforforge/kubejs/cloth-config 被外部脚本误禁用"的关键机制。
     */
    private static void restoreMistakenlyDisabled(Set<String> whiteList, Path modsDir, Set<String> trackedDisabled) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*" + DISABLED_MARKER)) {
            for (Path disabledFile : stream) {
                String disabledName = disabledFile.getFileName().toString();
                if (!disabledName.endsWith(DISABLED_MARKER)) continue;
                String jarName = disabledName.substring(0, disabledName.length() - DISABLED_MARKER.length());
                String lowerJar = jarName.toLowerCase(Locale.ROOT);

                if (!whiteList.contains(lowerJar)) continue;
                if (isUnifiedDisabled(jarName)) continue;
                if (trackedDisabled.contains(jarName)) {
                    LOGGER.debug("[QLM Zombie] 用户已主动追踪禁用，不恢复: {}", jarName);
                    continue;
                }

                Path jarPath = modsDir.resolve(jarName);
                try {
                    if (Files.exists(jarPath)) {
                        Files.deleteIfExists(disabledFile);
                    } else {
                        Files.move(disabledFile, jarPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    restoredMods.add(jarName);
                    restoredCount++;
                    LOGGER.info("[QLM Zombie] 自动恢复被误禁用的白名单模组: {}", jarName);
                } catch (IOException ex) {
                    LOGGER.warn("[QLM Zombie] 恢复模组失败: {}", jarName, ex);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 扫描 .disabled 文件时出错", e);
        }
    }

    /**
     * 禁用命中统一禁用策略（DEFAULT_DISABLED ∪ SERVER_DISABLED）的模组。
     * 使用精确前缀匹配，不扫描整个 mods 目录的关键字。
     */
    private static void disableKnownProblemMods(Path modsDir, Set<String> trackedDisabled) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path modFile : stream) {
                String fileName = modFile.getFileName().toString();
                if (!isUnifiedDisabled(fileName)) continue;
                if (trackedDisabled.contains(fileName)) continue;

                Path disabledPath = modFile.resolveSibling(fileName + DISABLED_MARKER);
                if (Files.exists(disabledPath)) continue;

                String reason = isServerDisabled(fileName) ? "SERVER_DISABLED" : "DEFAULT_DISABLED";
                disableMod(modFile, reason);
                trackedDisabled.add(fileName);
            }
        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 扫描已知问题模组时出错", e);
        }
    }

    /**
     * 检测并删除 mods 目录中的重复 JAR。
     * 优先保留白名单中的版本，删除非白名单的重复项。
     * 白名单中的 JAR 永远不会被删除。
     */
    private static void detectAndRemoveDuplicates(Path modsDir, Set<String> whiteList) {
        try {
            Map<String, List<Path>> modBaseNames = new HashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path modFile : stream) {
                    String fileName = modFile.getFileName().toString();
                    String baseName = stripVersion(fileName);
                    modBaseNames.computeIfAbsent(baseName, k -> new ArrayList<>()).add(modFile);
                }
            }

            for (Map.Entry<String, List<Path>> entry : modBaseNames.entrySet()) {
                if (entry.getValue().size() <= 1) continue;

                List<Path> dups = entry.getValue();
                // 排序优先级（从高到低）：
                // 1. 在白名单中（精准白名单，ModDependencyHandler 自身释放）
                // 2. 文件名以 "-all.jar" 结尾（fat-jar / 一体化打包，优先于残包）
                // 3. 文件大小更大（通常 fat-jar 更大）
                // 4. 按文件名做稳定排序（字母序回退，保证结果确定）
                dups.sort((a, b) -> {
                    String aName = a.getFileName().toString().toLowerCase(Locale.ROOT);
                    String bName = b.getFileName().toString().toLowerCase(Locale.ROOT);
                    boolean aWhite = whiteList.contains(aName);
                    boolean bWhite = whiteList.contains(bName);
                    if (aWhite != bWhite) return aWhite ? -1 : 1;
                    boolean aAll = aName.endsWith("-all.jar");
                    boolean bAll = bName.endsWith("-all.jar");
                    if (aAll != bAll) return aAll ? -1 : 1;
                    try {
                        long aSize = Files.size(a);
                        long bSize = Files.size(b);
                        if (aSize != bSize) return Long.compare(bSize, aSize); // 大的在前
                    } catch (IOException ignored) {
                        // 读取失败时跳过大小比较
                    }
                    return aName.compareTo(bName);
                });

                Path keep = dups.get(0);
                hasDuplicates = true;
                String conflictName = entry.getKey() + " (保留: " + keep.getFileName() + ")";
                detectedConflicts.add(conflictName);

                for (int i = 1; i < dups.size(); i++) {
                    Path toDelete = dups.get(i);
                    String fileName = toDelete.getFileName().toString();
                    // 白名单中的 JAR 绝对不删除
                    if (whiteList.contains(fileName.toLowerCase(Locale.ROOT))) {
                        LOGGER.info("[QLM Zombie] 重复但属于白名单，保留: {}", fileName);
                        continue;
                    }
                    try {
                        Files.deleteIfExists(toDelete);
                        deletedDuplicates.add(fileName);
                        LOGGER.info("[QLM Zombie] 删除重复 mod: {}", fileName);
                    } catch (IOException ex) {
                        LOGGER.warn("[QLM Zombie] 删除重复 mod 失败: {}", fileName, ex);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 重复检测失败", e);
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
            List<String> lines = new ArrayList<>();
            lines.add("# QLM Zombie Dependency Handler - Disabled Mod Tracking");
            lines.add("# This file tracks mods that were automatically disabled by unified policy.");
            lines.add("# Policy = DEFAULT_DISABLED_PREFIXES ∪ (SERVER_DISABLED_PREFIXES when dist=DEDICATED_SERVER)");
            lines.add("#   - DEFAULT_DISABLED: e.g. ToughAsNails/ThirstWasTaken/thirstmod (口渴冲突模组，双端都禁用)");
            lines.add("#   - SERVER_DISABLED: e.g. crafting-dead-core/decoration/survival/worldguard (仅专用服务端禁用)");
            lines.add("# If you manually enable a mod listed here, it will NOT be re-disabled.");
            lines.add("# Whitelist mods (embedded in mod JAR) are auto-restored even if .disabled exists,");
            lines.add("# unless they hit the unified disabled policy above.");
            lines.addAll(trackedDisabled);
            Files.write(trackingPath, lines,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.debug("[QLM Zombie] 保存追踪文件: {} 条记录", trackedDisabled.size());
        } catch (IOException e) {
            LOGGER.warn("[QLM Zombie] 保存禁用追踪文件失败: {}", e.getMessage());
        }
    }

    private static boolean isDefaultDisabled(String fileName) {
        // 先剥离 [中文名] 前缀，再检查前缀匹配
        // 例如 "[意志坚定] ToughAsNails-1.20.1-9.2.0.171.jar" → "toughasnails-..."
        String stripped = stripBracketPrefix(fileName);
        // 第一轮：标准前缀直接匹配（命中快，最常见场景）
        for (String prefix : DEFAULT_DISABLED_PREFIXES) {
            if (stripped.startsWith(prefix)) return true;
        }
        // 第二轮（保险）：移除 - _ 空格三种分隔符后再做精确前缀匹配，
        // 对付 "tough-as nails_mod" 这类分隔符混乱的极端文件名。
        String normalized = stripped.replace("-", "").replace("_", "").replace(" ", "");
        for (String rawPrefix : DEFAULT_DISABLED_PREFIXES) {
            String normPrefix = rawPrefix.replace("-", "").replace("_", "").replace(" ", "");
            if (!normPrefix.isEmpty() && normalized.startsWith(normPrefix)) return true;
        }
        return false;
    }

    /**
     * 专用服务端环境下禁用的模组前缀判断。
     * 使用与 {@link #isDefaultDisabled(String)} 相同的"前缀+分隔符归一化"双重匹配逻辑。
     * 在非 DEDICATED_SERVER 环境下恒返回 false。
     */
    private static boolean isServerDisabled(String fileName) {
        if (!isDedicatedServerEnv()) return false;
        String stripped = stripBracketPrefix(fileName);
        for (String prefix : SERVER_DISABLED_PREFIXES) {
            if (stripped.startsWith(prefix)) return true;
        }
        String normalized = stripped.replace("-", "").replace("_", "").replace(" ", "");
        for (String rawPrefix : SERVER_DISABLED_PREFIXES) {
            String normPrefix = rawPrefix.replace("-", "").replace("_", "").replace(" ", "");
            if (!normPrefix.isEmpty() && normalized.startsWith(normPrefix)) return true;
        }
        return false;
    }

    /**
     * 聚合禁用判断（DEFAULT_DISABLED ∪ SERVER_DISABLED，取并集）。
     * 所有释放/恢复/禁用流程都应使用此方法，保证 DEFAULT_DISABLED 与 SERVER_DISABLED
     * 两套策略在每个分支点行为一致，避免"某分支恢复了但另一分支又禁用"的振荡。
     */
    private static boolean isUnifiedDisabled(String fileName) {
        return isDefaultDisabled(fileName) || isServerDisabled(fileName);
    }

    private static String stripVersion(String fileName) {
        // 先剥离 [中文名] 前缀，使带前缀和不带前缀的同名模组能被识别为重复
        // 例如 "[精致存储] refinedstorage-1.12.4.jar" 和 "refinedstorage-1.12.4.jar" 都返回 "refinedstorage"
        String name = stripBracketPrefix(fileName);
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.replaceAll("-\\d+.*$", "");
        name = name.replaceAll("_\\d+.*$", "");
        return name;
    }

    private static void logSummary() {
        hasConflicts = !disabledMods.isEmpty();
        LOGGER.info("[QLM Zombie] ====== 依赖释放汇总 ======");
        LOGGER.info("[QLM Zombie] 运行环境 Dist: {} ({})", FMLEnvironment.dist,
                isDedicatedServerEnv() ? "专用服务端 -  crafting-dead* 自动禁用" : "客户端/LAN主机 - 全模组释放");
        LOGGER.info("[QLM Zombie] 嵌入 JAR 总数（白名单）: {}", totalLibsCount);
        LOGGER.info("[QLM Zombie] 成功释放: {}", releasedCount);
        LOGGER.info("[QLM Zombie] 跳过(已存在): {}", skippedCount);
        LOGGER.info("[QLM Zombie] 自动恢复(误禁用): {}", restoredCount);
        LOGGER.info("[QLM Zombie] 失败: {}", failedCount);
        LOGGER.info("[QLM Zombie] 自动禁用(统一策略): {}", disabledMods.size());
        if (!disabledMods.isEmpty()) {
            LOGGER.info("[QLM Zombie] 禁用列表: {}", String.join(", ", disabledMods));
        }
        if (!restoredMods.isEmpty()) {
            LOGGER.info("[QLM Zombie] 恢复列表: {}", String.join(", ", restoredMods));
        }
        LOGGER.info("[QLM Zombie] 重复检测: {}", hasDuplicates ? "检测到重复" : "无重复");
        LOGGER.info("[QLM Zombie] 冲突检测: {}", hasConflicts ? "存在禁用项" : "无冲突");
        LOGGER.info("[QLM Zombie] =============================");
    }

    // ============ 公共 API ============

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

    public static int getRestoredCount() {
        return restoredCount;
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

    public static List<String> getRestoredMods() {
        return Collections.unmodifiableList(restoredMods);
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
     * Kept for backward compatibility with QLMCommands. Uses the new conservative
     * policy: only disable DEFAULT_DISABLED_PREFIXES mods, never delete whitelist mods.
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

        Set<String> whiteList = new HashSet<>();
        if (internalLibs != null) {
            for (String name : internalLibs) {
                whiteList.add(name.toLowerCase(Locale.ROOT));
            }
        }

        Set<String> trackedDisabled = new HashSet<>();

        try {
            // Detect duplicates: same mod file appearing multiple times with different versions
            Map<String, List<Path>> modBaseNames = new HashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path modFile : stream) {
                    String fileName = modFile.getFileName().toString();
                    String baseName = stripVersion(fileName);
                    modBaseNames.computeIfAbsent(baseName, k -> new ArrayList<>()).add(modFile);
                }
            }

            for (Map.Entry<String, List<Path>> entry : modBaseNames.entrySet()) {
                if (entry.getValue().size() > 1) {
                    hasDuplicates = true;
                    List<Path> dups = entry.getValue();
                    // 同 detectAndRemoveDuplicates：白名单 → -all fat-jar → 更大文件 → 字母序
                    dups.sort((a, b) -> {
                        String aName = a.getFileName().toString().toLowerCase(Locale.ROOT);
                        String bName = b.getFileName().toString().toLowerCase(Locale.ROOT);
                        boolean aWhite = whiteList.contains(aName);
                        boolean bWhite = whiteList.contains(bName);
                        if (aWhite != bWhite) return aWhite ? -1 : 1;
                        boolean aAll = aName.endsWith("-all.jar");
                        boolean bAll = bName.endsWith("-all.jar");
                        if (aAll != bAll) return aAll ? -1 : 1;
                        try {
                            long aSize = Files.size(a);
                            long bSize = Files.size(b);
                            if (aSize != bSize) return Long.compare(bSize, aSize);
                        } catch (IOException ignored) {
                        }
                        return aName.compareTo(bName);
                    });
                    Path keep = dups.get(0);
                    String conflictName = entry.getKey() + " (保留: " + keep.getFileName() + ")";
                    detectedConflicts.add(conflictName);
                    for (int i = 1; i < dups.size(); i++) {
                        Path toDelete = dups.get(i);
                        String fileName = toDelete.getFileName().toString();
                        if (whiteList.contains(fileName.toLowerCase(Locale.ROOT))) {
                            continue;
                        }
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

            // Disable mods hitting unified policy (DEFAULT_DISABLED ∪ SERVER_DISABLED) - no more keyword scanning
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path modFile : stream) {
                    String fileName = modFile.getFileName().toString();
                    if (!isUnifiedDisabled(fileName)) continue;
                    if (trackedDisabled.contains(fileName)) continue;
                    Path disabledPath = modFile.resolveSibling(fileName + DISABLED_MARKER);
                    if (Files.exists(disabledPath)) continue;
                    String reason = isServerDisabled(fileName) ? "SERVER_DISABLED" : "DEFAULT_DISABLED";
                    disableMod(modFile, reason);
                    trackedDisabled.add(fileName);
                    detectedConflicts.add(fileName + " (禁用: " + reason + ")");
                }
            }
            hasConflicts = !disabledMods.isEmpty();

        } catch (IOException e) {
            LOGGER.error("[QLM Zombie] 冲突检测失败", e);
        }
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
