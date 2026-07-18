package com.qlm.zombie;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.QLMEntities;
import com.qlm.zombie.item.QLMItems;
import com.qlm.zombie.loot.QLMGlobalLootModifiers;
import com.qlm.zombie.music.QLMSounds;
import com.qlm.zombie.zombie.ZombieEvolutionHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// 注: "qlmzombie" 为资源目录 (data/qlmzombie / assets/qlmzombie) 与 mods.toml 的 modId
// 常量名使用 MOD_ID 而非 MODID，避免 IDE 拼写检查误报
@Mod(QLMZombieMod.MOD_ID)
public class QLMZombieMod {

    public static final String MOD_ID = "qlmzombie";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean needsRestart = false;

    @SuppressWarnings("removal")
    public QLMZombieMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        QLMItems.ITEMS.register(modEventBus);
        QLMSounds.register(modEventBus);
        QLMGlobalLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        QLMEntities.ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::registerEntityAttributes);

        // 以下两个类已通过 @Mod.EventBusSubscriber 自动注册到 Forge 事件总线
        // 此处显式注册作为双保险，确保事件监听器正常工作
        MinecraftForge.EVENT_BUS.register(DayPhaseManager.class);
        MinecraftForge.EVENT_BUS.register(ZombieEvolutionHandler.class);
        MinecraftForge.EVENT_BUS.register(com.qlm.zombie.structure.AbandonedShopGenerator.class);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QLMConfig.SPEC, "qlmzombie-common.toml");

        // 从内部 libs 自动释放所有 mod，并检测冲突
        ModDependencyHandler.initializeFromLibs();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[QLM Zombie] 七零喵僵尸末日生存mod 已加载");
            LOGGER.info("[QLM Zombie] 功能: 难度渐进 + 血月/幸运之月/丰收之月 + 僵尸进化 + 尸潮系统 + 怪物封禁 + 初始物资发放 + 成就系统 + 连锁挖矿/连锁砍树(支持mod工具/树木/矿石) + 建筑物宝箱随机武器装备 + 废弃商店生成");
            LOGGER.info("[QLM Zombie] 建筑物宝箱已注入战利品（TaCZ 火器 / Spartan 装备 / 原版工具/防具 / QLM 自制物资），包括各种枪械、弹药、配件、近战武器与防具。");
            LOGGER.info("[QLM Zombie] 废弃商店已启用：主世界随机生成废弃商店结构，奖励箱放置在货架上，有概率生成mod物品。");
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
            event.getEntity().sendSystemMessage(Component.literal(msg));
        }
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) (ResourceManager resourceManager) -> {
            LOGGER.info("[QLM Zombie] 成就系统已加载");
        });
    }

    private void registerEntityAttributes(final EntityAttributeCreationEvent event) {
        event.put(QLMEntities.FAKE_PLAYER.get(), FakePlayerEntity.createAttributes().build());
    }
}