/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ----------------------------------------------------------------------------
 * QLM ModSDK — 任务调度器
 * 类似 Bukkit 的 BukkitScheduler，使用 PriorityQueue 按到期时间排序。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.task;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 服务端任务调度器。由主 mod 每 tick 调用 {@link #tick()} 执行到期任务。
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * TaskScheduler scheduler = SDKModSDK.getTaskScheduler();
 * scheduler.runTaskLater(() -> System.out.println("5 秒后执行"), 100L);
 * int taskId = scheduler.runTaskTimer(() -> System.out.println("每秒触发"), 0L, 20L);
 * }</pre>
 */
public final class TaskScheduler {

    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>(
            (a, b) -> Long.compare(a.getNextRunTick(), b.getNextRunTick()));
    private final AtomicInteger nextTaskId = new AtomicInteger(1);
    private final ReentrantLock lock = new ReentrantLock();

    private volatile long currentTick = 0L;

    /** 在下一 tick 执行。 */
    public int runTask(Runnable runnable) {
        return runTaskLater(runnable, 1L);
    }

    /** 延迟 delayTicks 后执行一次。 */
    public int runTaskLater(Runnable runnable, long delayTicks) {
        if (runnable == null) return -1;
        if (delayTicks < 0) delayTicks = 0;
        int taskId = nextTaskId.getAndIncrement();
        ScheduledTask task = new ScheduledTask(taskId, runnable, currentTick + delayTicks, -1L);
        lock.lock();
        try {
            queue.offer(task);
        } finally {
            lock.unlock();
        }
        return taskId;
    }

    /** 延迟 delayTicks 后开始，每 periodTicks 重复执行。 */
    public int runTaskTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (runnable == null) return -1;
        if (delayTicks < 0) delayTicks = 0;
        if (periodTicks <= 0) periodTicks = 1;
        int taskId = nextTaskId.getAndIncrement();
        ScheduledTask task = new ScheduledTask(taskId, runnable, currentTick + delayTicks, periodTicks);
        lock.lock();
        try {
            queue.offer(task);
        } finally {
            lock.unlock();
        }
        return taskId;
    }

    /** 取消指定 id 的任务。 */
    public void cancelTask(int taskId) {
        lock.lock();
        try {
            for (ScheduledTask task : queue) {
                if (task.getTaskId() == taskId) {
                    task.cancel();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** 取消所有任务。 */
    public void cancelAll() {
        lock.lock();
        try {
            for (ScheduledTask task : queue) {
                task.cancel();
            }
            queue.clear();
        } finally {
            lock.unlock();
        }
    }

    /** 由主 mod 每 tick 调用，执行到期任务。 */
    public void tick() {
        currentTick++;
        // 最多处理一定数量的任务以避免单 tick 阻塞
        int processed = 0;
        while (processed < 1000) {
            ScheduledTask task;
            lock.lock();
            try {
                task = queue.peek();
                if (task == null) break;
                if (task.isCanceled()) {
                    queue.poll();
                    continue;
                }
                if (task.getNextRunTick() > currentTick) {
                    break;
                }
                queue.poll();
            } finally {
                lock.unlock();
            }

            try {
                task.getRunnable().run();
            } catch (Throwable t) {
                System.err.println("[QLM ModSDK] 任务 #" + task.getTaskId() + " 执行异常: " + t);
                t.printStackTrace();
            }
            processed++;

            if (task.isRepeating() && !task.isCanceled()) {
                task.setNextRunTick(currentTick + task.getPeriodTicks());
                lock.lock();
                try {
                    queue.offer(task);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /** 当前调度器 tick（每次 {@link #tick()} 自增 1）。 */
    public long getCurrentTick() {
        return currentTick;
    }

    /** 队列中尚未执行的任务数（含取消但未出队的）。 */
    public int getPendingTaskCount() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}
