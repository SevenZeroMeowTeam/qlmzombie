package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.QLMEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AIPlayerSpawnHandler {

    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastCheckTick = 0;
    private static final String[] AI_NAMES = {
            "Alex", "Steve", "Him", "Noor", "Sunny", "Ari", "Zuri", "Makena",
            "Kai", "Efe", "七零", "喵喵", "战士", "幸存者", "游侠", "猎人",
            "守卫", "探险家", "流浪者", "拓荒者", "哨兵", "铁卫", "游骑兵"
    };

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!QLMConfig.ENABLE_AI_PLAYER_SPAWN.get()) return;

        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        long currentTick = overworld.getGameTime();
        long intervalTicks = QLMConfig.AI_PLAYER_SPAWN_INTERVAL.get() * 20L;

        if (lastCheckTick == 0) {
            lastCheckTick = currentTick;
            return;
        }

        if (currentTick - lastCheckTick < intervalTicks) return;
        lastCheckTick = currentTick;

        int maxCount = QLMConfig.AI_PLAYER_MAX_COUNT.get();
        int currentCount = countAIPlayers(overworld);
        if (currentCount >= maxCount) return;

        double chance = QLMConfig.AI_PLAYER_SPAWN_CHANCE.get();
        if (RANDOM.nextDouble() > chance) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        ServerPlayer targetPlayer = players.get(RANDOM.nextInt(players.size()));

        int spawnRadius = QLMConfig.AI_PLAYER_SPAWN_RADIUS.get();
        int minDistance = 24;
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double distance = minDistance + RANDOM.nextDouble() * (spawnRadius - minDistance);
        double spawnX = targetPlayer.getX() + Math.cos(angle) * distance;
        double spawnZ = targetPlayer.getZ() + Math.sin(angle) * distance;

        BlockPos spawnPos = new BlockPos((int) spawnX, 0, (int) spawnZ);
        BlockPos surfacePos = overworld.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, spawnPos);

        if (surfacePos.getY() < overworld.getMinBuildHeight() + 1) {
            surfacePos = new BlockPos(surfacePos.getX(), overworld.getMinBuildHeight() + 1, surfacePos.getZ());
        }

        trySpawnAIPlayer(overworld, surfacePos);
    }

    private static void trySpawnAIPlayer(ServerLevel level, BlockPos pos) {
        FakePlayerEntity ai = QLMEntities.FAKE_PLAYER.get().create(level);
        if (ai == null) return;

        String name = AI_NAMES[RANDOM.nextInt(AI_NAMES.length)] + "_" + (100 + RANDOM.nextInt(900));
        ai.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        ai.setCustomNameStr(name);
        ai.setPlayerUUID(UUID.randomUUID());

        if (RANDOM.nextFloat() < 0.25F) {
            ai.giveRandomWeapon();
        }

        level.addFreshEntity(ai);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.distanceToSqr(ai.getX(), ai.getY(), ai.getZ()) < 256 * 256) {
                player.displayClientMessage(
                        Component.literal("§e[AI玩家] §a" + name + " §7出现在附近！手持食物右键可驯服"),
                        false
                );
            }
        }
    }

    private static int countAIPlayers(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FakePlayerEntity) {
                count++;
            }
        }
        return count;
    }
}