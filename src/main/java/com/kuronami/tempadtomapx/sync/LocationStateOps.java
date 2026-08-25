package com.kuronami.tempadtomapx.sync;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * サーバー側の「前回送信状態 vs 現在状態」比較のための純粋関数群。
 * Tempad / NeoForge の型に依存しない（JUnit が MC クラスパス無しで回る）。
 */
public final class LocationStateOps {

    private LocationStateOps() {
    }

    /**
     * 比較・送信用に状態を正規化する。
     * キー昇順の TreeMap へ詰め替え（payload の順序を決定論的に）＋ null 値の除去。
     *
     * @param state 地点集合（null 許容・null は空として扱う）
     * @return 正規化された不変マップ
     */
    public static Map<UUID, PointSnapshot> normalize(Map<UUID, PointSnapshot> state) {
        TreeMap<UUID, PointSnapshot> sorted = new TreeMap<>();
        if (state != null) {
            for (Map.Entry<UUID, PointSnapshot> entry : state.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sorted.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return java.util.Collections.unmodifiableSortedMap(sorted);
    }

    /**
     * 前回送信した状態と現在の状態が異なるか。
     * 順序の違いは同一とみなし、内容の差分のみを検出する。
     *
     * @param previousSent 前回送信分（初回は null）
     * @param current      現在の状態
     * @return 送信が必要なら true
     */
    public static boolean stateChanged(Map<UUID, PointSnapshot> previousSent,
                                       Map<UUID, PointSnapshot> current) {
        return !normalize(current).equals(normalize(previousSent));
    }

    /**
     * 指定次元の地点だけを抜き出す（挿入順保持・null 安全）。
     * ルート B の制約「{@code getCurrentWaypointSet()} は現在次元のセットしか返さない」
     * に対応する次元フィルタ。他次元分は呼び出し元の desired 内に保留される。
     *
     * @param state       地点集合（null 許容）
     * @param dimensionId 抽出する次元 ID（例: {@code minecraft:overworld}・null 許容）
     * @return フィルタされた不変ではないマップ（呼び出し側で扱いやすいよう新規インスタンス）
     */
    public static Map<UUID, PointSnapshot> filterByDimension(Map<UUID, PointSnapshot> state, String dimensionId) {
        java.util.LinkedHashMap<UUID, PointSnapshot> result = new java.util.LinkedHashMap<>();
        if (state != null && dimensionId != null) {
            for (Map.Entry<UUID, PointSnapshot> entry : state.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && dimensionId.equals(entry.getValue().dimensionId())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }
}
