package com.kuronami.tempadtomapx.client;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.network.LocationsPacket;
import com.kuronami.tempadtomapx.network.TtmxNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

/**
 * クライアント側の配線（Fabric 1.20.1）。このクラスは client entrypoint からだけ
 * ロードされる＝dedicated server では決してクラスロードされない。
 *
 * <p>役割: S2C 受信（netty スレッド→ main thread へ hop）と、
 * Xaero 反映を 10 tick ごとに試みること。地点の検出はサーバー側が担う。</p>
 */
public final class TtmxClient implements ClientModInitializer {

    /** 未適用データの再試行間隔。Xaero HUD 初期化待ちの吸収が主目的。 */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    @Override
    public void onInitializeClient() {
        // Fabric 1.20.1 の raw buf 受信。バッファはネットワークスレッドで使い切ってから
        // main thread へ渡す（buf 自体は持ち越せない）
        ClientPlayNetworking.registerGlobalReceiver(TtmxNetworking.LOCATIONS_ID,
                (mc, handler, buf, responseSender) -> {
                    var entries = LocationsPacket.read(buf);
                    mc.execute(() -> SyncManager.onLocationsFromServer(LocationsPacket.toMap(entries)));
                });

        ClientTickEvents.END_CLIENT_TICK.register(TtmxClient::onEndClientTick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> SyncManager.resetSession());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> SyncManager.resetSession());
    }

    private static void onEndClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if ((mc.player.tickCount % FLUSH_INTERVAL_TICKS) != 0) {
            return;
        }
        try {
            SyncManager.flushPending();
        } catch (Throwable t) {
            Ttmx.LOGGER.warn("Tempad to Map sync cycle failed: {}", t.toString());
        }
    }
}
