package com.qlm.zombie.restriction;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 新手保护系统：
 * - 前25天（PEACE阶段）不生成任何敌对生物
 * - 保护新手玩家安全度过前期
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class NewbieProtectionHandler {

    // 新手保护天数
    private static final int PROTECTION_DAYS = 25;

    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        // 只保护主世界
        if (level.dimension() != Level.OVERWORLD) return;

        // 检查当前天数
        long day = level.getDayTime() / 24000L;
        if (day >= PROTECTION_DAYS) return; // 已过保护期

        // 只阻止敌对生物（实现 Enemy 接口的）
        if (entity instanceof Enemy) {
            event.setCanceled(true);
            QLMZombieMod.LOGGER.debug("[新手保护] 阻止敌对生物生成: {} (Day {})", 
                entity.getType().getDescriptionId(), day);
        }
    }

    /** 是否处于新手保护期 */
    public static boolean isInProtection(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return false;
        long day = level.getDayTime() / 24000L;
        return day < PROTECTION_DAYS;
    }
}