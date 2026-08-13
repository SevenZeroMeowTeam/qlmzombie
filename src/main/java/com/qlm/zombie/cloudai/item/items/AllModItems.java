package com.qlm.zombie.cloudai.item.items;

import com.qlm.zombie.cloudai.ai.AiEntityManager;
import com.qlm.zombie.cloudai.core.CloudAiConstants;
import com.qlm.zombie.cloudai.item.base.IAutoItem;
import com.qlm.zombie.cloudai.item.base.RegisterManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * CloudAI Follower 全部物品枚举
 * 使用方式：新增物品 = 只在此类加一个枚举条目（遵循 IAutoItem 接口）
 * 5 个预设物品:
 *   AI_CALLER       - 召唤 AI 追随者（右键召唤 FakePlayer）
 *   AI_RECOVER      - AI 恢复药水（恢复 AI HP / 清除负面）
 *   MODE_SWITCH     - 模式切换器（FOLLOW<->COMBAT<->GATHER<->GUARD）
 *   AI_SPEED_PILL   - AI 加速药丸（AI 临时加速 60 秒）
 *   AI_SHIELD       - AI 护盾符（AI 临时获得抗性提升 60 秒）
 */
public enum AllModItems implements IAutoItem {

    // ==================== AI_CALLER ====================
    AI_CALLER("ai_caller", Rarity.RARE) {
        @Override
        public Item.Properties buildProp() {
            return new Item.Properties().rarity(Rarity.RARE).stacksTo(1);
        }

        @Override
        public void addToTooltip(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
            list.add(Component.literal("§7右键召唤一名 CloudAI 追随者"));
            list.add(Component.literal("§7消耗该物品，AI 将绑定到召唤者"));
        }

        @Override
        public InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
            try {
                boolean ok = AiEntityManager.getInstance().spawnAiForPlayer(player);
                if (ok) {
                    ItemStack stack = player.getItemInHand(hand);
                    stack.shrink(1);
                    player.sendSystemMessage(Component.literal("§a[CloudAI] AI 追随者已召唤！"));
                    return InteractionResultHolder.consume(stack);
                } else {
                    player.sendSystemMessage(Component.literal("§c[CloudAI] 召唤失败，你已绑定 AI（最多 1 个）"));
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            } catch (Exception e) {
                player.sendSystemMessage(Component.literal("§c[CloudAI] 召唤异常: " + e.getMessage()));
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
        }
    },

    // ==================== AI_RECOVER ====================
    AI_RECOVER("ai_recover", Rarity.UNCOMMON) {
        @Override
        public Item.Properties buildProp() {
            return new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16).craftRemainder(Items.GLASS_BOTTLE);
        }

        @Override
        public UseAnim getUseAnim() { return UseAnim.DRINK; }

        @Override
        public int getUseDuration() { return 32; }

        @Override
        public void addToTooltip(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
            list.add(Component.literal("§7恢复附近 AI 追随者 50% HP"));
            list.add(Component.literal("§7并清除 AI 所有负面效果"));
        }

        @Override
        public InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public ItemStack onFinishUsingItem(ItemStack stack, Level level, Player player) {
            if (!level.isClientSide) {
                int healed = AiEntityManager.getInstance().healNearbyAiOf(player, 0.5f);
                player.sendSystemMessage(Component.literal("§a[CloudAI] 已恢复 " + healed + " 名 AI 的生命值"));
            }
            if (player.getAbilities().instabuild) return stack;
            ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
            stack.shrink(1);
            if (stack.isEmpty()) return empty;
            if (!player.getInventory().add(empty)) player.drop(empty, false);
            return stack;
        }
    },

    // ==================== MODE_SWITCH ====================
    MODE_SWITCH("mode_switch", Rarity.UNCOMMON) {
        @Override
        public Item.Properties buildProp() {
            return new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1);
        }

        @Override
        public void addToTooltip(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
            list.add(Component.literal("§7右键切换 AI 工作模式"));
            list.add(Component.literal("§7顺序: 跟随 → 战斗 → 采集 → 守卫"));
            list.add(Component.literal("§7当前模式可通过物品名显示"));
        }

        @Override
        public InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
            String next = AiEntityManager.getInstance().cycleModeFor(player);
            player.sendSystemMessage(Component.literal("§b[CloudAI] AI 模式已切换为: §e" + next));
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
    },

    // ==================== AI_SPEED_PILL ====================
    AI_SPEED_PILL("ai_speed_pill", Rarity.RARE) {
        @Override
        public Item.Properties buildProp() {
            return new Item.Properties().rarity(Rarity.RARE).stacksTo(16);
        }

        @Override
        public UseAnim getUseAnim() { return UseAnim.EAT; }

        @Override
        public int getUseDuration() { return 24; }

        @Override
        public void addToTooltip(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
            list.add(Component.literal("§7吃下为附近 AI 添加 60 秒移动加速 II"));
        }

        @Override
        public InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public ItemStack onFinishUsingItem(ItemStack stack, Level level, Player player) {
            if (!level.isClientSide) {
                List<LivingEntity> ais = AiEntityManager.getInstance().getNearbyAiOf(player, CloudAiConstants.GATHER_SEARCH_RADIUS);
                for (LivingEntity ai : ais) {
                    ai.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1));
                }
                player.sendSystemMessage(Component.literal("§a[CloudAI] 已为 " + ais.size() + " 名 AI 添加移动加速 II (60秒)"));
            }
            if (player.getAbilities().instabuild) return stack;
            stack.shrink(1);
            return stack;
        }
    },

    // ==================== AI_SHIELD ====================
    AI_SHIELD("ai_shield", Rarity.EPIC) {
        @Override
        public Item.Properties buildProp() {
            return new Item.Properties().rarity(Rarity.EPIC).stacksTo(8);
        }

        @Override
        public UseAnim getUseAnim() { return UseAnim.BOW; }

        @Override
        public int getUseDuration() { return 16; }

        @Override
        public void addToTooltip(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
            list.add(Component.literal("§7为附近 AI 添加 60 秒抗性提升 II"));
            list.add(Component.literal("§7（减伤 40%，免疫击退）"));
        }

        @Override
        public InteractionResultHolder<ItemStack> onUse(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public ItemStack onFinishUsingItem(ItemStack stack, Level level, Player player) {
            if (!level.isClientSide) {
                List<LivingEntity> ais = AiEntityManager.getInstance().getNearbyAiOf(player, CloudAiConstants.GATHER_SEARCH_RADIUS);
                for (LivingEntity ai : ais) {
                    ai.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 1));
                    ai.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0));
                }
                player.sendSystemMessage(Component.literal("§a[CloudAI] 已为 " + ais.size() + " 名 AI 添加抗性护盾 (60秒)"));
            }
            if (player.getAbilities().instabuild) return stack;
            stack.shrink(1);
            return stack;
        }
    };

    // 无效果食物属性占位（仅触发使用动画，不改变饱食度；若版本无对应 FoodProperties 方法则使用默认 Item.Properties）
    @SuppressWarnings("deprecation")
    private static final net.minecraft.world.item.Item.Properties FOOD_PROPS_FALLBACK =
            new net.minecraft.world.item.Item.Properties().stacksTo(16);

    // ============== 枚举基础字段 ==============
    private final String itemName;
    private final Rarity rarity;
    private final RegistryObject<Item> registryObject;

    AllModItems(String itemName, Rarity rarity) {
        this.itemName = itemName;
        this.rarity = rarity;
        // 通过 RegisterManager 统一注册，实际物品类使用 CloudAiBaseItem（匿名类代理 IAutoItem 方法）
        this.registryObject = RegisterManager.register(itemName, () -> new CloudAiBaseItem(this));
    }

    @Override
    public String getItemName() { return itemName; }

    /** 物品默认稀有度（供 buildProp 参考） */
    public Rarity getRarity() { return rarity; }

    /** 获取注册后的物品实例 */
    public Item getItem() { return registryObject.get(); }

    /** 获取 RegistryObject */
    public RegistryObject<Item> getRegistryObject() { return registryObject; }

    /**
     * 物品实现类：把 IAutoItem 的方法绑定到 Item 生命周期
     * AllModItems 枚举条目内的方法均由本类代理调用
     */
    private static final class CloudAiBaseItem extends Item {
        private final IAutoItem auto;

        CloudAiBaseItem(IAutoItem auto) {
            // 调用枚举实现的 buildProp
            super(auto.buildProp());
            this.auto = auto;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            auto.addToTooltip(stack, level, tooltip, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = auto.onUse(level, player, hand);
            if (r != null) return r;
            return super.use(level, player, hand);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (entity instanceof Player player) {
                ItemStack r = auto.onFinishUsingItem(stack, level, player);
                if (r != null) return r;
            }
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            UseAnim a = auto.getUseAnim();
            return a != null ? a : super.getUseAnimation(stack);
        }

        @Override
        public int getUseDuration(ItemStack stack) {
            int d = auto.getUseDuration();
            return d > 0 ? d : super.getUseDuration(stack);
        }
    }
}
