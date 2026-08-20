package com.qlm.zombie.dependency;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Crafting Dead 感染/受伤效果拦截器（兜底层）。
 *
 * <p>背景：Crafting Dead survival 的 {@code SurvivalDamageSource} 静态初始化使用
 * {@code RegistryAccess.FROZEN}（仅含静态注册表）查找数据驱动注册表
 * {@code minecraft:damage_type}，任何环境（客户端/服务端）首次 tick
 * {@code InfectionMobEffect} 时都会抛出 {@code IllegalStateException: Missing registry}，
 * 导致客户端崩溃或服务端断开玩家（Internal server error）。
 *
 * <p>CD 的 {@code .8} 与 {@code .10} 版本该类字节码完全一致，降级无效，
 * 唯一可靠修复是拦截 CD 有害效果的施加（双端），玩家不再获得这些效果，
 * 就不会触发崩溃；CD 的僵尸/装备/箱子等其他内容完全不受影响。
 *
 * <p>拦截清单：
 * <ul>
 *   <li>{@code craftingdeadsurvival:infection} — 感染效果（崩溃确认）</li>
 *   <li>{@code craftingdead:bleeding} — 流血效果（同类风险）</li>
 *   <li>{@code craftingdeadsurvival:broken_leg} — 断腿效果（同类风险）</li>
 * </ul>
 *
 * <p>实现要点（2026-08-20 build64 重构，修复 09:19 崩溃）：
 * <ul>
 *   <li><b>主防线改为 mixin</b> {@code MixinLivingEntity} 在
 *       {@code LivingEntity.addEffect(MobEffectInstance)} 入口拦截返回 false ——
 *       Forge 1.20.1 中 {@code MobEffectEvent.Applicable} 与 {@code Added} 均<b>不可取消</b>，
 *       调用 {@code setCanceled()} 会抛 {@code UnsupportedOperationException} 导致服务器崩溃
 *       （2026-08-19 18:32 用 Added 崩溃、2026-08-20 09:19 用 Applicable 仍崩溃，两次同根因）。</li>
 *   <li>本类仅保留兜底 {@link #onEntityJoinLevel(EntityJoinLevelEvent)}：实体加入世界
 *       （含玩家登录）时移除已存在的 CD 有害效果（覆盖从 NBT 加载/历史残留），
 *       该事件不涉及 setCanceled，安全。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "qlmzombie", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CdInfectionGuard {

    private static final Set<String> BLOCKED_EFFECTS = Set.of(
            "craftingdeadsurvival:infection",
            "craftingdead:bleeding",
            "craftingdeadsurvival:broken_leg"
    );

    private CdInfectionGuard() {
    }

    /**
     * 兜底：实体加入世界（含玩家登录）时移除已存在的 CD 有害效果。
     * 先收集再删除，避免遍历时修改 activeEffects 地图。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffect effect : living.getActiveEffectsMap().keySet()) {
            if (isBlockedEffect(effect)) {
                toRemove.add(effect);
            }
        }
        for (MobEffect effect : toRemove) {
            living.removeEffect(effect);
        }
    }

    private static boolean isBlockedEffect(MobEffect effect) {
        if (effect == null) {
            return false;
        }
        var key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return key != null && BLOCKED_EFFECTS.contains(key.toString());
    }
}
