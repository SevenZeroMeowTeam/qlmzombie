package com.qlm.zombie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 镐子能力系统：合成镐子时有概率获得特殊能力。
 * - OBSIDIAN_BREAKER：可破坏黑曜石/哭泣黑曜石，并有掉落物
 * - RANGE_3X3：3×3 范围挖掘（以玩家面向平面为中心）
 * - RANGE_5X5：5×5 范围挖掘（以玩家面向平面为中心）
 * 多个能力可叠加。
 */
public enum PickaxeAbility {
    OBSIDIAN_BREAKER(0, "qlmzombie.pickaxe_ability.obsidian_breaker",
            "可破坏黑曜石/哭泣黑曜石", ChatFormatting.LIGHT_PURPLE, 5),
    RANGE_3X3(1, "qlmzombie.pickaxe_ability.range_3x3",
            "3×3 范围挖掘", ChatFormatting.AQUA, 3),
    RANGE_5X5(2, "qlmzombie.pickaxe_ability.range_5x5",
            "5×5 范围挖掘", ChatFormatting.GOLD, 1);

    public static final String NBT_TAG = "qlm_pickaxe_abilities";
    public static final String NBT_FLAGS = "flags";

    private final int bitIndex;
    private final String translationKey;
    private final String description;
    private final ChatFormatting formatting;
    private final int weight; // 合成时获得此能力的概率权重（/100）

    PickaxeAbility(int bitIndex, String translationKey, String description,
                   ChatFormatting formatting, int weight) {
        this.bitIndex = bitIndex;
        this.translationKey = translationKey;
        this.description = description;
        this.formatting = formatting;
        this.weight = weight;
    }

    public int getBitIndex() { return bitIndex; }
    public String getTranslationKey() { return translationKey; }
    public String getDescription() { return description; }
    public ChatFormatting getFormatting() { return formatting; }
    public int getWeight() { return weight; }

    public Component getDisplayName() {
        return Component.translatable(translationKey).withStyle(formatting);
    }

    private int bitMask() {
        return 1 << bitIndex;
    }

    // ==================== NBT 读写 ====================

    /** 检查 ItemStack 是否拥有指定能力 */
    public static boolean hasAbility(ItemStack stack, PickaxeAbility ability) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return false;
        int flags = tag.getCompound(NBT_TAG).getInt(NBT_FLAGS);
        return (flags & ability.bitMask()) != 0;
    }

    /** 检查 ItemStack 是否拥有任意镐子能力 */
    public static boolean hasAnyAbility(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return false;
        return tag.getCompound(NBT_TAG).getInt(NBT_FLAGS) != 0;
    }

    /** 获取 ItemStack 拥有的所有能力列表 */
    public static List<PickaxeAbility> getAbilities(ItemStack stack) {
        List<PickaxeAbility> abilities = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return abilities;
        int flags = tag.getCompound(NBT_TAG).getInt(NBT_FLAGS);
        for (PickaxeAbility a : values()) {
            if ((flags & a.bitMask()) != 0) abilities.add(a);
        }
        return abilities;
    }

    /** 为 ItemStack 附加指定能力 */
    public static void addAbility(ItemStack stack, PickaxeAbility ability) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag abilityTag = tag.contains(NBT_TAG)
                ? tag.getCompound(NBT_TAG) : new CompoundTag();
        int flags = abilityTag.getInt(NBT_FLAGS);
        flags |= ability.bitMask();
        abilityTag.putInt(NBT_FLAGS, flags);
        tag.put(NBT_TAG, abilityTag);
    }

    /**
     * 合成镐子时随机附加能力。
     * 仅随机赋予一个能力（不再叠加）。
     * 黑曜石破坏 5% / 3×3 范围 3% / 5×5 范围 1%。
     */
    public static void rollAbilities(ItemStack stack, RandomSource rnd) {
        if (!(stack.getItem() instanceof PickaxeItem)) return;
        if (hasAnyAbility(stack)) return; // 已有能力不重复 roll

        int roll = rnd.nextInt(100);
        if (roll < 5) {
            addAbility(stack, OBSIDIAN_BREAKER);
        } else if (roll < 8) {
            addAbility(stack, RANGE_3X3);
        } else if (roll < 9) {
            addAbility(stack, RANGE_5X5);
        }
    }
}
