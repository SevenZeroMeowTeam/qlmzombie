package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.advancements.AdvancementManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PlayerInitHandler {
    private static final Random RANDOM = new Random();
    private static final String INIT_TAG = "qlm_zombie_init";

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persistentData = player.getPersistentData();

        // 发送mod说明（每次登录都显示）
        sendModIntroduction(player);

        if (!persistentData.getBoolean(INIT_TAG)) {
            QLMZombieMod.LOGGER.info("[QLM Zombie] 玩家 {} 首次登录，发放初始物资...", player.getName().getString());
            giveStarterGear(player);
            persistentData.putBoolean(INIT_TAG, true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[QLM Zombie] 初始物资已发放！"), false);

            // 初始化成就系统
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                AdvancementManager.initializeAdvancements(serverPlayer);
            }
        }
    }

    private static void sendModIntroduction(Player player) {
        int peacefulDays = com.qlm.zombie.config.QLMConfig.PEACEFUL_DAYS.get();
        int normalDays = com.qlm.zombie.config.QLMConfig.NORMAL_DAYS.get();
        int hardDays = com.qlm.zombie.config.QLMConfig.HARD_DAYS.get();
        int extremeDays = com.qlm.zombie.config.QLMConfig.EXTREME_DAYS.get();

        player.displayClientMessage(Component.literal(""), false);
        player.displayClientMessage(Component.literal("§6===== [七零喵团队 SevenZeroMeowTeam 僵尸末日生存mod] ====="), false);

        // 依赖释放状态
        int total = ModDependencyHandler.getTotalLibsCount();
        int released = ModDependencyHandler.getReleasedCount();
        boolean hasConflicts = ModDependencyHandler.hasConflicts();
        boolean hasDups = ModDependencyHandler.hasDuplicates();
        List<String> conflicts = ModDependencyHandler.getDetectedConflicts();
        List<String> disabled = ModDependencyHandler.getDisabledMods();
        List<String> deleted = ModDependencyHandler.getDeletedDuplicates();
        boolean needsRestart = QLMZombieMod.needsRestart;

        if (total > 0) {
            player.displayClientMessage(Component.literal(
                    "§a✅ 已扫描 " + total + " 个内部 mod，释放 " + released + " 个"), false);
        }
        if (hasConflicts) {
            player.displayClientMessage(Component.literal(
                    "§6⚠ 检测到 " + conflicts.size() + " 组冲突，已自动禁用 " + disabled.size() + " 个 mod"), false);
            for (String c : conflicts) {
                player.displayClientMessage(Component.literal(
                        "  §7- " + c), false);
            }
            if (!disabled.isEmpty()) {
                player.displayClientMessage(Component.literal(
                        "  §7已禁用的文件位于 mods 目录中，后缀为 .jar.disabled"), false);
            }
        }
        if (hasDups) {
            player.displayClientMessage(Component.literal(
                    "§6⚠ 检测到 " + deleted.size() + " 个重复 mod，已自动删除（仅保留版本最完整的一个）"), false);
            for (String d : deleted) {
                player.displayClientMessage(Component.literal(
                        "  §7- 已删除: " + d), false);
            }
        }
        if (needsRestart) {
            player.displayClientMessage(Component.literal(
                    "§c⚠ 请重启游戏以加载新安装/禁用的 mod！"), false);
        }

        player.displayClientMessage(Component.literal("§e🌙 月相系统:"), false);
        player.displayClientMessage(Component.literal("  §f- 血月: 安全日后每14天一次，怪物激增，禁止睡觉"), false);
        player.displayClientMessage(Component.literal("  §f- 幸运之月: 7%概率，获得Luck II"), false);
        player.displayClientMessage(Component.literal("  §f- 丰收之月: 7%概率，作物加速生长"), false);
        player.displayClientMessage(Component.literal("§e⚔️ 难度阶段:"), false);
        player.displayClientMessage(Component.literal("  §a安全日(1-" + peacefulDays + "天): §f和平模式，无敌对生物"), false);
        player.displayClientMessage(
                Component.literal("  §a简单(" + (peacefulDays + 1) + "-" + normalDays + "天): §fEasy难度，僵尸低概率进化"), false);
        player.displayClientMessage(
                Component.literal("  §a普通(" + (normalDays + 1) + "-" + hardDays + "天): §fNormal难度，僵尸进化概率提升"), false);
        player.displayClientMessage(
                Component.literal("  §c困难(" + (hardDays + 1) + "-" + extremeDays + "天): §fHard难度锁定，僵尸高概率进化"), false);
        player.displayClientMessage(Component.literal("  §4极限(" + (extremeDays + 1) + "天+): §fHard难度锁定，僵尸极高概率进化"),
                false);
        player.displayClientMessage(Component.literal("§e👹 尸潮系统:"), false);
        player.displayClientMessage(Component.literal("  §f- 血月期间触发5波尸潮，难度递增"), false);
        player.displayClientMessage(Component.literal("  §f- 第3波起出现精英僵尸和骷髅"), false);
        player.displayClientMessage(Component.literal("  §f- 第5波出现尸潮领主(小Boss)"), false);
        player.displayClientMessage(Component.literal("  §f- 抵挡成功可获得丰厚奖励"), false);
        player.displayClientMessage(Component.literal("§e🎮 Boss 三阶段战斗:"), false);
        player.displayClientMessage(Component.literal("  §f- 阶段1(100%-67%HP): 基础属性，速度+2，伤害+3"), false);
        player.displayClientMessage(Component.literal("  §f- 阶段2(66%-34%HP): 狂暴状态，速度+3，伤害+4，聊天栏提示"), false);
        player.displayClientMessage(Component.literal("  §f- 阶段3(≤33%HP): 狂怒，速度+4，伤害+5，再生+抗性，名称变红"), false);
        player.displayClientMessage(Component.literal("§e🧟 AI 智能优化:"), false);
        player.displayClientMessage(Component.literal("  §f- 僵尸破门 (NORMAL阶段+)，破坏挡路方块，后期自动搭方块追击"), false);
        player.displayClientMessage(Component.literal("  §f- HARD阶段起: 自爆僵尸、木桶僵尸、§e💣TNT僵尸 §f(投掷点燃TNT)"), false);
        player.displayClientMessage(Component.literal("  §f- HARD阶段起: §d🧪药水僵尸 §f(投掷负面buff药水)、§5🔮僵尸召唤师 §f(召唤带buff僵尸)"),
                false);
        player.displayClientMessage(Component.literal("  §f- 僵尸/骷髅主动搜索最近玩家 (80格半径)"), false);
        player.displayClientMessage(Component.literal("  §f- 骷髅有几率射出必中箭 (带破甲+伤害增幅)"), false);
        player.displayClientMessage(Component.literal("  §f- NORMAL+阶段: §d⚔强化骷髅 §f(亡灵之弓/亡灵弩/骷髅之刃/亡灵之剑)"), false);
        player.displayClientMessage(Component.literal("  §f- 骷髅默认无限箭矢"), false);
        player.displayClientMessage(Component.literal("  §f- 村民 1% 概率成为 §2🛡 村庄守卫者 §f(攻击附近怪物保护村庄)"), false);
        player.displayClientMessage(Component.literal("  §f- 守卫者有 1% 概率获得 §d✦ 强化守护者之剑 ✦ §f(锋利III+火焰附加II+耐久III)"),
                false);
        player.displayClientMessage(Component.literal("§e🤖 AI 玩家 §7- v1.9.1"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer spawn [名字] [皮肤URL] 生成AI玩家"), false);
        player.displayClientMessage(Component.literal("  §f- 主世界随机生成AI玩家(默认每3分钟检测, 15%概率)"), false);
        player.displayClientMessage(Component.literal("  §f- 25%概率手持mod武器 + TACZ子弹无限"), false);
        player.displayClientMessage(Component.literal("  §f- 100+命名空间 + 300+关键词 覆盖libs几乎全部mod"), false);
        player.displayClientMessage(Component.literal("  §f- 覆盖: TACZ/拔刀剑/斯巴达/卓越前线/神器/血魔法"), false);
        player.displayClientMessage(Component.literal("  §f- 覆盖: 植物魔法/气动工艺/通用机械/沉浸工程/机械动力"), false);
        player.displayClientMessage(Component.literal("  §f- 驯服: 手持骨头/腐肉/熟肉/面包/苹果/金苹果右键, 33%概率"), false);
        player.displayClientMessage(Component.literal("  §f- 空手右键驯服AI: 打开真实背包GUI(27格+4护甲槽)"), false);
        player.displayClientMessage(Component.literal("  §f- 自动进食 | 自动装备 | 自动挖矿"), false);
        player.displayClientMessage(Component.literal("  §f- 自动拾取: 仅拾取自己击杀生物掉落的物品(不抢玩家战利品)"), false);
        player.displayClientMessage(Component.literal("  §f- 自动工作站: 工作台/熔炉/高炉/烟熏炉/锻造台/制箭台/切石机"), false);
        player.displayClientMessage(Component.literal("  §f- 探测: 砂轮/铁砧/酿造台/织布机/制图台"), false);
        player.displayClientMessage(Component.literal("  §f- 熔炉60+配方: 矿石/食物/建材全支持"), false);
        player.displayClientMessage(Component.literal("  §f- AI玩家属性: 100HP / 25攻击伤害"), false);
        player.displayClientMessage(Component.literal("  §f- 村民属性强化: 50HP / 15攻击伤害"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer list/tp/tame/skin/kill 管理命令"), false);
        player.displayClientMessage(Component.literal("  §f- 可在 qlmzombie-common.toml 的 [ai_player_spawn] 中调整生成参数"),
                false);
        player.displayClientMessage(Component.literal("§e⭐ 进阶技能: 重制版 §7- v1.9.1"), false);
        player.displayClientMessage(Component.literal("  §f- 开局赠送5点技能点，按K键打开技能树面板"), false);
        player.displayClientMessage(
                Component.literal("  §f- 武器检测支持JSON配置: assets/qlmzombie/data/weapon_detection.json"), false);
        player.displayClientMessage(Component.literal("  §f- 编辑JSON即可添加新mod武器，无需重新编译"), false);
        player.displayClientMessage(Component.literal("§e📦 mod 管理:"), false);
        player.displayClientMessage(Component.literal("  §f- 启动时自动从内部 libs 释放所有依赖mod"), false);
        player.displayClientMessage(Component.literal("  §f- 自动检测重复mod并删除多余副本（仅保留一个版本）"), false);
        player.displayClientMessage(Component.literal("  §f- 自动检测冲突mod（如JEI/REI、WTHIT/Jade），默认保留REI/WTHIT"), false);
        player.displayClientMessage(
                Component.literal("  §a- 白名单保护: FTB团队/任务/区块、Architectury、Cloth Config、Bookshelf等必要mod不受影响"), false);
        player.displayClientMessage(Component.literal("  §f- 如需重新启用被禁用的mod，请删除 .jar.disabled 后缀"), false);
        player.displayClientMessage(Component.literal("§e🎵 音乐系统:"), false);
        player.displayClientMessage(Component.literal("  §f- 登录时播放史诗开场主题"), false);
        player.displayClientMessage(Component.literal("  §f- 血月升起时播放紧张局势音乐"), false);
        player.displayClientMessage(Component.literal("  §f- 血月期间播放战斗音乐"), false);
        player.displayClientMessage(Component.literal("  §f- 非血月探索时播放冒险序曲"), false);
        player.displayClientMessage(Component.literal("  §f- 尸潮领主(Boss)分3阶段切换战斗音乐"), false);
        player.displayClientMessage(Component.literal("  §f- 可通过替换 assets/qlmzombie/music/ 下的文件自定义音乐"), false);
        player.displayClientMessage(Component.literal("  §f- 可在 qlmzombie-common.toml 的 [music] 中调整播放开关/音量/间隔"), false);
        player.displayClientMessage(Component.literal("§e🎒 建筑物宝箱(随机武器装备):"), false);
        player.displayClientMessage(Component.literal("  §f- 范围：沙漠神殿 / 丛林神殿 / 要塞 / 水下遗迹 / 沉船"), false);
        player.displayClientMessage(Component.literal("  §f- 范围：堡垒 / 废弃传送门 / 末地城 / 前哨站 / 古城 / 村庄铁匠等"), false);
        player.displayClientMessage(Component.literal("  §f- 注入物品：QLM 自制物资(强化零件/战术弹药/僵尸核心/生化样本等)"), false);
        player.displayClientMessage(Component.literal("  §f- 注入物品：TaCZ 现代枪械、弹药、配件及投掷物(若TaCZ已加载)"), false);
        player.displayClientMessage(Component.literal("  §f- 注入物品：Spartan Weaponry/Shields 中世纪武器/盾牌(若已加载)"), false);
        player.displayClientMessage(Component.literal("  §f- 注入物品：原版铁/钻石装备与附魔弓(即使无其他mod也正常)"), false);
        player.displayClientMessage(Component.literal("  §f- 可在 qlmzombie-common.toml 的 [building_loot] 中总开关/调整注入概率"),
                false);
        player.displayClientMessage(Component.literal("§e🏪 废弃商店:"), false);
        player.displayClientMessage(Component.literal("  §f- 主世界随机生成废弃商店结构(2%概率)，包含圆石建筑和橡木货架"), false);
        player.displayClientMessage(Component.literal("  §f- 奖励箱放置在货架顶部(1-2个)，30%概率在地面额外生成宝箱"), false);
        player.displayClientMessage(Component.literal("  §f- 宝箱有概率生成 TaCZ 枪械/弹药、Spartan 武器/盾牌等 mod 物品"), false);
        player.displayClientMessage(Component.literal("  §f- 包含原版物资、QLM 自制物资及全局战利品注入的 mod 物品"), false);
        player.displayClientMessage(Component.literal("§e📊 计分板 HUD:"), false);
        player.displayClientMessage(Component.literal("  §f- 游戏内左侧显示彩色计分板，每秒刷新一次"), false);
        player.displayClientMessage(Component.literal("  §f- 显示：当前天数、游戏时间（12/24小时制 + 时间段）"), false);
        player.displayClientMessage(Component.literal("  §f- 显示：当前难度阶段（如困难期[锁定]）和月相状态"), false);
        player.displayClientMessage(Component.literal("  §f- 血月时月相文字显示为红色"), false);
        player.displayClientMessage(Component.literal("§e💚 血量UI(经验条样式血量条):"), false);
        player.displayClientMessage(Component.literal("  §f- 屏幕底部中央显示彩色 HP 条 + 数字，位于盔甲/饱食度图标上方"), false);
        player.displayClientMessage(Component.literal("  §f- 已隐藏原版心形血量阵列，可在 qlmzombie-common.toml 的 [health_ui] 中切换"),
                false);
        player.displayClientMessage(Component.literal("  §f- 血条动态变色：>66%亮红 → 50-66%橙红 → 25-50%深红 → <25%警告闪烁红"), false);
        player.displayClientMessage(Component.literal("  §f- 金苹果/信标金色吸收HP: 血条末端叠加金色段"), false);
        player.displayClientMessage(Component.literal("§e⏱ 时间系统:"), false);
        player.displayClientMessage(Component.literal("  §f- 游戏内一天 = 57600 tick（2400 tick = 1 小时，约48分钟现实时间）"), false);
        player.displayClientMessage(Component.literal("  §f- 与原版MC(24000 tick/天)相比，天数推进节奏更慢，生存体验更紧凑"), false);
        player.displayClientMessage(Component.literal("  §f- 天数、难度阶段、血月间隔、成就里程碑等均基于此时间体系计算"), false);
        player.displayClientMessage(Component.literal("§e🚫 封禁内容:"), false);
        player.displayClientMessage(Component.literal("  §f- 怪物: 女巫、蜘蛛、洞穴蜘蛛、末影人"), false);
        player.displayClientMessage(Component.literal("  §f- 维度: 下界、末地（无法进入）"), false);
        player.displayClientMessage(Component.literal("§e🏆 成就系统:"), false);
        player.displayClientMessage(Component.literal("  §f- 挑战成就默认隐藏，完成后方可解锁"), false);
        player.displayClientMessage(Component.literal("  §f- 尸潮浪潮/尸潮征服者：在血月中成功抵挡5波尸潮获得"), false);
        player.displayClientMessage(Component.literal("  §f- 完成挑战后自动解锁新挑战并收到提示"), false);
        player.displayClientMessage(Component.literal("§e💡 提示:"), false);
        player.displayClientMessage(Component.literal("  §f- 血量低于10%时自动获得生命恢复II(60秒)，冷却5分钟"), false);
        player.displayClientMessage(Component.literal("  §f- 进化僵尸会显示红字名称，小心应对！"), false);
        player.displayClientMessage(Component.literal("  §f- 启动时自动从内部 libs 释放所有 mod"), false);
        player.displayClientMessage(Component.literal("  §f- 自动检测重复 mod 并删除多余副本"), false);
        player.displayClientMessage(Component.literal("  §f- 自动检测并禁用冲突 mod（如 JEI vs REI，默认保留 REI）"), false);
        player.displayClientMessage(Component.literal("  §f- 如需重新启用被禁用的 mod，请删除 .disabled 后缀"), false);
        player.displayClientMessage(Component.literal("  §f- 如需切换 JEI/REI 的偏好，请编辑源码中的冲突检测逻辑"), false);
        player.displayClientMessage(Component.literal("§e⛏ 连锁挖矿 / 连锁砍树:"), false);
        player.displayClientMessage(Component.literal("  §f- 持镐子挖矿石/石头 → 一键挖掉相连的同类方块"), false);
        player.displayClientMessage(Component.literal("  §f- 持铲子挖泥土/沙子/砂砾 → 一键挖掉相连的同类方块"), false);
        player.displayClientMessage(Component.literal("  §f- 持斧头砍原木 → 整棵树的原木一并掉落"), false);
        player.displayClientMessage(Component.literal("  §f- 支持所有mod工具（斯巴达武器/简单矿石等），自动识别工具类型"), false);
        player.displayClientMessage(Component.literal("  §f- 支持mod树木/矿石（如mod添加的原木/矿石均可连锁）"), false);
        player.displayClientMessage(Component.literal("  §f- 可在配置文件 qlmzombie-common.toml 的 [chain_mining] 中调整开关与上限"),
                false);
        player.displayClientMessage(Component.literal("§e📋 命令:"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm info §7查看当前状态"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm day §7查看当前天数"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm day <天数> §7设置天数(OP)"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm phase §7查看当前阶段"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm phases §7查看所有阶段"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm difficulty §7查看当前难度"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm mods §7查看mod状态及冲突"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm download §7重新释放所有内部mod(OP)"), false);
        player.displayClientMessage(Component.literal("§e🤖 AI 玩家 命令:"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer spawn [名字] [皮肤URL] §7生成AI玩家"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer list §7列出所有AI玩家"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer tp <名字> §7传送到指定AI玩家"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer tame <玩家> §7强制驯服最近AI玩家"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer skin <URL> §7设置最近AI玩家皮肤"), false);
        player.displayClientMessage(Component.literal("  §f- /qlm aiplayer kill §7移除最近AI玩家"), false);
        player.displayClientMessage(Component.literal("§6=========================================="), false);
        player.displayClientMessage(Component.literal(""), false);
    }

    private static void giveStarterGear(Player player) {
        player.getAttributes().getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                .setBaseValue(200.0D);
        player.setHealth(200.0F);

        giveArmorToSlot(player, Items.IRON_HELMET, EquipmentSlot.HEAD);
        giveArmorToSlot(player, Items.IRON_CHESTPLATE, EquipmentSlot.CHEST);
        giveArmorToSlot(player, Items.IRON_LEGGINGS, EquipmentSlot.LEGS);
        giveArmorToSlot(player, Items.IRON_BOOTS, EquipmentSlot.FEET);

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        makeUnbreakable(sword);
        addMultipleRandomEnchantments(sword, getSwordEnchantments(), 5);
        player.addItem(sword);

        ItemStack axe = new ItemStack(Items.IRON_AXE);
        makeUnbreakable(axe);
        addMultipleRandomEnchantments(axe, getAxeEnchantments(), 5);
        player.addItem(axe);

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        makeUnbreakable(pickaxe);
        addMultipleRandomEnchantments(pickaxe, getPickaxeEnchantments(), 5);
        player.addItem(pickaxe);

        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        makeUnbreakable(shovel);
        addMultipleRandomEnchantments(shovel, getShovelEnchantments(), 5);
        player.addItem(shovel);

        ItemStack bow = new ItemStack(Items.BOW);
        makeUnbreakable(bow);
        addMultipleRandomEnchantments(bow, getBowEnchantments(), 5);
        player.addItem(bow);

        player.addItem(new ItemStack(Items.ARROW, 64));
        player.addItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 48));
        player.addItem(new ItemStack(Items.BREAD, 64));

        giveSkillPoints(player, 5);

        QLMZombieMod.LOGGER.info("[QLM Zombie] 玩家 {} 初始物资发放完成", player.getName().getString());
    }

    private static void giveSkillPoints(Player player, int amount) {
        try {
            Class<?> playerUtilsKtClass = Class.forName("com.imoonday.advskills_re.util.PlayerUtilsKt");
            java.lang.reflect.Method getDataMethod = playerUtilsKtClass.getMethod("getData", Player.class);
            Object data = getDataMethod.invoke(null, player);

            if (data != null) {
                java.lang.reflect.Method getLevelMethod = data.getClass().getMethod("getLevel");
                Object level = getLevelMethod.invoke(data);

                java.lang.reflect.Method getExperienceMethod = level.getClass().getMethod("getExperience");
                int currentExp = (int) getExperienceMethod.invoke(level);

                java.lang.reflect.Method setExperienceMethod = level.getClass().getMethod("setExperience", int.class);
                setExperienceMethod.invoke(level, currentExp + amount);

                java.lang.reflect.Method setDirtyMethod = data.getClass().getMethod("setDirty", boolean.class);
                setDirtyMethod.invoke(data, true);

                java.lang.reflect.Method syncMethod = data.getClass().getMethod("sync");
                syncMethod.invoke(data);

                player.displayClientMessage(
                        Component.literal("§a[QLM Zombie] 已获得 §e" + amount + " §a点技能经验值（高级技能：重制版）！"), false);
                QLMZombieMod.LOGGER.info("[QLM Zombie] 玩家 {} 获得 {} 点技能经验值", player.getName().getString(), amount);
            }
        } catch (ClassNotFoundException e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] 高级技能mod未加载，跳过技能经验值发放");
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[QLM Zombie] 发放技能经验值失败: {}", e.getMessage());
        }
    }

    private static void giveArmorToSlot(Player player, net.minecraft.world.item.Item armorItem, EquipmentSlot slot) {
        ItemStack armor = new ItemStack(armorItem);
        makeUnbreakable(armor);
        addMultipleRandomEnchantments(armor, getArmorEnchantments(), 3);
        player.setItemSlot(slot, armor);
    }

    private static void makeUnbreakable(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 63);
    }

    private static Enchantment[] getSwordEnchantments() {
        return new Enchantment[] {
                Enchantments.SHARPNESS,
                Enchantments.SMITE,
                Enchantments.BANE_OF_ARTHROPODS,
                Enchantments.KNOCKBACK,
                Enchantments.FIRE_ASPECT,
                Enchantments.MOB_LOOTING,
                Enchantments.SWEEPING_EDGE,
                Enchantments.UNBREAKING,
                Enchantments.MENDING
        };
    }

    private static Enchantment[] getAxeEnchantments() {
        return new Enchantment[] {
                Enchantments.SHARPNESS,
                Enchantments.SMITE,
                Enchantments.BANE_OF_ARTHROPODS,
                Enchantments.KNOCKBACK,
                Enchantments.FIRE_ASPECT,
                Enchantments.BLOCK_EFFICIENCY,
                Enchantments.BLOCK_FORTUNE,
                Enchantments.UNBREAKING,
                Enchantments.MENDING
        };
    }

    private static Enchantment[] getPickaxeEnchantments() {
        return new Enchantment[] {
                Enchantments.BLOCK_EFFICIENCY,
                Enchantments.SILK_TOUCH,
                Enchantments.BLOCK_FORTUNE,
                Enchantments.UNBREAKING,
                Enchantments.MENDING
        };
    }

    private static Enchantment[] getShovelEnchantments() {
        return new Enchantment[] {
                Enchantments.BLOCK_EFFICIENCY,
                Enchantments.SILK_TOUCH,
                Enchantments.BLOCK_FORTUNE,
                Enchantments.UNBREAKING,
                Enchantments.MENDING
        };
    }

    private static Enchantment[] getBowEnchantments() {
        return new Enchantment[] {
                Enchantments.POWER_ARROWS,
                Enchantments.PUNCH_ARROWS,
                Enchantments.FLAMING_ARROWS,
                Enchantments.INFINITY_ARROWS,
                Enchantments.UNBREAKING,
                Enchantments.MENDING
        };
    }

    private static Enchantment[] getArmorEnchantments() {
        return new Enchantment[] {
                Enchantments.ALL_DAMAGE_PROTECTION,
                Enchantments.FIRE_PROTECTION,
                Enchantments.FALL_PROTECTION,
                Enchantments.BLAST_PROTECTION,
                Enchantments.PROJECTILE_PROTECTION,
                Enchantments.RESPIRATION,
                Enchantments.AQUA_AFFINITY,
                Enchantments.THORNS,
                Enchantments.DEPTH_STRIDER,
                Enchantments.FROST_WALKER,
                Enchantments.SOUL_SPEED,
                Enchantments.UNBREAKING,
                Enchantments.MENDING
        };
    }

    private static void addMultipleRandomEnchantments(ItemStack stack, Enchantment[] enchantments, int maxCount) {
        if (enchantments.length == 0)
            return;

        Map<Enchantment, Integer> existingEnchants = EnchantmentHelper.getEnchantments(stack);
        List<Enchantment> availableEnchants = new ArrayList<>();

        for (Enchantment ench : enchantments) {
            if (!existingEnchants.containsKey(ench)) {
                availableEnchants.add(ench);
            }
        }

        int count = Math.min(maxCount, availableEnchants.size());
        for (int i = 0; i < count; i++) {
            if (availableEnchants.isEmpty())
                break;

            int index = RANDOM.nextInt(availableEnchants.size());
            Enchantment enchantment = availableEnchants.remove(index);

            int maxLevel = enchantment.getMaxLevel();
            int level = maxLevel;

            if (maxLevel > 1) {
                level = RANDOM.nextInt(maxLevel) + 1;
            }

            existingEnchants.put(enchantment, level);
        }

        EnchantmentHelper.setEnchantments(existingEnchants, stack);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();

        if (original.getPersistentData().getBoolean(INIT_TAG)) {
            newPlayer.getPersistentData().putBoolean(INIT_TAG, true);
        }
    }
}