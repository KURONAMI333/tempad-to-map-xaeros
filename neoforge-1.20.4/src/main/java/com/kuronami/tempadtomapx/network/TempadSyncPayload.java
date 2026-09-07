package com.kuronami.tempadtomapx.network;

import com.kuronami.tempadtomapx.Ttmx;
import com.kuronami.tempadtomapx.sync.LocationStateOps;
import com.kuronami.tempadtomapx.sync.PointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S2C: 「このプレイヤー自身の」Tempad 地点の全量スナップショット。
 *
 * <p>1.20.4 の {@link CustomPacketPayload} は {@code write(FriendlyByteBuf)} +
 * {@code id()} の形で、{@code StreamCodec} / {@code CustomPacketPayload.Type} は
 * 存在しない（1.21.1 とここが違う＝payload 層の作り直し箇所。javap 実証）。
 * ワイヤ形式は fabric-1.20.1 / forge-1.20.1 セルと同一:
 * {@code varint count × (uuid id, utf name, blockPos pos, utf dimensionId, varint rgb)}。</p>
 */
public record TempadSyncPayload(List<PointSnapshot> entries) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            new ResourceLocation(Ttmx.MODID, "locations");

    /** 正規化済みスナップショットから payload を組む（location UUID 昇順・決定論的）。 */
    public static TempadSyncPayload of(Map<UUID, PointSnapshot> state) {
        return new TempadSyncPayload(new ArrayList<>(LocationStateOps.normalize(state).values()));
    }

    /** FriendlyByteBuf.Reader 相当（IPayloadRegistrar.play へ渡す）。 */
    public static TempadSyncPayload read(FriendlyByteBuf buf) {
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
        return new TempadSyncPayload(entries);
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

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (PointSnapshot s : entries) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.name());
            buf.writeBlockPos(new BlockPos(s.x(), s.y(), s.z()));
            buf.writeUtf(s.dimensionId());
            buf.writeVarInt(s.rgb());
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
