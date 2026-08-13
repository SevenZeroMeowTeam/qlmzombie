package com.qlm.zombie.cloudai.ai;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.qlm.zombie.cloudai.core.CloudAiConstants;
import com.qlm.zombie.cloudai.core.WsClient;
import com.qlm.zombie.cloudai.util.*;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.QLMEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 实体管理器（单例）
 * 职责:
 *  - spawnAiForPlayer: 物品调用入口 → 生成 FakePlayer
 *  - removeAiForPlayer: 移除绑定 AI
 *  - mode 切换（FOLLOW/COMBAT/GATHER/GUARD）
 *  - 四模式 tick 处理（由 ServerTickEvent 驱动）
 *  - WS 绑定（上传状态 / 下发指令到 CmdCache）
 *  - 皮肤设置（FakePlayer.SkinURL + slim 体型）
 *  - 指令缓存执行（FORWARD/STOP/JUMP/ATTACK）
 */
public class AiEntityManager {

    private static volatile AiEntityManager INSTANCE;
    private static final Gson GSON = new Gson();

    /** owner UUID -> FakePlayerEntity 映射 */
    private final ConcurrentHashMap<UUID, FakePlayerEntity> ownerToAi = new ConcurrentHashMap<>();
    /** owner UUID -> 当前工作模式 */
    private final ConcurrentHashMap<UUID, String> ownerToMode = new ConcurrentHashMap<>();
    /** owner UUID -> 上次攻击 tick */
    private final ConcurrentHashMap<UUID, Integer> ownerToLastAttackTick = new ConcurrentHashMap<>();
    /** owner UUID -> 守卫位置 */
    private final ConcurrentHashMap<UUID, BlockPos> ownerToGuardPos = new ConcurrentHashMap<>();
    /** AI UUID -> owner UUID（反向查找） */
    private final ConcurrentHashMap<UUID, UUID> aiToOwner = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    private AiEntityManager() {}

    public static AiEntityManager getInstance() {
        if (INSTANCE == null) {
            synchronized (AiEntityManager.class) {
                if (INSTANCE == null) INSTANCE = new AiEntityManager();
            }
        }
        return INSTANCE;
    }

    // ============================================================
    // 物品调用入口（由 AllModItems 枚举中的行为直接调用）
    // ============================================================

    /**
     * 为玩家召唤一个 CloudAI 追随者
     * FakePlayer 配置: 离线 UUID + SLIM 体型 + setSecure(false) + 清空皮肤缓存
     */
    public boolean spawnAiForPlayer(Player owner) {
        if (owner == null) return false;
        UUID ownerId = owner.getUUID();
        if (ownerToAi.containsKey(ownerId)) {
            FakePlayerEntity exist = ownerToAi.get(ownerId);
            return exist == null || !exist.isAlive();
        }
        Level level = owner.level();
        if (!(level instanceof ServerLevel serverLevel)) return false;

        // 1. FakePlayer: 离线 UUID + SLIM 体型
        String aiName = NamePool.randomName();
        UUID aiUuid = UuidUtil.offlineUuid("CloudAI_" + ownerId + "_" + aiName);
        GameProfile profile = new GameProfile(aiUuid, aiName);

        // 2. 通过 QLMEntities.FAKE_PLAYER 生成 FakePlayerEntity
        FakePlayerEntity ai = QLMEntities.FAKE_PLAYER.get().create(serverLevel);
        if (ai == null) return false;

        ai.setUUID(aiUuid);
        // 绑定 GameProfile（FakePlayerEntity 内部会使用它进行皮肤渲染）
        trySetGameProfile(ai, profile);
        // SLIM 体型
        trySetSlim(ai, true);
        // 清空皮肤缓存（强制重新拉取皮肤）
        tryClearSkinCache(ai);

        // 位置: 玩家前方 2 格
        Vec3 spawnPos = owner.position().add(owner.getLookAngle().normalize().scale(2.0D));
        ai.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, owner.getYRot(), 0F);
        ai.setCustomName(Component.literal(aiName));
        ai.setCustomNameVisible(true);

        // 绑定主人
        trySetOwner(ai, owner);

        // 默认皮肤 URL（随机兜底）
        String skinPath = CloudAiConstants.DEFAULT_SKIN_PATH;
        String skinUrl = CloudAiConstants.FALLBACK_SKINS[
                new Random().nextInt(CloudAiConstants.FALLBACK_SKINS.length)];
        trySetSkinUrl(ai, skinUrl);

        // 添加到世界
        serverLevel.addFreshEntity(ai);

        // 建立映射
        ownerToAi.put(ownerId, ai);
        aiToOwner.put(aiUuid, ownerId);
        ownerToMode.put(ownerId, CloudAiConstants.Modes.FOLLOW);

        // 上传 WS：AI 上线
        uploadWsAiEvent(owner, "spawn", aiName, aiUuid.toString(), CloudAiConstants.Modes.FOLLOW);

        return true;
    }

    /** 移除绑定 AI */
    public boolean removeAiForPlayer(Player owner) {
        if (owner == null) return false;
        UUID ownerId = owner.getUUID();
        FakePlayerEntity ai = ownerToAi.remove(ownerId);
        if (ai != null) {
            UUID aiId = ai.getUUID();
            aiToOwner.remove(aiId);
            ownerToMode.remove(ownerId);
            ownerToLastAttackTick.remove(ownerId);
            ownerToGuardPos.remove(ownerId);
            CmdCache.clearFor(ownerId.toString());
            ai.discard();
            uploadWsAiEvent(owner, "remove", ai.getName().getString(), aiId.toString(), null);
            return true;
        }
        return false;
    }

    /** 模式切换（循环） */
    public String cycleModeFor(Player owner) {
        UUID ownerId = owner.getUUID();
        String cur = ownerToMode.getOrDefault(ownerId, CloudAiConstants.Modes.FOLLOW);
        String[] all = CloudAiConstants.Modes.ALL;
        int idx = 0;
        for (int i = 0; i < all.length; i++) { if (all[i].equals(cur)) { idx = i; break; } }
        String next = all[(idx + 1) % all.length];
        ownerToMode.put(ownerId, next);
        // GUARD 模式：记录当前位置为守卫点
        if (CloudAiConstants.Modes.GUARD.equals(next)) {
            FakePlayerEntity ai = ownerToAi.get(ownerId);
            if (ai != null) ownerToGuardPos.put(ownerId, ai.blockPosition());
        }
        uploadWsAiEvent(owner, "mode", null, null, next);
        return next;
    }

    /** 治疗附近 AI，返回治疗数量 */
    public int healNearbyAiOf(Player owner, float hpRatio) {
        if (owner == null) return 0;
        List<LivingEntity> ais = getNearbyAiOf(owner, CloudAiConstants.COMBAT_SEARCH_RADIUS);
        int healed = 0;
        for (LivingEntity le : ais) {
            float maxHp = le.getMaxHealth();
            float toHeal = maxHp * hpRatio;
            le.heal(toHeal);
            // 清除负面效果
            List<net.minecraft.world.effect.MobEffectInstance> effects = new ArrayList<>(le.getActiveEffects());
            for (net.minecraft.world.effect.MobEffectInstance eff : effects) {
                if (eff.getEffect().isBeneficial()) continue;
                le.removeEffect(eff.getEffect());
            }
            healed++;
        }
        return healed;
    }

    /** 获取玩家附近的所有绑定 AI */
    public List<LivingEntity> getNearbyAiOf(Player owner, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        if (owner == null) return result;
        FakePlayerEntity ai = ownerToAi.get(owner.getUUID());
        if (ai != null && ai.isAlive() && ai.distanceTo(owner) <= radius) {
            result.add(ai);
        }
        return result;
    }

    // ============================================================
    // Server Tick 处理（四模式 tick + 指令缓存执行 + 状态上传）
    // ============================================================

    public void onServerTick(ServerLevel level, int currentTick) {
        tickCounter = currentTick;
        // 清理过期指令缓存（每 10 tick）
        if (currentTick % 10 == 0) CmdCache.cleanupExpired();

        Iterator<Map.Entry<UUID, FakePlayerEntity>> it = ownerToAi.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FakePlayerEntity> e = it.next();
            UUID ownerId = e.getKey();
            FakePlayerEntity ai = e.getValue();
            if (ai == null || !ai.isAlive()) {
                it.remove();
                aiToOwner.values().removeIf(ownerId::equals);
                continue;
            }
            String mode = ownerToMode.getOrDefault(ownerId, CloudAiConstants.Modes.FOLLOW);

            // 执行指令缓存（优先执行）
            executeCachedCmds(ownerId.toString(), ai, currentTick);

            // 按模式执行行为
            Player owner = level.getPlayerByUUID(ownerId);
            switch (mode) {
                case CloudAiConstants.Modes.FOLLOW -> tickFollow(owner, ai);
                case CloudAiConstants.Modes.COMBAT -> tickCombat(owner, ai, currentTick);
                case CloudAiConstants.Modes.GATHER -> tickGather(owner, ai);
                case CloudAiConstants.Modes.GUARD  -> tickGuard(owner, ai, ownerId, currentTick);
            }
        }

        // 每 60 tick 上传一次 WS 状态
        if (currentTick % 60 == 0) uploadWsSnapshot(level);
    }

    // ------------ 四模式 tick 实现 ------------

    private void tickFollow(Player owner, FakePlayerEntity ai) {
        if (owner == null) return;
        if (ai.distanceTo(owner) > CloudAiConstants.FOLLOW_DISTANCE + 1.0D) {
            ai.getNavigation().moveTo(owner, 1.0D * AiAttackHelper.getSpeedMultiplier());
        } else if (ai.distanceTo(owner) < CloudAiConstants.FOLLOW_DISTANCE - 0.5D) {
            ai.getNavigation().stop();
        }
    }

    private void tickCombat(Player owner, FakePlayerEntity ai, int currentTick) {
        // 1) 跟随主人（当距离过远时）
        if (owner != null && ai.distanceTo(owner) > CloudAiConstants.COMBAT_SEARCH_RADIUS) {
            ai.getNavigation().moveTo(owner, 1.0D * AiAttackHelper.getSpeedMultiplier());
            return;
        }
        // 2) 搜索并攻击最近敌对
        LivingEntity target = AiAttackHelper.findNearestHostile(ai.level(), ai, CloudAiConstants.COMBAT_SEARCH_RADIUS);
        if (target == null) {
            // 无目标 → 跟随
            if (owner != null) tickFollow(owner, ai);
            return;
        }
        ai.setTarget(target);
        ai.getNavigation().moveTo(target, 1.1D * AiAttackHelper.getSpeedMultiplier());
        UUID ownerId = aiToOwner.get(ai.getUUID());
        int last = ownerId != null ? ownerToLastAttackTick.getOrDefault(ownerId, 0) : 0;
        if (AiAttackHelper.isAttackCooldownReady(last, currentTick)
                && AiAttackHelper.isWithinMeleeRange(ai, target)) {
            ai.doHurtTarget(target);
            if (ownerId != null) ownerToLastAttackTick.put(ownerId, currentTick);
        }
    }

    private void tickGather(Player owner, FakePlayerEntity ai) {
        // 1) 拾取附近掉落物
        List<ItemEntity> drops = EnvScan.nearbyDrops(ai.level(), ai, CloudAiConstants.GATHER_SEARCH_RADIUS);
        if (!drops.isEmpty()) {
            ItemEntity nearest = drops.stream().min(Comparator.comparingDouble(ai::distanceToSqr)).orElse(null);
            if (nearest != null) {
                ai.getNavigation().moveTo(nearest, 1.0D * AiAttackHelper.getSpeedMultiplier());
            }
        } else if (owner != null) {
            // 无掉落 → 跟随
            tickFollow(owner, ai);
        }
    }

    private void tickGuard(Player owner, FakePlayerEntity ai, UUID ownerId, int currentTick) {
        BlockPos guardPos = ownerToGuardPos.computeIfAbsent(ownerId, k -> ai.blockPosition());
        // 1) 如果离开守卫点超过 5 格 → 返回
        if (ai.blockPosition().distManhattan(guardPos) > 5) {
            ai.getNavigation().moveTo(guardPos.getX()+0.5, guardPos.getY(), guardPos.getZ()+0.5,
                    1.0D * AiAttackHelper.getSpeedMultiplier());
            return;
        }
        // 2) 守卫区域内的敌对
        LivingEntity target = AiAttackHelper.findNearestHostile(ai.level(), ai, CloudAiConstants.COMBAT_SEARCH_RADIUS);
        if (target != null) {
            ai.setTarget(target);
            ai.getNavigation().moveTo(target, 1.1D * AiAttackHelper.getSpeedMultiplier());
            int last = ownerToLastAttackTick.getOrDefault(ownerId, 0);
            if (AiAttackHelper.isAttackCooldownReady(last, currentTick)
                    && AiAttackHelper.isWithinMeleeRange(ai, target)) {
                ai.doHurtTarget(target);
                ownerToLastAttackTick.put(ownerId, currentTick);
            }
        }
    }

    // ------------ 指令缓存执行 ------------

    private void executeCachedCmds(String aiIdKey, FakePlayerEntity ai, int currentTick) {
        if (CmdCache.consume(aiIdKey, CmdCache.CMD_JUMP)) {
            if (ai.onGround()) {
                // jumpFromGround 是 protected；通过手动设置 Y 速度实现跳跃（原版 0.42）
                ai.setDeltaMovement(ai.getDeltaMovement().add(0.0D, 0.42D, 0.0D));
                ai.hasImpulse = true;
            }
        }
        if (CmdCache.consume(aiIdKey, CmdCache.CMD_FORWARD)) {
            Vec3 look = ai.getLookAngle().normalize();
            ai.setDeltaMovement(ai.getDeltaMovement().add(look.x * 0.2, 0, look.z * 0.2));
        }
        if (CmdCache.consume(aiIdKey, CmdCache.CMD_STOP)) {
            ai.getNavigation().stop();
            ai.setDeltaMovement(0, ai.getDeltaMovement().y, 0);
        }
        if (CmdCache.consume(aiIdKey, CmdCache.CMD_ATTACK)) {
            LivingEntity target = ai.getTarget();
            if (target == null) {
                target = AiAttackHelper.findNearestHostile(ai.level(), ai, CloudAiConstants.COMBAT_SEARCH_RADIUS);
            }
            if (target != null && AiAttackHelper.isWithinMeleeRange(ai, target)) {
                ai.doHurtTarget(target);
                UUID ownerId = aiToOwner.get(ai.getUUID());
                if (ownerId != null) ownerToLastAttackTick.put(ownerId, currentTick);
            }
        }
    }

    // ------------ WS 状态上传 ------------

    private void uploadWsAiEvent(Player owner, String type, String aiName, String aiUuid, String mode) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "ai_event");
            m.put("event", type);
            // LLM 模型声明 - 让服务端决策层使用 DeepSeek-R1
            m.put("llm_model", CloudAiConstants.LLM_MODEL);
            m.put("llm_model_display", CloudAiConstants.LLM_MODEL_DISPLAY);
            if (owner != null) {
                m.put("owner_uuid", owner.getUUID().toString());
                m.put("owner_name", owner.getName().getString());
            }
            if (aiName != null) m.put("ai_name", aiName);
            if (aiUuid != null) m.put("ai_uuid", aiUuid);
            if (mode != null) m.put("mode", mode);
            WsClient.getInstance().send(GSON.toJson(m));
        } catch (Exception ignored) {}
    }

    private void uploadWsSnapshot(ServerLevel level) {
        if (!WsClient.getInstance().isConnected()) return;
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map.Entry<UUID, FakePlayerEntity> e : ownerToAi.entrySet()) {
                FakePlayerEntity ai = e.getValue();
                if (ai == null || !ai.isAlive()) continue;
                Player owner = level.getPlayerByUUID(e.getKey());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("owner_uuid", e.getKey().toString());
                m.put("owner_name", owner != null ? owner.getName().getString() : "unknown");
                m.put("ai_uuid", ai.getUUID().toString());
                m.put("ai_name", ai.getName().getString());
                m.put("mode", ownerToMode.getOrDefault(e.getKey(), CloudAiConstants.Modes.FOLLOW));
                m.put("hp", (double) ai.getHealth());
                m.put("max_hp", (double) ai.getMaxHealth());
                m.put("x", ai.getX()); m.put("y", ai.getY()); m.put("z", ai.getZ());
                list.add(m);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("type", "snapshot");
            root.put("tick", tickCounter);
            // LLM 模型声明 - 服务端 RAG/决策层路由到 DeepSeek-R1
            root.put("llm_model", CloudAiConstants.LLM_MODEL);
            root.put("llm_model_display", CloudAiConstants.LLM_MODEL_DISPLAY);
            root.put("llm_api_base", CloudAiConstants.LLM_API_BASE);
            root.put("ais", list);
            WsClient.getInstance().send(GSON.toJson(root));
        } catch (Exception ignored) {}
    }

    /** WS 消息接收：解析为指令缓存 / mode 切换 等（由 EventBusSubscriber 调用） */
    public void handleWsMessage(String raw) {
        if (raw == null || raw.isEmpty()) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = GSON.fromJson(raw, Map.class);
            if (m == null) return;
            String type = (String) m.get("type");
            if ("cmd".equals(type)) {
                String ownerUuid = (String) m.get("owner_uuid");
                String cmd = (String) m.get("cmd");
                if (ownerUuid != null && cmd != null) {
                    CmdCache.put(ownerUuid, cmd);
                }
            } else if ("mode_set".equals(type)) {
                String ownerUuid = (String) m.get("owner_uuid");
                String mode = (String) m.get("mode");
                if (ownerUuid != null && mode != null) {
                    boolean valid = false;
                    for (String mv : CloudAiConstants.Modes.ALL) if (mv.equals(mode)) { valid = true; break; }
                    if (valid) ownerToMode.put(UUID.fromString(ownerUuid), mode);
                }
            }
        } catch (Exception ignored) {}
    }

    // ============================================================
    // FakePlayer 反射辅助（避免 API 不兼容时的编译失败）
    // ============================================================

    private static void trySetGameProfile(FakePlayerEntity ai, GameProfile profile) {
        try {
            java.lang.reflect.Field f = FakePlayerEntity.class.getDeclaredField("gameProfile");
            f.setAccessible(true);
            f.set(ai, profile);
        } catch (Exception ignored) {}
    }

    private static void trySetSlim(FakePlayerEntity ai, boolean slim) {
        try {
            java.lang.reflect.Method m = FakePlayerEntity.class.getMethod("setSlim", boolean.class);
            m.setAccessible(true);
            m.invoke(ai, slim);
        } catch (Exception e) {
            // 回退: 通过实体数据
            try {
                java.lang.reflect.Field f = FakePlayerEntity.class.getDeclaredField("DATA_IS_SLIM");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<Boolean> acc =
                        (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) f.get(null);
                ai.getEntityData().set(acc, slim);
            } catch (Exception ignored) {}
        }
    }

    private static void tryClearSkinCache(FakePlayerEntity ai) {
        try {
            java.lang.reflect.Method m = FakePlayerEntity.class.getMethod("clearSkinCache");
            m.setAccessible(true);
            m.invoke(ai);
        } catch (Exception ignored) { /* 无则忽略 */ }
    }

    private static void trySetSkinUrl(FakePlayerEntity ai, String url) {
        try {
            java.lang.reflect.Method m = FakePlayerEntity.class.getMethod("setSkinURL", String.class);
            m.setAccessible(true);
            m.invoke(ai, url);
        } catch (Exception e) {
            // 回退: 通过实体数据
            try {
                java.lang.reflect.Field f = FakePlayerEntity.class.getDeclaredField("DATA_SKIN_URL");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<String> acc =
                        (net.minecraft.network.syncher.EntityDataAccessor<String>) f.get(null);
                ai.getEntityData().set(acc, url);
            } catch (Exception ignored) {}
        }
    }

    private static void trySetOwner(FakePlayerEntity ai, Player owner) {
        try {
            java.lang.reflect.Method m = FakePlayerEntity.class.getMethod("setOwnerUUID", UUID.class);
            m.setAccessible(true);
            m.invoke(ai, owner.getUUID());
        } catch (Exception e) {
            // 回退: 通过实体数据
            try {
                java.lang.reflect.Field f = FakePlayerEntity.class.getDeclaredField("DATA_OWNER_UUID");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<Optional<UUID>> acc =
                        (net.minecraft.network.syncher.EntityDataAccessor<Optional<UUID>>) f.get(null);
                ai.getEntityData().set(acc, Optional.of(owner.getUUID()));
            } catch (Exception ignored) {}
        }
        try {
            java.lang.reflect.Field f = FakePlayerEntity.class.getDeclaredField("DATA_TAMED");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            net.minecraft.network.syncher.EntityDataAccessor<Boolean> acc =
                    (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) f.get(null);
            ai.getEntityData().set(acc, true);
        } catch (Exception ignored) {}
    }
}
