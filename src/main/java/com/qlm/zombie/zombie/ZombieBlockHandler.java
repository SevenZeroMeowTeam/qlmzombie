package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 僵尸方块处理系统：
 * - 僵尸可以破坏阻挡路径的方块（非基岩、非液体）
 * - 僵尸可以放置方块搭建追击玩家
 * - 方块破坏与放置均有粒子特效
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ZombieBlockHandler {

    // 破坏冷却（tick）
    private static final int BREAK_COOLDOWN = 40;
    private static final int PLACE_COOLDOWN = 60;

    // 僵尸破坏追踪
    private static final Map<Integer, Long> lastBreakTime = new HashMap<>();
    private static final Map<Integer, Long> lastPlaceTime = new HashMap<>();

    // 可破坏方块列表（白名单）
    private static final Set<net.minecraft.world.level.block.Block> BREAKABLE_BLOCKS = new HashSet<>(Set.of(
        Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.COBBLESTONE,
        Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
        Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
        Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
        Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE,
        Blocks.GRAVEL, Blocks.SAND, Blocks.SANDSTONE,
        Blocks.GLASS, Blocks.GLASS_PANE,
        Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR,
        Blocks.OAK_TRAPDOOR,
        Blocks.TORCH, Blocks.LADDER, Blocks.VINE
    ));

    @SubscribeEvent
    public static void onZombieTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;

            // 每20tick处理一次（性能优化）
            if (level.getGameTime() % 20 != 0) continue;

            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, 
                new AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
                         level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
                z -> z.isAlive() && z.getTarget() != null)) {

                // 每5tick检查一次
                long gameTime = level.getGameTime();
                if (gameTime % 5 != 0) continue;

                // 获取目标方向
                Vec3 targetPos = zombie.getTarget().position();
                Vec3 zombiePos = zombie.position();
                BlockPos targetBlock = BlockPos.containing(
                    targetPos.x, zombiePos.y, targetPos.z
                );

                // 检查前方是否有方块阻挡
                Vec3 lookVec = zombie.getLookAngle();
                BlockPos frontPos = BlockPos.containing(
                    zombiePos.x + lookVec.x * 2,
                    zombiePos.y,
                    zombiePos.z + lookVec.z * 2
                );

                BlockState frontState = level.getBlockState(frontPos);

                // 如果前方有方块阻挡玩家
                if (!frontState.isAir() && frontState.getDestroySpeed(level, frontPos) >= 0 &&
                    !frontState.is(Blocks.BEDROCK) && !frontState.is(Blocks.OBSIDIAN) &&
                    !frontState.is(Blocks.CRYING_OBSIDIAN) && !frontState.is(Blocks.WATER) &&
                    !frontState.is(Blocks.LAVA)) {

                    tryBreakBlock(zombie, level, frontPos, gameTime);
                }

                // 如果僵尸在坑里，尝试放置方块搭建
                if (zombiePos.y < targetPos.y - 1) {
                    tryPlaceBlock(zombie, level, zombiePos, gameTime);
                }
            }
        }
    }

    private static void tryBreakBlock(Zombie zombie, ServerLevel level, BlockPos pos, long gameTime) {
        int entityId = zombie.getId();
        Long lastTime = lastBreakTime.get(entityId);
        if (lastTime != null && gameTime - lastTime < BREAK_COOLDOWN) return;
        lastBreakTime.put(entityId, gameTime);

        BlockState state = level.getBlockState(pos);

        // 检查是否可破坏
        if (!BREAKABLE_BLOCKS.contains(state.getBlock()) && !state.is(BlockTags.DOORS) &&
            !state.is(BlockTags.FENCES) && !state.is(BlockTags.WALLS)) {
            return;
        }

        // 破坏方块
        level.destroyBlock(pos, true, zombie);

        // 粒子特效
        level.sendParticles(
            ParticleTypes.CRIT,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            10, 0.3, 0.3, 0.3, 0.1
        );

        QLMZombieMod.LOGGER.debug("[僵尸方块] 僵尸破坏方块: {}", pos);
    }

    private static void tryPlaceBlock(Zombie zombie, ServerLevel level, Vec3 zombiePos, long gameTime) {
        int entityId = zombie.getId();
        Long lastTime = lastPlaceTime.get(entityId);
        if (lastTime != null && gameTime - lastTime < PLACE_COOLDOWN) return;
        lastPlaceTime.put(entityId, gameTime);

        // 在脚下放置方块（搭建追击）
        BlockPos belowPos = BlockPos.containing(zombiePos.x, zombiePos.y - 1, zombiePos.z);
        if (level.getBlockState(belowPos).isAir()) {
            // 使用泥土搭建
            level.setBlock(belowPos, Blocks.DIRT.defaultBlockState(), 3);

            // 粒子特效
            level.sendParticles(
                ParticleTypes.CRIT,
                belowPos.getX() + 0.5, belowPos.getY() + 0.5, belowPos.getZ() + 0.5,
                5, 0.2, 0.2, 0.2, 0.1
            );

            QLMZombieMod.LOGGER.debug("[僵尸方块] 僵尸放置方块: {}", belowPos);
        }
    }
}