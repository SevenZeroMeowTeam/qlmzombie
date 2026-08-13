package com.qlm.zombie.skill;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 技能点系统
 * - 初始给予5技能点
 * - 通过成就奖励获得更多技能点
 * - 技能点可用于提升各种属性
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class SkillPointHandler {

    private static final String NBT_SKILL_POINTS = "qlm_skill_points";
    private static final String NBT_SKILL_TOTAL = "qlm_skill_total";
    private static final String NBT_SKILL_SPENT = "qlm_skill_spent";
    private static final String NBT_INITIAL_GIFT = "qlm_skill_initial_gift";

    private static final int INITIAL_POINTS = 5;

    private static final Map<UUID, PlayerSkillData> playerData = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        loadData(player);
    }

    private static void loadData(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag tag = persistent.getCompound(NBT_SKILL_POINTS);

        PlayerSkillData data = new PlayerSkillData();
        data.points = tag.getInt("points");
        data.totalPoints = tag.getInt("totalPoints");
        data.spentPoints = tag.getInt("spentPoints");
        data.initialGift = tag.getBoolean("initialGift");

        // 首次登录给予5技能点
        if (!data.initialGift) {
            data.initialGift = true;
            data.points += INITIAL_POINTS;
            data.totalPoints += INITIAL_POINTS;
            player.sendSystemMessage(Component.literal("§a✔ 获得 " + INITIAL_POINTS + " 初始技能点！使用 /qlm skill 查看"));
        }

        playerData.put(player.getUUID(), data);
        saveData(player, data);
    }

    private static void saveData(ServerPlayer player, PlayerSkillData data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("points", data.points);
        tag.putInt("totalPoints", data.totalPoints);
        tag.putInt("spentPoints", data.spentPoints);
        tag.putBoolean("initialGift", data.initialGift);
        player.getPersistentData().put(NBT_SKILL_POINTS, tag);
    }

    /** 添加技能点 */
    public static void addPoints(ServerPlayer player, int amount) {
        PlayerSkillData data = playerData.get(player.getUUID());
        if (data == null) return;
        data.points += amount;
        data.totalPoints += amount;
        saveData(player, data);
    }

    /** 花费技能点 */
    public static boolean spendPoints(ServerPlayer player, int amount) {
        PlayerSkillData data = playerData.get(player.getUUID());
        if (data == null || data.points < amount) return false;
        data.points -= amount;
        data.spentPoints += amount;
        saveData(player, data);
        return true;
    }

    /** 获取可用技能点 */
    public static int getPoints(ServerPlayer player) {
        PlayerSkillData data = playerData.get(player.getUUID());
        return data != null ? data.points : 0;
    }

    /** 显示技能点信息 */
    public static void showSkillInfo(ServerPlayer player) {
        PlayerSkillData data = playerData.get(player.getUUID());
        if (data == null) return;

        player.sendSystemMessage(Component.literal("§6§l===== 技能点信息 ====="));
        player.sendSystemMessage(Component.literal("§e可用技能点: §b" + data.points));
        player.sendSystemMessage(Component.literal("§e总获取技能点: §b" + data.totalPoints));
        player.sendSystemMessage(Component.literal("§e已花费技能点: §b" + data.spentPoints));
        player.sendSystemMessage(Component.literal("§7技能点可通过完成成就获得"));
    }

    private static class PlayerSkillData {
        int points = 0;
        int totalPoints = 0;
        int spentPoints = 0;
        boolean initialGift = false;
    }
}