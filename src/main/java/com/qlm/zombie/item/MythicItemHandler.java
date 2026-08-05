package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 神话级物品特殊能力处理器：
 * 1. 神话级武器/工具/盔甲可破坏基岩等不可破坏方块。
 * 2. 神话级物品无视游戏规则限制（出生点保护、领地保护、冒险模式等）。
 * 3. 脱下（不再持有/装备）神话级物品后，恢复正常限制。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MythicItemHandler {

    /**
     * 神话级物品左击不可破坏方块（基岩等）时直接破坏。
     * 同时允许在冒险模式下破坏方块（无视 mayBuild 限制）。
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level world = event.getLevel();
        if (world.isClientSide()) return;

        Player player = event.getEntity();
        if (player == null || player.isSpectator()) return;
        if (!hasMythicItem(player)) return;

        BlockPos pos = event.getPos();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;

        // 判断是否需要神话介入：方块不可破坏 或 玩家在冒险模式
        boolean unbreakable = state.getDestroySpeed(world, pos) < 0;
        boolean adventure = !player.getAbilities().mayBuild;
        if (!unbreakable && !adventure) return;

        // 手动掉落资源（无视 doTileDrops 规则）并移除方块
        BlockEntity be = world.getBlockEntity(pos);
        Block.dropResources(state, world, pos, be, player, player.getMainHandItem());
        world.removeBlock(pos, false);

        // 取消事件，阻止 Minecraft 默认处理
        event.setCanceled(true);
    }

    /**
     * 神话级物品无视保护规则取消（出生点保护 / 领地保护等）。
     * 使用最低优先级，确保在其他 mod 取消事件后仍可撤销。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!hasMythicItem(player)) return;
        if (event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    /**
     * 检查玩家是否持有（主手）或穿着（盔甲槽）任意神话级物品。
     * 脱下后返回 false，恢复正常游戏规则限制。
     */
    private static boolean hasMythicItem(Player player) {
        if (isMythic(player.getMainHandItem())) return true;
        for (ItemStack armor : player.getArmorSlots()) {
            if (isMythic(armor)) return true;
        }
        return false;
    }

    private static boolean isMythic(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!WeaponQuality.hasQuality(stack)) return false;
        WeaponQuality q = WeaponQuality.getQuality(stack);
        return q == WeaponQuality.MYTHIC;
    }
}
