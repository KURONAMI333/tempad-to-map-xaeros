package com.kuronami.tempadtomapx.client;

import com.kuronami.tempadtomapx.compat.xaero.XaeroBridge;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * クライアント側の同期状態機械。サーバーから受信した「自分の地点」全量スナップショットを
 * あるべき状態として保持し、Xaero へ差分適用する。
 *
 * <p>セマンティクス: 片方向ミラー（Tempad[server attachment] → payload → Xaero）。</p>
 *
 * <p><b>次元フィルタ（JM 版との機能差）</b>: ルート B の
 * {@code getCurrentWaypointSet()} は「プレイヤーが今いる次元」のセットしか返さない。
 * 他次元の地点は誤ったセットに入るため、ここで現在次元分だけを抽出して渡し、
 * 他次元分は desired 内に保留される（プレイヤーがその次元に入った時点で反映）。</p>
 *
 * <p>payload が届いても Xaero 側が準備できていなければ dirty を保持したまま
 * tick ループから再試行する（取りこぼし無し）。適用済みの内容は
 * {@link XaeroBridge} 側で追跡されるため、ここは受信バッファのみを持つ。</p>
 */
public final class SyncManager {

    /** サーバーから受信した最新のあるべき状態（全次元・未反映分を含む保留キューを兼ねる）。 */
    private static Map<UUID, PointSnapshot> desired = null;

    /** desired の未適用フラグ。 */
    private static boolean dirty = false;

    private SyncManager() {
    }

    /**
     * サーバーからの payload 受信（main thread）。受信した集合を新しいあるべき状態とする。
     */
    public static void onLocationsFromServer(Map<UUID, PointSnapshot> fresh) {
        Map<UUID, PointSnapshot> next = new LinkedHashMap<>(fresh);
        if (next.equals(desired)) {
            return; // 内容同一（順序違いを含む）なら触らず済む
        }
        desired = next;
        dirty = true;
    }

    /**
     * 未適用の受信データがあれば現在次元分だけ Xaero へ適用する。
     * クライアント tick から定期呼び出しされる。
     * Xaero 不在・セッション未初期化・失敗時は静かに何もせず次回再試行。
     *
     * @return 実際に何か適用したら true
     */
    public static boolean flushPending() {
        if (!dirty || desired == null) {
            return false;
        }
        String dimensionId = currentDimensionId();
        if (dimensionId == null) {
            return false;
        }
        Map<UUID, PointSnapshot> currentDimDesired = LocationStateOps.filterByDimension(desired, dimensionId);
        if (XaeroBridge.syncDimension(dimensionId, currentDimDesired)) {
            dirty = false;
            return true;
        }
        return false;
    }

    /** dirty 状態か（テスト・デバッグ用）。 */
    public static boolean hasPending() {
        return dirty;
    }

    /**
     * ワールド接続/切断の境界でのリセット。Xaero 上のウェイポイントは
     * 永続データなので削除しに行かない。サーバーはログイン時に必ず再送してくる。
     */
    public static void resetSession() {
        desired = null;
        dirty = false;
        XaeroBridge.forgetSession();
    }

    /**
     * プレイヤーの現在次元 ID（例: {@code minecraft:overworld}）。
     * レベルが未ロードなら null。
     */
    private static String currentDimensionId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        return mc.level.dimension().location().toString();
    }
}
