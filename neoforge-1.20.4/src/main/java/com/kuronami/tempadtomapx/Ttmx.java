package com.kuronami.tempadtomapx;

import com.kuronami.tempadtomapx.client.TtmxClient;
import com.kuronami.tempadtomapx.network.TtmxNetwork;
import com.kuronami.tempadtomapx.server.ServerLocationWatcher;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * Tempad to Map: Xaero's edition — NeoForge 1.20.4 / Tempad 2.x 系セル。
 *
 * <p>構成は 076 本体（NeoForge 1.21.1）と同型。差分は payload 層（1.20.4 は
 * {@code write/id} 型 payload + {@code RegisterPayloadHandlerEvent} 登録＝StreamCodec 無し）
 * と、サーバー読取が Tempad 2.x の {@code TempadLocationHandler.getLocations} 直参照になった点。</p>
 */
@Mod(Ttmx.MODID)
public final class Ttmx {

    public static final String MODID = "tempadtomapx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ttmx(IEventBus modEventBus) {
        // payload 登録は両 dist で必須（サーバーが送るにも登録が要る）
        TtmxNetwork.init(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            // クライアント専用クラスへの参照はこの分岐の中だけで行う
            TtmxClient.init();
        }
        // サーバー側の監視ループ。イベント自体が server 専用なので
        // クライアント dist ではハンドラが走らず Tempad 関連クラスもロードされない
        ServerLocationWatcher.init(NeoForge.EVENT_BUS);
    }
}
