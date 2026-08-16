package com.qlm.zombie.zombie;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.item.EquipmentQuality;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 战利品越丰厚，僵尸越多：
 * - 夜晚每 10 秒评估每个在线玩家的"战利品财富值"（装备/物品价值 + 品质加成）
 * - 财富越高，刷出的额外僵尸/骷髅越多（最多 4 只/玩家，保持在场数量）
 * - 财富怪带标记 qlm_wealth_spawn，击杀时仍正常掉落
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class LootWealthHandler {

    private static final String NBT_WEALTH_SPAWN = "qlm_wealth_spawn";
    private static final int CHECK_INTERVAL = 200; // 10秒
    private static final double SPAWN_RADIUS = 48.0D;

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        long tod = overworld.getDayTime() % 24000L;
        if (tod < 13000L) return; // 仅夜晚

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            if (player.level().dimension() != Level.OVERWORLD) continue;

            int wealth = computeWealth(player);
            int targetCount = wealthToMobCount(wealth);
            if (targetCount <= 0) continue;

            spawnWealthMobs(player, targetCount);
        }
    }

    /** 财富值 -> 额外刷怪数量 */
    private static int wealthToMobCount(int wealth) {
        if (wealth >= 600) return 4;
        if (wealth >= 300) return 3;
        if (wealth >= 150) return 2;
        if (wealth >= 60) return 1;
        return 0;
    }

    /** 计算玩家战利品财富值 */
    private static int computeWealth(Player player) {
        int wealth = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            wealth += valueOf(player.getItemBySlot(slot));
        }
        for (ItemStack s : player.getInventory().items) {
            wealth += valueOf(s);
        }
        return wealth;
    }

    private static int valueOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Item item = stack.getItem();
        int base;
        if (item == Items.DIAMOND || item == Items.DIAMOND_BLOCK) base = 10;
        else if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_BLOCK
                || item == Items.NETHERITE_SWORD || item == Items.NETHERITE_PICKAXE
                || item == Items.NETHERITE_AXE || item == Items.NETHERITE_HELMET
                || item == Items.NETHERITE_CHESTPLATE || item == Items.NETHERITE_LEGGINGS
                || item == Items.NETHERITE_BOOTS) base = 20;
        else if (item == Items.IRON_INGOT || item == Items.IRON_BLOCK) base = 3;
        else if (item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK) base = 4;
        else if (item == Items.EMERALD || item == Items.EMERALD_BLOCK) base = 5;
        else if (item instanceof EnchantedBookItem) base = 5;
        else if (item instanceof TieredItem tiered && tiered.getTier() == Tiers.NETHERITE) base = 12;
        else if (item instanceof TieredItem tiered && tiered.getTier() == Tiers.DIAMOND) base = 8;
        else if (item instanceof TieredItem tiered && tiered.getTier() == Tiers.IRON) base = 3;
        else if (item instanceof TieredItem tiered && tiered.getTier() == Tiers.GOLD) base = 4;
        else base = 1;

        // 品质加成（劣质1 ~ 神话45）
        EquipmentQuality q = EquipmentQuality.fromStack(stack);
        if (q != null) base += q.getId() * 5;

        return base * stack.getCount();
    }

    /** 补充财富怪到场数量 */
    private static void spawnWealthMobs(ServerPlayer player, int targetCount) {
        ServerLevel level = player.serverLevel();
        RandomSource rnd = player.getRandom();

        // 统计玩家附近已有的财富怪
        int existing = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SPAWN_RADIUS),
            m -> m.getPersistentData().getBoolean(NBT_WEALTH_SPAWN)).size();
        int need = targetCount - existing;
        if (need <= 0) return;

        for (int i = 0; i < need; i++) {
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double dist = 24 + rnd.nextDouble() * 16;
            int x = player.getBlockX() + (int) (Math.cos(ang) * dist);
            int z = player.getBlockZ() + (int) (Math.sin(ang) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y <= level.getMinBuildHeight() + 1) continue;

            Mob mob = rnd.nextBoolean() ? EntityType.ZOMBIE.create(level) : EntityType.SKELETON.create(level);
            if (mob == null) continue;
            mob.moveTo(x + 0.5, y, z + 0.5, rnd.nextFloat() * 360.0F, 0.0F);
            mob.getPersistentData().putBoolean(NBT_WEALTH_SPAWN, true);

            // 财富越高怪越强（小幅度强化）
            try {
                AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(hp.getBaseValue() * (1.0 + Math.min(0.5, targetCount * 0.1)));
                AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * (1.0 + Math.min(0.5, targetCount * 0.1)));
                mob.setHealth(mob.getMaxHealth());
            } catch (Exception ignored) {
            }

            level.addFreshEntity(mob);
        }
        QLMZombieMod.LOGGER.debug("[财富刷怪] 玩家 {} 财富触发，补充 {} 只财富僵尸", player.getName().getString(), need);
    }
}
