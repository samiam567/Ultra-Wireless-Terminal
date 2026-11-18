package com.ultrawireless.terminal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: save edited Create clipboard content onto the Ultra Wireless Terminal item.
 *
 * <p>Carries an optional {@link CompoundTag} representing Create's {@code ClipboardContent}.
 * When null/empty, clears the clipboard data from the terminal.
 */
public record ClipboardSavePacket(CompoundTag tag) implements CustomPacketPayload {

    public static final Type<ClipboardSavePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UltraWirelessTerminalMod.MODID, "save_clipboard"));

    public static final StreamCodec<FriendlyByteBuf, ClipboardSavePacket> CODEC =
            StreamCodec.of(ClipboardSavePacket::encode, ClipboardSavePacket::decode);

    private static void encode(FriendlyByteBuf buf, ClipboardSavePacket packet) {
        if (packet.tag == null || packet.tag.isEmpty()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeNbt(packet.tag);
        }
    }

    private static ClipboardSavePacket decode(FriendlyByteBuf buf) {
        boolean hasTag = buf.readBoolean();
        CompoundTag tag = hasTag ? buf.readNbt() : null;
        return new ClipboardSavePacket(tag);
    }

    @Override
    public Type<ClipboardSavePacket> type() {
        return TYPE;
    }
}
