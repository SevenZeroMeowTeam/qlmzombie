package com.qlm.zombie.craftingdead.item.gun;

/**
 * 弹药类型枚举：定义 Crafting Dead 风格枪械系统的所有标准弹药口径
 * 每个弹药类型包含：基础伤害值、最大堆叠数量
 * 使用方法：AmmoType._556x45.damage 获取该弹药单发伤害
 */
public enum AmmoType {

    /** 5.56x45mm 北约标准步枪弹（M4A1等） */
    _556x45("5.56x45mm NATO", 6.0F, 64),

    /** 7.62x39mm 中间威力步枪弹（AK47等） */
    _762x39("7.62x39mm Soviet", 7.5F, 64),

    /** 9x19mm 帕拉贝鲁姆手枪弹（冲锋枪/手枪） */
    _9x19("9x19mm Parabellum", 4.0F, 128),

    /** .45 ACP 柯尔特自动手枪弹（沙漠之鹰等） */
    _45_ACP(".45 ACP", 9.0F, 48),

    /** .50 BMG 勃朗宁重机枪弹（反器材狙击） */
    _50_BMG(".50 BMG", 20.0F, 16),

    /** 12号霰弹（霰弹枪） */
    _12_Gauge("12 Gauge", 5.0F, 32),

    /** .338 拉普阿马格南狙击弹（远程高精度狙击） */
    _338_Lapua(".338 Lapua", 16.0F, 20);

    /** 弹药显示名称（中文描述用） */
    public final String displayName;

    /** 每发弹药的基础伤害值 */
    public final float damage;

    /** 物品栏中该弹药的最大堆叠数量 */
    public final int stackSize;

    /**
     * 弹药类型构造函数
     * @param displayName  弹药显示名称
     * @param damage       单发基础伤害
     * @param stackSize    最大堆叠数
     */
    AmmoType(String displayName, float damage, int stackSize) {
        this.displayName = displayName;
        this.damage = damage;
        this.stackSize = stackSize;
    }
}
