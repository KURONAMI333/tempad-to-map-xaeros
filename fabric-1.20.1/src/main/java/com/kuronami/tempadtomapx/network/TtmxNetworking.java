package com.kuronami.tempadtomapx.network;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric 1.20.1 raw byte-buf networking（S2C 1 チャンネル）。
 * 1.21.1 の payload 登録（RegisterPayloadHandlersEvent + StreamCodec）は
 * 1.20.1 Fabric に存在しないので、ここがローダー固有の作り直し部。
 */
public final class TtmxNetworking {

    public static final ResourceLocation LOCATIONS_ID =
            new ResourceLocation(Ttmx.MODID, "locations");

    private TtmxNetworking() {
    }

    /**
     * 正規化済みスナップショットを全量送信する（決定論的順序＝location UUID 昇順）。
     * 差分検出はサーバー側 watcher が済ませており、変化時だけ呼ばれる。
     */
    public static void sendLocations(ServerPlayer player, Map<UUID, PointSnapshot> state) {
        List<PointSnapshot> entries =
                new ArrayList<>(LocationStateOps.normalize(state).values());
        FriendlyByteBuf buf = PacketByteBufs.create();
        LocationsPacket.write(buf, entries);
        ServerPlayNetworking.send(player, LOCATIONS_ID, buf);
    }
}
