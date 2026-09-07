package com.kuronami.tempadtomapx.sync;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tempad 2.x の色導出（名前+UUID ハッシュ）の性質テスト。
 * 決定論的・範囲内・染料代表 RGB 表との一致を機械保証する。
 */
class PointColorTest {

    @Test
    void deriveIsDeterministic() {
        UUID id = UUID.fromString("3f2b8c1e-1234-4abc-9d0e-5f6a7b8c9d01");
        assertEquals(PointColor.derive("Base Camp", id), PointColor.derive("Base Camp", id));
        assertEquals(PointColor.derive(null, id), PointColor.derive(null, id));
    }

    @Test
    void deriveChangesWhenIdentityChanges() {
        // 名前が同じでも UUID が違えば（確率的に）別の色になりうる。
        // 少なくとも「同一入力→同一色」が全入力で成り立つことを別観点で確認する
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            seen.add(PointColor.derive("same name", new UUID(0, i)));
        }
        assertTrue(seen.size() > 1, "UUID を変えても色が一切変わらないのはハッシュ壊れ");
    }

    @Test
    void derivedRgbIsAlwaysADyeRepresentative() {
        for (int i = 0; i < 64; i++) {
            int rgb = PointColor.derive("point" + i, new UUID(i, i));
            boolean isDyeColor = false;
            for (int dye : PointColor.DYE_RGB) {
                if (dye == rgb) {
                    isDyeColor = true;
                    break;
                }
            }
            assertTrue(isDyeColor, "染料代表 RGB 以外の値: " + rgb);
        }
    }

    @Test
    void dyeTableMatchesDyeColorsConversionSource() {
        // DyeColors.DYE_RGB（076 本体の表）と同値であること＝クライアント側の
        // nearestDyeIndex が元の染料 index を復元できる（往復一致）
        for (int dyeIndex = 0; dyeIndex < 16; dyeIndex++) {
            int rgb = PointColor.dyeRepresentativeRgb(dyeIndex);
            assertEquals(dyeIndex, com.kuronami.tempadtomapx.compat.xaero.DyeColors.nearestDyeIndex(rgb),
                    "dyeIndex=" + dyeIndex);
        }
    }
}
