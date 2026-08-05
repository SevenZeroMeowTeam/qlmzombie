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
 * QLM ModSDK — 自定义方块描述符（Builder API）
 * 不直接继承 Block，而以数据类描述方块属性，由 SDK 内部转换为 Forge Block。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;

/**
 * 自定义方块的数据描述类。通过 Builder 模式构造后，
 * 交给 {@link SDKRegistry#registerBlock(String, CustomBlock)} 注册到 Forge。
 *
 * <p>注意：Minecraft 1.20.1 已移除 {@code Material} 类，故 {@code material}
 * 字段改为字符串描述符（如 "stone", "wood", "metal", "glass"），
 * 仅作为元数据；具体方块属性由 {@link SDKRegistry} 内部通过
 * {@code Block.Properties.of()} 构建。</p>
 */
public final class CustomBlock {

    private final String id;
    private final String material;
    private final float hardness;
    private final float resistance;
    private final float lightLevel;
    private final SoundType soundType;
    private final Item dropItem;
    private final boolean isTransparent;
    private final boolean isCollidable;

    private CustomBlock(Builder b) {
        this.id = b.id;
        this.material = b.material;
        this.hardness = b.hardness;
        this.resistance = b.resistance;
        this.lightLevel = b.lightLevel;
        this.soundType = b.soundType;
        this.dropItem = b.dropItem;
        this.isTransparent = b.isTransparent;
        this.isCollidable = b.isCollidable;
    }

    public String getId() { return id; }
    public String getMaterial() { return material; }
    public float getHardness() { return hardness; }
    public float getResistance() { return resistance; }
    public float getLightLevel() { return lightLevel; }
    public SoundType getSoundType() { return soundType; }
    public Item getDropItem() { return dropItem; }
    public boolean isTransparent() { return isTransparent; }
    public boolean isCollidable() { return isCollidable; }

    /**
     * 创建 Builder。
     *
     * @param id 方块的注册 id（小写，下划线分隔）
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 自定义方块 Builder */
    public static final class Builder {
        private final String id;
        private String material = "stone";
        private float hardness = 1.5f;
        private float resistance = 6.0f;
        private float lightLevel = 0.0f;
        private SoundType soundType = SoundType.STONE;
        private Item dropItem = null;
        private boolean isTransparent = false;
        private boolean isCollidable = true;

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("CustomBlock id 不能为空");
            }
            this.id = id;
        }

        public Builder material(String material) {
            this.material = material;
            return this;
        }

        public Builder hardness(float hardness) {
            this.hardness = hardness;
            return this;
        }

        public Builder resistance(float resistance) {
            this.resistance = resistance;
            return this;
        }

        public Builder lightLevel(float lightLevel) {
            this.lightLevel = lightLevel;
            return this;
        }

        public Builder soundType(SoundType soundType) {
            this.soundType = soundType;
            return this;
        }

        public Builder dropItem(Item dropItem) {
            this.dropItem = dropItem;
            return this;
        }

        public Builder transparent(boolean transparent) {
            this.isTransparent = transparent;
            return this;
        }

        public Builder collidable(boolean collidable) {
            this.isCollidable = collidable;
            return this;
        }

        public CustomBlock build() {
            return new CustomBlock(this);
        }
    }
}
