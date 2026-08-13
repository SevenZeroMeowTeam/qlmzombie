/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * AIPlayerSkinManager — AI 玩家皮肤调度器
 *
 * 职责：
 *   - 给新生成的 FakePlayerEntity 分配一张随机形象皮肤
 *   - 优先在线抓取（LittleSkin skinlib 高赞/最新页）→ 失败用内置兜底
 *   - 支持 Alex 模型（细胳膊 slim）与 Steve 模型（正常宽胳膊）
 *   - 所有 I/O 异步执行，不阻塞主线程 / 生成 tick
 *   - 结果写入 FakePlayerEntity.setSkinURL() + setSlim()，通过
 *     同步数据通道发送给客户端渲染器 FakePlayerEntityRenderer
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.player;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.entity.FakePlayerEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class AIPlayerSkinManager {

    /** 单个 AI 获取皮肤最多等待多久（超时则兜底）。 */
    private static final long FETCH_TIMEOUT_MS = 4000;

    private AIPlayerSkinManager() {}

    /**
     * 给 AI 玩家随机分配一张皮肤（异步，立即返回）。
     * 分配成功 / 失败降级都会自动调用 entity.setSkinURL() / setSlim()。
     */
    public static void assignRandomSkin(FakePlayerEntity entity) {
        assignRandomSkin(entity, null);
    }

    /**
     * 给 AI 玩家随机分配皮肤，完成后在主线程（若提供 level）回调可选逻辑。
     */
    public static void assignRandomSkin(FakePlayerEntity entity, ServerLevel level) {
        if (entity == null) return;

        // 快速先给个兜底，保证绝对不是 Steve 默认
        String fallback = LittleSkinClient.randomFallbackSkin();
        boolean slimFallback = pickSlimByTid(fallback);
        entity.setSkinURL(fallback);
        entity.setSlim(slimFallback);

        String name = entity.getCustomNameStr();
        QLMZombieMod.LOGGER.debug("[Skin] AI {} 开始分配皮肤，兜底={}", name, fallback);

        CompletableFuture.supplyAsync(LittleSkinClient::fetchRandomSkinSync)
                .orTimeout(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((skinUrl, err) -> {
                    String finalUrl;
                    boolean slim;
                    if (err != null || skinUrl == null || skinUrl.isEmpty()) {
                        if (err != null) {
                            QLMZombieMod.LOGGER.debug("[Skin] AI {} 在线抓取皮肤失败，使用兜底: {}",
                                    name, err.getMessage());
                        }
                        // 50% 概率换成角色名方式（/skin/{name}.png），增加多样性
                        if (ThreadLocalRandom.current().nextFloat() < 0.5f && name != null && !name.isEmpty()) {
                            String byName = LittleSkinClient.buildPlayerSkinUrl(cleanNameForUrl(name));
                            finalUrl = byName;
                            slim = ThreadLocalRandom.current().nextBoolean();
                        } else {
                            finalUrl = fallback;
                            slim = slimFallback;
                        }
                    } else {
                        finalUrl = skinUrl;
                        slim = pickSlimByTid(skinUrl);
                    }
                    applySkin(entity, level, finalUrl, slim);
                });
    }

    private static void applySkin(FakePlayerEntity entity, ServerLevel level, String url, boolean slim) {
        Runnable setter = () -> {
            try {
                entity.setSkinURL(url);
                entity.setSlim(slim);
                QLMZombieMod.LOGGER.info("[Skin] AI {} 皮肤分配完成: {} (slim={})",
                        entity.getCustomNameStr(), url, slim);
            } catch (Exception e) {
                QLMZombieMod.LOGGER.warn("[Skin] 应用皮肤失败: {}", e.getMessage());
            }
        };
        if (level != null && !level.isClientSide) {
            level.getServer().execute(setter);
        } else {
            // 不强制主线程（同步数据写入本身是线程安全的）
            setter.run();
        }
    }

    /** 从 URL/TID 猜测 slim 类型：TID 偶数 → Alex (slim=true)，奇数 → Steve。
     *  这只是个启发式（LittleSkin 里每种 TID 都对应一定的皮肤），
     *  客户端渲染器拿到 PNG 后会再次通过 isSlimSkin() 检测覆盖，所以这里错了也没关系。 */
    private static boolean pickSlimByTid(String urlOrTid) {
        if (urlOrTid == null) return false;
        String digits = urlOrTid.replaceAll("\\D", "");
        if (digits.isEmpty()) return ThreadLocalRandom.current().nextBoolean();
        try {
            long n = Long.parseLong(digits.substring(Math.max(0, digits.length() - 6)));
            return (n & 1) == 0; // 偶数 Alex，奇数 Steve
        } catch (NumberFormatException e) {
            return ThreadLocalRandom.current().nextBoolean();
        }
    }

    /** 去掉 AI 名字里的编号/非字母，便于 /skin/{name}.png 尝试。 */
    private static String cleanNameForUrl(String name) {
        if (name == null) return "Steve";
        String s = name.replaceAll("_\\d+$", "")   // 去掉末尾 _123
                       .replaceAll("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]", "");
        if (s.isEmpty()) {
            // 中文名随机一个兜底
            String[] fall = {"Steve", "Alex", "Sunny", "Zuri", "Efe", "Kai", "Ari", "Noor", "Makena"};
            return fall[ThreadLocalRandom.current().nextInt(fall.length)];
        }
        return s;
    }
}
