/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ----------------------------------------------------------------------------
 * QLM ModSDK — 自定义实体描述符（Builder API）
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.registry;

import net.minecraft.world.entity.MobCategory;

/**
 * 自定义实体的数据描述类。通过 Builder 模式构造后，
 * 交给 {@link SDKRegistry#registerEntity(String, CustomEntity)} 注册。
 *
 * <p>注意：由于 Forge {@code EntityType} 需要一个具体的实体工厂，
 * SDK 仅缓存描述符；具体实体类的注册由调用方在合适时机通过
 * {@link SDKRegistry#registerEntity(String, CustomEntity, net.minecraft.world.entity.EntityType.EntityFactory)}
 * 完成，或仅缓存描述符供查询。</p>
 */
public final class CustomEntity {

    private final String id;
    private final MobCategory entityType;
    private final float health;
    private final float speed;
    private final float attackDamage;
    private final float followRange;
    private final boolean isHostile;
    private final boolean isTameable;
    private final int spawnEggColorPrimary;
    private final int spawnEggColorSecondary;

    private CustomEntity(Builder b) {
        this.id = b.id;
        this.entityType = b.entityType;
        this.health = b.health;
        this.speed = b.speed;
        this.attackDamage = b.attackDamage;
        this.followRange = b.followRange;
        this.isHostile = b.isHostile;
        this.isTameable = b.isTameable;
        this.spawnEggColorPrimary = b.spawnEggColorPrimary;
        this.spawnEggColorSecondary = b.spawnEggColorSecondary;
    }

    public String getId() { return id; }
    public MobCategory getEntityType() { return entityType; }
    public float getHealth() { return health; }
    public float getSpeed() { return speed; }
    public float getAttackDamage() { return attackDamage; }
    public float getFollowRange() { return followRange; }
    public boolean isHostile() { return isHostile; }
    public boolean isTameable() { return isTameable; }
    public int getSpawnEggColorPrimary() { return spawnEggColorPrimary; }
    public int getSpawnEggColorSecondary() { return spawnEggColorSecondary; }

    /**
     * 创建 Builder。
     *
     * @param id 实体的注册 id
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 自定义实体 Builder */
    public static final class Builder {
        private final String id;
        private MobCategory entityType = MobCategory.CREATURE;
        private float health = 20.0f;
        private float speed = 0.25f;
        private float attackDamage = 2.0f;
        private float followRange = 16.0f;
        private boolean isHostile = false;
        private boolean isTameable = false;
        private int spawnEggColorPrimary = 0xFFFFFF;
        private int spawnEggColorSecondary = 0x000000;

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("CustomEntity id 不能为空");
            }
            this.id = id;
        }

        public Builder entityType(MobCategory entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder health(float health) {
            this.health = health;
            return this;
        }

        public Builder speed(float speed) {
            this.speed = speed;
            return this;
        }

        public Builder attackDamage(float attackDamage) {
            this.attackDamage = attackDamage;
            return this;
        }

        public Builder followRange(float followRange) {
            this.followRange = followRange;
            return this;
        }

        public Builder hostile(boolean hostile) {
            this.isHostile = hostile;
            return this;
        }

        public Builder tameable(boolean tameable) {
            this.isTameable = tameable;
            return this;
        }

        /** 设置生成蛋的主色（背景色） */
        public Builder spawnEggColor(int primary) {
            this.spawnEggColorPrimary = primary;
            return this;
        }

        /** 设置生成蛋的主色（背景）与斑点色（前景） */
        public Builder spawnEggColor(int primary, int secondary) {
            this.spawnEggColorPrimary = primary;
            this.spawnEggColorSecondary = secondary;
            return this;
        }

        public CustomEntity build() {
            return new CustomEntity(this);
        }
    }
}
