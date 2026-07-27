package com.qlm.zombie.horde;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;

public enum HordeWave {
    WAVE_1(20, 0.0, 0, 0, 0, 0),
    WAVE_2(35, 0.15, 0, 0, 0, 0),
    WAVE_3(50, 0.30, 0.20, 15, 0, 0),
    WAVE_4(70, 0.45, 0.15, 20, 0, 0),
    WAVE_5(100, 0.60, 0.25, 30, 1, 0),
    WAVE_6(0, 0.0, 0.0, 0, 0, 1);

    private final int zombieCount;
    private final double eliteChance;
    private final double skeletonChance;
    private final int skeletonCount;
    private final int bossCount;
    private final int giantCount;

    HordeWave(int zombieCount, double eliteChance, double skeletonChance, int skeletonCount, int bossCount, int giantCount) {
        this.zombieCount = zombieCount;
        this.eliteChance = eliteChance;
        this.skeletonChance = skeletonChance;
        this.skeletonCount = skeletonCount;
        this.bossCount = bossCount;
        this.giantCount = giantCount;
    }

    public int getZombieCount() { return zombieCount; }
    public double getEliteChance() { return eliteChance; }
    public double getSkeletonChance() { return skeletonChance; }
    public int getSkeletonCount() { return skeletonCount; }
    public int getBossCount() { return bossCount; }
    public int getGiantCount() { return giantCount; }
}