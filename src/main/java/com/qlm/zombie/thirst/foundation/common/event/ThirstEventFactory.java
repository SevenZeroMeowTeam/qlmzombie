package com.qlm.zombie.thirst.foundation.common.event;

import net.minecraftforge.common.MinecraftForge;

public class ThirstEventFactory {

    public static void onRegisterThirstValue() {
        MinecraftForge.EVENT_BUS.post(new RegisterThirstValueEvent());
    }
}
