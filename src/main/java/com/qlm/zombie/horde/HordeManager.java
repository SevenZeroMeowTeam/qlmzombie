package com.qlm.zombie.horde;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.advancements.AdvancementManager;
import com.qlm.zombie.item.QLMItems;
import com.qlm.zombie.moon.MoonHelper;
import com.qlm.zombie.music.BossMusicManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Skeleton;
import com.qlm.zombie.entity.GiantZombieEntity;
import com.qlm.zombie.entity.QLMEntities;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = "qlmzombie")
public class HordeManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, Integer> playerHordeWave = new HashMap<>();
    private static final Map<UUID, Long> playerLastWaveTime = new HashMap<>();
    private static final Map<UUID, Integer> playerRemainingMonsters = new HashMap<>();
    private static final Map<UUID, Long> playerGlowTime = new HashMap<>();
    private static final Map<UUID, Boolean> playerHordeCompleted = new HashMap<>();
    private static final long WAVE_INTERVAL = 6000;
    private static final long GLOW_INTERVAL = 3000;
    private static final int SPAWN_RADIUS = 30;
    private static final int MAX_SPAWN_ATTEMPTS = 10;

    private static net.minecraft.world.item.Item getItemFromRegistry(String namespace, String path) {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getHolder(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path))
            .map(net.minecraft.core.Holder::value)
            .orElse(null);
    }

    private static net.minecraft.world.item.Item getItemFromRegistry(String itemId) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null) return null;
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getHolder(rl)
            .map(net.minecraft.core.Holder::value)
            .orElse(null);
    }

    private static void applyEnchantment(ItemStack stack, Enchantment enchantment, int level) {
        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(stack));
        enchantments.put(enchantment, level);
        EnchantmentHelper.setEnchantments(enchantments, stack);
    }

    private static net.minecraft.server.MinecraftServer currentServer = null;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (event.getServer() != null) {
            currentServer = event.getServer();
        }

        // 必须获取主世界 ServerLevel 才能正确判断血月状态，传 null 会被 MoonHelper 视为无效而永远返回 false
        ServerLevel overworld = currentServer == null ? null : currentServer.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null || !MoonHelper.isBloodMoon(overworld)) {
            clearAllHordes();
            return;
        }

        checkHordeCompletion();
        updateGlowEffect();
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie) && !(event.getEntity() instanceof Skeleton)) {
            return;
        }

        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }

        // 检查是否是 Boss 死亡
        if (event.getEntity() instanceof Zombie && event.getEntity().getPersistentData().getBoolean("qlm_horde_boss")) {
            BossMusicManager.onBossKilled((Zombie) event.getEntity());
            LOGGER.info("[QLM Zombie] 尸潮领主已被击败");
        }

        decrementMonsterCount();
    }

    public static void startHordeForPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        playerHordeWave.put(playerId, 0);
        playerLastWaveTime.put(playerId, 0L);
        playerRemainingMonsters.put(playerId, 0);
        playerHordeCompleted.put(playerId, false);
        LOGGER.info("[QLM Zombie] Starting horde for player {}", player.getName().getString());
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ 尸潮来袭！准备迎战！"), false);
    }

    public static void updateHorde(ServerPlayer player) {
        if (!MoonHelper.isBloodMoon(player.serverLevel())) return;

        UUID playerId = player.getUUID();
        ServerLevel level = player.serverLevel();
        long currentTime = level.getGameTime();
        int currentWave = playerHordeWave.getOrDefault(playerId, 0);
        long lastWaveTime = playerLastWaveTime.getOrDefault(playerId, 0L);

        if (playerHordeCompleted.getOrDefault(playerId, false)) {
            return;
        }

        if (currentWave >= HordeWave.values().length) {
            if (playerRemainingMonsters.getOrDefault(playerId, 0) <= 0) {
                completeHorde(player);
            }
            return;
        }

        if (currentTime - lastWaveTime >= WAVE_INTERVAL) {
            if (playerRemainingMonsters.getOrDefault(playerId, 0) <= 0) {
                HordeWave wave = HordeWave.values()[currentWave];
                int monsterCount = spawnWave(level, player.blockPosition(), wave);
                playerRemainingMonsters.put(playerId, monsterCount);
                playerHordeWave.put(playerId, currentWave + 1);
                playerLastWaveTime.put(playerId, currentTime);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c第 " + (currentWave + 1) + " 波来袭！"), false);
            }
        }
    }

    private static int spawnWave(ServerLevel level, BlockPos center, HordeWave wave) {
        Random random = new Random();
        int spawnedCount = 0;

        for (int i = 0; i < wave.getZombieCount(); i++) {
            BlockPos spawnPos = getRandomSpawnPos(level, center, random);
            if (spawnPos != null) {
                Zombie zombie = EntityType.ZOMBIE.create(level);
                if (zombie != null) {
                    zombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                    
                    if (random.nextDouble() < wave.getEliteChance()) {
                        makeEliteZombie(zombie);
                    }
                    
                    zombie.getPersistentData().putBoolean("qlm_horde_monster", true);
                    level.addFreshEntity(zombie);
                    spawnedCount++;
                }
            }
        }

        for (int i = 0; i < wave.getSkeletonCount(); i++) {
            BlockPos spawnPos = getRandomSpawnPos(level, center, random);
            if (spawnPos != null) {
                Skeleton skeleton = EntityType.SKELETON.create(level);
                if (skeleton != null) {
                    skeleton.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                    
                    if (random.nextDouble() < wave.getEliteChance() * 0.5) {
                        skeleton.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1));
                        skeleton.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1));
                    }
                    
                    skeleton.getPersistentData().putBoolean("qlm_horde_monster", true);
                    level.addFreshEntity(skeleton);
                    spawnedCount++;
                }
            }
        }

        if (wave.getBossCount() > 0) {
            for (int i = 0; i < wave.getBossCount(); i++) {
                BlockPos spawnPos = getRandomSpawnPos(level, center, random);
                if (spawnPos != null) {
                    Zombie boss = EntityType.ZOMBIE.create(level);
                    if (boss != null) {
                        boss.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                        makeBossZombie(boss);
                        boss.getPersistentData().putBoolean("qlm_horde_monster", true);
                        boss.getPersistentData().putBoolean("qlm_horde_boss", true);
                        level.addFreshEntity(boss);
                        spawnedCount++;
                        // Boss 生成后触发第一阶段音乐
                        BossMusicManager.onBossSpawned(boss, level);
                    }
                }
            }
        }

        if (wave.getGiantCount() > 0) {
            for (int i = 0; i < wave.getGiantCount(); i++) {
                BlockPos spawnPos = getRandomSpawnPos(level, center, random);
                if (spawnPos != null) {
                    GiantZombieEntity giant = QLMEntities.GIANT_ZOMBIE.get().create(level);
                    if (giant != null) {
                        giant.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                        giant.getPersistentData().putBoolean("qlm_horde_monster", true);
                        giant.getPersistentData().putBoolean("qlm_horde_boss", true);
                        level.addFreshEntity(giant);
                        spawnedCount++;
                        // Boss 生成后触发第一阶段音乐
                        BossMusicManager.onBossSpawned(giant, level);
                    }
                }
            }
        }

        return spawnedCount;
    }

    private static void checkHordeCompletion() {
        if (currentServer == null) return;

        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (playerHordeWave.containsKey(playerId)) {
                int wave = playerHordeWave.get(playerId);
                if (wave >= HordeWave.values().length && playerRemainingMonsters.getOrDefault(playerId, 0) <= 0) {
                    if (!playerHordeCompleted.getOrDefault(playerId, false)) {
                        completeHorde(player);
                    }
                }
            }
        }
    }

    private static void updateGlowEffect() {
        if (currentServer == null) return;

        long currentTime = System.currentTimeMillis();

        for (UUID playerId : playerHordeWave.keySet()) {
            Long lastGlow = playerGlowTime.get(playerId);
            if (lastGlow == null || currentTime - lastGlow >= GLOW_INTERVAL) {
                if (playerRemainingMonsters.getOrDefault(playerId, 0) > 0) {
                    makeHordeMonstersGlow();
                    playerGlowTime.put(playerId, currentTime);
                }
            }
        }

        expireGlowTags();
    }

    private static void expireGlowTags() {
        if (currentServer == null) return;
        for (ServerLevel level : currentServer.getAllLevels()) {
            long gameTime = level.getGameTime();
            // 遍历所有已加载实体并按类型筛选，避免使用 Double.MAX_VALUE 的 AABB 导致的数值溢出问题
            for (net.minecraft.world.entity.Entity entity : level.getEntities().getAll()) {
                long glowUntil = entity.getPersistentData().getLong("qlm_glow_until");
                if (glowUntil <= 0 || gameTime < glowUntil) continue;
                if (entity instanceof Zombie || entity instanceof Skeleton) {
                    entity.setGlowingTag(false);
                    entity.getPersistentData().remove("qlm_glow_until");
                }
            }
        }
    }

    private static void makeHordeMonstersGlow() {
        if (currentServer == null) return;

        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (playerHordeWave.containsKey(player.getUUID())) {
                ServerLevel level = player.serverLevel();
                // 以玩家位置为中心搜索，因为尸潮怪物生成在玩家附近而非世界出生点附近
                Vec3 playerPos = player.position();
                net.minecraft.world.phys.AABB searchBox = net.minecraft.world.phys.AABB.ofSize(playerPos, 200, 200, 200);

                level.getEntitiesOfClass(Zombie.class, searchBox).forEach(zombie -> {
                    if (zombie.getPersistentData().getBoolean("qlm_horde_monster")) {
                        zombie.setGlowingTag(true);
                        zombie.getPersistentData().putLong("qlm_glow_until", level.getGameTime() + 40L);
                    }
                });

                level.getEntitiesOfClass(Skeleton.class, searchBox).forEach(skeleton -> {
                    if (skeleton.getPersistentData().getBoolean("qlm_horde_monster")) {
                        skeleton.setGlowingTag(true);
                        skeleton.getPersistentData().putLong("qlm_glow_until", level.getGameTime() + 40L);
                    }
                });
            }
        }
    }

    private static void decrementMonsterCount() {
        for (UUID playerId : playerHordeWave.keySet()) {
            playerRemainingMonsters.merge(playerId, -1, Integer::sum);
        }
    }

    private static void completeHorde(ServerPlayer player) {
        UUID playerId = player.getUUID();
        playerHordeCompleted.put(playerId, true);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a🎉 尸潮已全部击退！获得奖励！"), false);
        LOGGER.info("[QLM Zombie] Player {} completed all horde waves", player.getName().getString());
        
        AdvancementManager.awardAdvancement(player, "horde_waves", "complete_all_waves");
        AdvancementManager.awardAdvancement(player, "survive_horde", "survive_horde");
        
        giveHordeRewards(player);
    }

    private static void giveHordeRewards(ServerPlayer player) {
        player.addItem(new ItemStack(Items.DIAMOND, 5));
        player.addItem(new ItemStack(Items.EMERALD, 10));
        player.addItem(new ItemStack(Items.GOLD_INGOT, 32));
        player.addItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 16));
        player.addItem(new ItemStack(Items.NETHERITE_INGOT, 2));
        player.addItem(new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        
        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        applyEnchantment(enchantedSword, Enchantments.SHARPNESS, 5);
        applyEnchantment(enchantedSword, Enchantments.UNBREAKING, 3);
        player.addItem(enchantedSword);

        ItemStack enchantedBow = new ItemStack(Items.BOW);
        applyEnchantment(enchantedBow, Enchantments.POWER_ARROWS, 5);
        applyEnchantment(enchantedBow, Enchantments.INFINITY_ARROWS, 1);
        player.addItem(enchantedBow);

        player.addItem(new ItemStack(Items.ARROW, 128));
        
        player.addItem(new ItemStack(Items.BREAD, 64));
        
        giveTaCZAKM(player);
        
        giveTouhouMaidenItem(player, "touhou_little_maid:stand_jizo");
        
        giveRandomModItems(player);
        
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 2));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 3));

        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e奖励已发放：钻石x5、绿宝石x10、金锭x32、附魔金苹果x16、下界合金锭x2、经验瓶x64、钻石剑(锋利V)、弓(力量V)、面包x64、满配AKM、替身地藏"), false);
    }

    private static void giveTaCZAKM(ServerPlayer player) {
        try {
            net.minecraft.world.item.Item akmItem = getItemFromRegistry("tacz", "akm");
            if (akmItem != null) {
                ItemStack akmStack = new ItemStack(akmItem);
                applyEnchantment(akmStack, Enchantments.SHARPNESS, 5);
                applyEnchantment(akmStack, Enchantments.UNBREAKING, 3);
                applyEnchantment(akmStack, Enchantments.MOB_LOOTING, 3);
                applyEnchantment(akmStack, Enchantments.FIRE_ASPECT, 2);
                applyEnchantment(akmStack, Enchantments.PIERCING, 5);
                player.addItem(akmStack);
                
                net.minecraft.world.item.Item ammoBoxItem = getItemFromRegistry("tacz", "creative_ammo_box");
                if (ammoBoxItem != null) {
                    player.addItem(new ItemStack(ammoBoxItem, 1));
                }
                
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6🔫 获得 TaCZ 满配AKM + 创造弹药箱"), false);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ 未找到 TaCZ AKM，请确保 TaCZ 已安装"), false);
            }
        } catch (Exception e) {
            LOGGER.warn("[QLM Zombie] Failed to give TaCZ AKM: {}", e.getMessage());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ TaCZ AKM 发放失败"), false);
        }
    }

    private static void giveTouhouMaidenItem(ServerPlayer player, String itemId) {
        try {
            net.minecraft.world.item.Item item = getItemFromRegistry(itemId);
            if (item != null) {
                player.addItem(new ItemStack(item, 1));
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§d🎎 获得 车万女仆 - 替身地藏"), false);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ 未找到 替身地藏，请确保 车万女仆 已安装"), false);
            }
        } catch (Exception e) {
            LOGGER.warn("[QLM Zombie] Failed to give Touhou Maiden item {}: {}", itemId, e.getMessage());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ 替身地藏 发放失败"), false);
        }
    }

    private static void giveRandomModItems(ServerPlayer player) {
        Random random = new Random();
        java.util.List<net.minecraft.world.item.Item> modItems = java.util.Arrays.asList(
            QLMItems.ZOMBIE_CORE.get(),
            QLMItems.INFECTED_ESSENCE.get(),
            QLMItems.SURVIVAL_KIT.get(),
            QLMItems.EMERGENCY_RATION.get(),
            QLMItems.MEDICAL_SUPPLY.get(),
            QLMItems.REINFORCED_PARTS.get(),
            QLMItems.BIOHAZARD_SAMPLE.get(),
            QLMItems.TACTICAL_AMMO.get()
        );
        
        int numItemsToGive = 2 + random.nextInt(3);
        
        StringBuilder modItemMessage = new StringBuilder("§6额外奖励: ");
        
        for (int i = 0; i < numItemsToGive; i++) {
            int randomIndex = random.nextInt(modItems.size());
            net.minecraft.world.item.Item item = modItems.get(randomIndex);
            int amount = 1 + random.nextInt(3);
            
            player.addItem(new ItemStack(item, amount));
            
            if (i > 0) {
                modItemMessage.append(", ");
            }
            modItemMessage.append(item.getDescription().getString()).append("x").append(amount);
        }
        
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(modItemMessage.toString()), false);
        LOGGER.info("[QLM Zombie] Player {} received random mod items: {}", player.getName().getString(), modItemMessage.toString());
    }

    private static void clearAllHordes() {
        playerHordeWave.clear();
        playerLastWaveTime.clear();
        playerRemainingMonsters.clear();
        playerGlowTime.clear();
        playerHordeCompleted.clear();
    }

    private static BlockPos getRandomSpawnPos(ServerLevel level, BlockPos center, Random random) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = SPAWN_RADIUS + random.nextDouble() * 10;
            int x = (int) (center.getX() + Math.cos(angle) * radius);
            int z = (int) (center.getZ() + Math.sin(angle) * radius);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);

            BlockPos pos = new BlockPos(x, y, z);
            while (y > level.getMinBuildHeight() && !level.getBlockState(pos).isAir()) {
                y--;
                pos = new BlockPos(x, y, z);
            }

            net.minecraft.world.level.block.state.BlockState belowState = level.getBlockState(pos.below());
            if (y > level.getMinBuildHeight() && !belowState.getCollisionShape(level, pos.below()).isEmpty() && level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static void makeEliteZombie(Zombie zombie) {
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1));
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1));
        zombie.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Integer.MAX_VALUE, 0));
        zombie.setCustomName(net.minecraft.network.chat.Component.literal("§c精英僵尸"));
        zombie.setCustomNameVisible(true);
        net.minecraft.world.entity.ai.attributes.AttributeInstance maxHp = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(60.0D);
        zombie.setHealth(60.0F);
    }

    private static void makeBossZombie(Zombie zombie) {
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 2));
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 3));
        zombie.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Integer.MAX_VALUE, 2));
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0));
        zombie.setCustomName(net.minecraft.network.chat.Component.literal("§4尸潮领主"));
        zombie.setCustomNameVisible(true);
        net.minecraft.world.entity.ai.attributes.AttributeInstance maxHp = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(200.0D);
        zombie.setHealth(200.0F);
        net.minecraft.world.entity.ai.attributes.AttributeInstance atk = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(15.0D);
    }

    public static boolean isInHorde(ServerPlayer player) {
        return playerHordeWave.containsKey(player.getUUID());
    }

    public static int getCurrentWave(ServerPlayer player) {
        return playerHordeWave.getOrDefault(player.getUUID(), 0);
    }
}