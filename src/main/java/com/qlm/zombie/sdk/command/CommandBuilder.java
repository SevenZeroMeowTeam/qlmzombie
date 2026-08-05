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
 * QLM ModSDK — 命令构建器（链式 API 创建 Forge 命令）
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 链式命令构建器。基于 Brigadier + Forge {@code Commands} API。
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * CommandBuilder.create("mycmd")
 *     .then("target", ArgumentType.PLAYER)
 *     .then("count", ArgumentType.INTEGER)
 *     .executes(ctx -> {
 *         // ctx.getSource(), EntityArgument.getPlayer(ctx, "target"), ...
 *         return 1;
 *     })
 *     .register();
 * }</pre>
 *
 * <p>注意：{@link #register()} 需要 SDK 已通过
 * {@link #bindDispatcher(CommandDispatcher)} 绑定 Forge 命令分发器
 * （通常在 {@code RegisterCommandsEvent} 中完成）。</p>
 */
public final class CommandBuilder {

    /** SDK 支持的参数类型 */
    public enum ArgumentType {
        STRING,
        INTEGER,
        DOUBLE,
        BOOLEAN,
        PLAYER,
        POSITION,
        BLOCK_POS,
        ITEM_STACK
    }

    /** 简化的执行器接口（不抛出受检异常） */
    @FunctionalInterface
    public interface Executor {
        int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static volatile CommandDispatcher<CommandSourceStack> boundDispatcher = null;

    private final String name;
    private final List<ArgNode> args = new ArrayList<>();
    private Executor executor;
    private Predicate<CommandSourceStack> requires = source -> true;

    private CommandBuilder(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("命令名不能为空");
        }
        this.name = name;
    }

    /**
     * 创建命令构建器。
     */
    public static CommandBuilder create(String name) {
        return new CommandBuilder(name);
    }

    /**
     * 绑定 Forge 命令分发器。在 {@code RegisterCommandsEvent} 中调用。
     */
    public static void bindDispatcher(CommandDispatcher<CommandSourceStack> dispatcher) {
        boundDispatcher = dispatcher;
    }

    /**
     * 添加参数。
     */
    public CommandBuilder then(String argName, ArgumentType type) {
        if (argName == null || argName.isEmpty()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        args.add(new ArgNode(argName, type));
        return this;
    }

    /**
     * 设置执行逻辑。
     */
    public CommandBuilder executes(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * 设置权限要求（返回 false 则命令不可见）。覆盖之前的 {@link #permission(int)}。
     */
    public CommandBuilder requires(Predicate<CommandSourceStack> requires) {
        this.requires = requires;
        return this;
    }

    /**
     * 设置权限等级（0=所有人，2=OP，4=控制台）。覆盖之前的 {@link #requires(Predicate)}。
     */
    public CommandBuilder permission(int level) {
        this.requires = source -> source.hasPermission(level);
        return this;
    }

    /**
     * 注册到 Forge 命令分发器。需要先调用 {@link #bindDispatcher}。
     *
     * @return 注册的命令节点，若分发器未绑定则返回 {@code null}
     */
    public LiteralCommandNode<CommandSourceStack> register() {
        CommandDispatcher<CommandSourceStack> dispatcher = boundDispatcher;
        if (dispatcher == null) {
            System.err.println("[QLM ModSDK] 命令 " + name + " 注册失败：分发器未绑定");
            return null;
        }
        if (executor == null) {
            System.err.println("[QLM ModSDK] 命令 " + name + " 注册失败：未设置 executes()");
            return null;
        }

        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name)
                .requires(requires);

        if (args.isEmpty()) {
            return dispatcher.register(literal.executes(ctx -> executor.execute(ctx)));
        }

        // 从最后一个参数往前构建嵌套链
        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            ArgNode node = args.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> arg =
                    Commands.argument(node.name, toBrigadierType(node.type));
            if (tail != null) {
                arg.then(tail);
            }
            tail = arg;
        }

        final Executor exec = executor;
        tail.executes(ctx -> exec.execute(ctx));
        literal.then(tail);
        return dispatcher.register(literal);
    }

    private static com.mojang.brigadier.arguments.ArgumentType<?> toBrigadierType(ArgumentType type) {
        switch (type) {
            case STRING:
                return com.mojang.brigadier.arguments.StringArgumentType.string();
            case INTEGER:
                return com.mojang.brigadier.arguments.IntegerArgumentType.integer();
            case DOUBLE:
                return com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg();
            case BOOLEAN:
                return com.mojang.brigadier.arguments.BoolArgumentType.bool();
            case PLAYER:
                return EntityArgument.player();
            case POSITION:
                return Vec3Argument.vec3();
            case BLOCK_POS:
                return BlockPosArgument.blockPos();
            case ITEM_STACK:
                // 1.20.1 中 ItemArgument.item() 需要 CommandBuildContext 参数，
                // SDK 静态上下文无法获取；调用方应使用 STRING 类型并通过 ItemArgument.getItem 解析，
                // 或在调用方直接使用 Brigadier API 注册。
                throw new UnsupportedOperationException(
                        "ITEM_STACK 参数类型在 1.20.1 中需要 CommandBuildContext，请改用 STRING 或自行注册");
            default:
                throw new IllegalArgumentException("未知参数类型: " + type);
        }
    }

    private static final class ArgNode {
        final String name;
        final ArgumentType type;

        ArgNode(String name, ArgumentType type) {
            this.name = name;
            this.type = type;
        }
    }
}
