package com.qlm.zombie.horde;

public enum HordeWave {
    WAVE_1(8, 0.05, 0.10, 2, 0, 0),
    WAVE_2(12, 0.10, 0.15, 3, 0, 0),
    WAVE_3(16, 0.15, 0.20, 4, 1, 0),
    WAVE_4(20, 0.20, 0.25, 5, 1, 1),
    WAVE_5(25, 0.25, 0.30, 6, 2, 1);

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
