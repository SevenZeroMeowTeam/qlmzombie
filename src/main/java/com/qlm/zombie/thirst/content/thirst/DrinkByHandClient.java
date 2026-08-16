package com.qlm.zombie.thirst.content.thirst;

import com.qlm.zombie.thirst.foundation.config.ClientConfig;
import com.qlm.zombie.thirst.foundation.network.ThirstModPacketHandler;
import com.qlm.zombie.thirst.foundation.network.message.DrinkByHandMessage;
import com.qlm.zombie.thirst.foundation.util.MathHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DrinkByHandClient
{
    public static void drinkByHand()
    {
        Minecraft mc = Minecraft.getInstance();

        Player player = mc.player;
        Level level = mc.level;
        // 健壮性：玩家/世界未就绪时直接返回，避免 NPE
        if (player == null || level == null) return;
        BlockPos blockPos = MathHelper.getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY).getBlockPos();
        boolean HandAvailable;

        if (level.getFluidState(blockPos).is(FluidTags.WATER) && player.isCrouching() && !player.isInvulnerable()) {

            if(!ClientConfig.DRINK_BOTH_HAND_NEEDED.get()){
                HandAvailable = player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
            }else {
                HandAvailable = player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() && player.getItemInHand(InteractionHand.OFF_HAND).isEmpty();
            }
            if(HandAvailable){
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 1.0F, 1.0F);
                ThirstModPacketHandler.INSTANCE.sendToServer(new DrinkByHandMessage(blockPos));
            }
        }
    }
}
