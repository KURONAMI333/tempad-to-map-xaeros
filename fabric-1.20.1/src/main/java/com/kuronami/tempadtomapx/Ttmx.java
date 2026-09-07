package com.kuronami.tempadtomapx;

import com.kuronami.tempadtomapx.server.ServerLocationWatcher;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tempad to Map: Xaero's edition — Fabric 1.20.1 entry point。
 *
 * <p>構成は 076 本体（NeoForge 1.21.1）と同型: サーバーが overworld ストレージの
 * Tempad 地点（2.x は {@code TempadLocationHandler.getLocations}）を読み、変化時だけ
 * 自前 S2C で送る。クライアントは Xaero's Minimap へ Reflection 直叩きで差分同期する。</p>
 *
 * <p>クライアント側の配線は {@code client.TtmxClient}（client entrypoint）が担うため、
 * このクラスは dedicated server でもそのまま動く。</p>
 */
public final class Ttmx implements ModInitializer {

    public static final String MODID = "tempadtomapx";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        // サーバー側の監視ループ（統合サーバー＝シングルプレイでも同じ経路で動く）
        ServerLocationWatcher.init();
    }
}
