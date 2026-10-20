package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.compat.MinecraftCompat;
import io.izzel.minecraftmcp.serverlink.RemoteMcpChannels;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

record FabricStringPayload(CustomPacketPayload.Type<FabricStringPayload> type, String text) implements CustomPacketPayload {
    static final CustomPacketPayload.Type<FabricStringPayload> REQUEST = MinecraftCompat.payloadType(RemoteMcpChannels.REQUEST);
    static final CustomPacketPayload.Type<FabricStringPayload> RESPONSE = MinecraftCompat.payloadType(RemoteMcpChannels.RESPONSE);
    static final CustomPacketPayload.Type<FabricStringPayload> CLIENT_REQUEST = MinecraftCompat.payloadType(RemoteMcpChannels.CLIENT_REQUEST);
    static final CustomPacketPayload.Type<FabricStringPayload> CLIENT_RESPONSE = MinecraftCompat.payloadType(RemoteMcpChannels.CLIENT_RESPONSE);

    static StreamCodec<FriendlyByteBuf, FabricStringPayload> codec(CustomPacketPayload.Type<FabricStringPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeUtf(payload.text), buf -> new FabricStringPayload(type, buf.readUtf(32767)));
    }

    /** Registers all four channels; requests go one way and responses the other, in both directions. */
    static void registerTypes() {
        //? if >=26.1 {
        /*PayloadTypeRegistry.serverboundPlay().register(REQUEST, codec(REQUEST));
        PayloadTypeRegistry.clientboundPlay().register(RESPONSE, codec(RESPONSE));
        PayloadTypeRegistry.clientboundPlay().register(CLIENT_REQUEST, codec(CLIENT_REQUEST));
        PayloadTypeRegistry.serverboundPlay().register(CLIENT_RESPONSE, codec(CLIENT_RESPONSE));
        *///?} else {
        PayloadTypeRegistry.playC2S().register(REQUEST, codec(REQUEST));
        PayloadTypeRegistry.playS2C().register(RESPONSE, codec(RESPONSE));
        PayloadTypeRegistry.playS2C().register(CLIENT_REQUEST, codec(CLIENT_REQUEST));
        PayloadTypeRegistry.playC2S().register(CLIENT_RESPONSE, codec(CLIENT_RESPONSE));
        //?}
    }
}
