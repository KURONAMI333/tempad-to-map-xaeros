package com.kuronami.tempadtomapx;

import com.kuronami.tempadtomapx.client.TtmxClient;
import com.kuronami.tempadtomapx.network.TtmxNetwork;
import com.kuronami.tempadtomapx.server.ServerLocationWatcher;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;

/**
 * Tempad to Map: Xaero's edition — Tempad に登録した地点を Xaero's Minimap の
 * ウェイポイントとして自動反映するブリッジ（両サイド型）。
 *
 * <p>構成: mod-075 Tempad to Map（JourneyMap 版）と同型で、サーバーが overworld
 * attachment {@code player_points} から各プレイヤー自身の地点を読み、変化時だけ
 * S2C payload で送る。クライアントは受信集合を <b>Xaero's Minimap へ Reflection
 * 直叩き（ルート B・XAERO_NOTES 参照）</b>で差分同期する。JourneyMap 版との差は
 * クライアント出力先のみ。</p>
 *
 * <p>設計方針（v0.1）: config 無しの固定挙動。常時同期・永続ウェイポイント・片方向。
 * Xaero 不在時は静かに何もしない（{@code xaeros-minimap} は依存宣言しない）。</p>
 */
@Mod(TempadToMapX.MODID)
public final class TempadToMapX {

    public static final String MODID = "tempadtomapx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TempadToMapX(IEventBus modEventBus, ModContainer modContainer) {
        // payload 登録は両 dist で必須（サーバーが送るにも登録が要る）
        modEventBus.addListener(TtmxNetwork::register);

        if (FMLEnvironment.dist.isClient()) {
            // クライアント専用クラスへの参照はこの分岐の中だけで行う
            TtmxClient.init(modEventBus);
        }
        // サーバー側の監視ループ。イベント自体が server 専用なので
        // クライアント dist ではハンドラが走らず Tempad 関連クラスもロードされない
        ServerLocationWatcher.init();
    }
}
