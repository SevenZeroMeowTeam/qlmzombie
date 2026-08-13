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
 * QLM ModSDK — 调度任务数据类
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.task;

/**
 * 任务数据类。由 {@link TaskScheduler} 创建并维护。
 *
 * <ul>
 *   <li>{@code taskId} — 任务唯一标识</li>
 *   <li>{@code runnable} — 任务执行体</li>
 *   <li>{@code nextRunTick} — 下次执行的服务器 tick（绝对值）</li>
 *   <li>{@code periodTicks} — 重复周期，{@code -1} 表示不重复</li>
 *   <li>{@code isCanceled} — 是否已取消</li>
 * </ul>
 */
public final class ScheduledTask {

    private final int taskId;
    private final Runnable runnable;
    private volatile long nextRunTick;
    private final long periodTicks;
    private volatile boolean isCanceled;

    public ScheduledTask(int taskId, Runnable runnable, long nextRunTick, long periodTicks) {
        this.taskId = taskId;
        this.runnable = runnable;
        this.nextRunTick = nextRunTick;
        this.periodTicks = periodTicks;
        this.isCanceled = false;
    }

    public int getTaskId() { return taskId; }
    public Runnable getRunnable() { return runnable; }
    public long getNextRunTick() { return nextRunTick; }
    public long getPeriodTicks() { return periodTicks; }
    public boolean isCanceled() { return isCanceled; }
    public boolean isRepeating() { return periodTicks > 0; }

    /** 设置下次执行的 tick（仅用于重复任务的内部更新）。 */
    public void setNextRunTick(long nextRunTick) {
        this.nextRunTick = nextRunTick;
    }

    /** 取消任务。 */
    public void cancel() {
        this.isCanceled = true;
    }
}
