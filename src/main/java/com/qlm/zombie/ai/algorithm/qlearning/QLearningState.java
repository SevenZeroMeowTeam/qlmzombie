/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * Q-Learning Reinforcement Learning Framework — 原创实现
 * 参考: Watkins (1989) Q-Learning; Sutton & Barto "Reinforcement Learning: An Introduction"
 *
 * QLearningState — 状态表示
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.qlearning;

import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Q-Learning 状态
 *
 * 将游戏环境抽象为离散状态:
 *   - 健康等级: HIGH / MID / LOW
 *   - 饥饿等级: FULL / HUNGRY / STARVING
 *   - 敌人距离: NONE / NEAR / FAR
 *   - 主人距离: CLOSE / FAR / OUT_OF_SIGHT
 *   - 装备等级: ARMED / BARE
 *
 * 状态空间大小: 3 × 3 × 3 × 3 × 2 = 162
 */
public class QLearningState {

    public enum HealthLevel { HIGH, MID, LOW }
    public enum HungerLevel { FULL, HUNGRY, STARVING }
    public enum EnemyDistance { NONE, NEAR, FAR }
    public enum OwnerDistance { CLOSE, FAR, OUT_OF_SIGHT }
    public enum EquipmentLevel { ARMED, BARE }

    public final HealthLevel health;
    public final HungerLevel hunger;
    public final EnemyDistance enemy;
    public final OwnerDistance owner;
    public final EquipmentLevel equipment;

    public QLearningState(HealthLevel health, HungerLevel hunger,
                          EnemyDistance enemy, OwnerDistance owner,
                          EquipmentLevel equipment) {
        this.health = health;
        this.hunger = hunger;
        this.enemy = enemy;
        this.owner = owner;
        this.equipment = equipment;
    }

    /** 从环境感知生成状态 */
    public static QLearningState fromEnvironment(FakePlayerEntity ai) {
        HealthLevel hl;
        float hpRatio = ai.getHealth() / Math.max(1.0F, ai.getMaxHealth());
        if (hpRatio > 0.66F) hl = HealthLevel.HIGH;
        else if (hpRatio > 0.33F) hl = HealthLevel.MID;
        else hl = HealthLevel.LOW;

        HungerLevel hgl;
        int food = ai.getFoodLevel();
        if (food >= 15) hgl = HungerLevel.FULL;
        else if (food >= 7) hgl = HungerLevel.HUNGRY;
        else hgl = HungerLevel.STARVING;

        EnemyDistance ed = EnemyDistance.NONE;
        LivingEntity target = ai.getTarget();
        if (target != null && target.isAlive()) {
            double distSq = ai.distanceToSqr(target);
            if (distSq < 16.0D) ed = EnemyDistance.NEAR;
            else if (distSq < 1024.0D) ed = EnemyDistance.FAR;
        }

        OwnerDistance od = OwnerDistance.OUT_OF_SIGHT;
        LivingEntity owner = ai.getOwner();
        if (owner instanceof Player) {
            double distSq = ai.distanceToSqr(owner);
            if (distSq < 36.0D) od = OwnerDistance.CLOSE;
            else if (distSq < 1024.0D) od = OwnerDistance.FAR;
        }

        EquipmentLevel el = ai.getMainHandItem().isEmpty() ? EquipmentLevel.BARE : EquipmentLevel.ARMED;

        return new QLearningState(hl, hgl, ed, od, el);
    }

    /** 唯一索引，用作 Q-Table 的 key */
    public int index() {
        int i = health.ordinal();
        i = i * 3 + hunger.ordinal();
        i = i * 3 + enemy.ordinal();
        i = i * 3 + owner.ordinal();
        i = i * 2 + equipment.ordinal();
        return i;
    }

    @Override
    public int hashCode() {
        return index();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof QLearningState other)) return false;
        return this.index() == other.index();
    }

    @Override
    public String toString() {
        return String.format("S[hp=%s,food=%s,enemy=%s,owner=%s,arm=%s]",
                health, hunger, enemy, owner, equipment);
    }
}
