package com.kuronami.tempadtomapx.server;

import com.kuronami.tempadtomapx.TempadToMapX;
import com.kuronami.tempadtomapx.network.TempadSyncPayload;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバー側の監視ループ。全 online プレイヤーを 30 tick ごとに軽く見て、
 * 「自分の地点」スナップショットに変化があればそのプレイヤーへだけ payload を送る。
 * ログイン直後にも必ず 1 回送る。
 *
 * <p>これらのイベントはサーバー dist でしか発火しない。クライアント dist では
 * ハンドラが走らないため Tempad 関連クラスもロードされない。</p>
 */
public final class ServerLocationWatcher {

    /** 20〜40 tick 帯の中間。地点変更は即座には要らないので軽量優先。 */
    private static final int SYNC_INTERVAL_TICKS = 30;

    /** playerId → 最後に送った状態。 */
    private static final Map<UUID, Map<UUID, PointSnapshot>> LAST_SENT = new ConcurrentHashMap<>();

    private ServerLocationWatcher() {
    }

    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerLocationWatcher::onServerTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerLocationWatcher::onLoggedIn);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerLocationWatcher::onLoggedOut);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncIfChanged(player);
        }
    }

    private static void onLoggedIn(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncIfChanged(player);
        }
    }

    private static void onLoggedOut(PlayerLoggedOutEvent event) {
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
        PacketDistributor.sendToPlayer(player, TempadSyncPayload.of(current));
        if (previous == null) {
            TempadToMapX.LOGGER.debug("Initial Tempad sync: {} point(s) -> {}", current.size(), player.getGameProfile().getName());
        }
    }
}
