package com.qlm.zombie.thirst.foundation.network;

import com.qlm.zombie.thirst.foundation.network.message.DrinkByHandMessage;
import com.qlm.zombie.thirst.foundation.network.message.PlayerThirstSyncMessage;
import com.qlm.zombie.thirst.Thirst;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ThirstModPacketHandler
{
    private static final String PROTOCOL_VERSION = "0.1.2";

    /** 主通道 {@code qlmzombie:main}（qlmzombie 双端）。serverAcceptedVersions 用 acceptMissingOr：
     *  不强制要求客户端必须注册本通道，便于兼容仅注册 thirst:main 的外置 ThirstWasTaken 客户端。 */
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            Thirst.asResource("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals)
    );

    /**
     * 兼容通道 {@code thirst:main}（仅 DEDICATED_SERVER 初始化，qlmzombie 客户端不注册）。
     *
     * <p>与原版 Thirst-Mod（Thirst was Taken）的网络协议完全一致：通道名 thirst:main、
     * PROTOCOL_VERSION 0.1.2、消息 ID 0=PlayerThirstSyncMessage / 1=DrinkByHandMessage（字段结构相同）。
     * 服务器端注册后，仍装有外置 ThirstWasTaken JAR 的客户端（其 mod 上报 thirst:main）即可通过
     * 握手（否则报 "Connection closed - mismatched mod channel list" / 服务器缺少 Thirst?main），
     * 并能正常完成喝水请求与口渴值同步。
     *
     * <p>serverAcceptedVersions 同样用 acceptMissingOr：正确客户端（仅注册 qlmzombie:main）
     * 未注册此通道也能正常连接，不会被拒绝。
     *
     * <p><b>仅当外置 {@code thirst} 模组未加载时注册</b>：ThirstWasTaken 已加入部署白名单，
     * 服务器端会保留外置 JAR（它自己注册 thirst:main）。若此时本模组再注册同名通道，
     * Forge 会抛 "Channel 'thirst:main' is already registered" 导致加载失败。
     * 因此外置 thirst 在位时跳过兼容通道（由外置模组提供），缺失时才由本模组兜底注册。
     */
    public static SimpleChannel THIRST_CHANNEL; // 仅服务端非 null（且外置 thirst 未加载时）

    public static void init()
    {
        INSTANCE.registerMessage(0, PlayerThirstSyncMessage.class, PlayerThirstSyncMessage::encode, PlayerThirstSyncMessage::decode, PlayerThirstSyncMessage::handle);
        INSTANCE.registerMessage(1, DrinkByHandMessage.class, DrinkByHandMessage::encode, DrinkByHandMessage::decode, DrinkByHandMessage::handle);

        // 外置 ThirstWasTaken（modId=thirst）已加载时，它自己注册 thirst:main，跳过兼容通道避免重复
        boolean externalThirstLoaded = ModList.get().isLoaded("thirst");
        if (FMLEnvironment.dist.isDedicatedServer() && !externalThirstLoaded)
        {
            THIRST_CHANNEL = NetworkRegistry.newSimpleChannel(
                    ResourceLocation.fromNamespaceAndPath("thirst", "main"),
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals)
            );
            THIRST_CHANNEL.registerMessage(0, PlayerThirstSyncMessage.class, PlayerThirstSyncMessage::encode, PlayerThirstSyncMessage::decode, PlayerThirstSyncMessage::handle);
            THIRST_CHANNEL.registerMessage(1, DrinkByHandMessage.class, DrinkByHandMessage::encode, DrinkByHandMessage::decode, DrinkByHandMessage::handle);
        }
    }

    /**
     * 服务器端向玩家发送口渴同步消息，按玩家连接实际注册的通道选择发送通道：
     * <ul>
     *   <li>外置 ThirstWasTaken 客户端：注册了 {@code thirst:main} → 用 THIRST_CHANNEL 发送；</li>
     *   <li>qlmzombie 客户端：注册了 {@code qlmzombie:main} → 用 INSTANCE 发送。</li>
     * </ul>
     */
    public static void sendPlayerSync(ServerPlayer player, PlayerThirstSyncMessage message)
    {
        Connection connection = player.connection.connection;
        if (THIRST_CHANNEL != null && THIRST_CHANNEL.isRemotePresent(connection))
        {
            THIRST_CHANNEL.sendTo(message, connection, NetworkDirection.PLAY_TO_CLIENT);
        }
        else
        {
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }
}
