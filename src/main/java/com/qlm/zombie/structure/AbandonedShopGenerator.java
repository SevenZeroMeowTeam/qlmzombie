package com.qlm.zombie.structure;

import com.qlm.zombie.QLMZombieMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AbandonedShopGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SHOP_WIDTH = 7;
    private static final int SHOP_HEIGHT = 4;
    private static final int SHOP_DEPTH = 5;
    private static final ResourceLocation LOOT_TABLE = ResourceLocation.parse("qlmzombie:chests/abandoned_shop");

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // 只处理服务端的完整区块（LevelChunk），跳过 ProtoChunk 以避免区块加载死锁
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();

        if (level.getRandom().nextFloat() > 0.02F) {
            return;
        }

        if (level.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        BlockPos centerPos = chunkPos.getMiddleBlockPosition(0);
        // 使用区块自身的高度图而非 level.getHeight()，避免在区块加载事件中
        // 同步调用 getChunk() 导致区块加载死锁（ServerHangWatchdog 60秒超时崩溃）
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, centerPos.getX(), centerPos.getZ());

        if (surfaceY < 60) {
            return;
        }

        BlockPos groundPos = new BlockPos(centerPos.getX(), surfaceY - 1, centerPos.getZ());
        BlockState groundState = chunk.getBlockState(groundPos);

        if (groundState.isAir() || groundState.is(Blocks.WATER) || groundState.is(Blocks.LAVA)) {
            return;
        }

        BlockPos shopPos = new BlockPos(centerPos.getX(), surfaceY, centerPos.getZ());
        long seed = level.getRandom().nextLong();
        // 延迟到下一 tick 执行商店生成，避免在 ChunkEvent.Load 中调用 level.setBlock()
        // 触发 getChunk() 导致区块加载死锁
        level.getServer().execute(() -> {
            generateShop(level, shopPos, RandomSource.create(seed));
        });
    }

    public static void generateShop(WorldGenLevel level, BlockPos pos, RandomSource random) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        
        generateShopStructure(level, x, y, z);
        
        List<BlockPos> shelfPositions = generateShelves(level, x, y, z, random);
        
        placeRewardChests(level, x, y, z, shelfPositions, random);
        
        LOGGER.info("[QLM Zombie] 废弃商店已生成于: ({}, {}, {})", x, y, z);
    }

    private static void generateShopStructure(WorldGenLevel level, int x, int y, int z) {
        for (int dx = 0; dx < SHOP_WIDTH; dx++) {
            for (int dz = 0; dz < SHOP_DEPTH; dz++) {
                for (int dy = 0; dy < SHOP_HEIGHT; dy++) {
                    BlockPos bp = new BlockPos(x + dx, y + dy, z + dz);
                    
                    if (dy == 0) {
                        level.setBlock(bp, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    } else if (dy == SHOP_HEIGHT - 1) {
                        if (dx > 0 && dx < SHOP_WIDTH - 1 && dz > 0 && dz < SHOP_DEPTH - 1) {
                            level.setBlock(bp, Blocks.COBBLESTONE.defaultBlockState(), 3);
                        } else {
                            level.setBlock(bp, Blocks.COBBLESTONE_SLAB.defaultBlockState(), 3);
                        }
                    } else if (dx == 0 || dx == SHOP_WIDTH - 1 || dz == 0) {
                        if (dx == SHOP_WIDTH / 2 && dz == 0 && dy > 0 && dy < SHOP_HEIGHT - 1) {
                            level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                        } else {
                            level.setBlock(bp, Blocks.COBBLESTONE.defaultBlockState(), 3);
                        }
                    } else {
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static List<BlockPos> generateShelves(WorldGenLevel level, int x, int y, int z, RandomSource random) {
        List<BlockPos> shelfPositions = new ArrayList<>();
        
        int[][] shelfLayout = {
            {1, 1, 0, 0, 0},
            {1, 1, 0, 0, 0},
            {0, 0, 0, 1, 1},
            {0, 0, 0, 1, 1}
        };
        
        for (int dx = 0; dx < shelfLayout.length; dx++) {
            for (int dz = 0; dz < shelfLayout[0].length; dz++) {
                if (shelfLayout[dx][dz] == 1) {
                    BlockPos basePos = new BlockPos(x + 1 + dx, y, z + 1 + dz);
                    generateShelf(level, basePos, random);
                    shelfPositions.add(new BlockPos(basePos.getX(), basePos.getY() + 2, basePos.getZ()));
                }
            }
        }
        
        return shelfPositions;
    }

    private static void generateShelf(WorldGenLevel level, BlockPos basePos, RandomSource random) {
        level.setBlock(basePos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        
        for (int dy = 1; dy < 3; dy++) {
            level.setBlock(basePos.above(dy), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            
            if (dy == 1) {
                level.setBlock(basePos.above(dy).east(), Blocks.OAK_FENCE.defaultBlockState(), 3);
                level.setBlock(basePos.above(dy).west(), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        
        level.setBlock(basePos.above(1).north(), Blocks.OAK_FENCE.defaultBlockState(), 3);
        level.setBlock(basePos.above(1).south(), Blocks.OAK_FENCE.defaultBlockState(), 3);
    }

    private static void placeRewardChests(WorldGenLevel level, int x, int y, int z, List<BlockPos> shelfPositions, RandomSource random) {
        int chestCount = random.nextInt(2) + 1;
        
        for (int i = 0; i < chestCount && i < shelfPositions.size(); i++) {
            int index = random.nextInt(shelfPositions.size());
            BlockPos chestPos = shelfPositions.remove(index);
            
            BlockState chestState = Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, Direction.NORTH);
            level.setBlock(chestPos, chestState, 3);
            
            BlockEntity be = level.getBlockEntity(chestPos);
            if (be instanceof ChestBlockEntity chestEntity) {
                chestEntity.setLootTable(LOOT_TABLE, random.nextLong());
            }
        }
        
        if (random.nextFloat() < 0.3F) {
            BlockPos floorChestPos = new BlockPos(x + SHOP_WIDTH / 2, y, z + SHOP_DEPTH - 2);
            if (level.getBlockState(floorChestPos).isAir()) {
                level.setBlock(floorChestPos.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                
                BlockState chestState = Blocks.CHEST.defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.SOUTH);
                level.setBlock(floorChestPos, chestState, 3);
                
                BlockEntity be = level.getBlockEntity(floorChestPos);
                if (be instanceof ChestBlockEntity chestEntity) {
                    chestEntity.setLootTable(LOOT_TABLE, random.nextLong());
                }
            }
        }
    }
}
