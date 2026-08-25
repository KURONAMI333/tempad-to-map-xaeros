package com.kuronami.tempadtomapx.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * サーバー dist での安全性確認用 trivial テスト。
 *
 * <p>本体 mod（tempadtomapx）が Tempad 不在・Xaero 不在のサーバー環境で
 * ModLoadingException 等を起こさずロードできていること自体が前提条件であり、
 * このテストが走る＝ロード成功の証拠になる。</p>
 */
@GameTestHolder("tempadtomapx")
public class TtmxGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void serverDistNoOpSafety(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.DIAMOND_BLOCK);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, pos));
    }
}
