package com.ultrawireless.terminal;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Empty client→server request; distinct payloads keep their own type id. */
final class EmptyPayload {
    private EmptyPayload() {}

    static <T> StreamCodec<FriendlyByteBuf, T> codecOf(Supplier<T> factory) {
        return StreamCodec.of((buf, p) -> {}, buf -> factory.get());
    }
}
