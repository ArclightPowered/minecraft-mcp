package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.compat.MinecraftCompat;
import io.izzel.minecraftmcp.serverlink.RemoteMcpChannels;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

record NeoForgeStringPayload(CustomPacketPayload.Type<NeoForgeStringPayload> type, String text) implements CustomPacketPayload {
    static final CustomPacketPayload.Type<NeoForgeStringPayload> REQUEST = MinecraftCompat.payloadType(RemoteMcpChannels.REQUEST);
    static final CustomPacketPayload.Type<NeoForgeStringPayload> RESPONSE = MinecraftCompat.payloadType(RemoteMcpChannels.RESPONSE);
    static final CustomPacketPayload.Type<NeoForgeStringPayload> CLIENT_REQUEST = MinecraftCompat.payloadType(RemoteMcpChannels.CLIENT_REQUEST);
    static final CustomPacketPayload.Type<NeoForgeStringPayload> CLIENT_RESPONSE = MinecraftCompat.payloadType(RemoteMcpChannels.CLIENT_RESPONSE);

    static StreamCodec<RegistryFriendlyByteBuf, NeoForgeStringPayload> codec(CustomPacketPayload.Type<NeoForgeStringPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeUtf(payload.text), buf -> new NeoForgeStringPayload(type, buf.readUtf(32767)));
    }

}
