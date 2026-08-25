package com.kuronami.tempadtomapx.compat.xaero;

import com.kuronami.tempadtomapx.sync.PointSnapshot;
import com.kuronami.tempadtomapx.sync.SyncPlan;
import com.kuronami.tempadtomapx.sync.WaypointDiffer;

import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Xaero's Minimap へのウェイポイント反映（ルート B・Reflection 直叩き）。
 * XAERO_NOTES の isolation パターンに従い、Xaero 型への参照はすべてこのクラス（と
 * Inner）に閉じ込める。全段 {@code catch (Throwable)} で吸収し、Xaero 不在・
 * バージョン違いでは静かに何もしない（v0.1 は例外ログも出さない完全サイレント）。
 *
 * <p>Reflection チェーン（xaeros-minimap neoforge-1.21.1-26.4.2・javap 一次確認 2026-08-26。
 * XAERO_NOTES の 2026-05 版からパッケージ移動があるため現行実名を採用）:</p>
 *
 * <pre>
 * xaero.hud.minimap.BuiltInHudModules.MINIMAP          // HudModule (public static final)
 *   → xaero.hud.module.HudModule.getCurrentSession()   // MinimapSession or null
 *     → xaero.hud.minimap.module.MinimapSession.getWorldManager()
 *       → xaero.hud.minimap.world.MinimapWorldManager.getCurrentWorld()  // MinimapWorld or null
 *         → xaero.hud.minimap.world.MinimapWorld.getCurrentWaypointSet() // WaypointSet or null
 *           → WaypointSet.add(Waypoint, boolean) / .remove(Waypoint) / .getWaypoints()
 * </pre>
 *
 * <p>Waypoint 要素型は {@code xaero.common.minimap.waypoints.Waypoint}。
 * コンストラクタは {@code (int x, int y, int z, String name, String initials,
 * WaypointColor, WaypointPurpose, boolean disabled)} を使用する。</p>
 */
public final class XaeroBridge {

    /**
     * 追跡中のウェイポイント。location UUID → 実体 + 入れた次元 + 反映済み内容。
     * 反映済み内容（{@code applied}）を持つことで、差分判定は mod-075 と同じ
     * 「PointSnapshot のレコード等価性」で行える。
     */
    private static final class Tracked {
        final Object waypoint;
        final String dimensionId;
        final PointSnapshot applied;

        Tracked(Object waypoint, String dimensionId, PointSnapshot applied) {
            this.waypoint = waypoint;
            this.dimensionId = dimensionId;
            this.applied = applied;
        }
    }

    /** キー = Tempad location UUID。Xaero へ渡した Waypoint 実体の保持用。 */
    private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();

    /** Reflection 解決が一度でも失敗したら true（Xaero 不在 / API drift）。以降即 no-op。 */
    private static volatile boolean permanentlyUnavailable = false;

    private XaeroBridge() {
    }

    /** ワールド接続/切断の境界で呼ぶ。実体参照だけ捨てる（永続 waypoint は触らない）。 */
    public static void forgetSession() {
        TRACKED.clear();
    }

    /**
     * 「プレイヤーが今いる次元」の地点集合を Xaero へ差分同期する。
     *
     * <p>他次元の地点は呼び出し側で除外してある（ルート B の
     * {@code getCurrentWaypointSet()} が返すのは現在次元のセットのみのため）。</p>
     *
     * @param playerDimId   プレイヤーの現在次元 ID（例: {@code minecraft:overworld}）
     * @param curDimDesired 現在次元のあるべき地点集合（空でもよい＝全削除が正）
     * @return 適用できたら true。Xaero 不在・セッション未初期化・Xaero 側の
     *         現在ワールドがまだ別次元（切替ラグ）の場合は false＝次回再試行
     */
    public static boolean syncDimension(String playerDimId, Map<UUID, PointSnapshot> curDimDesired) {
        if (permanentlyUnavailable) {
            return false;
        }
        try {
            return Inner.syncDimension(playerDimId, curDimDesired);
        } catch (Throwable t) {
            // v0.1 は完全サイレント（XAERO_NOTES silent fail ポリシー）
            return false;
        }
    }

    private static final class Inner {

        static Object minimapModule;         // BuiltInHudModules.MINIMAP の値
        static Method getCurrentSession;     // HudModule -> MinimapSession
        static Method getWorldManager;       // MinimapSession -> MinimapWorldManager
        static Method getCurrentWorld;       // MinimapWorldManager -> MinimapWorld
        static Method getCurrentWaypointSet; // MinimapWorld -> WaypointSet
        static Method getDimId;              // MinimapWorld -> ResourceKey<Level>
        static Method getWaypoints;          // WaypointSet -> Iterable<Waypoint>
        static Method addWithFlag;           // WaypointSet.add(Waypoint, boolean)
        static Method removeWaypoint;        // WaypointSet.remove(Waypoint)
        static Method waypointGetX;          // Waypoint.getX()
        static Method waypointGetY;          // Waypoint.getY()
        static Method waypointGetZ;          // Waypoint.getZ()
        static Constructor<?> waypointCtor;  // Waypoint(x,y,z,name,initials,color,purpose,disabled)
        static Method colorFromIndex;        // WaypointColor.fromIndex(int)
        static Object normalPurpose;         // WaypointPurpose.values()[0]

        static void resolve() throws Exception {
            Class<?> hudModulesClass = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            minimapModule = hudModulesClass.getField("MINIMAP").get(null);

            Class<?> hudModuleClass = Class.forName("xaero.hud.module.HudModule");
            getCurrentSession = hudModuleClass.getMethod("getCurrentSession");

            Class<?> sessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession");
            getWorldManager = sessionClass.getMethod("getWorldManager");

            Class<?> managerClass = Class.forName("xaero.hud.minimap.world.MinimapWorldManager");
            getCurrentWorld = managerClass.getMethod("getCurrentWorld");

            Class<?> worldClass = Class.forName("xaero.hud.minimap.world.MinimapWorld");
            getCurrentWaypointSet = worldClass.getMethod("getCurrentWaypointSet");
            getDimId = worldClass.getMethod("getDimId");

            Class<?> setClass = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet");
            getWaypoints = setClass.getMethod("getWaypoints");
            addWithFlag = setClass.getMethod("add", resolveElementClass(setClass), boolean.class);
            removeWaypoint = setClass.getMethod("remove", resolveElementClass(setClass));

            Class<?> elementClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            waypointGetX = elementClass.getMethod("getX");
            waypointGetY = elementClass.getMethod("getY");
            waypointGetZ = elementClass.getMethod("getZ");

            Class<?> colorClass = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
            colorFromIndex = colorClass.getMethod("fromIndex", int.class);
            Class<?> purposeClass = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose");
            normalPurpose = ((Object[]) purposeClass.getMethod("values").invoke(null))[0];

            waypointCtor = elementClass.getConstructor(
                    int.class, int.class, int.class,
                    String.class, String.class,
                    colorClass, purposeClass, boolean.class);
        }

        /** WaypointSet の要素型（add(Waypoint, boolean) の引数から解決）。 */
        static Class<?> resolveElementClass(Class<?> setClass) throws NoSuchMethodException {
            for (Method m : setClass.getMethods()) {
                if (m.getName().equals("add") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == boolean.class) {
                    return m.getParameterTypes()[0];
                }
            }
            throw new NoSuchMethodException("WaypointSet.add(Waypoint, boolean) not found");
        }

        static boolean syncDimension(String playerDimId, Map<UUID, PointSnapshot> curDimDesired) throws Exception {
            if (getCurrentSession == null) {
                try {
                    resolve();
                } catch (Throwable t) {
                    permanentlyUnavailable = true; // Xaero 不在 / API drift → 以降静かに no-op
                    return false;
                }
            }
            Object session = getCurrentSession.invoke(minimapModule);
            if (session == null) {
                return false; // HUD セッション未初期化 → 次回再試行
            }
            Object manager = getWorldManager.invoke(session);
            if (manager == null) {
                return false;
            }
            Object world = getCurrentWorld.invoke(manager);
            if (world == null) {
                return false;
            }
            // 次元フィルタ: Xaero 側の current world がプレイヤー次元と一致する時だけ触る
            // （不一致＝切替ラグ中。誤った次元のセットへ書き込む事故を防ぐ）
            ResourceKey<?> xaeroDim = (ResourceKey<?>) getDimId.invoke(world);
            if (xaeroDim == null || !playerDimId.equals(xaeroDim.location().toString())) {
                return false;
            }
            Object set = getCurrentWaypointSet.invoke(world);
            if (set == null) {
                return false;
            }

            // 現セット内の実体スナップショット（同一参照判定・残骸掃除用）
            Iterable<?> existing = (Iterable<?>) getWaypoints.invoke(set);
            IdentityHashMap<Object, Boolean> present = new IdentityHashMap<>();
            for (Object wp : existing) {
                present.put(wp, Boolean.TRUE);
            }

            // ユーザー削除の尊重: 自分が入れた実体がもうセット内に無い場合、
            // 追跡だけ降ろす（作り直さない。地点データが変わった時のみ作り直される）
            Iterator<Map.Entry<UUID, Tracked>> trackedIterator = TRACKED.entrySet().iterator();
            while (trackedIterator.hasNext()) {
                Map.Entry<UUID, Tracked> entry = trackedIterator.next();
                Tracked tracked = entry.getValue();
                if (playerDimId.equals(tracked.dimensionId) && !present.containsKey(tracked.waypoint)) {
                    trackedIterator.remove();
                }
            }

            // 差分プラン（現在次元の追跡分との比較・PointSnapshot レコード等価性）
            Map<UUID, PointSnapshot> trackedSnapshots = new LinkedHashMap<>();
            for (Map.Entry<UUID, Tracked> entry : TRACKED.entrySet()) {
                Tracked tracked = entry.getValue();
                if (playerDimId.equals(tracked.dimensionId)) {
                    trackedSnapshots.put(entry.getKey(), tracked.applied);
                }
            }
            SyncPlan plan = WaypointDiffer.plan(curDimDesired, trackedSnapshots);

            for (UUID id : plan.removals()) {
                Tracked removed = TRACKED.remove(id);
                if (removed != null) {
                    present.remove(removed.waypoint);
                    removeWaypoint.invoke(set, removed.waypoint);
                }
            }

            for (PointSnapshot snapshot : plan.upserts()) {
                Tracked previous = TRACKED.remove(snapshot.id());
                if (previous != null) {
                    present.remove(previous.waypoint);
                    removeWaypoint.invoke(set, previous.waypoint);
                }
                // 前セッション残留などの同座標残骸を掃除してから追加する
                // （重複蓄積防止。unchanged 分には行わないので手動削除分は復活しない）
                for (Object wp : present.keySet()) {
                    if (wp != null && wp != previous && samePosition(wp, snapshot.x(), snapshot.y(), snapshot.z())) {
                        removeWaypoint.invoke(set, wp);
                        present.put(wp, null); // value を潰して以降の掃除対象から外す
                    }
                }
                Object created = createWaypoint(snapshot);
                addWithFlag.invoke(set, created, Boolean.FALSE); // false = 末尾追加
                TRACKED.put(snapshot.id(),
                        new Tracked(created, snapshot.dimensionId(), snapshot));
            }
            return true;
        }

        static boolean samePosition(Object waypoint, int x, int y, int z) throws Exception {
            return (Integer) waypointGetX.invoke(waypoint) == x
                    && (Integer) waypointGetY.invoke(waypoint) == y
                    && (Integer) waypointGetZ.invoke(waypoint) == z;
        }

        static Object createWaypoint(PointSnapshot s) throws Exception {
            int enumIdx = DyeColors.dyeIndexToWaypointColorIndex(DyeColors.nearestDyeIndex(s.rgb()));
            Object color = colorFromIndex.invoke(null, enumIdx);
            return waypointCtor.newInstance(
                    s.x(), s.y(), s.z(),
                    s.name(), Initials.of(s.name()),
                    color, normalPurpose, Boolean.FALSE);
        }
    }
}
