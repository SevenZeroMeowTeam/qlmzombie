package com.qlm.zombie.dayphase;

import com.qlm.zombie.config.QLMConfig;
import net.minecraft.world.Difficulty;

public enum DayPhase {
    SAFE(Difficulty.PEACEFUL, "安全日"),
    EASY(Difficulty.EASY, "简单"),
    NORMAL(Difficulty.NORMAL, "普通"),
    HARD(Difficulty.HARD, "困难(锁定)"),
    EXTREME(Difficulty.HARD, "极限(锁定)");

    private final Difficulty difficulty;
    private final String displayName;

    DayPhase(Difficulty difficulty, String displayName) {
        this.difficulty = difficulty;
        this.displayName = displayName;
    }

    public static DayPhase forDay(long day) {
        int peacefulDays = QLMConfig.PEACEFUL_DAYS.get();
        int normalDays = QLMConfig.NORMAL_DAYS.get();
        int hardDays = QLMConfig.HARD_DAYS.get();
        int extremeDays = QLMConfig.EXTREME_DAYS.get();

        if (day <= peacefulDays) return SAFE;
        if (day <= normalDays) return EASY;
        if (day <= hardDays) return NORMAL;
        if (day <= extremeDays) return HARD;
        return EXTREME;
    }

    public int minDay() {
        int peacefulDays = QLMConfig.PEACEFUL_DAYS.get();
        int normalDays = QLMConfig.NORMAL_DAYS.get();
        int hardDays = QLMConfig.HARD_DAYS.get();
        int extremeDays = QLMConfig.EXTREME_DAYS.get();

        switch (this) {
            case SAFE: return 1;
            case EASY: return peacefulDays + 1;
            case NORMAL: return normalDays + 1;
            case HARD: return hardDays + 1;
            case EXTREME: return extremeDays + 1;
            default: return 1;
        }
    }

    public int maxDay() {
        int peacefulDays = QLMConfig.PEACEFUL_DAYS.get();
        int normalDays = QLMConfig.NORMAL_DAYS.get();
        int hardDays = QLMConfig.HARD_DAYS.get();
        int extremeDays = QLMConfig.EXTREME_DAYS.get();

        switch (this) {
            case SAFE: return peacefulDays;
            case EASY: return normalDays;
            case NORMAL: return hardDays;
            case HARD: return extremeDays;
            case EXTREME: return Integer.MAX_VALUE;
            default: return 1;
        }
    }

    public Difficulty difficulty() { return difficulty; }
    public String displayName() { return displayName; }

    public boolean isLocked() {
        return this == HARD || this == EXTREME;
    }
}