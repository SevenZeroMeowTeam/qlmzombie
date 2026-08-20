package com.qlm.zombie.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 拦截 {@link LivingEntity#addEffect(MobEffectInstance)}，阻止 Crafting Dead 有害效果施加。
 *
 * <p>背景：Crafting Dead Survival 的 {@code SurvivalDamageSource} 静态初始化使用
 * {@code RegistryAccess.FROZEN}（仅含静态注册表）查找数据驱动注册表
 * {@code minecraft:damage_type}，任何环境首次 tick {@code InfectionMobEffect} 时都会抛出
 * {@code IllegalStateException: Missing registry}，导致客户端崩溃或服务端断开玩家。
 *
 * <p>⚠️ 为什么不用 Forge 事件取消？—— Forge 1.20.1 中 {@code MobEffectEvent.Applicable}
 * 与 {@code MobEffectEvent.Added} 均<b>不可取消</b>，调用 {@code event.setCanceled()} 会抛
 * {@code UnsupportedOperationException} 导致服务器崩溃（2026-08-20 09:19 crash 根因；
 * 此前 2026-08-19 18:32 曾因在 Added 上 setCanceled 崩溃）。因此在 {@code addEffect}
 * 入口用 mixin 直接返回 false 才是唯一可靠拦截点。
 *
 * <p>拦截清单（与 CdInfectionGuard 保持一致）：
 * <ul>
 *   <li>{@code craftingdeadsurvival:infection} — 感染效果（崩溃确认）</li>
 *   <li>{@code craftingdead:bleeding} — 流血效果（同类风险）</li>
 *   <li>{@code craftingdeadsurvival:broken_leg} — 断腿效果（同类风险）</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    private static final Set<String> BLOCKED_EFFECTS = Set.of(
            "craftingdeadsurvival:infection",
            "craftingdead:bleeding",
            "craftingdeadsurvival:broken_leg"
    );

    /**
     * 入口拦截：addEffect(MobEffectInstance) 返回 false 即拒绝施加（CD 效果被阻止）。
     * 单参数版本会委托给双参数版本，拦截入口即可覆盖所有调用路径。
     */
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            at = @At("HEAD"), cancellable = true)
    private void qlmzombie$blockCdEffects(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        if (isBlocked(effectInstance)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isBlocked(MobEffectInstance effectInstance) {
        if (effectInstance == null || effectInstance.getEffect() == null) {
            return false;
        }
        var key = ForgeRegistries.MOB_EFFECTS.getKey(effectInstance.getEffect());
        return key != null && BLOCKED_EFFECTS.contains(key.toString());
    }
}
