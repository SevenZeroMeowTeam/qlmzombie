package com.qlm.zombie.structure;

import com.qlm.zombie.QLMZombieMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 随机建筑物生成器 — 在世界生成时随机生成不同类型的建筑物（小屋、瞭望塔、废墟），含战利品箱子
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class RandomBuildingGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation LOOT_TABLE = ResourceLocation.parse("qlmzombie:chests/random_building");
    private static final float GENERATE_CHANCE = 0.015F;
    /** 高楼生成概率（在普通建筑生成成功后，20% 概率改为生成高楼） */
    private static final float HIGHRISE_CHANCE = 0.20F;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // 只处理服务端的完整区块（LevelChunk），跳过 ProtoChunk 以避免区块加载死锁
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkPos chunkPos = chunk.getPos();

        // 建筑不重复：检查该区块是否已生成过建筑
        long chunkKey = chunkPos.toLong();
        if (HighriseBuildingGenerator.isChunkGenerated(chunkKey)) return;

        if (level.getRandom().nextFloat() > GENERATE_CHANCE) return;

        if (level.getServer().getPlayerList().getPlayers().isEmpty()) return;

        // 标记该区块已生成建筑
        HighriseBuildingGenerator.markChunkGenerated(chunkKey);

        BlockPos centerPos = chunkPos.getMiddleBlockPosition(0);
        // 使用区块自身的高度图而非 level.getHeight()，避免在区块加载事件中
        // 同步调用 getChunk() 导致区块加载死锁（ServerHangWatchdog 60秒超时崩溃）
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, centerPos.getX(), centerPos.getZ());

        if (surfaceY < 60) return;

        BlockPos groundPos = new BlockPos(centerPos.getX(), surfaceY - 1, centerPos.getZ());
        BlockState groundState = chunk.getBlockState(groundPos);

        if (groundState.isAir() || groundState.is(Blocks.WATER) || groundState.is(Blocks.LAVA)) return;

        BlockPos buildPos = new BlockPos(centerPos.getX(), surfaceY, centerPos.getZ());
        // 20% 概率生成 9 层高楼，否则随机生成普通建筑（小屋/瞭望塔/废墟）
        boolean isHighrise = level.getRandom().nextFloat() < HIGHRISE_CHANCE;
        long seed = level.getRandom().nextLong();
        // 延迟到下一 tick 执行建筑生成，避免在 ChunkEvent.Load 中调用 level.setBlock()
        // 触发 getChunk() 导致区块加载死锁
        if (isHighrise) {
            level.getServer().execute(() -> {
                HighriseBuildingGenerator.generate(level, buildPos, RandomSource.create(seed));
            });
        } else {
            int buildingType = level.getRandom().nextInt(3); // 0=小屋, 1=瞭望塔, 2=废墟
            level.getServer().execute(() -> {
                generateBuilding(level, buildPos, RandomSource.create(seed), buildingType);
            });
        }
    }

    public static void generateBuilding(WorldGenLevel level, BlockPos pos, RandomSource random, int type) {
        switch (type) {
            case 0 -> generateCabin(level, pos, random);
            case 1 -> generateWatchtower(level, pos, random);
            case 2 -> generateRuins(level, pos, random);
        }
    }

    // ============= 小屋 =============
    private static void generateCabin(WorldGenLevel level, BlockPos pos, RandomSource random) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int w = 5, h = 4, d = 5;

        // 地板
        for (int dx = 0; dx < w; dx++)
            for (int dz = 0; dz < d; dz++)
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);

        // 墙壁和内部
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < d; dz++) {
                    BlockPos bp = new BlockPos(x + dx, y + dy, z + dz);
                    boolean isWall = dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1;
                    if (isWall) {
                        // 门
                        if (dx == w / 2 && dz == 0 && dy == 1) {
                            level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                        } else if (dy == 0 || dy == h - 1) {
                            level.setBlock(bp, Blocks.OAK_LOG.defaultBlockState(), 3);
                        } else {
                            level.setBlock(bp, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                        }
                    } else {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 屋顶（三角形）
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                level.setBlock(new BlockPos(x + dx, y + h, z + dz), Blocks.SPRUCE_SLAB.defaultBlockState(), 3);
            }
        }

        // 窗户
        level.setBlock(new BlockPos(x, y + 1, z + d / 2), Blocks.GLASS_PANE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x + w - 1, y + 1, z + d / 2), Blocks.GLASS_PANE.defaultBlockState(), 3);

        // 工作台
        level.setBlock(new BlockPos(x + 1, y, z + 1), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);

        // 箱子
        placeChest(level, new BlockPos(x + w - 2, y, z + d - 2), Direction.NORTH, random);

        // 熔炉
        level.setBlock(new BlockPos(x + 1, y, z + d - 2), Blocks.FURNACE.defaultBlockState(), 3);

        // 床
        level.setBlock(new BlockPos(x + w - 2, y, z + 1), Blocks.RED_BED.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x + w - 3, y, z + 1), Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);

        LOGGER.info("[QLM Zombie] 随机小屋已生成于: ({}, {}, {})", x, y, z);
    }

    // ============= 瞭望塔 =============
    private static void generateWatchtower(WorldGenLevel level, BlockPos pos, RandomSource random) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int w = 3, h = 6;

        // 地基
        for (int dx = 0; dx < w; dx++)
            for (int dz = 0; dz < w; dz++)
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);

        // 柱子和内部
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    BlockPos bp = new BlockPos(x + dx, y + dy, z + dz);
                    boolean isCorner = (dx == 0 || dx == w - 1) && (dz == 0 || dz == w - 1);
                    if (isCorner) {
                        level.setBlock(bp, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                    } else if (dy == h - 1) {
                        level.setBlock(bp, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 3);
                    } else {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 楼梯
        for (int dy = 0; dy < h - 1; dy++) {
            level.setBlock(new BlockPos(x + 1, y + dy, z + 1),
                    Blocks.LADDER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST), 3);
        }

        // 顶部栏杆
        for (int dx = 0; dx < w; dx++) {
            level.setBlock(new BlockPos(x + dx, y + h, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + dx, y + h, z + w - 1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }
        for (int dz = 0; dz < w; dz++) {
            level.setBlock(new BlockPos(x, y + h, z + dz), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x + w - 1, y + h, z + dz), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
        }

        // 火把
        level.setBlock(new BlockPos(x + 1, y + h - 1, z), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.NORTH), 3);
        level.setBlock(new BlockPos(x + 1, y + h - 1, z + w - 1), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH), 3);

        // 箱子（在塔顶）
        placeChest(level, new BlockPos(x + 1, y + h - 1, z + 1), Direction.SOUTH, random);

        LOGGER.info("[QLM Zombie] 随机瞭望塔已生成于: ({}, {}, {})", x, y, z);
    }

    // ============= 废墟 =============
    private static void generateRuins(WorldGenLevel level, BlockPos pos, RandomSource random) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int w = 6, d = 6;

        // 破碎的地基
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                if (random.nextFloat() > 0.2F) {
                    BlockState floor = random.nextBoolean()
                            ? Blocks.COBBLESTONE.defaultBlockState()
                            : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floor, 3);
                }
            }
        }

        // 残破墙壁（随机高度）
        for (int dx = 0; dx < w; dx++) {
            int wallH = random.nextInt(3) + 1;
            for (int dy = 0; dy < wallH; dy++) {
                BlockPos bp = new BlockPos(x + dx, y + dy, z);
                BlockState wall = random.nextBoolean()
                        ? Blocks.STONE_BRICKS.defaultBlockState()
                        : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                if (random.nextFloat() > 0.3F) {
                    level.setBlock(bp, wall, 3);
                }
            }
        }
        for (int dz = 0; dz < d; dz++) {
            int wallH = random.nextInt(3) + 1;
            for (int dy = 0; dy < wallH; dy++) {
                BlockPos bp = new BlockPos(x, y + dy, z + dz);
                BlockState wall = random.nextBoolean()
                        ? Blocks.STONE_BRICKS.defaultBlockState()
                        : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                if (random.nextFloat() > 0.3F) {
                    level.setBlock(bp, wall, 3);
                }
            }
        }

        // 散落的碎片
        for (int i = 0; i < 5; i++) {
            int rx = x + random.nextInt(w);
            int rz = z + random.nextInt(d);
            level.setBlock(new BlockPos(rx, y, rz), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }

        // 箱子（藏在废墟角落）
        placeChest(level, new BlockPos(x + w - 1, y, z + d - 1), Direction.NORTH, random);

        // 蜘蛛网装饰
        for (int i = 0; i < 3; i++) {
            int rx = x + random.nextInt(w);
            int rz = z + random.nextInt(d);
            int ry = y + random.nextInt(2);
            if (level.getBlockState(new BlockPos(rx, ry, rz)).isAir()) {
                level.setBlock(new BlockPos(rx, ry, rz), Blocks.COBWEB.defaultBlockState(), 3);
            }
        }

        LOGGER.info("[QLM Zombie] 随机废墟已生成于: ({}, {}, {})", x, y, z);
    }

    // ============= 放置箱子 =============
    private static void placeChest(WorldGenLevel level, BlockPos pos, Direction facing, RandomSource random) {
        // 确保箱子下方有方块支撑
        if (level.getBlockState(pos.below()).isAir()) {
            level.setBlock(pos.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }

        BlockState chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        level.setBlock(pos, chestState, 3);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(LOOT_TABLE, random.nextLong());
        }
    }
}
