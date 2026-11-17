package com.ultrawireless.terminal;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.tom.storagemod.components.WorldPos;

/**
 * Client → server: rename (or clear the name of) one linked terminal. Blank / whitespace
 * names clear the entry so the terminal falls back to its coordinate label.
 *
 * <p>The terminal is identified by the same dimension + coords used by
 * {@link ActivatePacket}, and the server silently ignores renames for positions that are
 * not currently bound — prevents the name map from accumulating entries for unlinked
 * blocks.
 */
public record RenamePacket(String dimName, int x, int y, int z, String name) implements CustomPacketPayload {

    public static final Type<RenamePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "rename_terminal"));

    public static final StreamCodec<FriendlyByteBuf, RenamePacket> CODEC =
            StreamCodec.of(RenamePacket::encode, RenamePacket::decode);

    private static void encode(FriendlyByteBuf buf, RenamePacket packet) {
        buf.writeUtf(packet.dimName(), 256);
        buf.writeInt(packet.x());
        buf.writeInt(packet.y());
        buf.writeInt(packet.z());
        buf.writeUtf(packet.name() == null ? "" : packet.name(), 128);
    }

    private static RenamePacket decode(FriendlyByteBuf buf) {
        String dimName = buf.readUtf(256);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        String name = buf.readUtf(128);
        return new RenamePacket(dimName, x, y, z, name);
    }

    @Override
    public Type<RenamePacket> type() {
        return TYPE;
    }

    public WorldPos worldPos() {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimName));
        return new WorldPos(dim, new net.minecraft.core.BlockPos(x, y, z));
    }
}
