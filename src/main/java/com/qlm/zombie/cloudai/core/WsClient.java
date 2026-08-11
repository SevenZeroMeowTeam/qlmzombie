package com.qlm.zombie.cloudai.core;

import com.qlm.zombie.QLMZombieMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CloudAI WebSocket 客户端（单例）
 * 仅服务端使用，客户端不直接通信
 * 基于 Java 11+ 内置 HttpClient WebSocket
 *
 * 重要：使用专用线程池，避免阻塞 ForkJoinPool.commonPool（Minecraft 世界生成依赖该池）
 */
public class WsClient {

    private static volatile WsClient INSTANCE;

    /** 专用线程池 — 避免 HttpClient 回调阻塞 ForkJoinPool.commonPool */
    private final ExecutorService wsExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "CloudAI-WS-Worker");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CloudAI-WS-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final HttpClient httpClient;

    /** 最大重连次数（超过后停止重连，避免无限刷屏） */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private WsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CloudAiConstants.WS_CONNECT_TIMEOUT_MS))
                .executor(wsExecutor)
                .build();
    }

    public static WsClient getInstance() {
        if (INSTANCE == null) {
            synchronized (WsClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WsClient();
                }
            }
        }
        return INSTANCE;
    }

    /** 启动 WS 客户端（服务端启动时调用） */
    public synchronized void start() {
        if (running.getAndSet(true)) return;
        // 延迟 10 秒再首次连接，等服务端完全启动
        scheduler.schedule(this::connectInternal, 10, TimeUnit.SECONDS);
        startHeartbeat();
        startReconnect();
    }

    /** 停止 WS 客户端 */
    public synchronized void stop() {
        running.set(false);
        scheduler.shutdownNow();
        wsExecutor.shutdownNow();
        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown"); } catch (Exception ignored) {}
        }
    }

    /** 发送消息（非阻塞） */
    public void send(String message) {
        if (message == null || message.isEmpty()) return;
        if (!connected.get()) return;
        WebSocket ws = wsRef.get();
        if (ws == null) return;
        try {
            ws.sendText(message, true);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[CloudAI] WS send failed: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    // ============ internal ============

    private void connectInternal() {
        if (!running.get()) return;
        try {
            URI serverUri = URI.create(CloudAiConstants.WS_URL);
            httpClient.newWebSocketBuilder()
                    .buildAsync(serverUri, new WsListener())
                    .whenCompleteAsync((ws, err) -> {
                        if (err != null) {
                            connected.set(false);
                            // 仅前3次和每5次输出错误日志，避免刷屏
                            int count = reconnectCount.get();
                            if (count < 3 || count % 5 == 0) {
                                QLMZombieMod.LOGGER.warn("[CloudAI] WS connect failed (attempt {}): {}", count + 1, err.getMessage());
                            }
                        } else {
                            wsRef.set(ws);
                            connected.set(true);
                            reconnectCount.set(0); // 重置重连计数
                            QLMZombieMod.LOGGER.info("[CloudAI] WS connected to {}", CloudAiConstants.WS_URL);
                            trySendAuth();
                        }
                    }, wsExecutor); // 指定专用线程池，不使用 ForkJoinPool
        } catch (Exception e) {
            connected.set(false);
            QLMZombieMod.LOGGER.warn("[CloudAI] WS connect error: {}", e.getMessage());
        }
    }

    private void trySendAuth() {
        try {
            String authMsg = "{\"type\":\"auth\",\"token\":\"" + CloudAiConstants.WS_TOKEN + "\"}";
            send(authMsg);
        } catch (Exception e) {
            QLMZombieMod.LOGGER.warn("[CloudAI] WS auth send failed: {}", e.getMessage());
        }
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            if (connected.get()) {
                WebSocket ws = wsRef.get();
                if (ws != null) {
                    try { ws.sendPing(ByteBuffer.allocate(4)); } catch (Exception ignored) {}
                }
            }
        }, CloudAiConstants.WS_HEARTBEAT_INTERVAL_MS,
           CloudAiConstants.WS_HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startReconnect() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            if (!connected.get()) {
                int attempts = reconnectCount.incrementAndGet();
                if (attempts > MAX_RECONNECT_ATTEMPTS) {
                    // 超过最大重连次数，停止重连
                    QLMZombieMod.LOGGER.warn("[CloudAI] WS max reconnect attempts ({}) reached, giving up", MAX_RECONNECT_ATTEMPTS);
                    running.set(false);
                    return;
                }
                // 指数退避：5s, 10s, 20s, 40s, 60s, 60s, ...
                long delay = Math.min(5000L * (1L << Math.min(attempts - 1, 4)), 60000L);
                QLMZombieMod.LOGGER.info("[CloudAI] WS reconnecting (attempt {}, delay {}ms)...", attempts, delay);
                scheduler.schedule(this::connectInternal, delay, TimeUnit.MILLISECONDS);
            }
        }, CloudAiConstants.WS_RECONNECT_INTERVAL_MS,
           CloudAiConstants.WS_RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** WS 消息事件（Forge 事件总线上发布） */
    public static class WsMessageEvent extends net.minecraftforge.eventbus.api.Event {
        public final String message;
        public WsMessageEvent(String message) { this.message = message; }
    }

    private class WsListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            connected.set(true);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String full = textBuffer.toString();
                textBuffer.setLength(0);
                QLMZombieMod.LOGGER.debug("[CloudAI] WS recv: {}", full);
                try {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                            new WsMessageEvent(full));
                } catch (Exception e) {
                    QLMZombieMod.LOGGER.warn("[CloudAI] WS event bus post failed: {}", e.getMessage());
                }
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected.set(false);
            wsRef.set(null);
            QLMZombieMod.LOGGER.warn("[CloudAI] WS closed: code={} reason={}", statusCode, reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.set(false);
            wsRef.set(null);
            QLMZombieMod.LOGGER.warn("[CloudAI] WS error: {}", error != null ? error.getMessage() : "unknown");
        }
    }
}
