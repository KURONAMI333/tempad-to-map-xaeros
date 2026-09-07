package com.kuronami.tempadtomapx.sync;

import java.util.Objects;
import java.util.UUID;

/**
 * Tempad 2.x の地点から導出したウェイポイント色（純粋関数・JUnit 対象）。
 *
 * <p>2.x の {@code LocationData} には色フィールドが無いため（API_DIFF.md #6）、
 * 「地点名 + location UUID」のハッシュを染料 16 色に割り当て、その代表 RGB を
 * 返す。同一地点は常に同一色になり、Tempad 側のデータが変わらない限り色も
 * 変わらない。RGB は 076 本体と同じ「0xRRGGBB」の意味で {@link PointSnapshot}
 * に載せるので、クライアント側の変換パイプライン
 * （{@code DyeColors.nearestDyeIndex} → fromDye 対応表 → WaypointColor）は無変更。</p>
 */
public final class PointColor {

    private PointColor() {
    }

    /**
     * 地点名と location UUID から決定論的にウェイポイント色を導出する。
     *
     * @param name 地点名（null は空文字として扱う）
     * @param id   location UUID（null 禁止）
     * @return 染料 16 色の代表 RGB（0xRRGGBB・アルファ 0）
     */
    public static int derive(String name, UUID id) {
        Objects.requireNonNull(id, "id");
        String normalized = (name == null ? "" : name) + "#" + id;
        // String#hashCode は JVM 仕様で固定式＝プラットフォーム非依存の決定論的ハッシュ
        int hash = normalized.hashCode();
        // hashCode() は Integer.MIN_VALUE に対して Math.floorMod でも安全（負の剰余を出さない）
        int dyeIndex = Math.floorMod(mix(hash), DYE_COLOR_COUNT);
        return dyeRepresentativeRgb(dyeIndex);
    }

    /** 分布を均すための軽いミキシング（連番 UUID などの偏り対策）。 */
    private static int mix(int h) {
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return h;
    }

    private static final int DYE_COLOR_COUNT = 16;

    /**
     * 染料 index（0-15・{@code DyeColor.ordinal()} 順）の代表 RGB。
     * 出典: vanilla {@code DyeColor} の公知テキスト色（076 の DyeColors.DYE_RGB と同じ表）。
     * DyeColors 側は private 定数のため、こちらに同値を置く（テストで一致を機械保証する）。
     */
    static int dyeRepresentativeRgb(int dyeIndex) {
        int clamped = Math.max(0, Math.min(DYE_COLOR_COUNT - 1, dyeIndex));
        return DYE_RGB[clamped];
    }

    static final int[] DYE_RGB = {
            0xF9FFFE, // 0  white
            0xF9801D, // 1  orange
            0xC74EBD, // 2  magenta
            0x3AB3DA, // 3  light_blue
            0xFED83D, // 4  yellow
            0x80C71F, // 5  lime
            0xF38BAA, // 6  pink
            0x474F52, // 7  gray
            0x9D9D97, // 8  light_gray
            0x169C9C, // 9  cyan
            0x8932B8, // 10 purple
            0x3C44AA, // 11 blue
            0x835432, // 12 brown
            0x5E7C16, // 13 green
            0xB02E26, // 14 red
            0x1D1D21, // 15 black
    };
}
