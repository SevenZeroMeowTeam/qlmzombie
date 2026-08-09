package com.qlm.zombie.craftingdead.item.gun;

import net.minecraft.world.item.Rarity;

/**
 * 附件物品通用接口：定义所有枪械附件必须实现的方法
 * 供枪械物品查询附件类型、属性修饰描述与品质稀有度
 * 使用方法：if (item instanceof IAttachmentItem attach) { attach.getSlotType(); }
 */
public interface IAttachmentItem {

    /**
     * 获取该附件对应的槽位类型
     * 决定该附件可安装到哪些枪械上（需要枪械 hasAttachmentSlot 返回 true）
     * @return SlotType 附件槽枚举
     */
    IGun.SlotType getSlotType();

    /**
     * 获取附件的属性修饰描述字符串（用于物品 Tooltip 显示）
     * 格式示例："+20% 射速"、"-15% 后坐力"、"+30 弹匣容量"
     * @return 修饰描述字符串（支持颜色代码 §a §7 等）
     */
    String getModifierString();

    /**
     * 获取附件的品质稀有度
     * UNCOMMON - 普通附件（绿），RARE - 稀有附件（蓝），EPIC - 史诗附件（紫）
     * @return Rarity 枚举
     */
    Rarity getQualityRarity();
}
