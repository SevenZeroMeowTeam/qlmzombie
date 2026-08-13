package com.qlm.zombie.craftingdead.item.gun;

/**
 * 枪械通用接口：定义所有枪械必须实现的属性与附件槽查询方法
 * 供外部 AI/Bot 系统（如 TaCZ 模组接入）查询枪械基础属性
 * 使用方法：if (item instanceof IGun gun) { gun.getFireRateTicks(); }
 */
public interface IGun {

    /**
     * 附件槽类型枚举：定义枪械可装备的附件位置
     * SIGHT    - 瞄准镜槽（红点/全息/高倍镜）
     * GRIP     - 握把槽（垂直握把/转角握把）
     * BARREL   - 枪管槽（消音器/补偿器/加长枪管）
     * BIPOD    - 脚架槽（两脚架）
     * MAGAZINE - 弹匣槽（标准/扩容/弹鼓）
     * LASER    - 激光指示器槽
     */
    enum SlotType {
        SIGHT,
        GRIP,
        BARREL,
        BIPOD,
        MAGAZINE,
        LASER
    }

    /**
     * 获取该枪械使用的弹药类型
     * @return AmmoType 弹药枚举
     */
    AmmoType getAmmoType();

    /**
     * 获取射击间隔（tick），数值越小射速越快
     * 20 tick = 1秒，6 tick ≈ 3.33 发/秒
     * @return 射击冷却 tick 数
     */
    int getFireRateTicks();

    /**
     * 获取枪械单发基础伤害（与弹药伤害叠加计算）
     * @return 基础伤害值
     */
    float getDamage();

    /**
     * 获取弹匣容量（标准弹匣子弹数）
     * @return 弹匣容量
     */
    int getMagazineSize();

    /**
     * 获取弹道散布系数（0~1），数值越大散布越大
     * 站立射击基准散布，蹲下/瞄准时可外部调整
     * @return 散布系数
     */
    float getSpread();

    /**
     * 查询该枪械是否允许装备指定类型的附件
     * 用于判断附件物品是否可安装到此枪上
     * @param slot 附件槽类型
     * @return true 表示允许装备该槽位附件
     */
    boolean hasAttachmentSlot(SlotType slot);
}
