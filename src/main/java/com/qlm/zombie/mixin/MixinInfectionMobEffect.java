package com.qlm.zombie.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 Crafting Dead Survival 的 {@code InfectionMobEffect.applyEffectTick}，使其无害化。
 *
 * <p>背景：crafting-dead-survival 1.2.5.10 的 {@code SurvivalDamageSource} 静态初始化
 * （{@code <clinit>}）使用 {@code RegistryAccess.FROZEN} 查询数据驱动注册表
 * {@code minecraft:damage_type}，任何环境首次触发都会抛
 * {@code IllegalStateException: Missing registry}。而 {@code InfectionMobEffect.applyEffectTick}
 * 引用了 {@code SurvivalDamageSource.INFECTION}，导致玩家身上已存在的感染效果
 * （NBT 持久化，build64 之前遗留）每 tick 触发时抛出
 * {@code ExceptionInInitializerError}（JVM 缓存该错误），最终玩家被断开
 * （"Internal server error"，2026-08-20 10:44 事件）。
 *
 * <p>处理方式：在 {@code applyEffectTick} 入口直接 cancel，使感染效果完全无害
 * （不造成伤害、不触发 {@code SurvivalDamageSource} 类加载），效果 duration 正常递减后
 * 自然消失。新增的感染效果仍由 {@link MixinLivingEntity} 在 {@code addEffect} 入口拦截。
 */
@Mixin(targets = "com.craftingdead.survival.world.effect.InfectionMobEffect")
public abstract class MixinInfectionMobEffect {

    /**
     * 无害化：阻止 applyEffectTick 执行，避免访问 SurvivalDamageSource.INFECTION。
     *
     * <p>注意：crafting-dead-survival 是已发布的 srg 混淆 jar，其方法在字节码中为 SRG 名
     * {@code m_6742_}（即 applyEffectTick），因此这里直接写 SRG 名并使用 {@code remap=false}，
     * 避免 mixin 处理器按 mojmap 名查找失败。
     */
    @Inject(method = "m_6742_(Lnet/minecraft/world/entity/LivingEntity;I)V",
            remap = false, at = @At("HEAD"), cancellable = true)
    private void qlmzombie$disableApplyEffectTick(LivingEntity entity, int amplifier, CallbackInfo ci) {
        ci.cancel();
    }
}
