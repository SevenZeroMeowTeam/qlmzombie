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
 * QLM ModSDK — 自定义物品描述符（Builder API）
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.registry;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;

/**
 * 自定义物品的数据描述类。通过 Builder 模式构造后，
 * 交给 {@link SDKRegistry#registerItem(String, CustomItem)} 注册到 Forge。
 */
public final class CustomItem {

    private final String id;
    private final int maxStackSize;
    private final int maxDamage;
    private final Rarity rarity;
    private final boolean isFood;
    private final int nutrition;
    private final float saturation;
    private final UseAnim useAnimation;
    private final boolean isTool;
    private final String toolType;
    private final int toolLevel;

    private CustomItem(Builder b) {
        this.id = b.id;
        this.maxStackSize = b.maxStackSize;
        this.maxDamage = b.maxDamage;
        this.rarity = b.rarity;
        this.isFood = b.isFood;
        this.nutrition = b.nutrition;
        this.saturation = b.saturation;
        this.useAnimation = b.useAnimation;
        this.isTool = b.isTool;
        this.toolType = b.toolType;
        this.toolLevel = b.toolLevel;
    }

    public String getId() { return id; }
    public int getMaxStackSize() { return maxStackSize; }
    public int getMaxDamage() { return maxDamage; }
    public Rarity getRarity() { return rarity; }
    public boolean isFood() { return isFood; }
    public int getNutrition() { return nutrition; }
    public float getSaturation() { return saturation; }
    public UseAnim getUseAnimation() { return useAnimation; }
    public boolean isTool() { return isTool; }
    public String getToolType() { return toolType; }
    public int getToolLevel() { return toolLevel; }

    /**
     * 转换为 Forge {@link Item.Properties}。
     */
    public Item.Properties toItemProperties() {
        Item.Properties props = new Item.Properties();
        props.stacksTo(maxStackSize);
        if (maxDamage > 0) {
            props.durability(maxDamage);
        }
        if (rarity != null) {
            props.rarity(rarity);
        }
        if (isFood) {
            props.food(new FoodProperties.Builder()
                    .nutrition(nutrition)
                    .saturationMod(saturation)
                    .build());
        }
        return props;
    }

    /**
     * 创建 Builder。
     *
     * @param id 物品的注册 id
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 自定义物品 Builder */
    public static final class Builder {
        private final String id;
        private int maxStackSize = 64;
        private int maxDamage = 0;
        private Rarity rarity = Rarity.COMMON;
        private boolean isFood = false;
        private int nutrition = 0;
        private float saturation = 0.0f;
        private UseAnim useAnimation = UseAnim.NONE;
        private boolean isTool = false;
        private String toolType = null;
        private int toolLevel = 0;

        private Builder(String id) {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("CustomItem id 不能为空");
            }
            this.id = id;
        }

        public Builder maxStackSize(int maxStackSize) {
            this.maxStackSize = maxStackSize;
            return this;
        }

        public Builder maxDamage(int maxDamage) {
            this.maxDamage = maxDamage;
            return this;
        }

        public Builder rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder food(boolean food) {
            this.isFood = food;
            return this;
        }

        public Builder nutrition(int nutrition) {
            this.nutrition = nutrition;
            return this;
        }

        public Builder saturation(float saturation) {
            this.saturation = saturation;
            return this;
        }

        public Builder useAnimation(UseAnim useAnimation) {
            this.useAnimation = useAnimation;
            return this;
        }

        public Builder tool(boolean tool) {
            this.isTool = tool;
            return this;
        }

        public Builder toolType(String toolType) {
            this.toolType = toolType;
            return this;
        }

        public Builder toolLevel(int toolLevel) {
            this.toolLevel = toolLevel;
            return this;
        }

        public CustomItem build() {
            return new CustomItem(this);
        }
    }
}
