package com.kuronami.tempadtomapx.network;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.client.TtmxClient;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 SimpleChannel ベースのネットワーク層。
 * 1.21.1 NeoForge の payload 登録（RegisterPayloadHandlersEvent + StreamCodec）は
 * 1.20.1 に存在しないので、ここがローダー固有の作り直し部（mod-003 forge-1.20.1 実績方式）。
 *
 * <ul>
 *   <li>{@link NetworkRegistry#newSimpleChannel} でビルド</li>
 *   <li>handler signature: {@code BiConsumer<MSG, Supplier<NetworkEvent.Context>>}</li>
 *   <li>{@link SimpleChannel#send(PacketDistributor.PacketTarget, Object)}（target → msg の順）</li>
 * </ul>
 */
public final class TtmxNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ttmx.MODID, "main"),
            () -> PROTOCOL_VERSION,
            v -> true,   // accept any client version
            v -> true    // accept any server version
    );

    private TtmxNetwork() {
    }

    /** FMLCommonSetupEvent の enqueueWork から呼ぶ。 */
    public static void register() {
        CHANNEL.registerMessage(
                0,
                LocationsPacket.class,
                LocationsPacket::encode,
                LocationsPacket::decode,
                TtmxNetwork::handleOnClient,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    private static void handleOnClient(LocationsPacket packet, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TtmxClient.onLocationsFromServer(packet)));
        ctx.setPacketHandled(true);
    }

    /** 差分検出済みの全量スナップショットをこのプレイヤーへだけ送る。 */
    public static void sendLocations(ServerPlayer player, Map<UUID, PointSnapshot> state) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), LocationsPacket.of(state));
    }
}
