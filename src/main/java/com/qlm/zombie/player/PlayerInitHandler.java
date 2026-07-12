package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.advancements.AdvancementManager;
import net.minecraft.nbt.CompoundTag;
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

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(""), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6===== [七零喵团队 SevenZeroMeowTeam 僵尸末日生存mod] ====="), false);

        // 依赖释放状态
        int total = com.qlm.zombie.dependency.ModDependencyHandler.getTotalLibsCount();
        int released = com.qlm.zombie.dependency.ModDependencyHandler.getReleasedCount();
        boolean hasConflicts = com.qlm.zombie.dependency.ModDependencyHandler.hasConflicts();
        boolean hasDups = com.qlm.zombie.dependency.ModDependencyHandler.hasDuplicates();
        java.util.List<String> conflicts = com.qlm.zombie.dependency.ModDependencyHandler.getDetectedConflicts();
        java.util.List<String> disabled = com.qlm.zombie.dependency.ModDependencyHandler.getDisabledMods();
        java.util.List<String> deleted = com.qlm.zombie.dependency.ModDependencyHandler.getDeletedDuplicates();
        boolean needsRestart = com.qlm.zombie.QLMZombieMod.needsRestart;

        if (total > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§a✅ 已扫描 " + total + " 个内部 mod，释放 " + released + " 个"
            ), false);
        }
        if (hasConflicts) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6⚠ 检测到 " + conflicts.size() + " 组冲突，已自动禁用 " + disabled.size() + " 个 mod"
            ), false);
            for (String c : conflicts) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "  §7- " + c
                ), false);
            }
            if (!disabled.isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "  §7已禁用的文件位于 mods 目录中，后缀为 .jar.disabled"
                ), false);
            }
        }
        if (hasDups) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§6⚠ 检测到 " + deleted.size() + " 个重复 mod，已自动删除（仅保留版本最完整的一个）"
            ), false);
            for (String d : deleted) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "  §7- 已删除: " + d
                ), false);
            }
        }
        if (needsRestart) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§c⚠ 请重启游戏以加载新安装/禁用的 mod！"
            ), false);
        }

        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e🌙 月相系统:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 血月: 安全日后每14天一次，怪物激增，禁止睡觉"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 幸运之月: 7%概率，获得Luck II"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 丰收之月: 7%概率，作物加速生长"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e⚔️ 难度阶段:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §a安全日(1-" + peacefulDays + "天): §f和平模式，无敌对生物"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §a简单(" + (peacefulDays + 1) + "-" + normalDays + "天): §fEasy难度，僵尸低概率进化"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §a普通(" + (normalDays + 1) + "-" + hardDays + "天): §fNormal难度，僵尸进化概率提升"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §c困难(" + (hardDays + 1) + "-" + extremeDays + "天): §fHard难度锁定，僵尸高概率进化"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §4极限(" + (extremeDays + 1) + "天+): §fHard难度锁定，僵尸极高概率进化"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e👹 尸潮系统:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 血月期间触发5波尸潮，难度递增"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 第3波起出现精英僵尸和骷髅"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 第5波出现尸潮领主(小Boss)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 抵挡成功可获得丰厚奖励"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e🎮 Boss 三阶段战斗:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 阶段1(100%-67%HP): 基础属性，速度+2，伤害+3"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 阶段2(66%-34%HP): 狂暴状态，速度+3，伤害+4，聊天栏提示"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 阶段3(≤33%HP): 狂怒，速度+4，伤害+5，再生+抗性，名称变红"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e🧟 AI 智能优化:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 僵尸破门 (NORMAL阶段+)，破坏挡路方块，后期自动搭方块追击"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- HARD阶段起: 自爆僵尸 (爆炸追击)、木桶僵尸 (击杀生成小型僵尸群)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 僵尸/骷髅主动搜索最近玩家 (80格半径)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 骷髅有几率射出必中箭 (带破甲+伤害增幅)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 村民 1% 概率成为 §2🛡 村庄守卫者 §f(攻击附近怪物保护村庄)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 守卫者有 1% 概率获得 §d✦ 强化守护者之剑 ✦ §f(锋利III+火焰附加II+耐久III)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e📦 mod 管理:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 启动时自动从内部 libs 释放所有依赖mod"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 自动检测重复mod并删除多余副本（仅保留一个版本）"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 自动检测冲突mod（如JEI/REI、WTHIT/Jade），默认保留REI/WTHIT"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §a- 白名单保护: FTB团队/任务/区块、Architectury、Cloth Config、Bookshelf等必要mod不受影响"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 如需重新启用被禁用的mod，请删除 .jar.disabled 后缀"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e🎵 音乐系统:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 登录时播放史诗开场主题"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 血月升起时播放紧张局势音乐"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 血月期间播放战斗音乐"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 非血月探索时播放冒险序曲"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 可通过替换 assets/qlmzombie/music/ 下的文件自定义音乐"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e📊 计分板 HUD:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 游戏内左侧显示彩色计分板，每秒刷新一次"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 显示：当前天数、游戏时间（12/24小时制 + 时间段）"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 显示：当前难度阶段（如困难期[锁定]）和月相状态"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 血月时月相文字显示为红色"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e🚫 封禁内容:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 怪物: 女巫、蜘蛛、洞穴蜘蛛、末影人"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 维度: 下界、末地（无法进入）"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e🏆 成就系统:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 挑战成就默认隐藏，完成后方可解锁"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 尸潮浪潮/尸潮征服者：在血月中成功抵挡5波尸潮获得"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 完成挑战后自动解锁新挑战并收到提示"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e💡 提示:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 血量低于10%时自动获得生命恢复II(60秒)，冷却5分钟"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 进化僵尸会显示红字名称，小心应对！"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 启动时自动从内部 libs 释放所有 mod"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 自动检测重复 mod 并删除多余副本"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 自动检测并禁用冲突 mod（如 JEI vs REI，默认保留 REI）"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 如需重新启用被禁用的 mod，请删除 .disabled 后缀"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 如需切换 JEI/REI 的偏好，请编辑源码中的冲突检测逻辑"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e⛏ 连锁挖矿 / 连锁砍树:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 持镐子挖矿石/石头 → 一键挖掉相连的同类方块"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 持铲子挖泥土/沙子/砂砾 → 一键挖掉相连的同类方块"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 持斧头砍原木 → 整棵树的原木一并掉落"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- 可在配置文件 qlmzombie-common.toml 的 [chain_mining] 中调整开关与上限"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e📋 命令:"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm info §7查看当前状态"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm day §7查看当前天数"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm phase §7查看当前阶段"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm phases §7查看所有阶段"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm difficulty §7查看当前难度"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm mods §7查看可选mod状态及冲突"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("  §f- /qlm day <天数> §7设置天数(OP)"), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6=========================================="), false);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(""), false);
    }

    private static void giveStarterGear(Player player) {
        player.getAttributes().getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0D);
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

        QLMZombieMod.LOGGER.info("[QLM Zombie] 玩家 {} 初始物资发放完成", player.getName().getString());
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
        return new Enchantment[]{
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
        return new Enchantment[]{
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
        return new Enchantment[]{
            Enchantments.BLOCK_EFFICIENCY,
            Enchantments.SILK_TOUCH,
            Enchantments.BLOCK_FORTUNE,
            Enchantments.UNBREAKING,
            Enchantments.MENDING
        };
    }

    private static Enchantment[] getShovelEnchantments() {
        return new Enchantment[]{
            Enchantments.BLOCK_EFFICIENCY,
            Enchantments.SILK_TOUCH,
            Enchantments.BLOCK_FORTUNE,
            Enchantments.UNBREAKING,
            Enchantments.MENDING
        };
    }

    private static Enchantment[] getBowEnchantments() {
        return new Enchantment[]{
            Enchantments.POWER_ARROWS,
            Enchantments.PUNCH_ARROWS,
            Enchantments.FLAMING_ARROWS,
            Enchantments.INFINITY_ARROWS,
            Enchantments.UNBREAKING,
            Enchantments.MENDING
        };
    }

    private static Enchantment[] getArmorEnchantments() {
        return new Enchantment[]{
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
        if (enchantments.length == 0) return;
        
        Map<Enchantment, Integer> existingEnchants = EnchantmentHelper.getEnchantments(stack);
        List<Enchantment> availableEnchants = new ArrayList<>();
        
        for (Enchantment ench : enchantments) {
            if (!existingEnchants.containsKey(ench)) {
                availableEnchants.add(ench);
            }
        }
        
        int count = Math.min(maxCount, availableEnchants.size());
        for (int i = 0; i < count; i++) {
            if (availableEnchants.isEmpty()) break;
            
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