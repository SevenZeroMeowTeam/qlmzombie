package com.qlm.zombie.music;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.monster.Zombie;

public class BossMusicManager {

    public static void onBossSpawned(Zombie boss, ServerLevel level) {
        QLMZombieMod.LOGGER.info("[QLM Zombie] Boss spawned, playing boss music");
    }

    public static void onBossKilled(Zombie boss) {
        QLMZombieMod.LOGGER.info("[QLM Zombie] Boss killed, stopping boss music");
    }
}