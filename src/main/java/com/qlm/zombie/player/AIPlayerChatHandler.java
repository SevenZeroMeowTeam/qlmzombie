package com.qlm.zombie.player;

import com.qlm.zombie.ai.Player2APIService;
import com.qlm.zombie.ai.Player2APIService.AIResponse;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.QLMZombieMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = QLMZombieMod.MOD_ID)
public class AIPlayerChatHandler {

    private static final ConcurrentHashMap<String, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000;

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().getString();
        Level level = player.level();

        FakePlayerEntity targetAI = AISelectionHandler.findSelectedAI(player, player.blockPosition(), 15);
        if (targetAI == null) {
            targetAI = findNearestAIPlayer(level, player.blockPosition(), 15);
        }
        if (targetAI == null) return;

        long lastTime = COOLDOWN.getOrDefault(player.getName().getString(), 0L);
        if (System.currentTimeMillis() - lastTime < COOLDOWN_MS) {
            return;
        }
        COOLDOWN.put(player.getName().getString(), System.currentTimeMillis());

        if (!targetAI.isTamed()) {
            player.sendSystemMessage(Component.literal("§c[AI玩家] §7" + targetAI.getCustomNameStr() + " 还未被驯服，无法交流"));
            return;
        }

        String aiName = targetAI.getCustomNameStr();

        if (message.toLowerCase().startsWith(aiName.toLowerCase()) ||
            message.contains("@" + aiName.toLowerCase()) ||
            message.contains("@" + aiName)) {

            String task = message;
            if (task.toLowerCase().startsWith(aiName.toLowerCase())) {
                task = task.substring(aiName.length()).trim();
            } else if (task.contains("@" + aiName.toLowerCase())) {
                task = task.replace("@" + aiName, "").replace("@" + aiName.toLowerCase(), "").trim();
            }

            processPlayerCommand(targetAI, player, task);
        }
    }

    private static void processPlayerCommand(FakePlayerEntity ai, Player player, String command) {
        if (Player2APIService.isPlayer2Available()) {
            String characterId = ai.getCustomNameStr();
            Player2APIService.sendTask(characterId, command).thenAccept(response -> {
                if (response != null) {
                    AIResponse aiResponse;
                    if (response.equals(command)) {
                        aiResponse = Player2APIService.parseSimpleResponse(command);
                    } else {
                        aiResponse = Player2APIService.parseAIResponse(response);
                    }
                    executeAIResponse(ai, player, aiResponse);
                } else {
                    AIResponse fallbackResponse = Player2APIService.parseSimpleResponse(command);
                    executeAIResponse(ai, player, fallbackResponse);
                }
            }).exceptionally(ex -> {
                QLMZombieMod.LOGGER.warn("Player2 task execution failed: {}", ex.getMessage());
                AIResponse fallbackResponse = Player2APIService.parseSimpleResponse(command);
                executeAIResponse(ai, player, fallbackResponse);
                return null;
            });
        } else {
            AIResponse aiResponse = Player2APIService.parseSimpleResponse(command);
            executeAIResponse(ai, player, aiResponse);
        }
    }

    private static void executeAIResponse(FakePlayerEntity ai, Player player, AIResponse response) {
        String aiName = ai.getCustomNameStr();

        if (response.message() != null && !response.message().isEmpty()) {
            player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f" + response.message()));
        }

        switch (response.action()) {
            case "follow":
                ai.setSitting(false);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我会跟着你"));
                break;

            case "wait":
                ai.setSitting(true);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我在这里等待"));
                break;

            case "come":
                ai.setSitting(false);
                ai.getNavigation().moveTo(player, 1.0D);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我来了"));
                break;

            case "gather":
                handleGatherCommand(ai, player, response.targetItem(), response.targetCount());
                break;

            case "craft":
                handleCraftCommand(ai, player, response.targetItem(), response.targetCount());
                break;

            case "give":
                handleGiveCommand(ai, player, response.targetItem(), response.targetCount());
                break;

            case "attack":
                ai.setSitting(false);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我来攻击敌人"));
                break;

            case "mine":
                ai.setSitting(false);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我来挖矿，完成后会自动跟随你"));
                break;

            case "chop":
                ai.setSitting(false);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我来砍树，完成后会自动跟随你"));
                break;

            case "build":
            case "house":
                ai.setSitting(false);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我来搭建房子，就地取材，完成后会自动跟随你"));
                break;

            case "stop":
                ai.setSitting(true);
                ai.getNavigation().stop();
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f好的，我停止工作"));
                break;

            case "chat":
                if (response.message() != null) {
                    player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f" + response.message()));
                } else {
                    player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f嗯，我明白了"));
                }
                break;

            default:
                player.sendSystemMessage(Component.literal("§6[" + aiName + "] §f我不太明白你的意思"));
                break;
        }
    }

    private static void handleGatherCommand(FakePlayerEntity ai, Player player, String item, int count) {
        ai.setSitting(false);

        String itemName = item != null ? item : "资源";
        player.sendSystemMessage(Component.literal("§a[" + ai.getCustomNameStr() + "] §f好的，我去收集 " + count + " 个" + itemName));
    }

    private static void handleCraftCommand(FakePlayerEntity ai, Player player, String item, int count) {
        ai.setSitting(false);

        String itemName = item != null ? item : "物品";
        player.sendSystemMessage(Component.literal("§a[" + ai.getCustomNameStr() + "] §f好的，我来制作 " + count + " 个" + itemName));
    }

    private static void handleGiveCommand(FakePlayerEntity ai, Player player, String itemName, int count) {
        String aiName = ai.getCustomNameStr();

        if (itemName == null || itemName.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[" + aiName + "] §7请告诉我你想要什么物品"));
            return;
        }

        int foundCount = 0;
        ItemStack foundStack = ItemStack.EMPTY;
        int foundSlot = -1;

        for (int i = 0; i < ai.getInventory().getContainerSize(); i++) {
            ItemStack stack = ai.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String stackName = stack.getItem().getDescriptionId().toLowerCase();
                String registryName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();

                if (stackName.contains(itemName.toLowerCase()) ||
                    registryName.contains(itemName.toLowerCase()) ||
                    itemName.toLowerCase().contains(registryName.split(":")[1])) {

                    foundStack = stack;
                    foundSlot = i;
                    foundCount += stack.getCount();
                    break;
                }
            }
        }

        if (foundCount > 0) {
            int transferCount = Math.min(count, foundCount);
            ItemStack transferStack = foundStack.copy();
            transferStack.setCount(transferCount);

            if (player.getInventory().add(transferStack)) {
                foundStack.shrink(transferCount);
                ai.getInventory().setItem(foundSlot, foundStack);
                player.sendSystemMessage(Component.literal("§a[" + aiName + "] §f给你 " + transferCount + " 个" + itemName));
            } else {
                player.sendSystemMessage(Component.literal("§c[" + aiName + "] §7你的背包满了"));
            }
        } else {
            player.sendSystemMessage(Component.literal("§c[" + aiName + "] §7我没有" + itemName));
        }
    }

    private static FakePlayerEntity findNearestAIPlayer(Level level, BlockPos pos, double range) {
        FakePlayerEntity nearest = null;
        double nearestDist = range * range;

        java.util.List<FakePlayerEntity> aiPlayers = level.getEntitiesOfClass(FakePlayerEntity.class, 
            new net.minecraft.world.phys.AABB(
                pos.getX() - range, pos.getY() - range, pos.getZ() - range,
                pos.getX() + range, pos.getY() + range, pos.getZ() + range
            ));

        for (FakePlayerEntity ai : aiPlayers) {
            double dist = ai.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = ai;
            }
        }
        return nearest;
    }
}