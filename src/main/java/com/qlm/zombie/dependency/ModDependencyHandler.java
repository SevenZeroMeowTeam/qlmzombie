package com.qlm.zombie.dependency;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

public class ModDependencyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LIBS_INTERNAL_PATH = "libs/";
    private static final String DEPENDENCY_MARKER_FILE = "qlmzombie_deps_installed.txt";
    private static final String DISABLED_MARKER = ".disabled";

    // 定义 mod 冲突组：同一组内的 mod 不能同时启用
    // 以 jar 文件名中的关键字匹配
    // 玩家可自行调整优先保留的 mod（见 README.md）
    private static final List<ConflictGroup> CONFLICT_GROUPS = List.of(
        new ConflictGroup("物品管理器冲突", "请选择一个物品管理 mod（推荐 REI，如偏好 JEI 可自行调整）",
            List.of("jei-", "roughlyenoughitems-", "roughlyenough", "rei-")),
        new ConflictGroup("物品悬停信息冲突", "请选择一个悬停信息 mod（推荐 WTHIT）",
            List.of("wthit-", "jade-", "hwyla-", "waila-")),
        new ConflictGroup("性能优化冲突", "现代化修复已包含多数优化，如需其他优化请先移除现代化修复",
            List.of("sodiumdynamiclights", "radium-")),
        new ConflictGroup("加载器不兼容", "以下 mod 不是 Forge 版本，无法加载",
            List.of("fabric", "-fabric_", "-fabric."))
    );

    // 永远不释放的文件（排除列表）
    private static final List<String> EXCLUDE_PATTERNS = List.of(
        "qlmzombie-",    // 自己 mod
        ".connector/",    // 连接器中间文件
        ".input",         // 中间输入文件
        "README.txt"     // 说明文件
    );

    // 必要 mod 白名单：这些 mod 永远不会被"重复 mod 删除"或"冲突检测禁用"
    // 用于处理 FTB 系列、前置库等有实际功能但名称前缀容易混淆的 mod
    private static final List<String> KEEP_ALWAYS_KEYWORDS = List.of(
        "ftb-teams",      // FTB 团队（核心，FTB 系列都依赖它）
        "ftb-quests",     // FTB 任务（功能 mod）
        "ftb-chunks",     // FTB 区块（功能 mod）
        "ftb-library",    // FTB Library（FTB 前置库）
        "architectury",   // Architectury（几乎所有 Fabric/Forge 兼容 mod 依赖）
        "cloth-config",   // Cloth Config（配置前置）
        "bookshelf",      // Bookshelf（前置库）
        "playerengine",   // PlayerEngine（AI NPC 核心依赖）
        "player2npc"      // Player2NPC（AI NPC mod）
    );

    // 前缀提取白名单：如果文件名包含这些关键字，直接将它们作为前缀
    // 用于解决 FTB 系列、某些特殊前缀被截断的问题
    private static final List<String> FORCE_PREFIX_KEYWORDS = List.of(
        "ftb-quests",
        "ftb-teams",
        "ftb-chunks",
        "ftb-library",
        "ftb-xmod"
    );

    private static volatile boolean initialized = false;
    private static volatile int totalLibsCount = 0;
    private static volatile int releasedCount = 0;
    private static volatile List<String> detectedConflicts = new ArrayList<>();
    private static volatile List<String> disabledMods = new ArrayList<>();
    private static volatile List<String> deletedDuplicates = new ArrayList<>();

    /**
     * 从 jar 文件名中提取用于判断是否为"同一 mod"的前缀。
     * 逻辑：去掉 .jar 后缀，从左向右找第一个数字字符或"v"后面接数字的位置，保留前面的部分。
     * 例如：
     *   jei-1.20.1-forge-15.2.0.27.jar      →  "jei"
     *   jei-1.20.1-forge-15.3.0.3.jar       →  "jei"
     *   roughlyenoughitems-14.3.0.4.jar     →  "roughlyenoughitems"
     *   architectury-9.2.14-forge.jar       →  "architectury"
     *   create-mc1.20.1_v0.5.1f.jar         →  "create"
     */
    public static String extractModPrefix(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();

        // 强制前缀：对 FTB 系列等 mod 直接使用特定关键字作为前缀
        for (String kw : FORCE_PREFIX_KEYWORDS) {
            if (lower.contains(kw)) {
                return kw;
            }
        }

        String name = lower;
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.isEmpty()) return null;

        // 找第一个是数字或"."的位置作为版本号开始的位置
        StringBuilder prefix = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!started) {
                // 跳过开头的非字母字符
                if (Character.isLetter(c)) {
                    started = true;
                    prefix.append(c);
                }
            } else {
                // 已经开始收集前缀了
                if (Character.isDigit(c) && i > 0 && (name.charAt(i - 1) == '-' || name.charAt(i - 1) == '_' || name.charAt(i - 1) == '.')) {
                    // 碰到 "名称-数字" 的分界：例如 jei-1.20.1...
                    break;
                }
                // 允许字母、'-'、'_'，但如果连续出现数字且前面已经是 '-' 就认为开始版本号
                if (Character.isDigit(c)) {
                    // 检查是否已经进入版本号模式（前一个字符不是字母）
                    boolean inVersionMode = false;
                    int digitLen = 0;
                    for (int j = i; j < name.length() && (Character.isDigit(name.charAt(j)) || name.charAt(j) == '.'); j++) {
                        digitLen++;
                        if (digitLen >= 2) { inVersionMode = true; break; }
                    }
                    if (inVersionMode) break;
                    // 单个数字当作名字的一部分继续保留
                    prefix.append(c);
                } else if (Character.isLetter(c) || c == '-' || c == '_') {
                    prefix.append(c);
                } else {
                    break; // 其他字符（如中文、括号）停止
                }
            }
        }

        String result = prefix.toString().replaceAll("[-_]+$", "").trim();
        if (result.isEmpty()) return null;
        return result;
    }

    private static Path getMarkerFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(DEPENDENCY_MARKER_FILE);
    }

    private static Path getDisabledMarkerDir() {
        return FMLPaths.CONFIGDIR.get().resolve("qlmzombie_disabled");
    }

    private static boolean shouldExclude(String fileName) {
        String lower = fileName.toLowerCase();
        for (String pattern : EXCLUDE_PATTERNS) {
            if (lower.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ConflictGroup> findConflictFor(String fileName) {
        String lower = fileName.toLowerCase();
        for (ConflictGroup group : CONFLICT_GROUPS) {
            for (String keyword : group.keywords()) {
                if (lower.contains(keyword.toLowerCase())) {
                    return Optional.of(group);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 扫描内部 libs 目录中的所有 jar 文件
     */
    public static List<String> scanInternalLibs() {
        List<String> found = new ArrayList<>();
        ClassLoader classLoader = ModDependencyHandler.class.getClassLoader();

        try {
            // 尝试从 classpath 扫描已知文件名
            Path libsDirOnDisk = FMLPaths.MODSDIR.get().getParent().resolve("libs");
            if (Files.exists(libsDirOnDisk)) {
                try (Stream<Path> stream = Files.walk(libsDirOnDisk)) {
                    List<Path> jars = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".jar"))
                        .collect(Collectors.toList());
                    for (Path p : jars) {
                        String name = p.getFileName().toString();
                        if (!shouldExclude(name)) {
                            found.add(name);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 同时使用手动定义的内部 jar 列表（基于 build.gradle 中的依赖）
        // 这是主要方式，因为打包后 jar 文件位于 qlmzombie.jar 内的 libs/ 目录
        List<String> knownInternalJars = List.of(
            // --------- 必需依赖（在 build.gradle 中定义） ---------
            "kubejs-forge-2001.6.5-build.26.jar",
            "rhino-forge-2001.2.3-build.10.jar",
            "architectury-9.2.14-forge.jar",
            "Enhanced-Celestials-forge-1.20.1-5.0.3.2.jar",
            "cloth-config-11.1.136-forge.jar",
            "Data_Anchor-forge-1.20.1-1.0.0.20.jar",
            "Corgilib-Forge-1.20.1-4.0.3.4.jar",
            "kubejsadditions-forge-4.3.4.jar",
            "lootjs-forge-1.20.1-2.13.1.jar",
            "taczjs-forge-1.4.2+mc1.20.1.jar",
            "tacz-1.20.1-1.1.8-hotfix.jar",
            "playerengine-forge-1.20.1-1.4.0.jar",
            "player2npc-forge-1.20.1-1.4.0.jar",
            "AdvancedSkillsRe-forge-1.1.0-beta.1.jar",
            "kotlinforforge-4.12.0-all.jar",

            // --------- 可选依赖（在 libs 文件夹中的其他 mod） ---------
            "blueprint-1.20.1-7.1.4.jar",
            "CreativeCore_FORGE_v2.12.39_mc1.20.1.jar",
            "Fastload-Reforged-mc1.20.1-3.4.0.jar",
            "ForgeConfigAPIPort-v8.0.3-1.20.1-Fabric.jar",
            "ForgeConfigScreens-v8.0.2-1.20.1-Forge.jar",
            "[AI改进] AI-Improvements-1.20-0.5.2.jar",
            "[拆解台] uncrafting_table-1.20.1-forge-1.1.0.jar",
            "[车万女仆附属：爱恋] touhou-maid-affection-1.7.2.2.jar",
            "[女仆实用任务] 1.20.1-maid_useful_task-1.4.2.jar",
            "[玩家救援] PlayerRevive_FORGE_v2.0.31_mc1.20.1.jar",
            "[真正的力量] True_POWER-1.20.1-1.1.8.jar",
            "[精妙核心] sophisticatedcore-1.20.1-1.3.65.2126.jar",
            "[精妙背包] sophisticatedbackpacks-1.20.1-3.24.59.1960.jar",
            "[经验机制改革] Clumps-forge-1.20.1-12.0.0.4.jar",
            "[配方性能优化] FastSuite-1.20.1-5.1.2.jar",
            "[铁氧体磁芯] ferritecore-6.0.1-forge.jar",
            "[附魔描述] EnchantmentDescriptions-Forge-1.20.1-17.1.21.jar",
            "[骷髅 AI 修复] SkeletonAIFix-v20.1.1-1.20.1-Forge.jar",
            "appliedenergistics2-forge-15.4.10.jar",
            "badpackets-forge-0.4.3.jar",
            "balm-forge-1.20.1-7.3.39-all.jar",
            "cofh_core-1.20.1-11.0.2.56.jar",
            "curios-forge-5.14.1+1.20.1.jar",
            "embeddium-0.3.31+mc1.20.1.jar",
            "enhancedai-3.3.7.3.jar",
            "fastboot-1.20.x-1.2.jar",
            "geckolib-forge-1.20.1-4.8.4.jar",
            "insanelib-1.23.4.6.jar",
            "kleiders_custom_renderer-7.4.1-forge-1.20.1.jar",
            "moonlight-1.20-2.16.34-forge.jar",
            "oculus-mc1.20.1-1.8.0.jar",
            "player-animation-lib-forge-1.0.2-rc1+1.20.jar",
            "sodiumoptionsapi-forge-1.0.10-1.20.1.jar",
            "thermal_foundation-1.20.1-11.0.6.70.jar",
            "zac-1.1.0.jar",
            "environmental-1.20.1-4.1.2.jar",
            "[夸克] Quark-4.0-462.jar",
            "Quark-4.0-462.jar",
            "Zeta-1.0-31.jar",
            "artifacts-forge-9.5.19.jar",
            "mutil-1.20.1-6.3.0.jar",
            "tetra-1.20.1-6.15.0.jar",
            "ftb-library-forge-2001.2.13.jar",
            "[FTB 任务] ftb-quests-forge-2001.4.22.jar",
            "[FTB 团队] ftb-teams-forge-2001.3.2.jar",
            "ftb-chunks-forge-2001.3.8.jar",
            "SimpleStorageNetwork-1.20.1-1.11.3.jar",
            "flib-1.20.1-0.0.14.jar",
            "refinedstorage-1.12.4.jar",
            "EnderIO-1.20.1-6.2.18-beta-all.jar",
            "forestry-1.20.1-2.10.2.jar",
            "Botania-1.20.1-454-FORGE.jar",
            "bloodmagic-1.20.1-3.3.7-49.jar",
            "Patchouli-1.20.1-85-FORGE.jar",
            "pneumaticcraft-repressurized-6.0.22+mc1.20.1.jar",
            "ImmersiveEngineering-1.20.1-10.2.0-183.jar",
            "cofh_core-1.20.1-11.0.2.56.jar",
            "thermal_foundation-1.20.1-11.0.6.70.jar",
            "guideme-20.1.15.jar",
            "appliedenergistics2-forge-15.4.10.jar",
            "Mekanism-1.20.1-10.4.16.80.jar",
            "create-1.20.1-6.0.8.jar",
            "StorageDrawers-forge-1.20.1-12.14.3.jar",
            "ironchest-1.20.1-14.4.4.jar",
            "badpackets-forge-0.4.3.jar",
            "wthit-1.20.1-forge-8.21.1.jar",
            "jei-1.20.1-forge-15.20.0.133.jar",
            "[肉多多] dropthemeat-1.7.1.jar",
            "itemphysicguns-1.0.3-7686b43.jar",
            "[帕秋莉手册] Patchouli-1.20.1-85-FORGE.jar",
            "[卓越前线] superbwarfare-0.8.9-final-mc1.20.1-6effe4385-all.jar",
            "[物品物理掉落] ItemPhysic_FORGE_v1.8.13_mc1.20.1.jar",
            "[全键无冲] NonConflictKeys-Forge-1.19.X-1.20-1.0.0.jar",
            "balm-forge-1.20.1-7.3.39-all.jar",
            "[农夫乐事] FarmersDelight-1.20.1-1.3.2.jar",
            "collective-1.20.1-8.39.jar",
            "[精妙核心] sophisticatedcore-1.20.1-1.3.65.2126.jar",
            "[精妙背包] sophisticatedbackpacks-1.20.1-3.24.59.1960.jar",
            "moonlight-1.20-2.16.34-forge.jar",
            "[附魔描述] EnchantmentDescriptions-Forge-1.20.1-17.1.21.jar",
            "Bookshelf-Forge-1.20.1-20.2.15.jar",
            "[经验机制改革] Clumps-forge-1.20.1-12.0.0.4.jar",
            "kotlinforforge-4.12.0-all.jar",
            "fastboot-1.20.x-1.2.jar",
            "[崩溃漏洞修复] crashexploitfixer-forge-1.1.0+1.20.1.jar",
            "[镭] radium-mc1.20.1-0.12.4+git.26c9d8e.jar",
            "[崩溃助手] CrashAssistant-forge-1.19-1.20.1-1.11.10.jar",
            "[星光] starlight-1.1.2+forge.1cda73c.jar",
            "Fastload-Reforged-mc1.20.1-3.4.0.jar",
            "[钠／Embeddium：动态光源] sodiumdynamiclights-forge-1.0.10-1.20.1.jar",
            "[配方性能优化] FastSuite-1.20.1-5.1.2.jar",
            "sodiumoptionsapi-forge-1.0.10-1.20.1.jar",
            "[熔炉性能优化] FastFurnace-1.20.1-8.0.2.jar",
            "Placebo-1.20.1-8.6.3.jar",
            "[工作台性能优化] FastWorkbench-1.20.1-8.0.4.jar",
            "[现代化修复] modernfix-forge-5.27.58+mc1.20.1.jar",
            "[铁氧体磁芯] ferritecore-6.0.1-forge.jar",
            "[REI物品管理器] RoughlyEnoughItems-12.1.785-forge.jar",
            "[实体纹理特性] entity_texture_features_1.20.1-forge-7.1.jar",
            "[实体模型特性] entity_model_features-3.2.4-1.20.1-forge.jar",
            "kleiders_custom_renderer-7.4.1-forge-1.20.1.jar",
            "3d-armor-0.9.4.1-mod.jar",
            "[3D 皮肤层] skinlayers3d-forge-1.11.2-mc1.20.1.jar",
            "[斯巴达的武器] SpartanWeaponry-1.20.1-forge-3.2.1-all.jar",
            "bettercombat-forge-1.9.0+1.20.1.jar",
            "spartantoolkit-1.20.1-1.6.1.jar",
            "[斯巴达之盾] SpartanShields-1.20.1-forge-3.1.1.jar",
            "footwork-4.3.9.jar",
            "[旅人标题] TravelersTitles-1.20-Forge-4.0.2.jar",
            "YungsApi-1.20-Forge-4.0.6.jar",
            "[旅行地图] journeymap-forge-1.20.1-6.0.0-beta.3.jar",
            "oculus-mc1.20.1-1.8.0.jar",
            "embeddium-0.3.31+mc1.20.1.jar",
            "geckolib-forge-1.20.1-4.8.4.jar",
            "curios-forge-5.14.1+1.20.1.jar",
            "ForgeConfigAPIPort-v8.0.3-1.20.1-Fabric.jar",
            "ForgeConfigScreens-v8.0.2-1.20.1-Forge.jar",
            "PuzzlesLib-v8.1.33-1.20.1-Forge.jar",
            "[骷髅 AI 修复] SkeletonAIFix-v20.1.1-1.20.1-Forge.jar",
            "insanelib-1.23.4.6.jar",
            "enhancedai-3.3.7.3.jar",
            "[AI改进] AI-Improvements-1.20-0.5.2.jar",
            "[玉 🔍] Jade-1.20.1-Forge-11.13.2.jar",
            "Zombie Island 1.20.1 0.1.3.5.jar",
            "Infectious-forge-1.20.1-1.7.jar",
            "zac-1.1.0.jar",
            "[是，史蒂夫模型] ysm-2.6.5-forge+mc1.20.1-release.jar",
            "[女仆实用任务] 1.20.1-maid_useful_task-1.4.2.jar",
            "[玩家救援] PlayerRevive_FORGE_v2.0.31_mc1.20.1.jar",
            "CreativeCore_FORGE_v2.12.39_mc1.20.1.jar",
            "[车万女仆附属：爱恋] touhou-maid-affection-1.7.2.2.jar",
            "[车万女仆：真正的力量] True_POWER_of_Maid-1.20.1-1.2.2.jar",
            "[真正的力量] True_POWER-1.20.1-1.1.8.jar",
            "player-animation-lib-forge-1.0.2-rc1+1.20.jar",
            "[拔刀剑：重锋] SlashBladeResharped-1.20.1-1.9.65.jar",
            "mrqxs_Slashblade_Core-1.20.1-1.4.1.jar",
            "[车万女仆] touhoulittlemaid-1.5.3-forge+mc1.20.1.jar",
            "AdvancedSkillsRe-forge-1.1.0-beta.1.jar",
            "[旅行地图] journeymap-1.20.1-5.10.3-forge.jar",
            "[夸克-奇思妙想] QuarkOddities-1.20.1.jar",
            "dyairdrop-1.1.0-1.20.1-beta.jar",
            "bettercombat-forge-1.9.0+1.20.1.jar",
            "spartantoolkit-1.20.1-1.6.1.jar",
            "[车万女仆：真正的力量] True_POWER_of_Maid-1.20.1-1.2.2.jar",
            "[拔刀剑：重锋] SlashBladeResharped-1.20.1-1.9.65.jar",
            "mrqxs_Slashblade_Core-1.20.1-1.4.1.jar",
            "[车万女仆] touhoulittlemaid-1.5.3-forge+mc1.20.1.jar"
        );

        Set<String> result = new LinkedHashSet<>(found);
        for (String name : knownInternalJars) {
            if (!shouldExclude(name)) {
                result.add(name);
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * 检查 mods 目录中已存在的 jar，用于判断是否需要释放
     */
    public static Set<String> getExistingModsIn(Path modsDir) {
        Set<String> existing = new HashSet<>();
        try (Stream<Path> stream = Files.list(modsDir)) {
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".jar.disabled"))
                .collect(Collectors.toList());
            for (Path p : files) {
                String name = p.getFileName().toString();
                existing.add(name);
                // 同时记录不带 .disabled 后缀的名字
                if (name.endsWith(DISABLED_MARKER)) {
                    existing.add(name.substring(0, name.length() - DISABLED_MARKER.length()));
                }
            }
        } catch (Exception ignored) {
        }
        return existing;
    }

    /**
     * 从内部 jar 释放 mod 到 mods 目录
     */
    public static boolean extractModFromJar(String modFileName, Path targetDir) {
        String internalPath = LIBS_INTERNAL_PATH + modFileName;

        InputStream inputStream = null;
        try {
            inputStream = ModDependencyHandler.class.getClassLoader().getResourceAsStream(internalPath);
        } catch (Exception ignored) {
        }

        if (inputStream == null) {
            try {
                inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(internalPath);
            } catch (Exception ignored) {
            }
        }

        if (inputStream == null) {
            return false;
        }

        Path targetPath = targetDir.resolve(modFileName);

        try {
            if (Files.notExists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            if (Files.exists(targetPath)) {
                Files.delete(targetPath);
            }
            try (InputStream is = inputStream) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("[QLM Zombie] 已从内部释放: {}", modFileName);
            return true;
        } catch (IOException e) {
            LOGGER.warn("[QLM Zombie] 释放失败 {}: {}", modFileName, e.getMessage());
            return false;
        }
    }

    /**
     * 执行核心初始化：扫描 → 释放 → 冲突检测 → 禁用
     */
    public static synchronized void initializeFromLibs() {
        if (initialized) return;
        initialized = true;

        Path modsDir = FMLPaths.MODSDIR.get();
        List<String> internalLibs = scanInternalLibs();
        totalLibsCount = internalLibs.size();

        if (totalLibsCount == 0) {
            LOGGER.info("[QLM Zombie] 未在内部 libs 发现 jar，跳过自动释放");
            return;
        }

        LOGGER.info("[QLM Zombie] 扫描到内部 {} 个 mod 文件，开始自动释放...", totalLibsCount);

        Set<String> existingMods = getExistingModsIn(modsDir);
        List<String> releasedNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();

        for (String libFileName : internalLibs) {
            // 检查是否已存在（包括原始文件或 .disabled 状态）
            if (existingMods.contains(libFileName)) {
                skippedNames.add(libFileName);
                continue;
            }
            boolean ok = extractModFromJar(libFileName, modsDir);
            if (ok) {
                releasedNames.add(libFileName);
                releasedCount++;
            } else {
                failedNames.add(libFileName);
            }
        }

        if (!releasedNames.isEmpty()) {
            LOGGER.info("[QLM Zombie] 成功释放 {} 个 mod", releasedNames.size());
        }
        if (!skippedNames.isEmpty()) {
            LOGGER.info("[QLM Zombie] {} 个 mod 已存在，跳过", skippedNames.size());
        }
        if (!failedNames.isEmpty()) {
            LOGGER.warn("[QLM Zombie] {} 个 mod 释放失败（可能未打包进 jar）: {}",
                failedNames.size(), failedNames.stream().limit(5).collect(Collectors.joining(", ")));
        }

        // 释放完成后进行冲突检测
        detectAndResolveConflicts(modsDir, internalLibs);

        // 写入标记（下次启动不再提示）
        if (!releasedNames.isEmpty() || !disabledMods.isEmpty() || !deletedDuplicates.isEmpty()) {
            com.qlm.zombie.QLMZombieMod.needsRestart = true;
            writeDependencyMarker();
            LOGGER.info("[QLM Zombie] mod 初始化完成，提示玩家重启游戏");
        }
    }

    /**
     * 在 mods 目录中检测冲突 mod 并自动禁用
     */
    public static void detectAndResolveConflicts(Path modsDir, List<String> libFiles) {
        // 收集 mods 目录当前所有 mod（包括刚释放的 + 之前存在的）
        Map<String, Path> allJars = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(modsDir)) {
            List<Path> jars = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".jar"))
                .collect(Collectors.toList());
            for (Path p : jars) {
                allJars.put(p.getFileName().toString(), p);
            }
        } catch (Exception ignored) {
        }

        // ============= 重复 mod 检测（同一 mod，不同版本/文件名）=============
        // 策略：通过前缀识别同一 mod（如 jei-1.20.1-forge-15.2.0.27.jar 和 jei-1.20.1-forge-15.3.0.3.jar 视为重复）
        // 提取文件名中第一个"-"之前的部分作为"mod id"，或者对常见 mod 做关键字聚类
        Map<String, List<String>> duplicatesByPrefix = new LinkedHashMap<>();

        for (String fileName : allJars.keySet()) {
            String prefix = extractModPrefix(fileName);
            if (prefix == null || prefix.length() < 3) continue; // 太短的忽略（比如只有版本号）
            duplicatesByPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(fileName);
        }

        for (Map.Entry<String, List<String>> e : duplicatesByPrefix.entrySet()) {
            List<String> group = e.getValue();
            if (group.size() <= 1) continue;

            // 有重复：选择保留哪一个（策略：优先保留必要 mod 白名单，其次是 libs 中存在的，最后选文件名最长的）
            // 步骤 1：检查本组中有多少个白名单 mod
            int keepAlwaysCount = 0;
            for (String m : group) {
                if (isKeepAlways(m)) keepAlwaysCount++;
            }
            if (keepAlwaysCount > 0) {
                // 有白名单 mod → 全部保留（白名单 mod 不参与重复删除）
                // 但仍可能有非白名单的重复版本，对非白名单部分做删除
                LOGGER.info("[QLM Zombie] 检测到必要mod包含在重复组[{}]中，保留所有必要mod: {}",
                    e.getKey(), group.stream().filter(m -> isKeepAlways(m)).collect(Collectors.joining(", ")));
                // 只删除非白名单的多余 mod
                detectedConflicts.add("重复 mod [" + e.getKey() + "]: " + String.join(", ", group));
                for (String m : group) {
                    if (!isKeepAlways(m)) {
                        // 确认至少有一个白名单 mod 作为保留方，才可以删除这个非白名单的
                        // 简化：如果组里既有白名单又有非白名单，删除所有非白名单的；
                        // 如果全部是白名单，则都保留；
                        // 如果部分白名单、部分非白名单，删除非白名单的
                        if (keepAlwaysCount > 0) {
                            Path jarPath = allJars.get(m);
                            if (jarPath != null && Files.exists(jarPath)) {
                                try {
                                    Files.delete(jarPath);
                                    deletedDuplicates.add(m);
                                    LOGGER.info("[QLM Zombie] 重复 mod 已删除(非必要mod): {}", m);
                                } catch (IOException ex) {
                                    Path disabledPath = jarPath.getParent().resolve(m + DISABLED_MARKER);
                                    try {
                                        Files.move(jarPath, disabledPath, StandardCopyOption.REPLACE_EXISTING);
                                        disabledMods.add(m);
                                        LOGGER.info("[QLM Zombie] 重复 mod 无法删除，降级禁用: {}", m);
                                    } catch (IOException ignored2) {
                                    }
                                }
                            }
                        }
                    }
                }
                continue; // 本组处理完成，进入下一组
            }

            // 步骤 2：没有白名单 mod，按原有逻辑处理
            String keepName = null;
            // 优先级 1：保留 libs 中存在的
            for (String libName : libFiles) {
                for (String m : group) {
                    if (libName.equalsIgnoreCase(m) || libName.toLowerCase().contains(e.getKey().toLowerCase())) {
                        keepName = m;
                        break;
                    }
                }
                if (keepName != null) break;
            }
            // 优先级 2：选文件名最长的（通常是更新、更完整的版本）
            if (keepName == null) {
                int maxLen = 0;
                for (String m : group) {
                    if (m.length() > maxLen) { maxLen = m.length(); keepName = m; }
                }
            }

            detectedConflicts.add("重复 mod [" + e.getKey() + "]: " + String.join(", ", group));
            LOGGER.warn("[QLM Zombie] 检测到重复 mod [前缀: {}]: {}", e.getKey(), group);

            // 保留 keepName，删除其他的
            for (String m : group) {
                if (!m.equals(keepName) && !isKeepAlways(m)) {
                    Path jarPath = allJars.get(m);
                    if (jarPath != null && Files.exists(jarPath)) {
                        try {
                            Files.delete(jarPath);
                            deletedDuplicates.add(m);
                            LOGGER.info("[QLM Zombie] 重复 mod 已删除: {}", m);
                        } catch (IOException ex) {
                            Path disabledPath = jarPath.getParent().resolve(m + DISABLED_MARKER);
                            try {
                                Files.move(jarPath, disabledPath, StandardCopyOption.REPLACE_EXISTING);
                                disabledMods.add(m);
                                LOGGER.info("[QLM Zombie] 重复 mod 无法删除，降级禁用: {}", m);
                            } catch (IOException ignored2) {
                            }
                        }
                    }
                }
            }
        }

        // 对每个冲突组进行检测
        for (ConflictGroup group : CONFLICT_GROUPS) {
            List<String> matched = new ArrayList<>();
            for (Map.Entry<String, Path> entry : allJars.entrySet()) {
                String name = entry.getKey();
                String lower = name.toLowerCase();
                for (String keyword : group.keywords()) {
                    if (lower.contains(keyword.toLowerCase())) {
                        matched.add(name);
                        break;
                    }
                }
            }

            // 同一组内超过 1 个 → 存在冲突
            if (matched.size() > 1) {
                String conflictInfo = group.groupName() + ": " + String.join(", ", matched);
                detectedConflicts.add(conflictInfo);
                LOGGER.warn("[QLM Zombie] 检测到 mod 冲突: {} - {}", group.groupName(), matched);

                // 策略：保留第一个匹配的（通常在 libs 列表中排在前面的），其他重命名为 .disabled
                // 先找 libs 列表中哪个在前，优先保留
                String keepName = matched.get(0);
                for (String libName : libFiles) {
                    for (String m : matched) {
                        if (m.equals(libName) || libName.contains(
                            m.toLowerCase().replace(" ", "").replace("[", "").replace("]", "").substring(0, Math.min(8, m.length())))) {
                            // 简单匹配：如果 libs 中的名字和当前文件相似，则优先保留
                            keepName = m;
                            break;
                        }
                    }
                }

                // 禁用策略：针对某些冲突组使用特定优先级
                for (String m : matched) {
                    if (group.groupName().contains("物品管理器") && (m.toLowerCase().contains("rei-") || m.toLowerCase().contains("roughlyenough"))) {
                        keepName = m; // 优先保留 REI（玩家可自行调整，见 README.md）
                    }
                    if (group.groupName().contains("物品管理器") && m.toLowerCase().contains("jei-") && keepName.equals(matched.get(0)) && !(matched.stream().anyMatch(x -> x.toLowerCase().contains("rei-") || x.toLowerCase().contains("roughlyenough")))) {
                        keepName = m; // 没有 REI 时才保留 JEI
                    }
                    if (group.groupName().contains("悬停信息") && m.toLowerCase().contains("wthit-")) {
                        keepName = m; // 优先保留 WTHIT
                    }
                    if (group.groupName().contains("性能优化") && m.toLowerCase().contains("modernfix")) {
                        keepName = m; // 优先保留现代化修复
                    }
                    if (group.groupName().contains("加载器不兼容")) {
                        keepName = "__NONE__"; // 加载器不兼容：全部禁用
                    }
                }

                for (String m : matched) {
                    if (!m.equals(keepName) && !isKeepAlways(m)) {
                        Path jarPath = allJars.get(m);
                        if (jarPath != null && Files.exists(jarPath)) {
                            Path disabledPath = jarPath.getParent().resolve(m + DISABLED_MARKER);
                            try {
                                Files.move(jarPath, disabledPath, StandardCopyOption.REPLACE_EXISTING);
                                disabledMods.add(m);
                                LOGGER.info("[QLM Zombie] 冲突 mod 已禁用: {} → {}", m, disabledPath.getFileName());
                            } catch (IOException e) {
                                LOGGER.warn("[QLM Zombie] 无法禁用 mod {}: {}", m, e.getMessage());
                            }
                        }
                    }
                }

                if ("__NONE__".equals(keepName)) {
                    // 加载器不兼容：全部都禁用（但白名单保护仍然生效）
                    for (String m : matched) {
                        if (isKeepAlways(m)) continue;
                        Path jarPath = allJars.get(m);
                        if (jarPath != null && Files.exists(jarPath)) {
                            Path disabledPath = jarPath.getParent().resolve(m + DISABLED_MARKER);
                            try {
                                Files.move(jarPath, disabledPath, StandardCopyOption.REPLACE_EXISTING);
                                disabledMods.add(m);
                                LOGGER.info("[QLM Zombie] 加载器不兼容 mod 已禁用: {}", m);
                            } catch (IOException e) {
                                LOGGER.warn("[QLM Zombie] 无法禁用 mod {}: {}", m, e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        if (!disabledMods.isEmpty()) {
            LOGGER.info("[QLM Zombie] 本次自动禁用 {} 个冲突 mod，需重启游戏生效", disabledMods.size());
        }
    }

    private static void writeDependencyMarker() {
        try {
            Path markerFile = getMarkerFilePath();
            Path configDir = markerFile.getParent();
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("timestamp=").append(System.currentTimeMillis()).append("\n");
            sb.append("total=").append(totalLibsCount).append("\n");
            sb.append("released=").append(releasedCount).append("\n");
            sb.append("conflicts=").append(String.join("|", detectedConflicts)).append("\n");
            sb.append("disabled=").append(String.join("|", disabledMods)).append("\n");
            Files.writeString(markerFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[QLM Zombie] 无法写入依赖标记: {}", e.getMessage());
        }
    }

    /**
     * 判断文件是否命中"必要 mod 白名单"，如果命中则不应该被删除或禁用。
     */
    private static boolean isKeepAlways(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String kw : KEEP_ALWAYS_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasConflicts() {
        return !detectedConflicts.isEmpty();
    }

    public static List<String> getDetectedConflicts() {
        return new ArrayList<>(detectedConflicts);
    }

    public static List<String> getDisabledMods() {
        return new ArrayList<>(disabledMods);
    }

    public static List<String> getDeletedDuplicates() {
        return new ArrayList<>(deletedDuplicates);
    }

    public static boolean hasDuplicates() {
        return !deletedDuplicates.isEmpty();
    }

    public static int getTotalLibsCount() {
        return totalLibsCount;
    }

    public static int getReleasedCount() {
        return releasedCount;
    }

    /**
     * 记录一个冲突组：组名 + 提示信息 + 关键字列表（任一命中就算此组）
     */
    public record ConflictGroup(String groupName, String hint, List<String> keywords) {
    }
}