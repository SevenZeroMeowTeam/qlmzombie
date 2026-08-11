package com.qlm.zombie.structure;

import com.qlm.zombie.QLMZombieMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

/**
 * 海底废墟生成器 — 在深海/海洋生物群系的海底生成废墟结构，含保底其他模组物品奖励箱。
 */
public class OceanRuinGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation OCEAN_RUIN_LOOT = ResourceLocation.parse("qlmzombie:chests/ocean_ruin");

    private static final int WIDTH = 7;
    private static final int DEPTH = 7;
    private static final int HEIGHT = 5;

    /**
     * 检查是否为海洋/深海生物群系
     */
    public static boolean isOceanArea(WorldGenLevel level, BlockPos pos) {
        // 检查该位置是否在水下
        int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ());
        BlockPos floorPos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
        BlockPos waterPos = floorPos.above();
        FluidState fluid = level.getFluidState(waterPos);
        return !fluid.isEmpty() && fluid.is(net.minecraft.tags.FluidTags.WATER);
    }

    /**
     * 生成海底废墟
     */
    public static void generate(WorldGenLevel level, BlockPos basePos, RandomSource random) {
        int x = basePos.getX();
        int y = basePos.getY();
        int z = basePos.getZ();

        // 检查是否在水下
        if (!isOceanArea(level, basePos)) {
            LOGGER.info("[QLM Zombie] 海底废墟生成取消：目标区域非海洋 ({}, {}, {})", x, y, z);
            return;
        }

        BlockState wallMat = random.nextBoolean()
                ? Blocks.STONE_BRICKS.defaultBlockState()
                : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState floorMat = Blocks.PRISMARINE.defaultBlockState();

        // 1. 地基
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floorMat, 3);
            }
        }

        // 2. 残破墙壁
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = 0; dx < WIDTH; dx++) {
                for (int dz = 0; dz < DEPTH; dz++) {
                    boolean isEdge = dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1;
                    if (isEdge) {
                        // 随机残破效果
                        if (random.nextFloat() > 0.3F) {
                            BlockState wall = random.nextFloat() < 0.3F
                                    ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                                    : wallMat;
                            level.setBlock(new BlockPos(x + dx, y + dy, z + dz), wall, 3);
                        }
                    }
                }
            }
        }

        // 3. 门洞（正面中央）
        for (int dy = 0; dy < 2; dy++) {
            level.setBlock(new BlockPos(x + WIDTH / 2, y + dy, z), Blocks.AIR.defaultBlockState(), 3);
        }

        // 4. 内部走廊 + 房间分隔
        for (int dy = 0; dy < HEIGHT - 1; dy++) {
            // 中央十字走廊
            level.setBlock(new BlockPos(x + WIDTH / 2, y + dy, z + DEPTH / 2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + 3, y + dy, z + DEPTH / 2), Blocks.AIR.defaultBlockState(), 3);
        }

        // 5. 海泡菜装饰
        for (int i = 0; i < 8; i++) {
            int rx = x + random.nextInt(WIDTH);
            int rz = z + random.nextInt(DEPTH);
            int ry = y + random.nextInt(2);
            if (level.getBlockState(new BlockPos(rx, ry, rz)).isAir()) {
                level.setBlock(new BlockPos(rx, ry, rz), Blocks.SEA_PICKLE.defaultBlockState(), 3);
            }
        }

        // 6. 海灵灯笼
        level.setBlock(new BlockPos(x + 1, y + 2, z + 1), Blocks.PRISMARINE_BRICKS.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x + 1, y + 3, z + 1), Blocks.SEA_LANTERN.defaultBlockState(), 3);

        // 7. 奖励箱 — 2 个箱子，保底其他模组物品
        // 箱子 1：左前房间
        placeChest(level, new BlockPos(x + 1, y, z + 1), Direction.EAST, random, OCEAN_RUIN_LOOT);
        // 箱子 2：右后房间
        placeChest(level, new BlockPos(x + WIDTH - 2, y, z + DEPTH - 2), Direction.WEST, random, OCEAN_RUIN_LOOT);

        LOGGER.info("[QLM Zombie] 海底废墟已生成于: ({}, {}, {}) 含 2 个奖励箱", x, y, z);
    }

    private static void placeChest(WorldGenLevel level, BlockPos pos, Direction facing,
                                     RandomSource random, ResourceLocation lootTable) {
        if (level.getBlockState(pos.below()).isAir()) {
            level.setBlock(pos.below(), Blocks.PRISMARINE.defaultBlockState(), 3);
        }
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        level.setBlock(pos, chestState, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(lootTable, random.nextLong());
        }
    }
}
