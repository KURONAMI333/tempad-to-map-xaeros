package com.kuronami.tempadtomapx.network;

import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S2C: 「このプレイヤー自身の」Tempad 地点の全量スナップショット。
 * 1.20.1 Forge は {@code CustomPacketPayload} / {@code StreamCodec} が存在しないため、
 * raw {@link FriendlyByteBuf} の手書き encode/decode（mod-003 forge-1.20.1 実績方式）。
 *
 * <p>ワイヤ形式は fabric-1.20.1 セルと同一:
 * {@code varint count × (uuid id, utf name, blockPos pos, utf dimensionId, varint rgb)}。
 * 名前・次元は文字列で運ぶ（1.21.1 版の Component / ResourceKey 直列化は 1.20.1 に無い＝payload 層の
 * 作り直し箇所）。クライアントは受信した文字列をそのまま PointSnapshot へ入れる。</p>
 */
public final class LocationsPacket {

    private final List<PointSnapshot> entries;

    public LocationsPacket(List<PointSnapshot> entries) {
        this.entries = entries;
    }

    /** 正規化済みスナップショットから payload を組む（location UUID 昇順・決定論的）。 */
    public static LocationsPacket of(Map<UUID, PointSnapshot> state) {
        return new LocationsPacket(new ArrayList<>(LocationStateOps.normalize(state).values()));
    }

    public List<PointSnapshot> entries() {
        return entries;
    }

    /** 受信リストを SyncManager 向けのマップへ詰め替える（id 重複時は後勝ち）。 */
    public Map<UUID, PointSnapshot> toMap() {
        LinkedHashMap<UUID, PointSnapshot> map = new LinkedHashMap<>();
        for (PointSnapshot entry : entries) {
            if (entry != null && entry.id() != null) {
                map.put(entry.id(), entry);
            }
        }
        return map;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (PointSnapshot s : entries) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.name());
            buf.writeBlockPos(new BlockPos(s.x(), s.y(), s.z()));
            buf.writeUtf(s.dimensionId());
            buf.writeVarInt(s.rgb());
        }
    }

    public static LocationsPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<PointSnapshot> entries = new ArrayList<>(Math.max(0, Math.min(count, 4096)));
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            String dimensionId = buf.readUtf();
            int rgb = buf.readVarInt();
            entries.add(new PointSnapshot(id, name, pos.getX(), pos.getY(), pos.getZ(), dimensionId, rgb));
        }
        return new LocationsPacket(entries);
    }
}
