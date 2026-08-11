package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class QualityEquipmentHandler {

    private static final UUID QUALITY_HEALTH_MODIFIER = UUID.fromString("7e3b1c4a-5d2f-4a8e-9b6c-1d0f2e3a4b5c");
    private static final UUID QUALITY_ARMOR_MODIFIER = UUID.fromString("8f4c2d5b-6e3a-4b9f-ac7d-2e1a3f4b5c6d");

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        if (!(held.getItem() instanceof net.minecraft.world.item.PickaxeItem)) return;

        EquipmentQuality quality = EquipmentQuality.fromStack(held);
        if (quality == null || !quality.isIndestructible()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(Blocks.BEDROCK)) {
            Level level = (Level) event.getLevel();
            BlockPos pos = event.getPos();
            level.destroyBlock(pos, true, player);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        recalculateQualityModifiers(player, event.getSlot(), event.getFrom(), event.getTo());
    }

    private static void recalculateQualityModifiers(Player player, EquipmentSlot changedSlot, ItemStack from, ItemStack to) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        maxHealth.removeModifier(QUALITY_HEALTH_MODIFIER);

        float totalHealthBonus = 0;
        float totalArmorBonus = 0;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            ItemStack stack;
            if (slot == changedSlot) {
                stack = to;
            } else {
                stack = player.getItemBySlot(slot);
            }
            if (stack.isEmpty()) continue;

            EquipmentQuality quality = EquipmentQuality.fromStack(stack);
            if (quality == null) continue;

            CompoundTag tag = stack.getTag();
            if (tag == null) continue;

            totalHealthBonus += tag.getFloat(EquipmentQuality.NBT_HEALTH);
            totalArmorBonus += tag.getFloat(EquipmentQuality.NBT_ARMOR);
        }

        if (totalHealthBonus > 0) {
            maxHealth.addTransientModifier(new AttributeModifier(
                    QUALITY_HEALTH_MODIFIER,
                    "Quality Armor Health Bonus",
                    totalHealthBonus,
                    AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(QUALITY_ARMOR_MODIFIER);
            if (totalArmorBonus > 0) {
                armorAttr.addTransientModifier(new AttributeModifier(
                        QUALITY_ARMOR_MODIFIER,
                        "Quality Armor Bonus",
                        totalArmorBonus,
                        AttributeModifier.Operation.ADDITION));
            }
        }

        double currentMax = maxHealth.getValue();
        if (player.getHealth() > currentMax) {
            player.setHealth((float) currentMax);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;

        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            for (ItemStack armor : player.getArmorSlots()) {
                if (armor.isEmpty()) continue;
                EquipmentQuality quality = EquipmentQuality.fromStack(armor);
                if (quality != null && quality.isIndestructible()) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (event.getSource().getEntity() instanceof Player) {
            Player attacker = (Player) event.getSource().getEntity();
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.isEmpty()) return;

            EquipmentQuality quality = EquipmentQuality.fromStack(weapon);
            if (quality == null) return;

            float bonusAttack = 0;
            if (weapon.getTag() != null) {
                bonusAttack = weapon.getTag().getFloat(EquipmentQuality.NBT_ATTACK);
            }

            float attackMultiplier = quality.getAttackMultiplier();

            if (quality.isIndestructible() && bonusAttack >= 99999) {
                event.setAmount(99999);
            } else {
                float newAmount = event.getAmount() + bonusAttack;
                if (attackMultiplier > 1.0f && attackMultiplier < 99999) {
                    newAmount = (float) (event.getAmount() * attackMultiplier + bonusAttack);
                }
                event.setAmount(newAmount);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            recalculateQualityModifiers(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            recalculateQualityModifiers(player, EquipmentSlot.MAINHAND, ItemStack.EMPTY, ItemStack.EMPTY);
        }
    }
}
