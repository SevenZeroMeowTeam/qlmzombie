package com.qlm.zombie.mining;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MODID)
public class ChainMiningHandler {

    private static final Set<Block> LOG_BLOCKS = new HashSet<>(Arrays.asList(
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
            Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
            Blocks.CRIMSON_STEM, Blocks.WARPED_STEM,
            Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG
    ));

    private static final Set<Block> LEAF_BLOCKS = new HashSet<>(Arrays.asList(
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.CRIMSON_FUNGUS, Blocks.WARPED_FUNGUS,
            Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES
    ));

    private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // 防止递归触发
        if (Boolean.TRUE.equals(CHAINING.get())) return;

        Level level = (Level) event.getLevel();
        BlockState state = event.getState();
        Block block = state.getBlock();
        BlockPos pos = event.getPos();
        ItemStack held = player.getMainHandItem();

        boolean chainMiningEnabled = QLMConfig.ENABLE_CHAIN_MINING.get();
        boolean treeChopEnabled = QLMConfig.ENABLE_TREE_CHOP.get();

        if (!chainMiningEnabled && !treeChopEnabled) return;

        boolean creative = player.getAbilities().instabuild;

        // 连锁砍树：用斧头砍原木，将整棵树的原木一并挖掉（可选连带叶子）
        if (treeChopEnabled && isAxe(held) && LOG_BLOCKS.contains(block)) {
            CHAINING.set(Boolean.TRUE);
            try {
                doTreeChop(level, pos, player, held, creative);
            } finally {
                CHAINING.set(Boolean.FALSE);
            }
            return;
        }

        // 连锁挖矿：用对应工具挖同类方块（矿石/砂砾/泥土/沙子/石类）
        if (chainMiningEnabled && canChainMine(held, block)) {
            CHAINING.set(Boolean.TRUE);
            try {
                doChainMine(level, pos, state, player, held, creative);
            } finally {
                CHAINING.set(Boolean.FALSE);
            }
        }
    }

    // ============= 连锁砍树 =============
    private static void doTreeChop(Level level, BlockPos origin,
                                    Player player, ItemStack held, boolean creative) {
        int maxBlocks = QLMConfig.TREE_CHOP_MAX_BLOCKS.get();
        boolean includeLeaves = QLMConfig.TREE_CHOP_INCLUDE_LEAVES.get();

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        List<BlockPos> toBreak = new ArrayList<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
            BlockPos current = queue.remove(0);
            toBreak.add(current);

            // 6 向搜索（上下+四方），便于处理倾斜的树冠/树干
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = current.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (Math.abs(next.getX() - origin.getX()) > 32
                                || Math.abs(next.getY() - origin.getY()) > 64
                                || Math.abs(next.getZ() - origin.getZ()) > 32) {
                            continue;
                        }

                        BlockState nextState = level.getBlockState(next);
                        Block nextBlock = nextState.getBlock();
                        if (LOG_BLOCKS.contains(nextBlock)
                                || (includeLeaves && LEAF_BLOCKS.contains(nextBlock))) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        for (BlockPos p : toBreak) {
            if (p.equals(origin)) continue; // 原方块由玩家的主挖完成
            if (held.isEmpty() && !creative) break;
            destroyBlock(level, p, player, creative);
        }
    }

    // ============= 连锁挖矿 =============
    private static void doChainMine(Level level, BlockPos origin, BlockState originalState,
                                    Player player, ItemStack held, boolean creative) {
        int maxBlocks = QLMConfig.CHAIN_MINING_MAX_BLOCKS.get();
        int radius = QLMConfig.CHAIN_MINING_RADIUS.get();
        Block target = originalState.getBlock();

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        List<BlockPos> toBreak = new ArrayList<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
            BlockPos current = queue.remove(0);
            toBreak.add(current);

            // 仅水平四方 + 上下 6 格方向延伸（更接近原版连锁矿）
            BlockPos[] neighbors = new BlockPos[]{
                    current.north(), current.south(),
                    current.east(), current.west(),
                    current.above(), current.below()
            };
            for (BlockPos next : neighbors) {
                if (visited.contains(next)) continue;
                if (Math.abs(next.getX() - origin.getX()) > radius
                        || Math.abs(next.getY() - origin.getY()) > radius
                        || Math.abs(next.getZ() - origin.getZ()) > radius) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() == target) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        for (BlockPos p : toBreak) {
            if (p.equals(origin)) continue;
            if (held.isEmpty() && !creative) break;
            destroyBlock(level, p, player, creative);
        }
    }

    private static void destroyBlock(Level level, BlockPos pos, Player player, boolean creative) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        if (!level.mayInteract(player, pos)) return;

        // 使用原版的破坏逻辑：调用 player.gameMode.destroyBlock / level.destroyBlock
        // 触发正常的掉落与工具耐久消耗
        if (creative) {
            level.removeBlock(pos, false);
        } else {
            // 在生存模式下，使用 Level.destroyBlock 触发正常掉落、耐久消耗
            level.destroyBlock(pos, true, player);
        }
    }

    // ============= 工具检测 =============
    private static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof AxeItem || item.getDescriptionId().toLowerCase(Locale.ROOT).contains("axe");
    }

    private static boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof PickaxeItem || item.getDescriptionId().toLowerCase(Locale.ROOT).contains("pickaxe");
    }

    private static boolean isShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ShovelItem || item.getDescriptionId().toLowerCase(Locale.ROOT).contains("shovel");
    }

    private static boolean canChainMine(ItemStack held, Block block) {
        if (held == null || held.isEmpty()) return false;

        // 按工具 + 方块类型判断：只有正确工具才能连锁，避免空手连锁破坏
        if (isPickaxe(held) && QLMConfig.CHAIN_MINING_PICKAXE_ENABLED.get()) {
            return isStoneLike(block) || isOreLike(block);
        }
        if (isShovel(held) && QLMConfig.CHAIN_MINING_SHOVEL_ENABLED.get()) {
            return isDirtLike(block) || isSandLike(block) || isGravelLike(block);
        }
        return false;
    }

    private static boolean isOreLike(Block block) {
        String name = block.getDescriptionId().toLowerCase(Locale.ROOT);
        return name.contains("_ore")
                || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.COAL_BLOCK
                || block == Blocks.RAW_IRON_BLOCK
                || block == Blocks.RAW_COPPER_BLOCK
                || block == Blocks.RAW_GOLD_BLOCK;
    }

    private static boolean isStoneLike(Block block) {
        return block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.ANDESITE
                || block == Blocks.GRANITE
                || block == Blocks.DIORITE
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.TUFF
                || block == Blocks.BASALT
                || block == Blocks.POLISHED_ANDESITE
                || block == Blocks.POLISHED_GRANITE
                || block == Blocks.POLISHED_DIORITE
                || block == Blocks.POLISHED_DEEPSLATE
                || block == Blocks.SMOOTH_BASALT
                || block == Blocks.BLACKSTONE
                || block == Blocks.POLISHED_BLACKSTONE
                || block == Blocks.NETHERRACK
                || block == Blocks.END_STONE
                || block == Blocks.MOSSY_COBBLESTONE
                || block == Blocks.STONE_BRICKS
                || block == Blocks.MOSSY_STONE_BRICKS
                || block == Blocks.CRACKED_STONE_BRICKS
                || block == Blocks.DEEPSLATE_BRICKS
                || block == Blocks.DEEPSLATE_TILES
                || block == Blocks.PRISMARINE
                || block == Blocks.PRISMARINE_BRICKS
                || block == Blocks.DARK_PRISMARINE;
    }

    private static boolean isDirtLike(Block block) {
        return block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.PODZOL
                || block == Blocks.MYCELIUM
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT
                || block == Blocks.FARMLAND
                || block == Blocks.DIRT_PATH;
    }

    private static boolean isSandLike(Block block) {
        return block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.SUSPICIOUS_SAND
                || block == Blocks.SUSPICIOUS_GRAVEL;
    }

    private static boolean isGravelLike(Block block) {
        return block == Blocks.GRAVEL
                || block == Blocks.CLAY
                || block == Blocks.SOUL_SAND
                || block == Blocks.SOUL_SOIL
                || block == Blocks.MUD
                || block == Blocks.PACKED_MUD
                || block == Blocks.MUD_BRICKS;
    }
}