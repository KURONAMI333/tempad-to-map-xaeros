package com.kuronami.tempadtomapx.server;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.network.TtmxNetwork;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の監視ループ（NeoForge 1.20.4 版）。全 online プレイヤーを 30 tick ごとに
 * 見て、「自分の地点」スナップショットに変化があればそのプレイヤーへだけ送る。
 * ログイン直後にも必ず 1 回送る。ロジックは 076 本体と同一で、イベント差し替えのみ
 * （1.20.4 は TickEvent 系＝phase 確認が必要。1.21 の event.tick.ServerTickEvent ではない）。
 */
public final class ServerLocationWatcher {

    /** 20〜40 tick 帯の中間。地点変更は即座には要らないので軽量優先。 */
    private static final int SYNC_INTERVAL_TICKS = 30;

    /** playerId → 最後に送った状態。 */
    private static final Map<UUID, Map<UUID, PointSnapshot>> LAST_SENT = new ConcurrentHashMap<>();

    private ServerLocationWatcher() {
    }

    public static void init(IEventBus gameBus) {
        gameBus.addListener(ServerLocationWatcher::onServerTick);
        gameBus.addListener(ServerLocationWatcher::onLoggedIn);
        gameBus.addListener(ServerLocationWatcher::onLoggedOut);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncIfChanged(player);
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncIfChanged(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
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
