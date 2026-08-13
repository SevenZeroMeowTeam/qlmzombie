package com.qlm.zombie.cloudai.item.base;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 物品注册管理器
 * - 统一维护 DeferredRegister<Item>
 * - getVanillaItem(name): 获取原版物品，找不到返回 Items.AIR
 * - getModItem(modId, name): 获取其他模组物品，找不到返回 Items.AIR（避免 NPE）
 */
public final class RegisterManager {

    private RegisterManager() {}

    /** CloudAI 模块物品的 DeferredRegister（命名空间复用 qlmzombie） */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID);

    /** CloudAI 模块创意标签的 DeferredRegister（若需独立标签） */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, QLMZombieMod.MOD_ID);

    /** 已注册的 CloudAI 物品缓存（name -> RegistryObject） */
    private static final Map<String, RegistryObject<Item>> REGISTERED = new HashMap<>();

    /**
     * 注册一个物品
     * @param name   注册名（蛇形，如 ai_caller）
     * @param itemSupplier 物品构造器
     * @return RegistryObject<Item>
     */
    public static RegistryObject<Item> register(String name, Supplier<? extends Item> itemSupplier) {
        RegistryObject<Item> obj = ITEMS.register(name, itemSupplier);
        REGISTERED.put(name, obj);
        return obj;
    }

    /** 根据注册名获取 CloudAI 已注册物品（找不到返回 Items.AIR） */
    public static Item getCloudAiItem(String name) {
        RegistryObject<Item> obj = REGISTERED.get(name);
        if (obj == null || !obj.isPresent()) return Items.AIR;
        return obj.get();
    }

    /**
     * 获取原版物品（找不到返回 Items.AIR）
     */
    public static Item getVanillaItem(String name) {
        if (name == null || name.isEmpty()) return Items.AIR;
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath("minecraft", name);
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item != null ? item : Items.AIR;
    }

    /**
     * 获取第三方模组物品（找不到返回 Items.AIR，避免 NPE）
     * @param modId 其他模组的命名空间，如 tacz, spartanweaponry
     * @param name  物品注册名
     */
    public static Item getModItem(String modId, String name) {
        if (modId == null || name == null || modId.isEmpty() || name.isEmpty()) return Items.AIR;
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(modId, name);
        if (!ForgeRegistries.ITEMS.containsKey(key)) return Items.AIR;
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item != null ? item : Items.AIR;
    }

    /** 将 DeferredRegister 绑定到 ModEventBus（由主类在构造时调用） */
    public static void bind(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
