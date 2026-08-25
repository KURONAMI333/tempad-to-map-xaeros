package com.kuronami.tempadtomapx.server;

import com.kuronami.tempadtomapx.sync.PointSnapshot;

import earth.terrarium.tempad.api.locations.NamedGlobalVec3;
import earth.terrarium.tempad.common.location_handlers.PlayerPointsData;
import earth.terrarium.tempad.common.registries.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * サーバー側で「このプレイヤー自身の」Tempad 地点を読む。
 *
 * <p>読み取り経路: overworld Level の attachment {@code player_points}
 * （{@code ModAttachments.playerPoints}、ModAttachments.kt:97-101）→
 * {@code PlayerPointsData.get(playerUUID)}。これは本体の
 * DefaultLocationHandler（DefaultLocationHandler.kt:19）と同じデータで、
 * 全プレイヤーの地点が一元保存されている attachment のうち自分の UUID 分だけを
 * 取り出す。他者の地点は絶対に送らない。</p>
 *
 * <p>注意: {@code ModAttachments} は Tempad の common パッケージの public Kotlin
 * object（reflection 不要・通常の Java 呼び出し）。ただし公開 API 保証外のため、
 * ホスト更新でシグネチャが変わった場合はここで静かに無効化される
 * （Throwable を全部吸収して null を返す＝同期停止・クラッシュ無し）。</p>
 */
public final class LocationCollector {

    private LocationCollector() {
    }

    public static boolean isTempadLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("tempad");
    }

    /**
     * @return プレイヤー自身の地点集合。tempad 未ロード・attachment 読み取り失敗は
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
            com.kuronami.tempadtomapx.TempadToMapX.LOGGER.warn(
                    "Tempad attachment read failed (sync disabled for this cycle): {}", t.toString());
            return null;
        }
    }

    /** Tempad クラスへの参照はすべて Inner に閉じ込める（isolation パターン）。 */
    private static final class Inner {

        static Map<UUID, PointSnapshot> collect(ServerPlayer player) {
            ServerLevel overworld = player.server.overworld();
            PlayerPointsData data = overworld.getData(ModAttachments.INSTANCE.getPlayerPoints());
            if (data == null) {
                return new LinkedHashMap<>();
            }
            Map<UUID, NamedGlobalVec3> own = data.get(player.getUUID());
            if (own == null || own.isEmpty()) {
                return new LinkedHashMap<>();
            }
            Map<UUID, PointSnapshot> result = new LinkedHashMap<>();
            for (Map.Entry<UUID, NamedGlobalVec3> entry : own.entrySet()) {
                NamedGlobalVec3 location = entry.getValue();
                if (entry.getKey() != null && location != null) {
                    result.put(entry.getKey(), toSnapshot(entry.getKey(), location));
                }
            }
            return result;
        }

        static PointSnapshot toSnapshot(UUID id, NamedGlobalVec3 location) {
            Vec3 vec = location.getPos();
            BlockPos blockPos = BlockPos.containing(vec.x, vec.y, vec.z);
            return new PointSnapshot(
                    id,
                    location.getName().getString(),
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    location.getDimension().location().toString(),
                    location.getColor().getValue() & 0xFFFFFF);
        }
    }
}
