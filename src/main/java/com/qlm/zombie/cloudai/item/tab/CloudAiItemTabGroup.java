package com.qlm.zombie.cloudai.item.tab;

import com.qlm.zombie.cloudai.item.base.RegisterManager;
import com.qlm.zombie.cloudai.item.items.AllModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * CloudAI Follower 独立创意标签
 * 图标使用 AI_CALLER 物品
 * 自动聚合 AllModItems 中所有已注册物品
 */
public final class CloudAiItemTabGroup {

    private CloudAiItemTabGroup() {}

    public static final RegistryObject<CreativeModeTab> CLOUDAI_TAB = RegisterManager.TABS.register("cloudai_items", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.qlmzombie.cloudai_items"))
                    // 图标: AI_CALLER 物品
                    .icon(() -> new ItemStack(AllModItems.AI_CALLER.getItem()))
                    .displayItems((parameters, output) -> {
                        // 遍历 AllModItems 枚举，自动聚合到创意标签
                        for (AllModItems item : AllModItems.values()) {
                            output.accept(item.getItem());
                        }
                    })
                    .build()
    );

    /** 便捷获取：空 ItemStack 用作图标 fallback */
    public static ItemStack getIconStack() {
        try {
            return new ItemStack(AllModItems.AI_CALLER.getItem());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
