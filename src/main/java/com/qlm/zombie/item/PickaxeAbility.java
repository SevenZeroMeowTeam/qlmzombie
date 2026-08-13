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
 * 镐子能力系统：合成镐子时随品质与随机概率获得特殊能力。
 * - OBSIDIAN_BREAKER：可破坏黑曜石 / 哭泣黑曜石
 * - BEDROCK_BREAKER：可破坏基岩（神话级品质或随机附加）
 * - RANGE_3X3：3×3 范围挖掘
 * - RANGE_5X5：5×5
 * - RANGE_7X7：7×7
 * - RANGE_9X9：9×9
 * - RANGE_11X11：11×11
 *
 * 多个范围能力互斥（取最大的），黑曜石/基岩破坏可叠加。
 */
public enum PickaxeAbility {
    OBSIDIAN_BREAKER(0, "qlmzombie.pickaxe_ability.obsidian_breaker",
            "可破坏黑曜石/哭泣黑曜石", ChatFormatting.LIGHT_PURPLE, 15),
    BEDROCK_BREAKER (1, "qlmzombie.pickaxe_ability.bedrock_breaker",
            "可破坏基岩",              ChatFormatting.DARK_PURPLE,   1),
    RANGE_3X3      (2, "qlmzombie.pickaxe_ability.range_3x3",
            "3×3 范围挖掘",             ChatFormatting.AQUA,          25),
    RANGE_5X5      (3, "qlmzombie.pickaxe_ability.range_5x5",
            "5×5 范围挖掘",             ChatFormatting.BLUE,          12),
    RANGE_7X7      (4, "qlmzombie.pickaxe_ability.range_7x7",
            "7×7 范围挖掘",             ChatFormatting.LIGHT_PURPLE,   6),
    RANGE_9X9      (5, "qlmzombie.pickaxe_ability.range_9x9",
            "9×9 范围挖掘",             ChatFormatting.GOLD,           3),
    RANGE_11X11    (6, "qlmzombie.pickaxe_ability.range_11x11",
            "11×11 范围挖掘",           ChatFormatting.DARK_RED,       1);

    public static final String NBT_TAG = "qlm_pickaxe_abilities";
    public static final String NBT_FLAGS = "flags";

    private final int bitIndex;
    private final String translationKey;
    private final String description;
    private final ChatFormatting formatting;
    private final int weight; // 获取能力的相对权重（/ sum）

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

    private int bitMask() { return 1 << bitIndex; }

    public static boolean hasAbility(ItemStack stack, PickaxeAbility ability) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return false;
        int flags = tag.getCompound(NBT_TAG).getInt(NBT_FLAGS);
        return (flags & ability.bitMask()) != 0;
    }

    public static boolean hasAnyAbility(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return false;
        return tag.getCompound(NBT_TAG).getInt(NBT_FLAGS) != 0;
    }

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
     * 合成镐子时，基于品质等级 + 随机概率赋予能力。
     * 所有品质（包括劣质）都有概率获得任意能力，仅概率不同。
     * 品质越高，获得更稀有能力的概率越大。
     * 神话品质：直接拥有所有能力。
     */
    public static void rollAbilities(ItemStack stack, RandomSource rnd) {
        if (!(stack.getItem() instanceof PickaxeItem)) return;

        EquipmentQuality quality = EquipmentQuality.fromStack(stack);
        int qualityId = quality != null ? quality.getId() : 0;
        int maxQ = EquipmentQuality.values().length - 1; // 9
        float qFactor = 1.0F + qualityId * 0.3F; // 品质越高权重越高

        // --- 神话品质：直接拥有所有能力 ---
        if (qualityId >= 9) {
            addAbility(stack, OBSIDIAN_BREAKER);
            addAbility(stack, BEDROCK_BREAKER);
            addAbility(stack, RANGE_11X11);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt(EquipmentQuality.NBT_MINE_RANGE, 5);
            return;
        }

        // --- 黑曜石破坏：所有品质都有概率 ---
        // 劣质 5%，每级 +5%，普通(3) 20%，稀有(5) 30%，神器(6) 35%，史诗(7) 40%，传说(8) 45%
        if (!hasAbility(stack, OBSIDIAN_BREAKER)) {
            float obsidianChance = 0.05F + qualityId * 0.05F;
            if (quality.canBreakObsidianByQuality() || rnd.nextFloat() < obsidianChance) {
                addAbility(stack, OBSIDIAN_BREAKER);
            }
        }

        // --- 基岩破坏：所有品质都有概率（很低） ---
        // 劣质 0.1%，每级 +0.4%，普通(3) 1.3%，稀有(5) 2.1%，神器(6) 2.5%，传说(8) 3.3%
        if (!hasAbility(stack, BEDROCK_BREAKER)) {
            float bedrockChance = 0.001F + qualityId * 0.004F;
            if (quality.canBreakBedrockByQuality() || rnd.nextFloat() < bedrockChance) {
                addAbility(stack, BEDROCK_BREAKER);
            }
        }

        // --- 范围挖掘：所有品质都有概率 ---
        PickaxeAbility[] ranges = { RANGE_11X11, RANGE_9X9, RANGE_7X7, RANGE_5X5, RANGE_3X3 };
        PickaxeAbility granted = null;

        // 基础概率：劣质 5%，每级 +6%，最高 5×6+5=35% (传说 8 → 53%)
        float baseChance = 0.05F + qualityId * 0.06F;
        if (rnd.nextFloat() < baseChance) {
            // 品质决定可获得的最高范围等级（但仍可抽到低范围）
            int maxRangeIdByQ;
            if (qualityId >= 9) maxRangeIdByQ = RANGE_11X11.bitIndex;
            else if (qualityId >= 8) maxRangeIdByQ = RANGE_11X11.bitIndex;
            else if (qualityId >= 7) maxRangeIdByQ = RANGE_9X9.bitIndex;
            else if (qualityId >= 6) maxRangeIdByQ = RANGE_7X7.bitIndex;
            else if (qualityId >= 5) maxRangeIdByQ = RANGE_5X5.bitIndex;
            else if (qualityId >= 3) maxRangeIdByQ = RANGE_5X5.bitIndex;
            else if (qualityId >= 1) maxRangeIdByQ = RANGE_3X3.bitIndex;
            else maxRangeIdByQ = RANGE_3X3.bitIndex; // 劣质最多 3x3

            // 按权重从大到小抽
            int totalW = 0;
            for (PickaxeAbility r : ranges) {
                if (r.bitIndex > maxRangeIdByQ) continue;
                totalW += Math.max(1, (int)(r.weight * qFactor));
            }
            int roll = totalW > 0 ? rnd.nextInt(totalW) : 0;
            int acc = 0;
            for (PickaxeAbility r : ranges) {
                if (r.bitIndex > maxRangeIdByQ) continue;
                acc += Math.max(1, (int)(r.weight * qFactor));
                if (roll < acc) { granted = r; break; }
            }
        }
        if (granted != null) {
            addAbility(stack, granted);
            int rangeLvl = switch (granted) {
                case RANGE_3X3   -> 1;
                case RANGE_5X5   -> 2;
                case RANGE_7X7   -> 3;
                case RANGE_9X9   -> 4;
                case RANGE_11X11 -> 5;
                default -> 0;
            };
            CompoundTag tag = stack.getOrCreateTag();
            if (rangeLvl > tag.getInt(EquipmentQuality.NBT_MINE_RANGE)) {
                tag.putInt(EquipmentQuality.NBT_MINE_RANGE, rangeLvl);
            }
        }
    }
}
