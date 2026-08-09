package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * 镐子能力处理器：
 * - OBSIDIAN_BREAKER：左键黑曜石/哭泣黑曜石时直接破坏并掉落
 * - RANGE_3X3 / RANGE_5X5：破坏方块时以玩家面向平面为中心，范围破坏同种方块
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PickaxeAbilityHandler {

    private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 黑曜石类方块集合 */
    private static boolean isObsidianLike(Block block) {
        return block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN;
    }

    // ==================== 黑曜石破坏 ====================

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;

        // 仅对镐子生效
        if (!(held.getItem() instanceof net.minecraft.world.item.PickaxeItem)) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        Block block = state.getBlock();

        // 黑曜石破坏能力
        if (isObsidianLike(block) && PickaxeAbility.hasAbility(held, PickaxeAbility.OBSIDIAN_BREAKER)) {
            Level level = (Level) event.getLevel();
            BlockPos pos = event.getPos();

            // 手动破坏方块并产生掉落物
            // destroyBlock(pos, true, player) → 移除方块 + dropResources
            level.destroyBlock(pos, true, player);

            // 消耗镐子耐久（黑曜石硬度高，消耗 2 点）
            if (!player.getAbilities().instabuild) {
                held.hurtAndBreak(2, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
            }

            // 阻止原版处理（避免重复破坏或无掉落）
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    // ==================== 范围挖掘 ====================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (Boolean.TRUE.equals(CHAINING.get())) return;

        Player player = event.getPlayer();
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        if (!(held.getItem() instanceof net.minecraft.world.item.PickaxeItem)) return;

        // 检查是否有范围挖掘能力
        boolean has3x3 = PickaxeAbility.hasAbility(held, PickaxeAbility.RANGE_3X3);
        boolean has5x5 = PickaxeAbility.hasAbility(held, PickaxeAbility.RANGE_5X5);
        if (!has3x3 && !has5x5) return;

        // 5x5 优先于 3x3
        int range = has5x5 ? 2 : 1; // range=1 → 3x3, range=2 → 5x5

        Level level = (Level) event.getLevel();
        BlockPos origin = event.getPos();
        BlockState originState = event.getState();
        Block targetBlock = originState.getBlock();
        boolean creative = player.getAbilities().instabuild;

        // 根据玩家面向方向确定挖掘平面
        Direction facing = player.getDirection(); // 水平方向
        float pitch = player.getXRot();

        // 判断平面：
        // pitch > 45（低头看）→ X-Z 水平面
        // pitch < -45（抬头看）→ X-Z 水平面
        // 否则 → 根据 facing 确定垂直平面
        int[] plane = getPlaneAxes(facing, pitch);

        CHAINING.set(Boolean.TRUE);
        try {
            doAreaMine(level, origin, targetBlock, plane, range, player, held, creative);
        } finally {
            CHAINING.set(Boolean.FALSE);
        }
    }

    /**
     * 根据玩家朝向确定挖掘平面的两个轴。
     * 返回 [axis1, axis2]，axis 用 0=X, 1=Y, 2=Z 表示。
     * 第三个轴（垂直于平面）固定不变。
     */
    private static int[] getPlaneAxes(Direction facing, float pitch) {
        // 低头或抬头看 → 水平面 X-Z
        if (Math.abs(pitch) > 45.0F) {
            return new int[]{0, 2}; // X, Z
        }
        // 面向南北（Z 轴方向）→ X-Y 平面
        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            return new int[]{0, 1}; // X, Y
        }
        // 面向东西（X 轴方向）→ Z-Y 平面
        return new int[]{2, 1}; // Z, Y
    }

    /**
     * 在指定平面上以原点为中心进行范围破坏。
     * 只破坏与原方块同种的方块。
     */
    private static void doAreaMine(Level level, BlockPos origin, Block targetBlock,
                                     int[] plane, int range,
                                     Player player, ItemStack held, boolean creative) {
        for (int d1 = -range; d1 <= range; d1++) {
            for (int d2 = -range; d2 <= range; d2++) {
                if (d1 == 0 && d2 == 0) continue; // 原方块由玩家主挖完成

                BlockPos pos = offsetByPlane(origin, plane, d1, d2);
                BlockState state = level.getBlockState(pos);

                // 仅破坏同种方块
                if (state.getBlock() != targetBlock) continue;
                if (state.isAir()) continue;
                if (!level.mayInteract(player, pos)) continue;

                // 生存模式消耗耐久
                if (!creative) {
                    if (held.isEmpty()) break;
                    held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
                }

                // 破坏方块并产生掉落物
                level.destroyBlock(pos, true, player);
            }
        }
    }

    /** 根据平面轴偏移 BlockPos */
    private static BlockPos offsetByPlane(BlockPos origin, int[] plane, int d1, int d2) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        // plane[0] 和 plane[1] 是两个轴，分别偏移 d1 和 d2
        switch (plane[0]) {
            case 0 -> x += d1;
            case 1 -> y += d1;
            case 2 -> z += d1;
        }
        switch (plane[1]) {
            case 0 -> x += d2;
            case 1 -> y += d2;
            case 2 -> z += d2;
        }
        return new BlockPos(x, y, z);
    }
}
