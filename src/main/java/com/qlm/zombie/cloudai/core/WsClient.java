package com.qlm.zombie.cloudai.core;

import com.qlm.zombie.QLMZombieMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CloudAI WebSocket 客户端（单例）
 * 仅服务端使用，客户端不直接通信
 * 基于 Java 11+ 内置 HttpClient WebSocket
 */
public class WsClient {

    private static volatile WsClient INSTANCE;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CloudAI-WS-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final HttpClient httpClient;

    private WsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CloudAiConstants.WS_CONNECT_TIMEOUT_MS))
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
        connectInternal();
        startHeartbeat();
        startReconnect();
    }

    /** 停止 WS 客户端 */
    public synchronized void stop() {
        running.set(false);
        scheduler.shutdownNow();
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
        try {
            URI serverUri = URI.create(CloudAiConstants.WS_URL);
            httpClient.newWebSocketBuilder()
                    .buildAsync(serverUri, new WsListener())
                    .whenComplete((ws, err) -> {
                        if (err != null) {
                            connected.set(false);
                            QLMZombieMod.LOGGER.error("[CloudAI] WS connect failed: {}", err.getMessage());
                        } else {
                            wsRef.set(ws);
                            connected.set(true);
                            QLMZombieMod.LOGGER.info("[CloudAI] WS connected to {}", CloudAiConstants.WS_URL);
                            trySendAuth();
                        }
                    });
        } catch (Exception e) {
            connected.set(false);
            QLMZombieMod.LOGGER.error("[CloudAI] WS connect error: {}", e.getMessage());
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
                QLMZombieMod.LOGGER.info("[CloudAI] WS reconnecting...");
                connectInternal();
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
            QLMZombieMod.LOGGER.error("[CloudAI] WS error: {}", error != null ? error.getMessage() : "unknown");
        }
    }
}
