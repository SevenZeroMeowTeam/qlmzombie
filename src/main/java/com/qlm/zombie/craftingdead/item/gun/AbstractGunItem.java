package com.qlm.zombie.craftingdead.item.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 枪械物品抽象基类：实现 IGun 接口，所有具体枪械继承此类
 * 封装枪械基础属性、附件槽配置、Tooltip 显示与右键手持交互
 * 子类构造函数直接 super(参数) 传入属性即可，无需额外逻辑
 * 注意：本类不实现真实射击物理，留待后续接入 TaCZ 等第三方枪模组
 */
public abstract class AbstractGunItem extends Item implements IGun {

    /** 该枪械使用的弹药类型 */
    protected final AmmoType ammoType;

    /** 射击间隔 tick 数 */
    protected final int fireRateTicks;

    /** 枪械单发基础伤害 */
    protected final float damage;

    /** 标准弹匣容量 */
    protected final int magazineSize;

    /** 弹道散布系数（0~1） */
    protected final float spread;

    /** 该枪械允许装备的附件槽位集合 */
    protected final EnumSet<SlotType> allowedSlots;

    /**
     * 枪械物品构造函数
     * @param ammoType        弹药类型
     * @param fireRateTicks   射击间隔 tick
     * @param damage          基础伤害
     * @param magazineSize    弹匣容量
     * @param spread          散布系数
     * @param allowedSlots    允许的附件槽集合
     * @param rarity          物品稀有度（RARE / EPIC）
     */
    protected AbstractGunItem(AmmoType ammoType, int fireRateTicks, float damage,
                              int magazineSize, float spread,
                              EnumSet<SlotType> allowedSlots, Rarity rarity) {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(rarity));
        this.ammoType = ammoType;
        this.fireRateTicks = fireRateTicks;
        this.damage = damage;
        this.magazineSize = magazineSize;
        this.spread = spread;
        this.allowedSlots = allowedSlots;
    }

    // ==================== IGun 接口实现 ====================

    @Override
    public AmmoType getAmmoType() {
        return ammoType;
    }

    @Override
    public int getFireRateTicks() {
        return fireRateTicks;
    }

    @Override
    public float getDamage() {
        return damage;
    }

    @Override
    public int getMagazineSize() {
        return magazineSize;
    }

    @Override
    public float getSpread() {
        return spread;
    }

    @Override
    public boolean hasAttachmentSlot(SlotType slot) {
        return allowedSlots.contains(slot);
    }

    // ==================== 物品交互 ====================

    /**
     * 右键手持交互：服务端打印就绪提示（留待外部 AI/Bot 系统接管射击）
     * 不消耗物品、不真实射击，仅作为基础交互占位
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // 服务端向玩家发送聊天消息，提示枪械已就绪
            player.displayClientMessage(
                    Component.literal(String.format(
                            "[QLM CD] 枪械 %s 已就绪（需结合外部 AI/Bot 射击）",
                            this.getClass().getSimpleName().replace("GunItem", ""))),
                    true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ==================== Tooltip 显示 ====================

    /**
     * 物品悬停提示：显示弹药类型、伤害、射速、弹匣、散布、允许附件列表
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 基础属性信息
        tooltip.add(Component.literal("§7弹药类型：§e" + ammoType.displayName)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7伤害：§c" + damage + " §8(+弹药 " + ammoType.damage + ")")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7射速：§b每 " + fireRateTicks + " tick / 约 "
                        + String.format("%.1f", 20.0F / fireRateTicks) + " 发/秒")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7弹匣：§a" + magazineSize + " 发")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("§7散布：§d" + String.format("%.2f", spread))
                .withStyle(ChatFormatting.GRAY));

        // 分隔线
        tooltip.add(Component.literal(""));

        // 允许的附件槽列表
        tooltip.add(Component.literal("§7允许附件槽：").withStyle(ChatFormatting.GRAY));
        for (SlotType slot : SlotType.values()) {
            if (allowedSlots.contains(slot)) {
                tooltip.add(Component.literal("  §a✓ " + getSlotDisplayName(slot))
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /**
     * 将附件槽枚举转换为中文显示名
     * @param slot 槽位类型
     * @return 中文名称
     */
    private String getSlotDisplayName(SlotType slot) {
        return switch (slot) {
            case SIGHT    -> "瞄准镜";
            case GRIP     -> "握把";
            case BARREL   -> "枪管";
            case BIPOD    -> "脚架";
            case MAGAZINE -> "弹匣";
            case LASER    -> "激光指示器";
        };
    }
}
