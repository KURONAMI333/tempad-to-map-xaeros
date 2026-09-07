package com.kuronami.tempadtomapx.server;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.network.TtmxNetworking;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の監視ループ（Fabric 1.20.1 版）。全 online プレイヤーを 30 tick ごとに
 * 見て、「自分の地点」スナップショットに変化があればそのプレイヤーへだけ送る。
 * ログイン直後にも必ず 1 回送る。ロジックは 076 本体と同一で、イベント差し替えのみ。
 */
public final class ServerLocationWatcher {

    /** 20〜40 tick 帯の中間。地点変更は即座には要らないので軽量優先。 */
    private static final int SYNC_INTERVAL_TICKS = 30;

    /** playerId → 最後に送った状態。 */
    private static final Map<UUID, Map<UUID, PointSnapshot>> LAST_SENT = new ConcurrentHashMap<>();

    private ServerLocationWatcher() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ServerLocationWatcher::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                syncIfChanged(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_SENT.remove(handler.player.getUUID()));
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncIfChanged(player);
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
        TtmxNetworking.sendLocations(player, current);
        if (previous == null) {
            Ttmx.LOGGER.debug("Initial Tempad sync: {} point(s) -> {}", current.size(), player.getGameProfile().getName());
        }
    }
}
