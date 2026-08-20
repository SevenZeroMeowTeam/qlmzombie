package com.qlm.zombie.item;

import com.qlm.zombie.QLMZombieMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 神话装备合成系统：
 *  - 使用「下界星核」（qlmzombie:mythic_core）作为合成核心（下界合金锭 + 钻石 + 金苹果合成）
 *  - 神话合成配方（KubeJS + datapack）：四角下界合金锭 + 三边钻石 + 中心装备 + 下中下界星核
 *  - 合成结果为对应神话装备（武器/工具/盔甲）
 *  - 合成产物直接写入神话品质 NBT，绕过常规品质 roll
 *  - 由于合成昂贵（含下界星核核心），理论上稀有但稳定（不需要 roll 概率）
 *
 * 由于原版合成表无法精确实现"低概率出神话"，本系统采用：
 *  - 监听 PlayerEvent.ItemCraftedEvent
 *  - 检测合成产物是否为本次注册的神话合成配方
 *  - 是则强制改写品质为神话并写入无限属性
 */
@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class MythicCraftHandler {

    /**
     * 合成完成时检测：若产物 NBT 中带 qlm_mythic_forced 标记（由 KubeJS 脚本写入或合成表 NBT 写入），
     * 则强制赋予神话品质。
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack stack = event.getCrafting();
        if (stack == null || stack.isEmpty()) return;

        // 已经是神话品质的产物不重复处理
        EquipmentQuality existing = EquipmentQuality.fromStack(stack);
        if (existing == EquipmentQuality.MYTHIC) return;

        // 检查 NBT 标记
        var tag = stack.getTag();
        if (tag == null || !tag.getBoolean("qlm_mythic_forced")) return;

        // 强制赋予神话品质
        RandomSource rnd = event.getEntity() != null
                ? event.getEntity().getRandom()
                : RandomSource.create();
        EquipmentQuality.MYTHIC.applyToStack(stack, rnd);

        // 神话武器/工具：直接设置无限攻击 NBT
        Item item = stack.getItem();
        if (item instanceof SwordItem
                || item instanceof DiggerItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem) {
            stack.getOrCreateTag().putDouble(EquipmentQuality.NBT_RANDOM_DMG, 99999.0);
        }

        // 镐子直接附加全部能力
        if (item instanceof PickaxeItem) {
            PickaxeAbility.addAbility(stack, PickaxeAbility.OBSIDIAN_BREAKER);
            PickaxeAbility.addAbility(stack, PickaxeAbility.BEDROCK_BREAKER);
            PickaxeAbility.addAbility(stack, PickaxeAbility.RANGE_11X11);
            stack.getOrCreateTag().putInt(EquipmentQuality.NBT_MINE_RANGE, 5);
        }

        // 显示名前缀
        stack.setHoverName(Component.empty()
                .append(EquipmentQuality.MYTHIC.getDisplayComponent())
                .append(Component.translatable(stack.getDescriptionId()))
                .withStyle(EquipmentQuality.MYTHIC.getFormatting()));

        // 移除标记
        stack.removeTagKey("qlm_mythic_forced");

        // 通知玩家
        if (event.getEntity() instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.empty()
                    .append(Component.literal("[神话降临] ").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal("你成功合成了神话级装备！").withStyle(ChatFormatting.LIGHT_PURPLE)));
            if (item instanceof net.minecraft.world.item.ArmorItem) {
                sp.sendSystemMessage(Component.empty()
                        .append(Component.literal("[神话降临] ").withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal("神话盔甲套装缺一不可：集齐 4 件全套，才能无视虚空伤害并解锁全套神话庇护！").withStyle(ChatFormatting.AQUA)));
            }
        }
    }
}
