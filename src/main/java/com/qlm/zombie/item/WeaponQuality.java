package com.qlm.zombie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * 武器品质系统：每次合成武器时随机生成品质和伤害。
 * 品质越高，伤害越高；品质越低，伤害越低（最低 0.1）。
 * 神话品质伤害无上限（使用指数分布实现长尾）。
 */
public enum WeaponQuality {
    BROKEN  (0, "qlmzombie.quality.broken",    0.1, 2.0,            25, ChatFormatting.DARK_GRAY),
    COMMON  (1, "qlmzombie.quality.common",    1.0, 5.0,            30, ChatFormatting.WHITE),
    FINE    (2, "qlmzombie.quality.fine",      3.0, 12.0,           20, ChatFormatting.GREEN),
    RARE    (3, "qlmzombie.quality.rare",      8.0, 25.0,           12, ChatFormatting.AQUA),
    EPIC    (4, "qlmzombie.quality.epic",     15.0, 50.0,            8, ChatFormatting.LIGHT_PURPLE),
    LEGENDARY(5,"qlmzombie.quality.legendary",30.0, 100.0,           4, ChatFormatting.GOLD),
    MYTHIC  (6, "qlmzombie.quality.mythic",   50.0, Double.MAX_VALUE, 1, ChatFormatting.RED);

    // NBT 键
    public static final String NBT_TAG = "qlm_weapon_quality";
    public static final String NBT_TIER = "tier";
    public static final String NBT_DAMAGE = "damage";

    private final int tier;
    private final String translationKey;
    private final double minDamage;
    private final double maxDamage;
    private final int weight;
    private final ChatFormatting formatting;

    WeaponQuality(int tier, String translationKey, double minDamage, double maxDamage,
                  int weight, ChatFormatting formatting) {
        this.tier = tier;
        this.translationKey = translationKey;
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
        this.weight = weight;
        this.formatting = formatting;
    }

    public int getTier() { return tier; }
    public String getTranslationKey() { return translationKey; }
    public double getMinDamage() { return minDamage; }
    public double getMaxDamage() { return maxDamage; }
    public int getWeight() { return weight; }
    public ChatFormatting getFormatting() { return formatting; }

    public Component getDisplayName() {
        return Component.translatable(translationKey).withStyle(formatting);
    }

    public static WeaponQuality byTier(int tier) {
        for (WeaponQuality q : values()) {
            if (q.tier == tier) return q;
        }
        return BROKEN;
    }

    /** 按权重随机抽取品质（破损最常见，神话最稀有） */
    public static WeaponQuality randomQuality(RandomSource rnd) {
        int totalWeight = Arrays.stream(values()).mapToInt(WeaponQuality::getWeight).sum();
        int roll = rnd.nextInt(totalWeight);
        int accumulated = 0;
        for (WeaponQuality q : values()) {
            accumulated += q.weight;
            if (roll < accumulated) return q;
        }
        return BROKEN;
    }

    /**
     * 在当前品质的伤害区间内随机生成伤害值。
     * 神话品质使用指数分布，伤害无上限（中位数约 70.8，90% 分位约 119，99% 分位约 188）。
     * 其余品质在 [minDamage, maxDamage) 内均匀分布。
     */
    public double randomDamage(RandomSource rnd) {
        if (this == MYTHIC) {
            double u = 1.0 - rnd.nextDouble(); // (0, 1]，避免 log(0)
            return minDamage + (-Math.log(u)) * 30.0;
        }
        return minDamage + rnd.nextDouble() * (maxDamage - minDamage);
    }

    public static boolean hasQuality(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_TAG);
    }

    public static WeaponQuality getQuality(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return null;
        return byTier(tag.getCompound(NBT_TAG).getInt(NBT_TIER));
    }

    public static double getDamage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return 0.0;
        return tag.getCompound(NBT_TAG).getDouble(NBT_DAMAGE);
    }

    public static void applyQuality(ItemStack stack, WeaponQuality quality, double damage) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag qualityTag = new CompoundTag();
        qualityTag.putInt(NBT_TIER, quality.tier);
        qualityTag.putDouble(NBT_DAMAGE, damage);
        tag.put(NBT_TAG, qualityTag);
    }
}
