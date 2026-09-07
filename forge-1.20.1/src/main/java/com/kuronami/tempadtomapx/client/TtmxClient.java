package com.kuronami.tempadtomapx.client;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.network.LocationsPacket;

import net.minecraft.client.Minecraft;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * クライアント側の配線（Forge 1.20.1）。このクラスはサーバー dist では決してロードされない
 * （{@link com.kuronami.tempadtomapx.Ttmx} の dist 分岐と、S2C handler の実行側からのみ参照される）。
 *
 * <p>役割: サーバー受信データの Xaero 反映を 10 tick ごとに試みるだけ。
 * 地点の検出はサーバー側（ServerLocationWatcher）が担う。</p>
 */
public final class TtmxClient {

    /** 未適用データの再試行間隔。Xaero HUD 初期化待ちの吸収が主目的。 */
    private static final int FLUSH_INTERVAL_TICKS = 10;

    private TtmxClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(TtmxClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(TtmxClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(TtmxClient::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SyncManager.resetSession();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncManager.resetSession();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
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

    /**
     * S2C 受信入口（TtmxNetwork の handler から main thread で呼ばれる）。
     * 受信した全量を SyncManager のあるべき状態として渡す。
     */
    public static void onLocationsFromServer(LocationsPacket packet) {
        SyncManager.onLocationsFromServer(packet.toMap());
    }
}
