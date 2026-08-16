package com.qlm.zombie.thirst;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.thirst.content.purity.WaterPurity;
import com.qlm.zombie.thirst.content.registry.ItemInit;
import com.qlm.zombie.thirst.content.thirst.PlayerThirst;
import com.qlm.zombie.thirst.foundation.common.capability.IThirst;
import com.qlm.zombie.thirst.foundation.common.loot.ModLootModifiers;
import com.qlm.zombie.thirst.foundation.config.ClientConfig;
import com.qlm.zombie.thirst.foundation.config.CommonConfig;
import com.qlm.zombie.thirst.foundation.config.ContainerConfig;
import com.qlm.zombie.thirst.foundation.config.ItemSettingsConfig;
import com.qlm.zombie.thirst.foundation.config.KeyWordConfig;
import com.qlm.zombie.thirst.foundation.gui.ThirstBarRenderer;
import com.qlm.zombie.thirst.foundation.network.ThirstModPacketHandler;
import com.qlm.zombie.thirst.foundation.tab.ThirstTab;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * 口渴系统模块（Thirst Module）。
 * <p>移植自开源模组 <a href="https://github.com/ghen-git/Thirst-Mod">Thirst-Mod (Thirst was Taken)</a>
 * v1.20.1-1.3.15（MIT License），原包名 {@code dev.ghen.thirst}，整合进 QLM Zombie。
 * 按开源准则保留署名，详见 README「开源模组清单」与 {@code THIRSTMOD_LICENSE.md}。</p>
 */
public final class Thirst
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID = QLMZombieMod.MOD_ID;

    /**
     * 由 {@link QLMZombieMod} 在 mod 构造时调用，注册口渴系统的物品、配置、事件与能力。
     */
    public static void init(IEventBus modBus)
    {
        modBus.addListener(Thirst::commonSetup);
        modBus.addListener(Thirst::clientSetup);
        modBus.addListener(Thirst::registerCapabilities);

        if (FMLEnvironment.dist.isClient())
        {
            modBus.addListener(ThirstBarRenderer::registerThirstOverlay);
        }

        ItemInit.ITEMS.register(modBus);
        ThirstTab.register(modBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);

        // 配置
        ItemSettingsConfig.setup();
        CommonConfig.setup();
        ClientConfig.setup();
        KeyWordConfig.setup();
        ContainerConfig.setup();
    }

    private static void commonSetup(final FMLCommonSetupEvent event)
    {
        try
        {
            WaterPurity.init();
            ThirstModPacketHandler.init();
        }
        catch (Throwable t)
        {
            // 健壮性：初始化失败仅影响口渴系统，绝不导致游戏启动崩溃
            LOGGER.error("[QLM Zombie] 口渴模块初始化部分失败，已忽略（不影响游戏启动）", t);
        }

        // 兼容标记：仅影响口渴数值结算（无外部类依赖）
        if (ModList.get().isLoaded("farmersdelight"))
            PlayerThirst.checkFDEffects = true;

        if (ModList.get().isLoaded("bakery"))
            PlayerThirst.checkLetsDoBakeryEffects = true;

        if (ModList.get().isLoaded("brewery"))
            PlayerThirst.checkLetsDoBreweryEffects = true;
    }

    private static void clientSetup(final FMLClientSetupEvent event)
    {
        // 无额外客户端初始化
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(IThirst.class);
    }

    // 原模组注释：this is from Create but it looked very cool
    public static ResourceLocation asResource(String path)
    {
        return new ResourceLocation(ID, path);
    }
}
