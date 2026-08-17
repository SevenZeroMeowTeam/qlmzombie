package com.qlm.zombie.monitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 服务器性能监控：每 1 秒采样一次 TPS / MSPT / 在线人数 / 内存，
 * 写入服务器根目录 {@code qlm-metrics.json}（SeverAdmin web 后台读取并绘制
 * TPS / 延迟曲线监控图）。
 *
 * <p>TPS 采用"墙钟秒窗口内实际完成的 tick 数"计算（封顶 20），不依赖
 * {@code getAverageTickTime()} 的具体实现，版本兼容性更好。
 * 文件保留最近 {@link #MAX_POINTS} 个采样点（滚动窗口），由 web 端读取。
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class ServerMetricsMonitor {

    /** 输出文件名（写在服务器根目录，docker 部署时与 web 容器共享 mc-data 卷） */
    private static final String FILE_NAME = "qlm-metrics.json";
    /** 保留最近 120 个采样点（120 秒） */
    private static final int MAX_POINTS = 120;
    /** 采样间隔：20 tick = 1 秒 */
    private static final long SAMPLE_INTERVAL_TICKS = 20;

    private static final Deque<JsonObject> buffer = new ArrayDeque<>();

    /** 上次墙钟采样时间（ms） */
    private static long lastSampleMs = 0;
    /** 上次 tick 采样点（用于按 tick 节流） */
    private static long lastSampleTick = 0;
    /** 本次墙钟窗口内实际完成的 tick 数 */
    private static int ticksInWindow = 0;

    private ServerMetricsMonitor() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // 按 tick 节流：每 20 tick 处理一次
        long tick = server.getTickCount();
        if (tick - lastSampleTick < SAMPLE_INTERVAL_TICKS) return;
        lastSampleTick = tick;

        ticksInWindow++;
        long now = System.currentTimeMillis();
        if (lastSampleMs == 0) {
            lastSampleMs = now;
            return;
        }
        long elapsed = now - lastSampleMs;
        if (elapsed < 1000) return;
        lastSampleMs = now;

        // TPS = 墙钟 1 秒内实际完成的 tick 数（封顶 20）；MSPT = 平均每 tick 耗时（ms）
        double tps = Math.min(20.0, ticksInWindow * 1000.0 / elapsed);
        double mspt = elapsed / (double) Math.max(1, ticksInWindow);
        ticksInWindow = 0;

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);

        JsonObject point = new JsonObject();
        point.addProperty("t", now);
        point.addProperty("tps", Math.round(tps * 100.0) / 100.0);
        point.addProperty("mspt", Math.round(mspt * 10.0) / 10.0);
        point.addProperty("players", server.getPlayerCount());
        point.addProperty("memUsedMB", usedMB);
        point.addProperty("memMaxMB", maxMB);

        buffer.addLast(point);
        while (buffer.size() > MAX_POINTS) buffer.removeFirst();
        writeFile();
    }

    private static void writeFile() {
        try {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (JsonObject p : buffer) arr.add(p);
            root.add("points", arr);
            Path path = Path.of(FILE_NAME);
            Files.write(path, root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            QLMZombieMod.LOGGER.debug("[QLM Zombie] 写入性能监控文件失败: {}", e.getMessage());
        }
    }
}
