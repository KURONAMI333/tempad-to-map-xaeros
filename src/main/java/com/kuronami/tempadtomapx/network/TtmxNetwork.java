package com.kuronami.tempadtomapx.network;

import com.kuronami.tempadtomapx.client.TtmxClient;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * payload の登録（MOD バス・両 dist で実施。サーバーが送れるようにするため）と
 * クライアント受信ハンドラ。
 *
 * <p>1.21.1 では handler は既定で main thread 実行なので enqueueWork は使わない
 * （NEW_MOD_GUIDE networking 慣例）。Xaero state を触る反映処理も
 * main thread の tick ループから行うため、ここでは受け取るだけ。</p>
 */
public final class TtmxNetwork {

    private TtmxNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TempadSyncPayload.TYPE, TempadSyncPayload.STREAM_CODEC, TtmxNetwork::handleOnClient);
    }

    private static void handleOnClient(TempadSyncPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        // この handler はクライアントでしか発火しない。TtmxClient（client 専用クラス）
        // への参照はここで初めて解決される＝サーバー dist ではロードされない。
        TtmxClient.onLocationsFromServer(payload);
    }
}
