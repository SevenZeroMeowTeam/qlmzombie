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
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 9 层高楼生成器 — 每层 5 个房间，每层 1 个奖励箱（在不同房间），15% 概率为其他模组 loot 箱。
 *
 * 建筑规格：13×9 外部，36 格高（9 层 × 4 格/层）
 * 每层布局：3 个前排房间(3×3) + 2 个后排房间(5×3) + 十字走廊
 */
public class HighriseBuildingGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 默认 loot 表 */
    private static final ResourceLocation DEFAULT_LOOT = ResourceLocation.parse("qlmzombie:chests/random_building");
    /** 其他模组 loot 表（15% 概率使用） */
    private static final ResourceLocation OTHER_MOD_LOOT = ResourceLocation.parse("qlmzombie:chests/other_mod_building");
    /** 其他模组 loot 概率 */
    private static final float OTHER_MOD_CHANCE = 0.15F;

    /** 建筑尺寸 */
    private static final int WIDTH = 13;   // 宽
    private static final int DEPTH = 9;    // 深
    private static final int FLOOR_HEIGHT = 4; // 每层高度
    private static final int FLOORS = 9;   // 楼层数

    /** 已生成建筑的区块坐标集合（防重复） */
    private static final Set<Long> GENERATED_CHUNKS = ConcurrentHashMap.newKeySet();

    /**
     * 检查该区块是否已生成过建筑
     */
    public static boolean isChunkGenerated(long chunkKey) {
        return GENERATED_CHUNKS.contains(chunkKey);
    }

    /**
     * 标记区块已生成建筑
     */
    public static void markChunkGenerated(long chunkKey) {
        GENERATED_CHUNKS.add(chunkKey);
    }

    /**
     * 生成 9 层高楼
     * @param level 世界
     * @param basePos 地面位置（建筑左下角）
     * @param random 随机源
     */
    public static void generate(WorldGenLevel level, BlockPos basePos, RandomSource random) {
        int x = basePos.getX();
        int y = basePos.getY();
        int z = basePos.getZ();

        // 材料随机选择
        BlockState wallMat = random.nextBoolean()
                ? Blocks.STONE_BRICKS.defaultBlockState()
                : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState floorMat = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState pillarMat = Blocks.POLISHED_ANDESITE.defaultBlockState();

        // 逐层构建
        for (int floor = 0; floor < FLOORS; floor++) {
            int floorY = y + floor * FLOOR_HEIGHT;
            buildFloor(level, x, floorY, z, floor, wallMat, floorMat, pillarMat, random);
        }

        // 屋顶
        int roofY = y + FLOORS * FLOOR_HEIGHT;
        buildRoof(level, x, roofY, z, random);

        LOGGER.info("[QLM Zombie] 9层高楼已生成于: ({}, {}, {}) 共 {} 个奖励箱", x, y, z, FLOORS);
    }

    /**
     * 构建单层
     */
    private static void buildFloor(WorldGenLevel level, int x, int y, int z, int floor,
                                     BlockState wallMat, BlockState floorMat, BlockState pillarMat,
                                     RandomSource random) {
        // 1. 地板
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floorMat, 3);
            }
        }

        // 2. 天花板（也是上层的地板）
        int ceilingY = y + FLOOR_HEIGHT - 1;
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x + dx, ceilingY, z + dz), floorMat, 3);
            }
        }

        // 3. 外墙（4面）
        for (int dy = 0; dy < FLOOR_HEIGHT - 1; dy++) {
            for (int dx = 0; dx < WIDTH; dx++) {
                setWall(level, new BlockPos(x + dx, y + dy, z), wallMat, random);
                setWall(level, new BlockPos(x + dx, y + dy, z + DEPTH - 1), wallMat, random);
            }
            for (int dz = 0; dz < DEPTH; dz++) {
                setWall(level, new BlockPos(x, y + dy, z + dz), wallMat, random);
                setWall(level, new BlockPos(x + WIDTH - 1, y + dy, z + dz), wallMat, random);
            }
        }

        // 4. 内部分隔墙 — 将每层分为 5 个房间
        // 前排 3 个房间 (3×3): x=[1,3], [5,7], [9,11]; z=[1,3]
        // 后排 2 个房间 (5×3): x=[1,5], [7,11]; z=[5,7]
        // 走廊: x=4 (垂直), z=4 (水平)
        for (int dy = 0; dy < FLOOR_HEIGHT - 1; dy++) {
            // 垂直分隔墙 x=4（走廊左侧墙）
            for (int dz = 1; dz < DEPTH - 1; dz++) {
                if (dz == 4) continue; // 走廊交叉口留空
                if (dy == 1 && (dz == 2 || dz == 6)) continue; // 门洞
                level.setBlock(new BlockPos(x + 4, y + dy, z + dz), wallMat, 3);
            }
            // 垂直分隔墙 x=8（走廊右侧墙）
            for (int dz = 1; dz < DEPTH - 1; dz++) {
                if (dz == 4) continue;
                if (dy == 1 && (dz == 2 || dz == 6)) continue;
                level.setBlock(new BlockPos(x + 8, y + dy, z + dz), wallMat, 3);
            }
            // 水平分隔墙 z=4（前后排分隔）
            for (int dx = 1; dx < WIDTH - 1; dx++) {
                if (dx == 4 || dx == 8) continue; // 走廊交叉口
                if (dy == 1 && (dx == 2 || dx == 6 || dx == 10)) continue; // 门洞
                level.setBlock(new BlockPos(x + dx, y + dy, z + 4), wallMat, 3);
            }
            // 前排中间分隔墙 x=4（分 A/C 房间）— 已在上面处理
            // 前排中间分隔墙 x=8（分 C/D 房间）— 已在上面处理
        }

        // 5. 窗户（外墙上随机位置）
        for (int dy = 1; dy < FLOOR_HEIGHT - 1; dy++) {
            if (dy == 1 && floor == 0) continue; // 底层不留窗
            // 前墙窗户
            for (int dx = 2; dx < WIDTH - 1; dx += 3) {
                if (random.nextFloat() > 0.4F) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z), Blocks.GLASS_PANE.defaultBlockState(), 3);
                }
            }
            // 后墙窗户
            for (int dx = 2; dx < WIDTH - 1; dx += 3) {
                if (random.nextFloat() > 0.4F) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z + DEPTH - 1), Blocks.GLASS_PANE.defaultBlockState(), 3);
                }
            }
        }

        // 6. 楼梯（走廊交叉口 x=4, z=4）
        if (floor < FLOORS - 1) {
            for (int dy = 0; dy < FLOOR_HEIGHT - 1; dy++) {
                level.setBlock(new BlockPos(x + 4, y + dy, z + 4),
                        Blocks.LADDER.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 3);
            }
        }

        // 7. 火把（走廊照明）
        level.setBlock(new BlockPos(x + 4, y + 1, z + 1),
                Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(new BlockPos(x + 8, y + 1, z + 1),
                Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);

        // 8. 奖励箱 — 每层 1 个，在不同房间
        // 5 个房间位置（房间中心）：
        // 房间 0: 左前 (x+2, z+2)
        // 房间 1: 中前 (x+6, z+2)
        // 房间 2: 右前 (x+10, z+2)
        // 房间 3: 左后 (x+3, z+6)
        // 房间 4: 右后 (x+9, z+6)
        int[][] roomCenters = {
                {2, 2}, {6, 2}, {10, 2}, {3, 6}, {9, 6}
        };
        int roomIdx = floor % 5; // 每层在不同房间放奖励箱
        int chestX = x + roomCenters[roomIdx][0];
        int chestZ = z + roomCenters[roomIdx][1];

        // 15% 概率使用其他模组 loot 表
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
        // 平顶 + 矮墙
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3);
            }
        }
        // 矮墙
        for (int dx = 0; dx < WIDTH; dx++) {
            level.setBlock(new BlockPos(x + dx, y + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + dx, y + 1, z + DEPTH - 1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }
        for (int dz = 0; dz < DEPTH; dz++) {
            level.setBlock(new BlockPos(x, y + 1, z + dz), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + WIDTH - 1, y + 1, z + dz), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }
        // 屋顶火把
        level.setBlock(new BlockPos(x + WIDTH / 2, y + 1, z + DEPTH / 2), Blocks.TORCH.defaultBlockState(), 3);
    }

    /**
     * 放置墙壁方块（随机加入裂纹效果）
     */
    private static void setWall(WorldGenLevel level, BlockPos pos, BlockState wallMat, RandomSource random) {
        if (random.nextFloat() < 0.1F) {
            level.setBlock(pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 3);
        } else if (random.nextFloat() < 0.05F) {
            level.setBlock(pos, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 3);
        } else {
            level.setBlock(pos, wallMat, 3);
        }
    }

    /**
     * 放置奖励箱
     */
    private static void placeChest(WorldGenLevel level, BlockPos pos, Direction facing,
                                     RandomSource random, ResourceLocation lootTable) {
        // 确保下方有支撑
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
