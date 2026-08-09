package com.qlm.zombie.craftingdead.entity.zombie;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class ScientistZombie extends Zombie {

    public ScientistZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        if (this.random.nextFloat() < 0.5F) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GLASS_BOTTLE));
        }
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        CompoundTag displayTag = new CompoundTag();
        displayTag.putInt("color", 0xFFFFFF);
        chestplate.addTagElement("display", displayTag);
        this.setItemSlot(EquipmentSlot.CHEST, chestplate);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide && this.random.nextFloat() < 0.3F) {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker) {
                livingAttacker.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, false));
            }
        }
        return result;
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (!this.level().isClientSide && this.deathTime == 1 && this.random.nextFloat() < 0.5F) {
            for (int i = 0; i < 20; i++) {
                double x = this.getX() + (this.random.nextDouble() - 0.5D) * 2.0D;
                double y = this.getY() + this.random.nextDouble() * 2.0D;
                double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 2.0D;
                this.level().addParticle(ParticleTypes.CLOUD, x, y, z, 0.0D, 0.05D, 0.0D);
            }
        }
    }
}
