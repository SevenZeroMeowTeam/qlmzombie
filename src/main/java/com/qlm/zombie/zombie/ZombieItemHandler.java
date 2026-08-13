package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 僵尸双手持物品系统：
 * - 25%概率僵尸手持物品
 * - 主手+副手均可持有
 * - 70%原版物品，30%其他模组物品
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ZombieItemHandler {

    private static final double ZOMBIE_HOLD_ITEM_CHANCE = 0.25;
    private static final double DUAL_WIELD_CHANCE = 0.4; // 40%双手持

    // 原版物品池
    private static final List<ItemStack> VANILLA_MAINHAND = List.of(
        new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE),
        new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL),
        new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE),
        new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.WOODEN_AXE),
        new ItemStack(Items.BREAD), new ItemStack(Items.ROTTEN_FLESH),
        new ItemStack(Items.BONE), new ItemStack(Items.STRING), new ItemStack(Items.STICK)
    );

    // 副手物品池
    private static final List<ItemStack> VANILLA_OFFHAND = List.of(
        new ItemStack(Items.TORCH), new ItemStack(Items.SHIELD),
        new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ROTTEN_FLESH),
        new ItemStack(Items.BONE), new ItemStack(Items.ARROW),
        new ItemStack(Items.FLINT), new ItemStack(Items.FEATHER)
    );

    private static List<ItemStack> modItems = null;

    @SubscribeEvent
    public static void onZombieSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (zombie.getPersistentData().getBoolean("qlm_is_boss")) return;

        if (modItems == null) modItems = scanModItems();

        if (zombie.getRandom().nextDouble() >= ZOMBIE_HOLD_ITEM_CHANCE) return;

        // 主手
        ItemStack mainhand = getRandomItem(zombie);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, mainhand);
        zombie.setDropChance(EquipmentSlot.MAINHAND, 0.8f);

        // 40%概率副手
        if (zombie.getRandom().nextDouble() < DUAL_WIELD_CHANCE) {
            ItemStack offhand;
            if (zombie.getRandom().nextDouble() < 0.7) {
                offhand = VANILLA_OFFHAND.get(zombie.getRandom().nextInt(VANILLA_OFFHAND.size())).copy();
            } else {
                offhand = modItems != null && !modItems.isEmpty()
                    ? modItems.get(zombie.getRandom().nextInt(modItems.size())).copy()
                    : new ItemStack(Items.TORCH);
            }
            zombie.setItemSlot(EquipmentSlot.OFFHAND, offhand);
            zombie.setDropChance(EquipmentSlot.OFFHAND, 0.8f);
        }
    }

    private static ItemStack getRandomItem(Zombie zombie) {
        if (zombie.getRandom().nextDouble() < 0.7) {
            return VANILLA_MAINHAND.get(zombie.getRandom().nextInt(VANILLA_MAINHAND.size())).copy();
        }
        if (modItems != null && !modItems.isEmpty()) {
            return modItems.get(zombie.getRandom().nextInt(modItems.size())).copy();
        }
        return new ItemStack(Items.ROTTEN_FLESH);
    }

    private static List<ItemStack> scanModItems() {
        List<ItemStack> items = new ArrayList<>();
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            String namespace = entry.getKey().location().getNamespace();
            ItemStack stack = new ItemStack(entry.getValue());
            if (!"minecraft".equals(namespace) && !"qlmzombie".equals(namespace) &&
                !stack.isEmpty() && !stack.getItem().getDescriptionId().contains("spawn_egg")) {
                items.add(stack);
            }
        }
        if (items.size() > 50) items = items.subList(0, 50);
        return items;
    }
}