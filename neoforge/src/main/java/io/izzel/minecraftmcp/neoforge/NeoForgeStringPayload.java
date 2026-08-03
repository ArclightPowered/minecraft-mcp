package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.serverlink.ServerMcpChannels;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record NeoForgeStringPayload(CustomPacketPayload.Type<NeoForgeStringPayload> type, String text) implements CustomPacketPayload {
    static final CustomPacketPayload.Type<NeoForgeStringPayload> REQUEST = new CustomPacketPayload.Type<>(id(ServerMcpChannels.REQUEST));
    static final CustomPacketPayload.Type<NeoForgeStringPayload> RESPONSE = new CustomPacketPayload.Type<>(id(ServerMcpChannels.RESPONSE));
    static final CustomPacketPayload.Type<NeoForgeStringPayload> HELLO = new CustomPacketPayload.Type<>(id(ServerMcpChannels.HELLO));

    static StreamCodec<RegistryFriendlyByteBuf, NeoForgeStringPayload> codec(CustomPacketPayload.Type<NeoForgeStringPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeUtf(payload.text), buf -> new NeoForgeStringPayload(type, buf.readUtf(32767)));
    }

    private static Identifier id(String value) {
        String[] parts = value.split(":", 2);
        return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
