package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.dayphase.DayPhase;
import com.qlm.zombie.dayphase.DayPhaseManager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class PlayerProtectionHandler {

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (event.getSource().getEntity() == null
                && event.getSource().getDirectEntity() == null) {
            return;
        }

        DayPhase currentPhase = DayPhaseManager.getCurrentPhase();
        if (currentPhase == DayPhase.PEACE) {
            event.setCanceled(true);
        }
    }
}