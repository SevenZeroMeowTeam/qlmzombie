/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * QTable — Q 值表
 * 存储 state × action 的期望回报，使用 epsilon-greedy 策略选择动作
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.qlearning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Q-Table
 *
 * Q(s, a) 表示在状态 s 下采取动作 a 的期望累积回报。
 * 表以 state-index 为 key，每个状态对应一个动作数组。
 */
public class QTable {

    private final Map<Integer, float[]> table = new HashMap<>();
    private final int actionCount;

    public QTable(int actionCount) {
        this.actionCount = actionCount;
    }

    public float getQ(int stateIndex, int actionIndex) {
        float[] row = table.get(stateIndex);
        if (row == null) return 0.0F;
        if (actionIndex < 0 || actionIndex >= row.length) return 0.0F;
        return row[actionIndex];
    }

    public void setQ(int stateIndex, int actionIndex, float value) {
        float[] row = table.computeIfAbsent(stateIndex, k -> new float[actionCount]);
        if (actionIndex >= 0 && actionIndex < row.length) {
            row[actionIndex] = value;
        }
    }

    /** 获取状态下的最优动作 */
    public int bestAction(int stateIndex) {
        float[] row = table.get(stateIndex);
        if (row == null) return 0;
        int best = 0;
        float bestVal = row[0];
        for (int i = 1; i < row.length; i++) {
            if (row[i] > bestVal) {
                bestVal = row[i];
                best = i;
            }
        }
        return best;
    }

    /** epsilon-greedy 策略 */
    public int epsilonGreedy(int stateIndex, double epsilon) {
        if (ThreadLocalRandom.current().nextDouble() < epsilon) {
            return ThreadLocalRandom.current().nextInt(actionCount);
        }
        return bestAction(stateIndex);
    }

    public int getActionCount() {
        return actionCount;
    }

    public int getStateCount() {
        return table.size();
    }

    /** 序列化为 NBT（持久化到 AI 实体的 PersistentData） */
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Integer, float[]> entry : table.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putInt("state", entry.getKey());
            float[] values = entry.getValue();
            byte[] bytes = new byte[values.length * 4];
            int p = 0;
            for (float v : values) {
                int bits = Float.floatToIntBits(v);
                bytes[p++] = (byte) (bits);
                bytes[p++] = (byte) (bits >> 8);
                bytes[p++] = (byte) (bits >> 16);
                bytes[p++] = (byte) (bits >> 24);
            }
            row.putByteArray("values", bytes);
            list.add(row);
        }
        tag.put("qtable", list);
        tag.putInt("actionCount", actionCount);
        return tag;
    }

    public static QTable load(CompoundTag tag) {
        int actionCount = tag.getInt("actionCount");
        if (actionCount <= 0) actionCount = QLearningAction.values().length;
        QTable qt = new QTable(actionCount);
        ListTag list = tag.getList("qtable", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            int state = row.getInt("state");
            byte[] bytes = row.getByteArray("values");
            float[] values = new float[actionCount];
            for (int j = 0; j < Math.min(values.length, bytes.length / 4); j++) {
                int p = j * 4;
                int bits = (bytes[p] & 0xFF)
                        | ((bytes[p + 1] & 0xFF) << 8)
                        | ((bytes[p + 2] & 0xFF) << 16)
                        | ((bytes[p + 3] & 0xFF) << 24);
                values[j] = Float.intBitsToFloat(bits);
            }
            qt.table.put(state, values);
        }
        return qt;
    }
}
