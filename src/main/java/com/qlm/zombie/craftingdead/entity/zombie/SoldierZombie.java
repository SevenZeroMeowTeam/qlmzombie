package com.qlm.zombie.craftingdead.entity.zombie;

import com.qlm.zombie.craftingdead.effect.CDEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class SoldierZombie extends Zombie {

    private int bleedEffectTickCounter = 0;

    public SoldierZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        if (this.random.nextFloat() < 0.9F) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
        if (this.random.nextFloat() < 0.8F) {
            if (this.random.nextBoolean()) {
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            }
            if (this.random.nextBoolean()) {
                this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            }
            if (this.random.nextBoolean()) {
                this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            }
            if (this.random.nextBoolean()) {
                this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            bleedEffectTickCounter++;
            if (bleedEffectTickCounter >= 1200) {
                bleedEffectTickCounter = 0;
                AABB area = this.getBoundingBox().inflate(8.0D);
                for (Player player : this.level().getEntitiesOfClass(Player.class, area)) {
                    player.addEffect(new MobEffectInstance(CDEffects.BLEEDING.get(), 60, 0, false, false));
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F;
    }
}
