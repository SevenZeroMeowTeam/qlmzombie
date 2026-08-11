package com.qlm.zombie.structure;

import com.qlm.zombie.QLMZombieMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 9 层高楼生成器 — 每层 5 个房间，每层 1 个奖励箱。
 * 楼梯使用梯子通道（可通行），外墙带门洞，仅陆地生成，自动平整地基。
 */
public class HighriseBuildingGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation DEFAULT_LOOT = ResourceLocation.parse("qlmzombie:chests/random_building");
    private static final ResourceLocation OTHER_MOD_LOOT = ResourceLocation.parse("qlmzombie:chests/other_mod_building");
    private static final float OTHER_MOD_CHANCE = 0.15F;

    private static final int WIDTH = 13;
    private static final int DEPTH = 9;
    private static final int FLOOR_HEIGHT = 4;
    private static final int FLOORS = 9;

    private static final Set<Long> GENERATED_CHUNKS = ConcurrentHashMap.newKeySet();

    public static boolean isChunkGenerated(long chunkKey) {
        return GENERATED_CHUNKS.contains(chunkKey);
    }

    public static void markChunkGenerated(long chunkKey) {
        GENERATED_CHUNKS.add(chunkKey);
    }

    /**
     * 检查目标区域是否为陆地（非水面/海底）
     */
    public static boolean isLandArea(WorldGenLevel level, BlockPos basePos) {
        int x = basePos.getX();
        int z = basePos.getZ();
        int[][] checkPoints = {
                {x, z}, {x + WIDTH - 1, z}, {x, z + DEPTH - 1},
                {x + WIDTH - 1, z + DEPTH - 1}, {x + WIDTH / 2, z + DEPTH / 2}
        };
        for (int[] cp : checkPoints) {
            BlockPos pos = new BlockPos(cp[0], basePos.getY(), cp[1]);
            BlockState state = level.getBlockState(pos);
            FluidState fluid = level.getFluidState(pos);
            if (state.isAir() || !fluid.isEmpty() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 扫描建筑覆盖区域，找到最低的实体地面高度（防浮空）
     */
    public static int findMinGroundHeight(WorldGenLevel level, BlockPos basePos) {
        int minX = basePos.getX();
        int minZ = basePos.getZ();
        int minHeight = Integer.MAX_VALUE;
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, minX + dx, minZ + dz);
                if (h < minHeight) minHeight = h;
            }
        }
        return minHeight;
    }

    /**
     * 平整地基：填充建筑区域下方的空隙（防浮空）
     */
    public static void flattenFoundation(WorldGenLevel level, BlockPos basePos, int groundY) {
        int x = basePos.getX();
        int z = basePos.getZ();
        BlockState foundationMat = Blocks.STONE_BRICKS.defaultBlockState();

        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int colX = x + dx;
                int colZ = z + dz;
                // 从建筑底部向下扫描，填充空隙和水面
                for (int dy = groundY - 1; dy >= groundY - 5; dy--) {
                    BlockPos pos = new BlockPos(colX, dy, colZ);
                    BlockState state = level.getBlockState(pos);
                    FluidState fluid = level.getFluidState(pos);
                    if (state.isAir() || !fluid.isEmpty() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
                        level.setBlock(pos, foundationMat, 3);
                    } else {
                        break; // 遇到实体方块停止
                    }
                }
            }
        }
    }

    /**
     * 生成 9 层高楼
     */
    public static void generate(WorldGenLevel level, BlockPos basePos, RandomSource random) {
        int x = basePos.getX();
        int z = basePos.getZ();

        // 1. 找到建筑覆盖区域的最低地面高度（防浮空）
        int minGroundY = findMinGroundHeight(level, basePos);

        // 2. 检查是否为陆地
        BlockPos adjustedPos = new BlockPos(x, minGroundY, z);
        if (!isLandArea(level, adjustedPos)) {
            LOGGER.info("[QLM Zombie] 高楼生成取消：目标区域非陆地 ({}, {}, {})", x, minGroundY, z);
            return;
        }

        // 3. 平整地基
        flattenFoundation(level, adjustedPos, minGroundY);

        int y = minGroundY;
        BlockState wallMat = random.nextBoolean()
                ? Blocks.STONE_BRICKS.defaultBlockState()
                : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState floorMat = Blocks.SMOOTH_STONE.defaultBlockState();

        // 4. 逐层构建
        for (int floor = 0; floor < FLOORS; floor++) {
            int floorY = y + floor * FLOOR_HEIGHT;
            buildFloor(level, x, floorY, z, floor, wallMat, floorMat, random);
        }

        // 5. 屋顶
        int roofY = y + FLOORS * FLOOR_HEIGHT;
        buildRoof(level, x, roofY, z, random);

        LOGGER.info("[QLM Zombie] 9层高楼已生成于: ({}, {}, {}) 共 {} 个奖励箱", x, y, z, FLOORS);
    }

    /**
     * 构建单层
     */
    private static void buildFloor(WorldGenLevel level, int x, int y, int z, int floor,
                                     BlockState wallMat, BlockState floorMat,
                                     RandomSource random) {
        // 1. 地板
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floorMat, 3);
            }
        }

        // 2. 天花板（上层地板）— 楼梯井位置开洞
        int ceilingY = y + FLOOR_HEIGHT - 1;
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                // 楼梯井位置 (x+6, z+4) 开洞，2×2
                boolean isStairWell = (dx >= 5 && dx <= 6) && (dz >= 3 && dz <= 4);
                if (floor < FLOORS - 1 && isStairWell) {
                    level.setBlock(new BlockPos(x + dx, ceilingY, z + dz), Blocks.AIR.defaultBlockState(), 3);
                } else {
                    level.setBlock(new BlockPos(x + dx, ceilingY, z + dz), floorMat, 3);
                }
            }
        }

        // 3. 外墙（4面）— 留门洞
        for (int dy = 0; dy < FLOOR_HEIGHT - 1; dy++) {
            // 前墙 (z=0)
            for (int dx = 0; dx < WIDTH; dx++) {
                if (floor == 0 && dy <= 1 && dx >= 5 && dx <= 7) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z), Blocks.AIR.defaultBlockState(), 3);
                } else {
                    setWall(level, new BlockPos(x + dx, y + dy, z), wallMat, random);
                }
            }
            // 后墙 (z=DEPTH-1)
            for (int dx = 0; dx < WIDTH; dx++) {
                if (floor == 0 && dy <= 1 && dx >= 5 && dx <= 7) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z + DEPTH - 1), Blocks.AIR.defaultBlockState(), 3);
                } else {
                    setWall(level, new BlockPos(x + dx, y + dy, z + DEPTH - 1), wallMat, random);
                }
            }
            // 左墙 (x=0)
            for (int dz = 0; dz < DEPTH; dz++) {
                if (floor == 0 && dy <= 1 && dz >= 3 && dz <= 5) {
                    level.setBlock(new BlockPos(x, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
                } else {
                    setWall(level, new BlockPos(x, y + dy, z + dz), wallMat, random);
                }
            }
            // 右墙 (x=WIDTH-1)
            for (int dz = 0; dz < DEPTH; dz++) {
                if (floor == 0 && dy <= 1 && dz >= 3 && dz <= 5) {
                    level.setBlock(new BlockPos(x + WIDTH - 1, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
                } else {
                    setWall(level, new BlockPos(x + WIDTH - 1, y + dy, z + dz), wallMat, random);
                }
            }
        }

        // 4. 内部分隔墙 — 5 个房间 + 十字走廊
        for (int dy = 0; dy < FLOOR_HEIGHT - 1; dy++) {
            // 垂直分隔墙 x=4
            for (int dz = 1; dz < DEPTH - 1; dz++) {
                if (dz == 4) continue; // 走廊口
                if (dy == 1 && (dz == 2 || dz == 6)) continue; // 房间门洞
                level.setBlock(new BlockPos(x + 4, y + dy, z + dz), wallMat, 3);
            }
            // 垂直分隔墙 x=8
            for (int dz = 1; dz < DEPTH - 1; dz++) {
                if (dz == 4) continue;
                if (dy == 1 && (dz == 2 || dz == 6)) continue;
                level.setBlock(new BlockPos(x + 8, y + dy, z + dz), wallMat, 3);
            }
            // 水平分隔墙 z=4
            for (int dx = 1; dx < WIDTH - 1; dx++) {
                if (dx == 4 || dx == 8) continue; // 走廊口
                // 楼梯井位置留空（x=5-6, z=3-4）
                if (dx >= 5 && dx <= 6) continue;
                if (dy == 1 && (dx == 2 || dx == 6 || dx == 10)) continue; // 房间门洞
                level.setBlock(new BlockPos(x + dx, y + dy, z + 4), wallMat, 3);
            }
        }

        // 5. 窗户
        for (int dy = 1; dy < FLOOR_HEIGHT - 1; dy++) {
            if (dy == 1 && floor == 0) continue;
            for (int dx = 2; dx < WIDTH - 1; dx += 3) {
                if (random.nextFloat() > 0.4F) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z), Blocks.GLASS_PANE.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x + dx, y + dy, z + DEPTH - 1), Blocks.GLASS_PANE.defaultBlockState(), 3);
                }
            }
        }

        // 6. 楼梯 — 使用梯子通道（2×3 井道，玩家可自由上下）
        // 梯子位置：x+5~6, z+3~4（2×2 梯子井）
        if (floor < FLOORS - 1) {
            // 放置梯子从地板到天花板
            for (int dy = 0; dy < FLOOR_HEIGHT; dy++) {
                // 梯子放在 x+5, z+4（面向东）
                level.setBlock(new BlockPos(x + 5, y + dy, z + 4),
                        Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 3);
                // 梯子放在 x+6, z+3（面向西）
                level.setBlock(new BlockPos(x + 6, y + dy, z + 3),
                        Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST), 3);
            }
            // 确保楼梯井空间为空气
            for (int dy = 0; dy < FLOOR_HEIGHT; dy++) {
                level.setBlock(new BlockPos(x + 5, y + dy, z + 3), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x + 6, y + dy, z + 4), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // 7. 火把
        level.setBlock(new BlockPos(x + 4, y + 1, z + 1),
                Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(new BlockPos(x + 8, y + 1, z + 1),
                Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);

        // 8. 奖励箱 — 每层 1 个，在不同房间
        int[][] roomCenters = {
                {2, 2}, {10, 2}, {2, 6}, {10, 6}, {6, 2}
        };
        int roomIdx = floor % 5;
        int chestX = x + roomCenters[roomIdx][0];
        int chestZ = z + roomCenters[roomIdx][1];

        ResourceLocation lootTable = random.nextFloat() < OTHER_MOD_CHANCE
                ? OTHER_MOD_LOOT : DEFAULT_LOOT;

        placeChest(level, new BlockPos(chestX, y, chestZ), Direction.NORTH, random, lootTable);

        LOGGER.info("[QLM Zombie] 高楼第 {} 层奖励箱放置于房间 {} ({}, {}, {}) loot={}",
                floor + 1, roomIdx, chestX, y, chestZ, lootTable);
    }

    /**
     * 构建屋顶
     */
    private static void buildRoof(WorldGenLevel level, int x, int y, int z, RandomSource random) {
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3);
            }
        }
        for (int dx = 0; dx < WIDTH; dx++) {
            level.setBlock(new BlockPos(x + dx, y + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + dx, y + 1, z + DEPTH - 1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }
        for (int dz = 0; dz < DEPTH; dz++) {
            level.setBlock(new BlockPos(x, y + 1, z + dz), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + WIDTH - 1, y + 1, z + dz), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(x + WIDTH / 2, y + 1, z + DEPTH / 2), Blocks.TORCH.defaultBlockState(), 3);
    }

    private static void setWall(WorldGenLevel level, BlockPos pos, BlockState wallMat, RandomSource random) {
        if (random.nextFloat() < 0.1F) {
            level.setBlock(pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 3);
        } else if (random.nextFloat() < 0.05F) {
            level.setBlock(pos, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
        } else {
            level.setBlock(pos, wallMat, 3);
        }
    }

    private static void placeChest(WorldGenLevel level, BlockPos pos, Direction facing,
                                     RandomSource random, ResourceLocation lootTable) {
        if (level.getBlockState(pos.below()).isAir()) {
            level.setBlock(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        level.setBlock(pos, chestState, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(lootTable, random.nextLong());
        }
    }
}
