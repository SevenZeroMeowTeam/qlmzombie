package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
 * 神话级装备特殊能力处理器：
 *  - 可破坏任何不可破坏方块（基岩、末地传送门框架等）
 *  - 无视服务器保护规则（出生点保护 / 领地保护 / 冒险模式 mayBuild 限制）
 *  - 只对持有时/穿着神话装备（含神话品质 NBT）的玩家生效
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MythicItemHandler {

    /** 左键不可破坏方块 / 冒险模式下的方块 直接破坏并掉落资源 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level world = event.getLevel();
        if (world.isClientSide()) return;
        Player player = event.getEntity();
        if (player == null || player.isSpectator()) return;
        if (!hasMythic(player)) return;

        BlockPos pos = event.getPos();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;

        boolean unbreakable = state.getDestroySpeed(world, pos) < 0;
        boolean adventure = !player.getAbilities().mayBuild;
        if (!unbreakable && !adventure) return;

        BlockEntity be = world.getBlockEntity(pos);
        Block.dropResources(state, world, pos, be, player, player.getMainHandItem());
        world.removeBlock(pos, false);
        event.setCanceled(true);
    }

    /** 破坏事件取消了就撤销（其它mod的领地/保护） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!hasMythic(player) && event.isCanceled()) {
            // 非神话也有保护，避免误用；保留其他 mod 决定
        }
        if (hasMythic(player) && event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    /**
     * 神话能力判定：
     *  - 手持/副手神话武器或工具 → 立即生效（无视游戏规则、破坏基岩）
     *  - 神话盔甲 → 必须全套 4 件皆为神话品质才生效（缺一不可，少一个都不行）
     */
    public static boolean hasMythic(Player player) {
        if (isMythic(player.getMainHandItem())) return true;
        if (isMythic(player.getOffhandItem())) return true;
        // 神话盔甲套装：必须 4 件全套
        if (QualityEquipmentHandler.hasFullMythicArmor(player)) return true;
        return false;
    }

    /** 检查玩家神话盔甲套装完整性（缺一不可） */
    public static boolean hasFullMythicArmorSet(Player player) {
        return QualityEquipmentHandler.hasFullMythicArmor(player);
    }

    public static boolean isMythic(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        EquipmentQuality q = EquipmentQuality.fromStack(s);
        if (q != null && q.isIndestructible()) return true;
        return s.getTag() != null && s.getTag().getBoolean(EquipmentQuality.NBT_INDESTRUCT);
    }

    /** 判断某物品是否为神话武器/工具（非盔甲） */
    public static boolean isMythicWeaponOrTool(ItemStack s) {
        if (!isMythic(s)) return false;
        Item item = s.getItem();
        return item instanceof net.minecraft.world.item.SwordItem
                || item instanceof net.minecraft.world.item.DiggerItem
                || item instanceof net.minecraft.world.item.BowItem
                || item instanceof net.minecraft.world.item.CrossbowItem
                || item instanceof net.minecraft.world.item.TridentItem;
    }
}
