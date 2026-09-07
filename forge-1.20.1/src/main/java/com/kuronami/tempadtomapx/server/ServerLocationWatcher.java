package com.kuronami.tempadtomapx.server;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.network.TtmxNetwork;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の監視ループ（Forge 1.20.1 版）。全 online プレイヤーを 30 tick ごとに
 * 見て、「自分の地点」スナップショットに変化があればそのプレイヤーへだけ送る。
 * ログイン直後にも必ず 1 回送る。ロジックは 076 本体と同一で、イベント差し替えのみ
 * （TickEvent の phase 確認と ServerLifecycleHooks 経由の server 解決が 1.20.1 流）。
 *
 * <p>これらのイベントはサーバー dist / 統合サーバーの tick でしか発火しない。クライアント dist
 * 単独ではハンドラが走らず Tempad 関連クラスもロードされない。</p>
 */
public final class ServerLocationWatcher {

    /** 20〜40 tick 帯の中間。地点変更は即座には要らないので軽量優先。 */
    private static final int SYNC_INTERVAL_TICKS = 30;

    /** playerId → 最後に送った状態。 */
    private static final Map<UUID, Map<UUID, PointSnapshot>> LAST_SENT = new ConcurrentHashMap<>();

    private ServerLocationWatcher() {
    }

    /** MinecraftForge.EVENT_BUS.register(this.class) で登録される（@SubscribeEvent）。 */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncIfChanged(player);
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncIfChanged(player);
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SENT.remove(player.getUUID());
        }
    }

    private static void syncIfChanged(ServerPlayer player) {
        Map<UUID, PointSnapshot> current = LocationCollector.collect(player);
        if (current == null) {
            return; // tempad 不在 / 読み取り失敗 → 触れない
        }
        UUID playerId = player.getUUID();
        Map<UUID, PointSnapshot> previous = LAST_SENT.get(playerId);
        if (!LocationStateOps.stateChanged(previous, current)) {
            return;
        }
        LAST_SENT.put(playerId, current);
        TtmxNetwork.sendLocations(player, current);
        if (previous == null) {
            Ttmx.LOGGER.debug("Initial Tempad sync: {} point(s) -> {}", current.size(), player.getGameProfile().getName());
        }
    }
}
