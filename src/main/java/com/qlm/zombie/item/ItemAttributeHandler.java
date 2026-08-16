package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ItemAttributeHandler {

    private static final UUID IRON_SWORD_DAMAGE_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID IRON_AXE_DAMAGE_UUID   = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID IRON_PICKAXE_DAMAGE_UUID = UUID.fromString("f1a2b3c4-d5e6-7890-abcd-ef2345678901");
    private static final UUID QUALITY_DAMAGE_UUID     = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-234567890123");
    private static final UUID QUALITY_ARMOR_UUID      = UUID.fromString("d4e5f6a7-b8c9-0123-def0-345678901234");
    private static final UUID QUALITY_TOUGHNESS_UUID  = UUID.fromString("e5f6a7b8-c9d0-1234-ef01-456789012345");
    // ===== Apotheosis 风格词缀属性 UUID（品质越高词缀越强） =====
    private static final UUID AFFIX_ATTACK_SPEED_UUID = UUID.fromString("a1b2c3d4-0001-4a5b-9c1d-111111111111");
    private static final UUID AFFIX_MOVE_SPEED_UUID   = UUID.fromString("a1b2c3d4-0002-4a5b-9c1d-222222222222");
    private static final UUID AFFIX_KNOCKBACK_UUID    = UUID.fromString("a1b2c3d4-0003-4a5b-9c1d-333333333333");
    private static final UUID AFFIX_LUCK_UUID         = UUID.fromString("a1b2c3d4-0004-4a5b-9c1d-444444444444");
    private static final UUID AFFIX_MAX_HEALTH_UUID   = UUID.fromString("a1b2c3d4-0005-4a5b-9c1d-555555555555");

    /**
     * Apotheosis 风格词缀：根据品质等级计算属性加成（0-9 级）
     * 词缀：攻击速度 / 移动速度 / 击退 / 幸运 / 生命上限
     */
    private static double affixSpeed(EquipmentQuality q)  { return q.getId() * 0.05; }        // 每级 +5% 攻击速度
    private static double affixMove(EquipmentQuality q)   { return q.getId() * 0.004; }      // 每级 +0.4% 移动速度
    private static double affixKnock(EquipmentQuality q)  { return q.getId() >= 5 ? (q.getId() - 4) * 0.25 : 0; } // 稀有+ 击退
    private static double affixLuck(EquipmentQuality q)   { return q.getId() >= 4 ? (q.getId() - 3) : 0; }        // 优秀+ 幸运
    private static double affixHealth(EquipmentQuality q) { return q.getId() >= 2 ? q.getId() * 0.5 : 0; }        // 普通+ 生命

    private static boolean isQualityItem(Item item) {
        return item instanceof SwordItem
            || item instanceof DiggerItem
            || item instanceof ArmorItem
            || item instanceof ShieldItem
            || item instanceof ShearsItem
            || item instanceof BowItem
            || item instanceof CrossbowItem
            || item instanceof TridentItem
            || item instanceof FishingRodItem
            || item instanceof FlintAndSteelItem
            || com.qlm.zombie.craftingdead.item.CDItems.isQualityItem(item);
    }

    /**
     * 合成武器/工具/盔甲时：
     *   1. 基于材料等级 + 月相随机品质
     *   2. 随机伤害 + 固定属性加成写入 NBT
     *   3. 镐子附加能力（黑曜石破坏 / 基岩 / 范围挖掘）
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack stack = event.getCrafting();
        if (stack.isEmpty()) return;
        if (!isQualityItem(stack.getItem())) return;

        // 已有品质：不重复生成（避免刷品质）
        if (EquipmentQuality.fromStack(stack) != null) return;

        Player player = event.getEntity();
        RandomSource rnd = player != null ? player.getRandom() : RandomSource.create();

        // ---- 材料加成 ----
        float matShift = 0F;
        try {
            List<ItemStack> inputs = EquipmentQuality.extractCraftInputs(event);
            matShift = EquipmentQuality.computeMaterialBias(inputs);
        } catch (Throwable ignore) {}
        if (matShift == 0F) {
            // 反射拿不到输入就用 tier 维修材料推断
            matShift = EquipmentQuality.itemTierBias(stack.getItem());
        }

        // ---- 月相加成 ----
        float moonBias = 0F;
        if (player != null && player.level() instanceof ServerLevel sl) {
            moonBias = EquipmentQuality.computeMoonBias(sl);
        }

        // ---- 抽取品质并写入 NBT ----
        EquipmentQuality quality = EquipmentQuality.randomRoll(rnd, matShift, moonBias);
        quality.applyToStack(stack, rnd);

        // ---- 镐子能力 ----
        if (stack.getItem() instanceof PickaxeItem) {
            PickaxeAbility.rollAbilities(stack, rnd);
        }

        // ---- 神话品质直接显示名称前缀 ----
        stack.setHoverName(Component.empty()
                .append(quality.getDisplayComponent())
                .append(Component.translatable(stack.getDescriptionId()))
                .withStyle(quality.getFormatting()));

        QLMZombieMod.LOGGER.debug("[QLM-Quality] 合成品质: {} 材料加成:{} 月相加权:{} 伤害:{} ({})",
                quality.getDisplayName(), matShift, moonBias,
                EquipmentQuality.getRandomDamage(stack), stack.getDescriptionId());
    }

    // ==================== Attribute Modifiers ====================

    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null) return;

        EquipmentQuality q = EquipmentQuality.fromStack(stack);
        if (q != null) {
            applyQualityModifiers(event, stack, q);
        } else {
            applyVanillaOverrides(event, stack);
        }
    }

    private static void applyQualityModifiers(ItemAttributeModifierEvent event,
                                              ItemStack stack,
                                              EquipmentQuality q) {
        double randDmg = EquipmentQuality.getRandomDamage(stack);
        Item item = stack.getItem();

        // --- 神话级：攻击无限 ---
        if (q == EquipmentQuality.MYTHIC
                && (item instanceof SwordItem || item instanceof DiggerItem
                    || item instanceof BowItem || item instanceof CrossbowItem
                    || item instanceof TridentItem)) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(QUALITY_DAMAGE_UUID, "QLM Mythic Damage",
                            99999.0, AttributeModifier.Operation.ADDITION));
        } else {
            // 基础加成：品质倍率 × 随机伤害 + 品质固定加成
            float fixBonus = q.getBonusAttack();
            double finalBonus = fixBonus + randDmg * Math.max(0.2F, q.getAttackMultiplier());
            if (finalBonus > 0) {
                event.addModifier(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(QUALITY_DAMAGE_UUID, "QLM Quality Damage",
                                finalBonus, AttributeModifier.Operation.ADDITION));
            }
        }

        if (item instanceof ArmorItem || item instanceof ShieldItem) {
            var tag = stack.getTag();
            if (tag != null) {
                float bonusArmor = tag.getFloat(EquipmentQuality.NBT_ARMOR);
                float bonusTough = tag.getFloat(EquipmentQuality.NBT_TOUGHNESS);
                if (bonusArmor > 0) {
                    event.addModifier(Attributes.ARMOR,
                            new AttributeModifier(QUALITY_ARMOR_UUID, "QLM Quality Armor",
                                    bonusArmor, AttributeModifier.Operation.ADDITION));
                }
                if (bonusTough > 0) {
                    event.addModifier(Attributes.ARMOR_TOUGHNESS,
                            new AttributeModifier(QUALITY_TOUGHNESS_UUID, "QLM Quality Toughness",
                                    bonusTough, AttributeModifier.Operation.ADDITION));
                }
            }
        }

        // ===== Apotheosis 风格词缀属性（结合品质给予） =====
        if (q.getId() > 0) {
            // 武器/工具：攻击速度 + 击退 + 幸运
            if (item instanceof SwordItem || item instanceof DiggerItem || item instanceof TridentItem
                    || item instanceof BowItem || item instanceof CrossbowItem) {
                double spd = affixSpeed(q);
                if (spd > 0) event.addModifier(Attributes.ATTACK_SPEED,
                        new AttributeModifier(AFFIX_ATTACK_SPEED_UUID, "QLM Affix Attack Speed",
                                spd, AttributeModifier.Operation.MULTIPLY_TOTAL));
                double knock = affixKnock(q);
                if (knock > 0) event.addModifier(Attributes.ATTACK_KNOCKBACK,
                        new AttributeModifier(AFFIX_KNOCKBACK_UUID, "QLM Affix Knockback",
                                knock, AttributeModifier.Operation.ADDITION));
            }
            // 全部装备：幸运
            double luck = affixLuck(q);
            if (luck > 0) event.addModifier(Attributes.LUCK,
                    new AttributeModifier(AFFIX_LUCK_UUID, "QLM Affix Luck",
                            luck, AttributeModifier.Operation.ADDITION));
            // 盔甲：移动速度 + 生命上限
            if (item instanceof ArmorItem) {
                double move = affixMove(q);
                if (move > 0) event.addModifier(Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(AFFIX_MOVE_SPEED_UUID, "QLM Affix Move Speed",
                                move, AttributeModifier.Operation.MULTIPLY_TOTAL));
                double hp = affixHealth(q);
                if (hp > 0) event.addModifier(Attributes.MAX_HEALTH,
                        new AttributeModifier(AFFIX_MAX_HEALTH_UUID, "QLM Affix Health",
                                hp, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    private static void applyVanillaOverrides(ItemAttributeModifierEvent event, ItemStack stack) {
        // 铁剑基础 7 → 总 999；铁斧基础 9 → 总 55；铁镐基础 4 → 总 44
        if (stack.getItem() == Items.IRON_SWORD) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_SWORD_DAMAGE_UUID, "Iron Sword Damage Boost",
                            992.0, AttributeModifier.Operation.ADDITION));
        } else if (stack.getItem() == Items.IRON_AXE) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_AXE_DAMAGE_UUID, "Iron Axe Damage Boost",
                            46.0, AttributeModifier.Operation.ADDITION));
        } else if (stack.getItem() == Items.IRON_PICKAXE) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(IRON_PICKAXE_DAMAGE_UUID, "Iron Pickaxe Damage Boost",
                            40.0, AttributeModifier.Operation.ADDITION));
        }
    }

    // ==================== Tooltip ====================

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        EquipmentQuality q = EquipmentQuality.fromStack(stack);
        if (q == null) return;

        double randDmg = EquipmentQuality.getRandomDamage(stack);
        List<Component> tip = event.getToolTip();

        tip.add(1, Component.empty()
                .append(Component.literal("✦ 品质: ").withStyle(ChatFormatting.GRAY))
                .append(q.getDisplayComponent()));

        boolean isWeapon = stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof DiggerItem;

        if (isWeapon) {
            if (q == EquipmentQuality.MYTHIC) {
                tip.add(2, Component.literal("  ⚔ 攻击力 ∞ 无限").withStyle(ChatFormatting.DARK_PURPLE));
            } else {
                tip.add(2, Component.empty()
                        .append(Component.literal("  ⚔ 随机伤害 +").withStyle(ChatFormatting.RED))
                        .append(Component.literal(String.format("%.1f", randDmg)).withStyle(ChatFormatting.RED))
                        .append(Component.literal("  (×" + q.getAttackMultiplier() + ")").withStyle(ChatFormatting.GRAY)));
            }
        }

        if (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ShieldItem) {
            var tag = stack.getTag();
            if (tag != null) {
                float bH = tag.getFloat(EquipmentQuality.NBT_HEALTH);
                float bA = tag.getFloat(EquipmentQuality.NBT_ARMOR);
                if (bH > 0) {
                    tip.add(3, Component.empty()
                            .append(Component.literal("  ❤ 生命上限 +").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(String.format("%.0f", bH)).withStyle(ChatFormatting.GREEN)));
                }
                if (bA > 0) {
                    tip.add(4, Component.empty()
                            .append(Component.literal("  🛡 护甲 +").withStyle(ChatFormatting.BLUE))
                            .append(Component.literal(String.format("%.0f", bA)).withStyle(ChatFormatting.BLUE)));
                }
            }
        }

        if (stack.getItem() instanceof PickaxeItem) {
            int lvl = EquipmentQuality.getMineRangeLevel(stack);
            if (lvl > 0) {
                tip.add(Component.empty()
                        .append(Component.literal("  ⛏ 范围挖掘 " + EquipmentQuality.rangeLabel(lvl)).withStyle(ChatFormatting.AQUA)));
            }
            if (stack.getTag() != null && stack.getTag().getBoolean(EquipmentQuality.NBT_OBSIDIAN)) {
                tip.add(Component.literal("  ✦ 可破坏黑曜石/哭泣黑曜石").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (stack.getTag() != null && stack.getTag().getBoolean(EquipmentQuality.NBT_BREAK_BEDRK)) {
                tip.add(Component.literal("  ✦ 可破坏基岩").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        if (q.isIndestructible()) {
            tip.add(Component.empty()
                    .append(Component.literal("  ✦ 耐久: 无限制").withStyle(ChatFormatting.GOLD)));
            if (stack.getItem() instanceof ArmorItem) {
                tip.add(Component.empty()
                        .append(Component.literal("  ✦ 虚空免伤（需全套神话盔甲，缺一不可）").withStyle(ChatFormatting.AQUA)));
            }
        }

        // ===== Apotheosis 风格词缀属性显示 =====
        if (q.getId() > 0) {
            boolean isWeapon2 = stack.getItem() instanceof SwordItem || stack.getItem() instanceof DiggerItem
                    || stack.getItem() instanceof TridentItem || stack.getItem() instanceof BowItem
                    || stack.getItem() instanceof CrossbowItem;
            if (isWeapon2) {
                double spd = affixSpeed(q);
                if (spd > 0) tip.add(Component.empty()
                        .append(Component.literal("  ⚡ 攻击速度 +").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(String.format("%.0f%%", spd * 100)).withStyle(ChatFormatting.YELLOW)));
                double knock = affixKnock(q);
                if (knock > 0) tip.add(Component.empty()
                        .append(Component.literal("  💥 击退 +").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(String.format("%.1f", knock)).withStyle(ChatFormatting.YELLOW)));
            }
            double luck = affixLuck(q);
            if (luck > 0) tip.add(Component.empty()
                    .append(Component.literal("  🍀 幸运 +").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(String.format("%.0f", luck)).withStyle(ChatFormatting.GREEN)));
            if (stack.getItem() instanceof ArmorItem) {
                double move = affixMove(q);
                if (move > 0) tip.add(Component.empty()
                        .append(Component.literal("  🏃 移动速度 +").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(String.format("%.0f%%", move * 100)).withStyle(ChatFormatting.AQUA)));
                double hp = affixHealth(q);
                if (hp > 0) tip.add(Component.empty()
                        .append(Component.literal("  ❤ 生命上限 +").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(String.format("%.1f", hp)).withStyle(ChatFormatting.GREEN)));
            }
            if (q.getId() >= 5) {
                tip.add(Component.literal("  ✨ Apotheosis 风格词缀").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        // 显示 PickaxeAbility 详细能力
        if (PickaxeAbility.hasAnyAbility(stack)) {
            for (PickaxeAbility ab : PickaxeAbility.getAbilities(stack)) {
                tip.add(Component.literal("✦ ").append(ab.getDisplayName()));
            }
        }
    }
}
