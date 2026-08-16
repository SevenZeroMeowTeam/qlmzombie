package com.qlm.zombie.item;

import com.qlm.zombie.moon.MoonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 装备品质系统 — 10 级品质。
 * 劣质 → 一般 → 普通 → 精良 → 优秀 → 稀有 → 卓越 → 史诗 → 传说 → 神话
 *
 * 神话品质：攻击力无限，无耐久，可破坏基岩，全套盔甲虚空免伤。
 *
 * 品质生成加成来源：
 *  - 合成材料等级（下界合金 +3，钻石 +2，铁/金 +1，其他 0）
 *  - 月相（幸运之月 +1，血月 +1，丰收之月 +0.5 品质加权）
 */
public enum EquipmentQuality {
    INFERIOR (0, "劣质", "qlmzombie.quality.inferior",  ChatFormatting.GRAY,        0.40F,   0,   0,   0, false,  25,   0.5,    2.0),
    COMMON   (1, "一般", "qlmzombie.quality.common",    ChatFormatting.WHITE,       1.00F,   2,   0,   1, false,  22,   1.0,    5.0),
    NORMAL   (2, "普通", "qlmzombie.quality.normal",    ChatFormatting.GREEN,       1.30F,   3,   0,   1, false,  18,   1.5,    7.0),
    FINE     (3, "精良", "qlmzombie.quality.fine",      ChatFormatting.DARK_GREEN,  1.60F,   5,   1,   2, false,  15,   2.0,   10.0),
    EXCELLENT(4, "优秀", "qlmzombie.quality.excellent", ChatFormatting.AQUA,        2.50F,  10,   2,   4, false,  10,   3.0,   18.0),
    RARE     (5, "稀有", "qlmzombie.quality.rare",      ChatFormatting.LIGHT_PURPLE,4.00F,  18,   4,   8, false,   7,   6.0,   30.0),
    EXCEPTIONAL(6, "卓越", "qlmzombie.quality.exceptional", ChatFormatting.GOLD,    7.00F,  40,   8,  15, false,   4,  10.0,   60.0),
    EPIC     (7, "史诗", "qlmzombie.quality.epic",      ChatFormatting.RED,        15.00F, 100,  15,  30, false,   2.5F,20.0,  150.0),
    LEGENDARY(8, "传说", "qlmzombie.quality.legendary", ChatFormatting.DARK_RED,   40.00F, 500,  30,  60, false,   1.2F,50.0,  500.0),
    MYTHIC   (9, "神话", "qlmzombie.quality.mythic",    ChatFormatting.DARK_PURPLE, Float.MAX_VALUE, 99999, 100, 100, true, 0.3F, 99999.0, Double.MAX_VALUE);

    // ================= NBT Tags =================
    public static final String NBT_TAG         = "qlm_equipment_quality";
    public static final String NBT_ATTACK      = "qlm_bonus_attack";
    public static final String NBT_HEALTH      = "qlm_bonus_health";
    public static final String NBT_ARMOR       = "qlm_bonus_armor";
    public static final String NBT_TOUGHNESS   = "qlm_bonus_toughness";
    public static final String NBT_RANDOM_DMG  = "qlm_random_damage";
    public static final String NBT_INDESTRUCT  = "qlm_indestructible";
    public static final String NBT_BREAK_BEDRK = "qlm_break_bedrock";
    public static final String NBT_VOID_SAFE   = "qlm_void_safe";
    public static final String NBT_MINE_RANGE  = "qlm_mine_range";   // 0,1,2,3,4,5 对应 1x1,3x3,5x5,7x7,9x9,11x11
    public static final String NBT_OBSIDIAN    = "qlm_obsidian_break";

    // ================= Enum Fields =================
    private final int id;
    private final String displayName;
    private final String translationKey;
    private final ChatFormatting formatting;
    private final float attackMultiplier;
    private final float bonusAttack;
    private final float bonusHealth;
    private final float bonusArmor;
    private final boolean indestructible;
    private final float weight;          // 随机品质权重
    private final double minRandomDamage;
    private final double maxRandomDamage;

    EquipmentQuality(int id, String displayName, String translationKey, ChatFormatting formatting,
                     float attackMultiplier, float bonusAttack, float bonusHealth, float bonusArmor,
                     boolean indestructible, float weight, double minRandomDamage, double maxRandomDamage) {
        this.id = id;
        this.displayName = displayName;
        this.translationKey = translationKey;
        this.formatting = formatting;
        this.attackMultiplier = attackMultiplier;
        this.bonusAttack = bonusAttack;
        this.bonusHealth = bonusHealth;
        this.bonusArmor = bonusArmor;
        this.indestructible = indestructible;
        this.weight = weight;
        this.minRandomDamage = minRandomDamage;
        this.maxRandomDamage = maxRandomDamage;
    }

    public int getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getTranslationKey() { return translationKey; }
    public ChatFormatting getFormatting() { return formatting; }
    public float getAttackMultiplier() { return attackMultiplier; }
    public float getBonusAttack() { return bonusAttack; }
    public float getBonusHealth() { return bonusHealth; }
    public float getBonusArmor() { return bonusArmor; }
    public boolean isIndestructible() { return indestructible; }
    public float getWeight() { return weight; }
    public double getMinRandomDamage() { return minRandomDamage; }
    public double getMaxRandomDamage() { return maxRandomDamage; }

    public Component getDisplayComponent() {
        return Component.translatable(translationKey).withStyle(formatting);
    }

    /** 根据品质等级获取范围挖掘半径：0=1x1, 1=3x3, 2=5x5, 3=7x7, 4=9x9, 5=11x11 */
    public int getDefaultMineRangeLevel() {
        if (this.id >= 9) return 5; // 神话 11x11
        if (this.id >= 8) return 4; // 传说 9x9
        if (this.id >= 7) return 3; // 史诗 7x7
        if (this.id >= 6) return 2; // 卓越 5x5
        if (this.id >= 5) return 1; // 稀有 3x3
        return 0;
    }

    public boolean canBreakBedrockByQuality() { return this.id >= 9; }
    public boolean canBreakObsidianByQuality() { return this.id >= 5; } // 稀有以上可破坏黑曜石

    // ================= NBT Read =================

    public static EquipmentQuality fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return null;
        int qid = tag.getInt(NBT_TAG);
        for (EquipmentQuality q : values()) {
            if (q.id == qid) return q;
        }
        return null;
    }

    public static double getRandomDamage(ItemStack stack) {
        if (stack == null || stack.getTag() == null) return 0.0;
        return stack.getTag().getDouble(NBT_RANDOM_DMG);
    }

    public static int getMineRangeLevel(ItemStack stack) {
        if (stack == null || stack.getTag() == null) return 0;
        EquipmentQuality q = fromStack(stack);
        int byAbility = stack.getTag().getInt(NBT_MINE_RANGE);
        int byQuality = q != null ? q.getDefaultMineRangeLevel() : 0;
        return Math.max(byAbility, byQuality);
    }

    // ================= NBT Write =================

    /**
     * 将品质及所有衍生属性写入物品 NBT。
     * 如果指定了 randomDamage 参数，则使用该值；否则基于品质重新随机。
     */
    public void applyToStack(ItemStack stack, net.minecraft.util.RandomSource rnd) {
        var tag = stack.getOrCreateTag();
        tag.putInt(NBT_TAG, id);
        tag.putFloat(NBT_ATTACK, bonusAttack);
        tag.putFloat(NBT_HEALTH, bonusHealth);
        tag.putFloat(NBT_ARMOR, bonusArmor);
        tag.putFloat(NBT_TOUGHNESS, bonusArmor * 0.5F);

        double dmg = randomDamage(rnd);
        tag.putDouble(NBT_RANDOM_DMG, dmg);

        if (indestructible) {
            tag.putBoolean(NBT_INDESTRUCT, true);
            tag.putBoolean("Unbreakable", true);
            tag.putBoolean(NBT_BREAK_BEDRK, true);
            tag.putBoolean(NBT_VOID_SAFE, true);
        }
        if (canBreakBedrockByQuality()) {
            tag.putBoolean(NBT_BREAK_BEDRK, true);
        }
        if (canBreakObsidianByQuality()) {
            tag.putBoolean(NBT_OBSIDIAN, true);
        }

        // 镐子的范围挖掘：直接根据品质等级写入
        if (stack.getItem() instanceof PickaxeItem) {
            int rangeLvl = getDefaultMineRangeLevel();
            if (rangeLvl > 0 && !tag.contains(NBT_MINE_RANGE)) {
                tag.putInt(NBT_MINE_RANGE, rangeLvl);
            }
        }
    }

    /** 兼容旧调用：无 RNG 时使用 fixed damage */
    public void applyToStack(ItemStack stack) {
        applyToStack(stack, net.minecraft.util.RandomSource.create());
    }

    // ================= Damage Random =================

    /**
     * 根据当前品质随机一个伤害加成值。
     * 神话品质使用指数分布（无上限长尾），其余均匀分布。
     */
    public double randomDamage(net.minecraft.util.RandomSource rnd) {
        if (this == MYTHIC) {
            double u = 1.0 - rnd.nextDouble();
            return 99999.0 + (-Math.log(u)) * 2000.0; // 中位数约 11k，90% 分位约 5k+
        }
        return minRandomDamage + rnd.nextDouble() * (maxRandomDamage - minRandomDamage);
    }

    // ================= Weighted Random Roll =================

    /**
     * 根据权重随机品质，接受额外加成等级 shiftBonus（可小数）。
     * shiftBonus > 0 倾向高品质（下界合金 +3 等）。
     * moonBonus：0=普通夜晚，0.05 丰收之月微加权，0.1 血月，0.15 幸运之月。
     */
    public static EquipmentQuality randomRoll(net.minecraft.util.RandomSource rnd,
                                              float shiftBonus, float moonBias) {
        EquipmentQuality[] vals = values();
        // 构建权重表
        float[] ws = new float[vals.length];
        float total = 0;
        for (int i = 0; i < vals.length; i++) {
            float w = vals[i].weight;
            // shiftBonus: 越高 id 的乘以越多 (1 + shift * i/max)
            w *= 1.0F + shiftBonus * (float) i / (vals.length - 1);
            // moonBias: 仅对高品质加权（稀有及以上 = id >= 5）
            if (i >= 5) w *= 1.0F + moonBias;
            ws[i] = w;
            total += w;
        }
        float roll = rnd.nextFloat() * total;
        float acc = 0;
        for (int i = 0; i < vals.length; i++) {
            acc += ws[i];
            if (roll < acc) return vals[i];
        }
        return MYTHIC;
    }

    public static EquipmentQuality randomRoll(net.minecraft.util.RandomSource rnd) {
        return randomRoll(rnd, 0.0F, 0.0F);
    }

    // ================= Craft Material Bias =================

    /**
     * 基于合成台输入格中的物品判断材料等级加成。
     * 下界合金 +3，钻石 +2，铁/金/红石/烈焰棒 +1，其他 0。
     */
    public static float computeMaterialBias(List<ItemStack> inputs) {
        if (inputs == null || inputs.isEmpty()) return 0;
        int best = 0;
        for (ItemStack s : inputs) {
            if (s == null || s.isEmpty()) continue;
            Item it = s.getItem();
            String name = it.toString().toLowerCase(java.util.Locale.ROOT);
            int t = 0;
            if (name.contains("netherite")) t = 3;
            else if (name.contains("diamond")) t = 2;
            else if (name.contains("iron") || name.contains("gold") || name.contains("redstone")
                    || name.contains("blaze") || name.contains("prismarine")) t = 1;
            if (t > best) best = t;
        }
        return best * 0.5F; // 3 * 0.5 = +1.5 shiftBonus
    }

    /**
     * 基于月相返回 moonBias。
     */
    public static float computeMoonBias(ServerLevel level) {
        if (level == null) return 0;
        if (MoonHelper.isLuckyMoon(level))   return 0.4F;
        if (MoonHelper.isBloodMoon(level))   return 0.25F;
        if (MoonHelper.isHarvestMoon(level)) return 0.1F;
        return 0;
    }

    // ================= Mining Range Helpers =================

    public static final int[] RANGE_RADIUS = {0, 1, 2, 3, 4, 5};
    public static String rangeLabel(int rangeLevel) {
        int side = 1 + RANGE_RADIUS[Math.min(rangeLevel, RANGE_RADIUS.length - 1)] * 2;
        return side + "×" + side;
    }

    /** 从所有合成输入槽提取物品（兼容 PlayerEvent.ItemCraftedEvent 无直接访问） */
    public static List<ItemStack> extractCraftInputs(PlayerEvent.ItemCraftedEvent event) {
        List<ItemStack> result = new ArrayList<>();
        if (event == null) return result;
        Object invHolder = event.getInventory();
        try {
            // net.minecraft.world.inventory.CraftingContainer 或子类
            Class<?> cls = invHolder.getClass();
            for (Field f : cls.getFields()) {
                if (java.util.List.class.isAssignableFrom(f.getType()) ||
                        ItemStack[].class.isAssignableFrom(f.getType())) {
                    Object val = f.get(invHolder);
                    if (val instanceof java.util.List<?> list) {
                        for (Object o : list) {
                            if (o instanceof ItemStack s && !s.isEmpty()) result.add(s);
                        }
                    } else if (val instanceof ItemStack[] arr) {
                        for (ItemStack s : arr) if (s != null && !s.isEmpty()) result.add(s);
                    }
                }
            }
        } catch (Throwable ignored) { /* 反射失败就使用结果物品名推断 */ }
        // 回退：根据合成产物名称推断材料等级（粗略）
        if (result.isEmpty() && event.getCrafting() != null) {
            result.add(event.getCrafting().copy());
        }
        return result;
    }

    // ================= Vanilla-compatible shiftBonus via tiered repair material =================

    public static float itemTierBias(Item resultItem) {
        if (resultItem instanceof TieredItem t) {
            Tier tier = t.getTier();
            try {
                Ingredient repair = tier.getRepairIngredient();
                for (ItemStack s : repair.getItems()) {
                    String n = s.toString().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("netherite")) return 3 * 0.5F;
                    if (n.contains("diamond"))   return 2 * 0.5F;
                    if (n.contains("iron") || n.contains("gold")) return 1 * 0.5F;
                }
            } catch (Throwable ignored) {}
        } else if (resultItem instanceof ArmorItem a) {
            ArmorMaterial m = a.getMaterial();
            for (ItemStack s : m.getRepairIngredient().getItems()) {
                String n = s.toString().toLowerCase(java.util.Locale.ROOT);
                if (n.contains("netherite")) return 3 * 0.5F;
                if (n.contains("diamond"))   return 2 * 0.5F;
                if (n.contains("iron") || n.contains("gold")) return 1 * 0.5F;
            }
        }
        return 0;
    }
}
