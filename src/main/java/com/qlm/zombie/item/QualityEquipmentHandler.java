package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 品质装备事件处理器。
 *  - 盔甲变化：刷新 MAX_HEALTH / ARMOR 属性加成
 *  - 受伤时：检查神话盔甲虚空免伤、处理玩家攻击的品质加成
 *  - 左键方块时：神话级工具允许破坏基岩（辅助确认，PickaxeAbilityHandler 为主）
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class QualityEquipmentHandler {

    private static final UUID QUALITY_HEALTH_MODIFIER = UUID.fromString("7e3b1c4a-5d2f-4a8e-9b6c-1d0f2e3a4b5c");
    private static final UUID QUALITY_ARMOR_MODIFIER  = UUID.fromString("8f4c2d5b-6e3a-4b9f-ac7d-2e1a3f4b5c6d");
    private static final UUID QUALITY_TOUGH_MODIFIER  = UUID.fromString("9f5d3e6c-7f4b-5c0a-bd8e-3f2b4c5d6e7f");

    // ===================== 基岩破坏辅助（神话品质物品） =====================

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;

        EquipmentQuality quality = EquipmentQuality.fromStack(held);
        if (quality == null) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(Blocks.BEDROCK) && quality.canBreakBedrockByQuality()) {
            Level level = (Level) event.getLevel();
            BlockPos pos = event.getPos();
            level.destroyBlock(pos, true, player);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    // ===================== 品质属性（盔甲） =====================

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player p)) return;
        recalculateQualityModifiers(p);
    }

    private static void recalculateQualityModifiers(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.removeModifier(QUALITY_HEALTH_MODIFIER);

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.removeModifier(QUALITY_ARMOR_MODIFIER);
        AttributeInstance tough = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (tough != null) tough.removeModifier(QUALITY_TOUGH_MODIFIER);

        float totalHealth = 0, totalArmor = 0, totalTough = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) continue;
            var tag = stack.getTag();
            if (tag == null) continue;
            totalHealth += tag.getFloat(EquipmentQuality.NBT_HEALTH);
            totalArmor  += tag.getFloat(EquipmentQuality.NBT_ARMOR);
            totalTough  += tag.getFloat(EquipmentQuality.NBT_TOUGHNESS);
        }

        if (totalHealth > 0) {
            maxHealth.addTransientModifier(new AttributeModifier(QUALITY_HEALTH_MODIFIER,
                    "QLM Quality Health", totalHealth, AttributeModifier.Operation.ADDITION));
        }
        if (armor != null && totalArmor > 0) {
            armor.addTransientModifier(new AttributeModifier(QUALITY_ARMOR_MODIFIER,
                    "QLM Quality Armor", totalArmor, AttributeModifier.Operation.ADDITION));
        }
        if (tough != null && totalTough > 0) {
            tough.addTransientModifier(new AttributeModifier(QUALITY_TOUGH_MODIFIER,
                    "QLM Quality Toughness", totalTough, AttributeModifier.Operation.ADDITION));
        }

        double curMax = maxHealth.getValue();
        if (player.getHealth() > curMax) player.setHealth((float) curMax);
    }

    // ===================== 受伤事件 =====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();

        // --- 盔甲方：虚空免伤（掉出世界）——必须全套神话盔甲 ---
        if (entity instanceof Player victim) {
            if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
                if (hasFullMythicArmor(victim)) {
                    event.setCanceled(true);
                    victim.teleportTo(victim.getX(), Math.max(32, victim.getY() + 64), victim.getZ());
                    victim.sendSystemMessage(Component.empty()
                            .append(Component.literal("[神话庇护] ").withStyle(ChatFormatting.DARK_PURPLE))
                            .append(Component.literal("虚空之力已被全套神话盔甲抵御！").withStyle(ChatFormatting.AQUA)));
                    return;
                }
            }
        }

        // --- 攻击方：品质伤害加成（补充 AttributeModifier，对 Living 类实体也生效） ---
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.isEmpty()) return;
            EquipmentQuality quality = EquipmentQuality.fromStack(weapon);
            if (quality == null) return;

            double randomDmg = EquipmentQuality.getRandomDamage(weapon);
            float fixBonus = quality.getBonusAttack();

            if (quality == EquipmentQuality.MYTHIC) {
                event.setAmount(99999.0F);
                return;
            }

            float mul = quality.getAttackMultiplier();
            if (mul >= Float.MAX_VALUE || quality.isIndestructible()) {
                event.setAmount(99999.0F);
                return;
            }

            float base = event.getAmount();
            float extra = (float) (fixBonus + randomDmg * Math.max(0.2F, mul));
            float newAmt = base * Math.max(1.0F, mul) + extra;
            if (newAmt > 0 && newAmt != base) event.setAmount(newAmt);
        }
    }

    // ===================== 登录/重生重算 =====================

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player p = event.getEntity();
        recalculateQualityModifiers(p);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player p = event.getEntity();
        recalculateQualityModifiers(p);
        PermanentKillStats.refreshPlayerModifiers(p);
    }

    /**
     * 检查玩家是否穿着全套神话盔甲（4 件全部为神话品质）。
     * 任意一件不是神话 → 不生效（脱一件都不行）。
     */
    public static boolean hasFullMythicArmor(Player player) {
        int mythicCount = 0;
        int armorSlotCount = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            armorSlotCount++;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) return false;
            EquipmentQuality q = EquipmentQuality.fromStack(stack);
            if (q == EquipmentQuality.MYTHIC) mythicCount++;
        }
        return mythicCount == 4 && armorSlotCount == 4;
    }
}
