package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.serverlink.RemoteMcpChannels;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record NeoForgeStringPayload(CustomPacketPayload.Type<NeoForgeStringPayload> type, String text) implements CustomPacketPayload {
    static final CustomPacketPayload.Type<NeoForgeStringPayload> REQUEST = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.REQUEST));
    static final CustomPacketPayload.Type<NeoForgeStringPayload> RESPONSE = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.RESPONSE));
    static final CustomPacketPayload.Type<NeoForgeStringPayload> CLIENT_REQUEST = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.CLIENT_REQUEST));
    static final CustomPacketPayload.Type<NeoForgeStringPayload> CLIENT_RESPONSE = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.CLIENT_RESPONSE));

    static StreamCodec<RegistryFriendlyByteBuf, NeoForgeStringPayload> codec(CustomPacketPayload.Type<NeoForgeStringPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeUtf(payload.text), buf -> new NeoForgeStringPayload(type, buf.readUtf(32767)));
    }

    private static Identifier id(String value) {
        String[] parts = value.split(":", 2);
        return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
