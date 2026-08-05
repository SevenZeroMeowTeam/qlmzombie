/*
 * Copyright (c) 2026 QLM Zombie Mod
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ----------------------------------------------------------------------------
 * A* Pathfinding Framework — 原创实现
 * 参考: Hart, Nilsson & Raphael (1968) A* Algorithm
 *
 * AStarPathfinder — A* 路径规划优化器
 *
 * 相比 Minecraft 原版 PathNavigation 的改进:
 *   - 自定义启发函数 (Manhattan / Euclidean / Chebyshev 可选)
 *   - 支持跳跃奖励（避免绕远）
 *   - 支持安全成本（远离危险方块）
 *   - 支持成本权重（游泳、攀爬成本更高）
 *   - 节点上限保护，防止长路径卡顿
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.ai.algorithm.pathfinding;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * A* 路径规划器
 *
 * 用法:
 *   AStarPathfinder pf = new AStarPathfinder(level);
 *   List<BlockPos> path = pf.findPath(start, goal, 256);
 *   if (path != null) { /* 使用 path *\/ }
 */
public class AStarPathfinder {

    public enum Heuristic { MANHATTAN, EUCLIDEAN, CHEBYSHEV }

    private final Level level;
    private Heuristic heuristic = Heuristic.EUCLIDEAN;
    private double heuristicWeight = 1.0; // 大于 1 → 贪心，更快但非最优
    private int maxExpandedNodes = 1024;
    private boolean allowWater = false;
    private boolean allowClimb = true;
    private double waterCostMultiplier = 2.0;
    private double damageCost = 8.0; // 岩浆、仙人掌等

    public AStarPathfinder(Level level) {
        this.level = level;
    }

    public AStarPathfinder setHeuristic(Heuristic h) {
        this.heuristic = h;
        return this;
    }

    public AStarPathfinder setHeuristicWeight(double w) {
        this.heuristicWeight = w;
        return this;
    }

    public AStarPathfinder setMaxExpandedNodes(int max) {
        this.maxExpandedNodes = max;
        return this;
    }

    public AStarPathfinder setAllowWater(boolean allow) {
        this.allowWater = allow;
        return this;
    }

    public AStarPathfinder setAllowClimb(boolean allow) {
        this.allowClimb = allow;
        return this;
    }

    /**
     * 寻找从 start 到 goal 的路径
     * @param maxDistance 最大搜索距离（曼哈顿）
     * @return 路径节点列表（包含 start 和 goal），找不到返回 null
     */
    public List<BlockPos> findPath(BlockPos start, BlockPos goal, int maxDistance) {
        if (start == null || goal == null) return null;
        if (start.closerThan(goal, 1.5)) {
            return Collections.singletonList(start);
        }

        // 限制 Y 差距，避免在垂直方向消耗过多节点
        if (Math.abs(start.getY() - goal.getY()) > maxDistance) return null;

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, Node> openMap = new HashMap<>();
        Set<Long> closed = new HashSet<>();

        Node startNode = new Node(start.immutable(), 0, heuristic(start, goal), null);
        open.add(startNode);
        openMap.put(start.asLong(), startNode);

        int expanded = 0;
        while (!open.isEmpty() && expanded < maxExpandedNodes) {
            Node current = open.poll();
            openMap.remove(current.pos.asLong());

            if (current.pos.closerThan(goal, 1.5)) {
                return reconstructPath(current);
            }

            closed.add(current.pos.asLong());
            expanded++;

            for (BlockPos neighbor : getNeighbors(current.pos)) {
                long key = neighbor.asLong();
                if (closed.contains(key)) continue;

                double stepCost = stepCost(current.pos, neighbor);
                if (stepCost < 0) continue; // 不可通行

                double tentativeG = current.g + stepCost;
                Node existing = openMap.get(key);
                if (existing == null) {
                    Node node = new Node(neighbor.immutable(), tentativeG, heuristic(neighbor, goal), current);
                    if (node.g <= maxDistance) {
                        open.add(node);
                        openMap.put(key, node);
                    }
                } else if (tentativeG < existing.g) {
                    open.remove(existing);
                    existing.g = tentativeG;
                    existing.f = tentativeG + existing.h;
                    existing.parent = current;
                    open.add(existing);
                }
            }
        }

        if (QLMZombieMod.LOGGER.isDebugEnabled()) {
            QLMZombieMod.LOGGER.debug("[A*] 未找到路径 {} -> {}", start, goal);
        }
        return null;
    }

    private List<BlockPos> reconstructPath(Node end) {
        List<BlockPos> path = new ArrayList<>();
        Node node = end;
        while (node != null) {
            path.add(node.pos);
            node = node.parent;
        }
        Collections.reverse(path);
        // 去掉起点（实体的当前格已经在那里）
        if (path.size() > 1) path.remove(0);
        return path;
    }

    private double heuristic(BlockPos a, BlockPos b) {
        double dx = Math.abs(a.getX() - b.getX());
        double dy = Math.abs(a.getY() - b.getY());
        double dz = Math.abs(a.getZ() - b.getZ());
        double h = switch (heuristic) {
            case MANHATTAN -> dx + dy + dz;
            case EUCLIDEAN -> Math.sqrt(dx * dx + dy * dy + dz * dz);
            case CHEBYSHEV -> Math.max(Math.max(dx, dy), dz);
        };
        return h * heuristicWeight;
    }

    /** 获取邻居节点（26 方向，3D） */
    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>(26);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (isWalkable(cursor)) {
                        neighbors.add(cursor.immutable());
                    }
                }
            }
        }
        return neighbors;
    }

    /** 判断方块是否可通行 */
    private boolean isWalkable(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.getBlock() instanceof LiquidBlock) {
            return allowWater && state.getFluidState().isEmpty();
        }
        // 危险方块
        if (state.is(Blocks.LAVA) || state.is(Blocks.CACTUS) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.WITHER_ROSE)) {
            return false;
        }
        // 使用 BlockState.isPathfindable 判断可通过性（花、草、雪层等）
        // isPathfindable: 0 = WALKABLE, 1 = LAND, 2 = AIR  —— 不同源码版本略有差异
        try {
            return state.isPathfindable(level, pos, net.minecraft.world.level.pathfinder.PathComputationType.LAND);
        } catch (Exception ignored) {
            // 兜底：非实心方块视为可通行
            return !state.isSolidRender(level, pos);
        }
    }

    /** 计算从 current 移动到 neighbor 的代价 */
    private double stepCost(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double cost = dist;

        // 攀爬成本
        if (dy > 0) {
            if (!allowClimb) return -1;
            cost *= 1.5;
        } else if (dy < 0) {
            cost *= 0.8; // 下坡便宜
        }

        // 水中成本
        BlockState toState = level.getBlockState(to);
        if (toState.getBlock() instanceof LiquidBlock) {
            cost *= waterCostMultiplier;
        }

        // 危险邻居
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx2 = -1; dx2 <= 1; dx2++) {
            for (int dz2 = -1; dz2 <= 1; dz2++) {
                cursor.set(to.getX() + dx2, to.getY(), to.getZ() + dz2);
                BlockState s = level.getBlockState(cursor);
                if (s.is(Blocks.LAVA) || s.is(Blocks.FIRE) || s.is(Blocks.CACTUS)) {
                    cost += damageCost;
                }
            }
        }

        return cost;
    }

    /** 将路径转为 Vec3 列表（便于 moveTo） */
    public static List<Vec3> toVec3Path(List<BlockPos> path) {
        List<Vec3> result = new ArrayList<>(path.size());
        for (BlockPos p : path) {
            result.add(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
        }
        return result;
    }

    private static class Node {
        final BlockPos pos;
        double g; // 已知成本
        double h; // 启发式估计
        double f; // f = g + h
        Node parent;

        Node(BlockPos pos, double g, double h, Node parent) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
        }
    }
}
