package com.qlm.zombie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 装备品质系统 — 10 级品质。
 * 劣质 → 一般 → 普通 → 精良 → 高级 → 稀有 → 神器 → 传说 → 史诗 → 神话
 *
 * 神话品质：攻击力 99999，无耐久，可破坏基岩。
 */
public enum EquipmentQuality {
    INFERIOR(0, "劣质", "qlmzombie.quality.inferior", ChatFormatting.GRAY, 0.5F, 0, 0, 0, false),
    COMMON(1, "一般", "qlmzombie.quality.common", ChatFormatting.WHITE, 1.0F, 2, 0, 1, false),
    NORMAL(2, "普通", "qlmzombie.quality.normal", ChatFormatting.GREEN, 1.5F, 4, 1, 2, false),
    FINE(3, "精良", "qlmzombie.quality.fine", ChatFormatting.BLUE, 2.0F, 7, 2, 3, false),
    ADVANCED(4, "高级", "qlmzombie.quality.advanced", ChatFormatting.AQUA, 3.0F, 12, 3, 5, false),
    RARE(5, "稀有", "qlmzombie.quality.rare", ChatFormatting.LIGHT_PURPLE, 5.0F, 20, 5, 8, false),
    ARTIFACT(6, "神器", "qlmzombie.quality.artifact", ChatFormatting.GOLD, 10.0F, 50, 10, 15, false),
    LEGENDARY(7, "传说", "qlmzombie.quality.legendary", ChatFormatting.RED, 25.0F, 200, 20, 25, false),
    EPIC(8, "史诗", "qlmzombie.quality.epic", ChatFormatting.DARK_RED, 100.0F, 2000, 50, 50, false),
    MYTHIC(9, "神话", "qlmzombie.quality.mythic", ChatFormatting.DARK_PURPLE, 99999.0F, 99999, 100, 100, true);

    public static final String NBT_TAG = "qlm_equipment_quality";
    public static final String NBT_ATTACK = "qlm_bonus_attack";
    public static final String NBT_HEALTH = "qlm_bonus_health";
    public static final String NBT_ARMOR = "qlm_bonus_armor";
    public static final String NBT_INDESTRUCTIBLE = "qlm_indestructible";
    public static final String NBT_BREAK_BEDROCK = "qlm_break_bedrock";

    private final int id;
    private final String displayName;
    private final String translationKey;
    private final ChatFormatting formatting;
    private final float attackMultiplier;  // 攻击力倍率
    private final float bonusAttack;       // 固定攻击力加成
    private final float bonusHealth;       // 生命上限加成
    private final float bonusArmor;        // 护甲加成
    private final boolean indestructible;  // 无耐久 + 可破坏基岩

    EquipmentQuality(int id, String displayName, String translationKey, ChatFormatting formatting,
                     float attackMultiplier, float bonusAttack, float bonusHealth, float bonusArmor,
                     boolean indestructible) {
        this.id = id;
        this.displayName = displayName;
        this.translationKey = translationKey;
        this.formatting = formatting;
        this.attackMultiplier = attackMultiplier;
        this.bonusAttack = bonusAttack;
        this.bonusHealth = bonusHealth;
        this.bonusArmor = bonusArmor;
        this.indestructible = indestructible;
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

    public Component getDisplayComponent() {
        return Component.translatable(translationKey).withStyle(formatting);
    }

    /**
     * 获取 NBT 中存储的品质
     */
    public static EquipmentQuality fromStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_TAG)) return null;
        int qid = tag.getInt(NBT_TAG);
        for (EquipmentQuality q : values()) {
            if (q.id == qid) return q;
        }
        return null;
    }

    /**
     * 将品质写入 NBT
     */
    public void applyToStack(net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getOrCreateTag();
        tag.putInt(NBT_TAG, id);
        tag.putFloat(NBT_ATTACK, bonusAttack);
        tag.putFloat(NBT_HEALTH, bonusHealth);
        tag.putFloat(NBT_ARMOR, bonusArmor);
        if (indestructible) {
            tag.putBoolean(NBT_INDESTRUCTIBLE, true);
            tag.putBoolean(NBT_BREAK_BEDROCK, true);
            // 无耐久：设置 Unbreakable
            tag.putBoolean("Unbreakable", true);
            // 伤害值设为攻击力
            if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) {
                // 基础攻击力 + 品质加成
            }
        }
    }

    /**
     * 随机 roll 品质（权重分布）
     * 劣质 20% / 一般 20% / 普通 15% / 精良 12% / 高级 10% / 稀有 8% / 神器 6% / 传说 4% / 史诗 3% / 神话 2%
     */
    public static EquipmentQuality randomRoll(net.minecraft.util.RandomSource rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 20) return INFERIOR;
        if (roll < 40) return COMMON;
        if (roll < 55) return NORMAL;
        if (roll < 67) return FINE;
        if (roll < 77) return ADVANCED;
        if (roll < 85) return RARE;
        if (roll < 91) return ARTIFACT;
        if (roll < 95) return LEGENDARY;
        if (roll < 98) return EPIC;
        return MYTHIC;
    }
}
