package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.serverlink.ServerMcpChannels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record FabricStringPayload(CustomPacketPayload.Type<FabricStringPayload> type, String text) implements CustomPacketPayload {
    static final CustomPacketPayload.Type<FabricStringPayload> REQUEST = new CustomPacketPayload.Type<>(id(ServerMcpChannels.REQUEST));
    static final CustomPacketPayload.Type<FabricStringPayload> RESPONSE = new CustomPacketPayload.Type<>(id(ServerMcpChannels.RESPONSE));
    static final CustomPacketPayload.Type<FabricStringPayload> HELLO = new CustomPacketPayload.Type<>(id(ServerMcpChannels.HELLO));

    static StreamCodec<FriendlyByteBuf, FabricStringPayload> codec(CustomPacketPayload.Type<FabricStringPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeUtf(payload.text), buf -> new FabricStringPayload(type, buf.readUtf(32767)));
    }

    private static Identifier id(String value) {
        String[] parts = value.split(":", 2);
        return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
