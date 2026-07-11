package com.qlm.zombie;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import com.qlm.zombie.command.QLMCommands;
import com.qlm.zombie.horde.HordeManager;
import com.qlm.zombie.player.PlayerHealthHandler;
import com.qlm.zombie.player.PlayerInitHandler;
import com.qlm.zombie.advancements.AdvancementManager;
import com.qlm.zombie.item.QLMItems;
import com.qlm.zombie.restriction.MobRestrictionHandler;
import com.qlm.zombie.zombie.ZombieEvolutionHandler;
import com.qlm.zombie.ai.AIOptimizationHandler;
import com.qlm.zombie.music.QLMSounds;
import com.qlm.zombie.music.BossMusicManager;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(QLMZombieMod.MODID)
public class QLMZombieMod {
    public static final String MODID = "qlmzombie";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean needsRestart = false;

    @SuppressWarnings("removal")
    public QLMZombieMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        QLMItems.ITEMS.register(modEventBus);
        QLMSounds.register(modEventBus);
        
        MinecraftForge.EVENT_BUS.register(DayPhaseManager.class);
        MinecraftForge.EVENT_BUS.register(ZombieEvolutionHandler.class);
        MinecraftForge.EVENT_BUS.register(PlayerInitHandler.class);
        MinecraftForge.EVENT_BUS.register(PlayerHealthHandler.class);
        MinecraftForge.EVENT_BUS.register(HordeManager.class);
        MinecraftForge.EVENT_BUS.register(BossMusicManager.class);
        MinecraftForge.EVENT_BUS.register(MobRestrictionHandler.class);
        MinecraftForge.EVENT_BUS.register(AdvancementManager.class);
        MinecraftForge.EVENT_BUS.register(QLMCommands.class);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QLMConfig.SPEC, "qlmzombie-common.toml");

        // 从内部 libs 自动释放所有 mod，并检测冲突
        ModDependencyHandler.initializeFromLibs();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[QLM Zombie] 七零喵僵尸末日生存mod 已加载");
            LOGGER.info("[QLM Zombie] 功能: 难度渐进 + 血月/幸运之月/丰收之月 + 僵尸进化 + 尸潮系统 + 怪物封禁 + 初始物资发放 + 成就系统");
            if (needsRestart) {
                LOGGER.warn("[QLM Zombie] ================================================");
                LOGGER.warn("[QLM Zombie] 依赖mod已释放到 mods 目录，请重启游戏！");
                LOGGER.warn("[QLM Zombie] ================================================");
            }
        });
    }

    private void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() == null) return;

        int total = ModDependencyHandler.getTotalLibsCount();
        int released = ModDependencyHandler.getReleasedCount();
        boolean hasConflicts = ModDependencyHandler.hasConflicts();
        int disabled = ModDependencyHandler.getDisabledMods().size();

        if (needsRestart) {
            String msg = "§e[七零喵团队 SevenZeroMeowTeam] §c需要重启游戏以加载新安装的mod！";
            if (total > 0) {
                msg += " §7(内部mod " + total + "个，已释放 " + released + "个";
                if (hasConflicts) {
                    msg += "，检测到冲突mod " + disabled + "个已禁用";
                }
                msg += ")";
            }
            event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        }
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                LOGGER.info("[QLM Zombie] 成就系统已加载");
            }
        });
    }
}