package com.qlm.zombie.item;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * 玩家加载（每次登录）时发放登录武器：
 *  - 1 把 [卓越前线] Superb Warfare 的莫辛纳甘（superbwarfare:mosin_nagant）
 *  - 1 盒创造弹药盒（superbwarfare:creative_ammo_box，无限弹药）
 *
 * 健壮性：任一物品缺失/未安装卓越前线模组时仅提示，不影响其他功能。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class LoginWeaponGiftHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** [卓越前线] superbwarfare 的莫辛纳甘 */
    private static final String MOSIN_NAGANT = "superbwarfare:mosin_nagant";
    /** [卓越前线] superbwarfare 的创造弹药盒（无限弹药） */
    private static final String CREATIVE_AMMO_BOX = "superbwarfare:creative_ammo_box";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        boolean gaveMosin = giveItem(player, MOSIN_NAGANT, 1);
        boolean gaveAmmoBox = giveItem(player, CREATIVE_AMMO_BOX, 1);

        if (gaveMosin && gaveAmmoBox) {
            player.sendSystemMessage(Component.literal(
                    "§6[七零喵] §a已发放登录武器：§f莫辛纳甘 §7×1 §a+ §f创造弹药盒 §7×1 §8（卓越前线）"));
        } else if (gaveMosin) {
            player.sendSystemMessage(Component.literal(
                    "§6[七零喵] §a已发放莫辛纳甘，但未找到创造弹药盒"));
        } else {
            player.sendSystemMessage(Component.literal(
                    "§c[七零喵] 未找到卓越前线模组物品，请确认已安装 [卓越前线] superbwarfare"));
        }
    }

    /** 从注册表取物品并放入玩家背包（背包满则掉落），返回是否成功 */
    private static boolean giveItem(ServerPlayer player, String itemId, int count) {
        try {
            Item item = getItemFromRegistry(itemId);
            if (item == null || item == Items.AIR) return false;
            ItemStack stack = new ItemStack(item, count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[QLM Zombie] 发放登录武器 {} 失败: {}", itemId, e.getMessage());
            return false;
        }
    }

    private static Item getItemFromRegistry(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return null;
        return ForgeRegistries.ITEMS.getHolder(rl)
                .map(net.minecraft.core.Holder::value)
                .orElse(null);
    }
}
