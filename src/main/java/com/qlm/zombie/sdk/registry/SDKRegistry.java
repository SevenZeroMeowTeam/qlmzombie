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
 * QLM ModSDK — 注册表管理器
 * 统一管理自定义方块/物品/实体的注册，基于 Forge DeferredRegister。
 * ----------------------------------------------------------------------------
 */
package com.qlm.zombie.sdk.registry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDK 注册表。在 mod 加载早期调用 {@link #init(IEventBus)} 注册 Forge 事件总线，
 * 然后调用 {@link #registerBlock} / {@link #registerItem} / {@link #registerEntity}
 * 添加自定义内容；描述符缓存到 ConcurrentHashMap，Forge 在 RegisterEvent 阶段
 * 通过 DeferredRegister 自动批量注册。
 */
public final class SDKRegistry {

    /** SDK 自身的 mod id；用于 Forge 注册表命名空间 */
    public static final String SDK_MOD_ID = "qlmsdk";

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SDK_MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SDK_MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SDK_MOD_ID);

    private static final Map<String, CustomBlock> BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CustomItem> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CustomEntity> ENTITY_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, RegistryObject<Block>> BLOCK_RO = new ConcurrentHashMap<>();
    private static final Map<String, RegistryObject<Item>> ITEM_RO = new ConcurrentHashMap<>();
    private static final Map<String, RegistryObject<EntityType<?>>> ENTITY_RO = new ConcurrentHashMap<>();

    private static volatile boolean initialized = false;

    private SDKRegistry() {}

    /**
     * 在 mod 构造函数中调用，将 DeferredRegister 绑定到 mod 事件总线。
     */
    public static synchronized void init(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        initialized = true;
    }

    // ====================================================================
    // Block
    // ====================================================================

    /**
     * 注册自定义方块（延迟注册到 Forge DeferredRegister）。
     * 同时会自动创建对应的 BlockItem。
     */
    public static void registerBlock(String id, CustomBlock block) {
        if (id == null || block == null) return;
        BLOCK_CACHE.put(id, block);
        BLOCK_RO.computeIfAbsent(id, key -> BLOCKS.register(key, () -> createForgeBlock(block)));
        // 自动注册对应 BlockItem，便于创造栏 / 给予命令使用
        ITEM_RO.computeIfAbsent(id, key -> ITEMS.register(key, () -> new BlockItem(
                BLOCK_RO.get(key).get(), new Item.Properties())));
    }

    public static CustomBlock getBlock(String id) {
        return BLOCK_CACHE.get(id);
    }

    public static List<CustomBlock> getAllBlocks() {
        return Collections.unmodifiableList(new ArrayList<>(BLOCK_CACHE.values()));
    }

    /** 获取已注册方块的 RegistryObject（可能尚未 resolve）。 */
    public static RegistryObject<Block> getBlockRegistryObject(String id) {
        return BLOCK_RO.get(id);
    }

    // ====================================================================
    // Item
    // ====================================================================

    /**
     * 注册自定义物品（延迟注册到 Forge DeferredRegister）。
     */
    public static void registerItem(String id, CustomItem item) {
        if (id == null || item == null) return;
        ITEM_CACHE.put(id, item);
        ITEM_RO.computeIfAbsent(id, key -> ITEMS.register(key, () -> new Item(item.toItemProperties())));
    }

    public static CustomItem getItem(String id) {
        return ITEM_CACHE.get(id);
    }

    public static List<CustomItem> getAllItems() {
        return Collections.unmodifiableList(new ArrayList<>(ITEM_CACHE.values()));
    }

    public static RegistryObject<Item> getItemRegistryObject(String id) {
        return ITEM_RO.get(id);
    }

    // ====================================================================
    // Entity
    // ====================================================================

    /**
     * 注册自定义实体描述符（仅缓存，不创建 Forge EntityType）。
     * 由于 Forge EntityType 需要具体 Entity 工厂，调用方应使用
     * {@link #registerEntity(String, CustomEntity, EntityType.EntityFactory)} 完成实际注册。
     */
    public static void registerEntity(String id, CustomEntity entity) {
        if (id == null || entity == null) return;
        ENTITY_CACHE.put(id, entity);
    }

    /**
     * 注册自定义实体（缓存描述符 + 实际注册到 Forge DeferredRegister）。
     *
     * @param id      实体 id
     * @param entity  实体描述符
     * @param factory Forge EntityType 工厂
     * @param <T>     实体类型
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void registerEntity(String id, CustomEntity entity, EntityType.EntityFactory<T> factory) {
        if (id == null || entity == null || factory == null) return;
        ENTITY_CACHE.put(id, entity);
        ENTITY_RO.computeIfAbsent(id, key -> {
            return (RegistryObject<EntityType<?>>) (RegistryObject<?>) ENTITY_TYPES.register(key,
                    () -> EntityType.Builder.of(factory, entity.getEntityType())
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(32)
                            .updateInterval(2)
                            .build(key));
        });
    }

    public static CustomEntity getEntity(String id) {
        return ENTITY_CACHE.get(id);
    }

    public static List<CustomEntity> getAllEntities() {
        return Collections.unmodifiableList(new ArrayList<>(ENTITY_CACHE.values()));
    }

    public static RegistryObject<EntityType<?>> getEntityRegistryObject(String id) {
        return ENTITY_RO.get(id);
    }

    // ====================================================================
    // Forge Block 构建
    // ====================================================================

    private static Block createForgeBlock(CustomBlock block) {
        // 1.20.1 已移除 Material 类，统一用 Block.Properties.of() 无参版本
        Block.Properties props = Block.Properties.of()
                .strength(block.getHardness(), block.getResistance())
                .sound(block.getSoundType());
        if (block.getLightLevel() > 0.0f) {
            props.lightLevel(state -> (int) block.getLightLevel());
        }
        if (!block.isCollidable()) {
            props.noCollission();
        }
        if (block.isTransparent()) {
            props.noOcclusion();
        }
        return new Block(props);
    }
}
