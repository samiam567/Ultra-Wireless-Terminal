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
 * Request from the client to set a specific bound terminal as the active one and (if it
 * is currently in range) open it.
 *
 * <p>The terminal is identified by its dimension + block coordinates. The server only
 * accepts the switch when the position is still bound to the held item and in range;
 * an out-of-range click is refused and the current selection is kept.
 */
public record ActivatePacket(String dimName, int x, int y, int z) implements CustomPacketPayload {

    public static final Type<ActivatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "activate"));

    public static final StreamCodec<FriendlyByteBuf, ActivatePacket> CODEC =
            StreamCodec.of(ActivatePacket::encode, ActivatePacket::decode);

    private static void encode(FriendlyByteBuf buf, ActivatePacket packet) {
        buf.writeUtf(packet.dimName(), 256);
        buf.writeInt(packet.x());
        buf.writeInt(packet.y());
        buf.writeInt(packet.z());
    }

    private static ActivatePacket decode(FriendlyByteBuf buf) {
        String dimName = buf.readUtf(256);
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        return new ActivatePacket(dimName, x, y, z);
    }

    @Override
    public Type<ActivatePacket> type() {
        return TYPE;
    }

    /**
     * Reconstructs the {@link WorldPos} for server-side matching. The dimension is looked
     * up as a level registry key from the resource location sent by the client.
     */
    public WorldPos worldPos() {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimName));
        return new WorldPos(dim, new net.minecraft.core.BlockPos(x, y, z));
    }
}
