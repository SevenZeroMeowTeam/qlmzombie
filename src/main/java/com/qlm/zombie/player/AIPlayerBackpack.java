package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * AI玩家背包系统：
 * - 每个玩家拥有一个独立的AI背包（3行27格）
 * - 通过 /qlm backpack 打开
 * - 背包数据持久化保存在NBT中
 * - 可以像真实玩家背包一样交互
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AIPlayerBackpack {

    private static final String NBT_BACKPACK = "qlm_ai_backpack";
    private static final int BACKPACK_SIZE = 27; // 3行

    private static final Map<UUID, List<ItemStack>> backpackCache = new HashMap<>();

    /** 打开AI背包 */
    public static void openBackpack(ServerPlayer player) {
        List<ItemStack> items = getBackpackItems(player);
        player.openMenu(new SimpleMenuProvider((windowId, inv, p) -> {
            return new ChestMenu(MenuType.GENERIC_9x3, windowId, inv, new net.minecraft.world.SimpleContainer(items.toArray(new ItemStack[0])), 3) {
                @Override
                public void removed(Player p) {
                    // 保存背包数据
                    List<ItemStack> saved = new ArrayList<>();
                    for (int i = 0; i < BACKPACK_SIZE; i++) {
                        saved.add(getSlot(i).getItem());
                    }
                    saveBackpackItems((ServerPlayer) p, saved);
                    super.removed(p);
                }
            };
        }, Component.literal("§6§lAI玩家背包")));
    }

    /** 获取AI背包物品 */
    public static List<ItemStack> getBackpackItems(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (backpackCache.containsKey(uuid)) {
            return new ArrayList<>(backpackCache.get(uuid));
        }

        // 从NBT加载
        List<ItemStack> items = new ArrayList<>();
        CompoundTag persistent = player.getPersistentData();
        CompoundTag tag = persistent.getCompound(NBT_BACKPACK);
        for (int i = 0; i < BACKPACK_SIZE; i++) {
            if (tag.contains("slot_" + i)) {
                items.add(ItemStack.of(tag.getCompound("slot_" + i)));
            } else {
                items.add(ItemStack.EMPTY);
            }
        }
        backpackCache.put(uuid, items);
        return new ArrayList<>(items);
    }

    /** 保存AI背包物品 */
    public static void saveBackpackItems(ServerPlayer player, List<ItemStack> items) {
        UUID uuid = player.getUUID();
        backpackCache.put(uuid, new ArrayList<>(items));

        // 保存到NBT
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < BACKPACK_SIZE && i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                tag.put("slot_" + i, items.get(i).save(new CompoundTag()));
            }
        }
        player.getPersistentData().put(NBT_BACKPACK, tag);
    }

    /** 玩家登录时加载背包 */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 预热缓存
            getBackpackItems(player);
        }
    }
}