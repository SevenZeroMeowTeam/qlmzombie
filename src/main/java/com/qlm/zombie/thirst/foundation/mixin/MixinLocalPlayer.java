package com.qlm.zombie.thirst.foundation.mixin;

import com.qlm.zombie.thirst.foundation.common.capability.IThirst;
import com.qlm.zombie.thirst.foundation.common.capability.ModCapabilities;
import com.qlm.zombie.thirst.foundation.config.CommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer{

    /**
     * @reason prevent sprinting when thirst
     * @return food level or thirst level
     */

    @Redirect(method ="hasEnoughFoodToStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;getFoodLevel()I"))
    public int hasEnoughThirstToStartSprinting(FoodData instance){
        int Food = instance.getFoodLevel();
        if(!CommonConfig.MOVE_SLOW_WHEN_THIRSTY.get()) return Food;

        if(Food < 6.0F){
            return Food;
        }else {
           Food = Minecraft.getInstance().player.getCapability(ModCapabilities.PLAYER_THIRST)
                   .lazyMap(IThirst::getThirst).orElse(Food);
        }
        return Food;
    }
}
