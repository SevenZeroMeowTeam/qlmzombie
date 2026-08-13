package com.qlm.zombie.boss;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.zombie.ZombieHordeHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Boss死亡掉落宝箱
 * - 宝箱包含原版稀有物品 + 其他模组随机物品
 * - 大Boss掉落更高级的宝箱
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class BossDropHandler {

    private static final Random RANDOM = new Random();

    // 小Boss宝箱 - 基础物品池
    private static final List<ItemStack> MINI_BOSS_LOOT = new ArrayList<>(List.of(
        new ItemStack(Items.DIAMOND, 3 + RANDOM.nextInt(5)),
        new ItemStack(Items.EMERALD, 5 + RANDOM.nextInt(10)),
        new ItemStack(Items.GOLDEN_APPLE, 2 + RANDOM.nextInt(3)),
        new ItemStack(Items.ENDER_PEARL, 1 + RANDOM.nextInt(3)),
        new ItemStack(Items.OBSIDIAN, 5 + RANDOM.nextInt(10)),
        new ItemStack(Items.EXPERIENCE_BOTTLE, 10 + RANDOM.nextInt(20)),
        new ItemStack(Items.IRON_INGOT, 5 + RANDOM.nextInt(10))
    ));

    // 大Boss宝箱 - 高级物品池
    private static final List<ItemStack> BIG_BOSS_LOOT = new ArrayList<>(List.of(
        new ItemStack(Items.DIAMOND, 10 + RANDOM.nextInt(20)),
        new ItemStack(Items.EMERALD, 15 + RANDOM.nextInt(30)),
        new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2 + RANDOM.nextInt(4)),
        new ItemStack(Items.NETHERITE_INGOT, 2 + RANDOM.nextInt(4)),
        new ItemStack(Items.NETHER_STAR, 1),
        new ItemStack(Items.ENDER_PEARL, 5 + RANDOM.nextInt(10)),
        new ItemStack(Items.EXPERIENCE_BOTTLE, 20 + RANDOM.nextInt(40)),
        new ItemStack(Items.DIAMOND_BLOCK, 2 + RANDOM.nextInt(5)),
        new ItemStack(Items.EMERALD_BLOCK, 3 + RANDOM.nextInt(6))
    ));

    // 缓存其他模组物品（自动注册）
    private static List<ItemStack> modItems = null;

    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) return;
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        CompoundTag tag = zombie.getPersistentData();
        if (!tag.getBoolean(ZombieHordeHandler.NBT_IS_BOSS)) return;

        String bossType = tag.getString(ZombieHordeHandler.NBT_BOSS_TYPE);
        BlockPos pos = zombie.blockPosition();

        // 初始化其他模组物品列表
        if (modItems == null) {
            modItems = scanModItems();
        }

        // 放置宝箱
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            if ("big".equals(bossType)) {
                fillBigBossChest(chest, level);
            } else {
                fillMiniBossChest(chest, level);
            }
        }

        QLMZombieMod.LOGGER.info("[Boss掉落] {} 死亡，宝箱已生成于 {}", bossType, pos);
    }

    private static void fillMiniBossChest(ChestBlockEntity chest, ServerLevel level) {
        // 3-5个基础物品
        int count = 3 + RANDOM.nextInt(3);
        for (int i = 0; i < count; i++) {
            ItemStack item = MINI_BOSS_LOOT.get(RANDOM.nextInt(MINI_BOSS_LOOT.size())).copy();
            item.setCount(1 + RANDOM.nextInt(item.getMaxStackSize()));
            chest.setItem(RANDOM.nextInt(27), item);
        }

        // 1-2个其他模组物品
        if (modItems != null && !modItems.isEmpty()) {
            int modCount = 1 + RANDOM.nextInt(2);
            for (int i = 0; i < modCount; i++) {
                ItemStack modItem = modItems.get(RANDOM.nextInt(modItems.size())).copy();
                modItem.setCount(1);
                chest.setItem(RANDOM.nextInt(27), modItem);
            }
        }
    }

    private static void fillBigBossChest(ChestBlockEntity chest, ServerLevel level) {
        // 3-6个高级物品
        int count = 3 + RANDOM.nextInt(4);
        for (int i = 0; i < count; i++) {
            ItemStack item = BIG_BOSS_LOOT.get(RANDOM.nextInt(BIG_BOSS_LOOT.size())).copy();
            item.setCount(1 + RANDOM.nextInt(item.getMaxStackSize()));
            // 附魔装备
            if (item.is(Items.DIAMOND) || item.is(Items.NETHERITE_INGOT)) {
                // 保持原样
            }
            chest.setItem(RANDOM.nextInt(27), item);
        }

        // 2-3个其他模组物品
        if (modItems != null && !modItems.isEmpty()) {
            int modCount = 2 + RANDOM.nextInt(2);
            for (int i = 0; i < modCount; i++) {
                ItemStack modItem = modItems.get(RANDOM.nextInt(modItems.size())).copy();
                modItem.setCount(1 + RANDOM.nextInt(3));
                chest.setItem(RANDOM.nextInt(27), modItem);
            }
        }

        // 特殊奖励：附魔钻石剑
        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        enchantedSword.enchant(Enchantments.SHARPNESS, 4);
        enchantedSword.enchant(Enchantments.FIRE_ASPECT, 2);
        chest.setItem(RANDOM.nextInt(27), enchantedSword);

        // 附魔钻石甲
        ItemStack enchantedChest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        enchantedChest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
        chest.setItem(RANDOM.nextInt(27), enchantedChest);
    }

    /** 扫描所有已注册的模组物品（排除原版和本模组物品） */
    private static List<ItemStack> scanModItems() {
        List<ItemStack> items = new ArrayList<>();
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            String namespace = entry.getKey().location().getNamespace();
            ItemStack stack = new ItemStack(entry.getValue());
            // 排除原版（minecraft）和本模组（qlmzombie）
            if (!"minecraft".equals(namespace) && !"qlmzombie".equals(namespace)) {
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }
        QLMZombieMod.LOGGER.info("[Boss掉落] 扫描到 {} 个其他模组物品可用于宝箱", items.size());
        return items;
    }
}