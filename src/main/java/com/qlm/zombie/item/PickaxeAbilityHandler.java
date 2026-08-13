package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * - 黑曜石 / 基岩破坏
 * - 范围挖掘：3×3 / 5×5 / 7×7 / 9×9 / 11×11
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PickaxeAbilityHandler {

    private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Set<Long>> SEEN_BLOCKS = ThreadLocal.withInitial(HashSet::new);

    private static boolean isObsidianLike(Block block) {
        return block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
    }

    /**
     * 左键时根据品质/能力允许破坏基岩、黑曜石、哭泣黑曜石。
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        if (!(held.getItem() instanceof net.minecraft.world.item.PickaxeItem)) return;

        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // --- 基岩破坏 ---
        if (block == Blocks.BEDROCK) {
            boolean byAbility = PickaxeAbility.hasAbility(held, PickaxeAbility.BEDROCK_BREAKER);
            boolean byQuality = held.getTag() != null && held.getTag().getBoolean(EquipmentQuality.NBT_BREAK_BEDRK);
            if (byAbility || byQuality) {
                level.destroyBlock(pos, true, player);
                if (!player.getAbilities().instabuild) {
                    held.hurtAndBreak(5, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
                }
                event.setUseBlock(Event.Result.DENY);
                event.setUseItem(Event.Result.DENY);
                event.setCanceled(true);
                return;
            }
        }

        // --- 黑曜石 / 哭泣黑曜石破坏 ---
        if (isObsidianLike(block)) {
            boolean byAbility = PickaxeAbility.hasAbility(held, PickaxeAbility.OBSIDIAN_BREAKER);
            boolean byQuality = held.getTag() != null && held.getTag().getBoolean(EquipmentQuality.NBT_OBSIDIAN);
            if (byAbility || byQuality) {
                level.destroyBlock(pos, true, player);
                if (!player.getAbilities().instabuild) {
                    held.hurtAndBreak(2, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
                }
                event.setUseBlock(Event.Result.DENY);
                event.setUseItem(Event.Result.DENY);
                event.setCanceled(true);
            }
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

        int rangeLvl = EquipmentQuality.getMineRangeLevel(held);
        if (rangeLvl <= 0) return;

        int radius = EquipmentQuality.RANGE_RADIUS[Math.min(rangeLvl, EquipmentQuality.RANGE_RADIUS.length - 1)];

        Level level = (Level) event.getLevel();
        BlockPos origin = event.getPos();
        Block originBlock = event.getState().getBlock();
        boolean creative = player.getAbilities().instabuild;

        Direction facing = player.getDirection();
        float pitch = player.getXRot();
        int[] plane = getPlaneAxes(facing, pitch);

        Set<Long> seen = SEEN_BLOCKS.get();
        seen.clear();
        seen.add(origin.asLong());

        CHAINING.set(Boolean.TRUE);
        try {
            doAreaMine(level, origin, originBlock, plane, radius, player, held, creative, seen);
        } finally {
            CHAINING.set(Boolean.FALSE);
            seen.clear();
        }
    }

    private static int[] getPlaneAxes(Direction facing, float pitch) {
        if (Math.abs(pitch) > 45.0F) return new int[]{0, 2}; // X-Z
        if (facing == Direction.NORTH || facing == Direction.SOUTH) return new int[]{0, 1}; // X-Y
        return new int[]{2, 1}; // Z-Y
    }

    private static void doAreaMine(Level level, BlockPos origin, Block targetBlock,
                                   int[] plane, int radius,
                                   Player player, ItemStack held, boolean creative,
                                   Set<Long> seen) {
        for (int d1 = -radius; d1 <= radius; d1++) {
            for (int d2 = -radius; d2 <= radius; d2++) {
                if (d1 == 0 && d2 == 0) continue;
                BlockPos pos = offsetByPlane(origin, plane, d1, d2);
                long key = pos.asLong();
                if (seen.contains(key)) continue;
                seen.add(key);

                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (state.isAir()) continue;
                if (!level.mayInteract(player, pos)) continue;

                // 允许破坏同种方块 OR 稀有以上品质（id>=5）无视同种限制破坏基岩/黑曜石
                boolean canBreak = (block == targetBlock);
                if (!canBreak) {
                    EquipmentQuality q = EquipmentQuality.fromStack(held);
                    if (q != null && q.canBreakObsidianByQuality() && isObsidianLike(block)) {
                        canBreak = true;
                    } else if (q != null && q.canBreakBedrockByQuality() && block == Blocks.BEDROCK) {
                        canBreak = true;
                    } else if (PickaxeAbility.hasAbility(held, PickaxeAbility.OBSIDIAN_BREAKER) && isObsidianLike(block)) {
                        canBreak = true;
                    } else if (PickaxeAbility.hasAbility(held, PickaxeAbility.BEDROCK_BREAKER) && block == Blocks.BEDROCK) {
                        canBreak = true;
                    }
                }
                if (!canBreak) continue;

                if (!creative) {
                    if (held.isEmpty()) return;
                    held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(p.getUsedItemHand()));
                }
                level.destroyBlock(pos, true, player);
            }
        }
    }

    private static BlockPos offsetByPlane(BlockPos origin, int[] plane, int d1, int d2) {
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
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
