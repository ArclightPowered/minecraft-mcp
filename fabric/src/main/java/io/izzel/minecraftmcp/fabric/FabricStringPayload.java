package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.serverlink.RemoteMcpChannels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record FabricStringPayload(CustomPacketPayload.Type<FabricStringPayload> type, String text) implements CustomPacketPayload {
    static final CustomPacketPayload.Type<FabricStringPayload> REQUEST = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.REQUEST));
    static final CustomPacketPayload.Type<FabricStringPayload> RESPONSE = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.RESPONSE));
    static final CustomPacketPayload.Type<FabricStringPayload> CLIENT_REQUEST = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.CLIENT_REQUEST));
    static final CustomPacketPayload.Type<FabricStringPayload> CLIENT_RESPONSE = new CustomPacketPayload.Type<>(id(RemoteMcpChannels.CLIENT_RESPONSE));

    static StreamCodec<FriendlyByteBuf, FabricStringPayload> codec(CustomPacketPayload.Type<FabricStringPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeUtf(payload.text), buf -> new FabricStringPayload(type, buf.readUtf(32767)));
    }

    private static Identifier id(String value) {
        String[] parts = value.split(":", 2);
        return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
