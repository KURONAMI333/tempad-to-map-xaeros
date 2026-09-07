package com.kuronami.tempadtomapx.network;

import com.kuronami.tempadtomapx.client.TtmxClient;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * payload の登録（MOD バス・両 dist）とクライアント受信ハンドラ。
 *
 * <p>NeoForge 1.20.4 の登録 API は 1.21.1 と名前も形も違う（javap 実証）:
 * イベントが {@link RegisterPayloadHandlerEvent}、registrar へ渡すのは
 * {@code ResourceLocation} + {@code FriendlyByteBuf.Reader}（{@code StreamCodec} 不要）、
 * S2C 専用は direction-aware builder の {@code .client(...)} で表現する。</p>
 */
public final class TtmxNetwork {

    private TtmxNetwork() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(TtmxNetwork::onRegisterPayloadHandler);
    }

    private static void onRegisterPayloadHandler(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar("1");
        registrar.play(TempadSyncPayload.ID, TempadSyncPayload::read,
                builder -> builder.client(TtmxNetwork::handleOnClient));
    }

    private static void handleOnClient(TempadSyncPayload payload, PlayPayloadContext context) {
        // ハンドラはネットワークスレッドで来るので main thread へ hop してから
        // クライアント反映へ渡す（TtmxClient は client dist でしかロードされない）
        context.workHandler().execute(() ->
                TtmxClient.onLocationsFromServer(payload));
    }

    /** 差分検出済みの全量スナップショットをこのプレイヤーへだけ送る。 */
    public static void sendLocations(ServerPlayer player, Map<UUID, PointSnapshot> state) {
        PacketDistributor.PLAYER.with(player).send(TempadSyncPayload.of(state));
    }
}
