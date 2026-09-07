package com.kuronami.tempadtomapx.server;

import com.kuronami.tempadtomapx.sync.PointColor;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import me.codexadrian.tempad.common.data.LocationData;
import me.codexadrian.tempad.common.data.TempadLocationHandler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * サーバー側で「このプレイヤー自身の」Tempad 地点を読む（Tempad 2.x 版）。
 *
 * <p>読み取り経路: {@code TempadLocationHandler.getLocations(Level, UUID)}（public static・
 * リフレクション不要）。本体 GUI（TempadItem.use）と同じメソッドで、SavedLocations に
 * 登録 getter（waystones 等）の集約を合成した一覧が返る。他者の地点は絶対に送らない。</p>
 *
 * <p>Level の渡し方: resourcefullib {@code SaveHandler.read} は内部で必ず
 * {@code level.getServer().overworld().getDataStorage()} へ集約する（バイトコード確認済み）。
* そのため本体が {@code player.level()} を渡すのと等価に、ここでは overworld を明示して渡す。</p>
 *
 * <p>注意: {@code common.data} パッケージは公開 API 保証外。ホスト更新でシグネチャが
 * 変わった場合はここで静かに無効化される（Throwable を全部吸収して null を返す＝同期停止・
 * クラッシュ無し）。{@code getLevelKey()} が null の地点（次元不定）はスキップする。</p>
 */
public final class LocationCollector {

    private LocationCollector() {
    }

    public static boolean isTempadLoaded() {
        return FabricLoader.getInstance().isModLoaded("tempad");
    }

    /**
     * @return プレイヤー自身の地点集合。tempad 未ロード・読み取り失敗は
     *         null（＝このプレイヤーには何も送らない）。空マップは正当な状態
     *         （地点ゼロ＝クライアント側の全削除が正）。
     */
    public static Map<UUID, PointSnapshot> collect(ServerPlayer player) {
        if (!isTempadLoaded()) {
            return null;
        }
        try {
            return Inner.collect(player);
        } catch (Throwable t) {
            com.kuronami.tempadtomapx.Ttmx.LOGGER.warn(
                    "Tempad location read failed (sync disabled for this cycle): {}", t.toString());
            return null;
        }
    }

    /** Tempad クラスへの参照はすべて Inner に閉じ込める（isolation パターン）。 */
    private static final class Inner {

        static Map<UUID, PointSnapshot> collect(ServerPlayer player) {
            ServerLevel overworld = player.server.overworld();
            Map<UUID, LocationData> own = TempadLocationHandler.getLocations(overworld, player.getUUID());
            if (own == null || own.isEmpty()) {
                return new LinkedHashMap<>();
            }
            Map<UUID, PointSnapshot> result = new LinkedHashMap<>();
            for (Map.Entry<UUID, LocationData> entry : own.entrySet()) {
                LocationData location = entry.getValue();
                if (entry.getKey() != null && location != null) {
                    // 次元不定（getLevelKey() == null）の地点はスキップする。
                    // Xaero 側の反映先セットが確定できない＆次元フィルタが壊れるため
                    if (location.getLevelKey() == null) {
                        continue;
                    }
                    result.put(entry.getKey(), toSnapshot(entry.getKey(), location));
                }
            }
            return result;
        }

        static PointSnapshot toSnapshot(UUID id, LocationData location) {
            var blockPos = location.getBlockPos();
            // 2.x の LocationData に色フィールドは無いため、名前+UUID から導出する
            int rgb = PointColor.derive(location.getName(), id);
            return new PointSnapshot(
                    id,
                    location.getName(),
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    location.getLevelKey().location().toString(),
                    rgb);
        }
    }
}
