package com.qlm.zombie.dayphase;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class DifficultyLockState extends SavedData {
    private static final String DATA_NAME = "qlmzombie_difficulty_lock";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_LAST_APPLIED_DAY = "lastAppliedDay";
    private static final String KEY_LAST_PHASE = "lastPhase";

    private boolean locked = false;
    private long lastAppliedDay = -1L;
    private String lastPhase = "";

    public DifficultyLockState() {
    }

    public static DifficultyLockState get(MinecraftServer server) {
        net.minecraft.server.level.ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            // 理论上主世界总是存在；若极端情况下为 null，返回一个临时空状态避免 NPE
            return new DifficultyLockState();
        }
        return overworld.getDataStorage().computeIfAbsent(DifficultyLockState::load, DifficultyLockState::new, DATA_NAME);
    }

    public static DifficultyLockState load(CompoundTag tag) {
        DifficultyLockState state = new DifficultyLockState();
        state.locked = tag.getBoolean(KEY_LOCKED);
        state.lastAppliedDay = tag.getLong(KEY_LAST_APPLIED_DAY);
        state.lastPhase = tag.getString(KEY_LAST_PHASE);
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(KEY_LOCKED, locked);
        tag.putLong(KEY_LAST_APPLIED_DAY, lastAppliedDay);
        tag.putString(KEY_LAST_PHASE, lastPhase);
        return tag;
    }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; setDirty(); }
    public long getLastAppliedDay() { return lastAppliedDay; }
    public void setLastAppliedDay(long day) { this.lastAppliedDay = day; setDirty(); }
    public String getLastPhase() { return lastPhase; }
    public void setLastPhase(String phase) { this.lastPhase = phase; setDirty(); }
}
