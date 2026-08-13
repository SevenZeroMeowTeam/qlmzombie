package com.qlm.zombie.mixin;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidBucketWrapper.class)
public abstract class MixinFluidBucketWrapper {

    @Shadow
    @NotNull
    protected ItemStack container;

    @Inject(method = "getFluid", at = @At("HEAD"), cancellable = true)
    private void qlmzombie$checkNullFluid(CallbackInfoReturnable<FluidStack> cir) {
        try {
            if (container.getItem() instanceof BucketItem bucket && bucket.getFluid() == null) {
                cir.setReturnValue(FluidStack.EMPTY);
            }
        } catch (Exception ignored) {
            cir.setReturnValue(FluidStack.EMPTY);
        }
    }
}
