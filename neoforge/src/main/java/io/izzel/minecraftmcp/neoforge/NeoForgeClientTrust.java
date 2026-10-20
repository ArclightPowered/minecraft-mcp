package io.izzel.minecraftmcp.neoforge;

import io.izzel.minecraftmcp.config.ChannelCaller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class NeoForgeClientTrust {
    private NeoForgeClientTrust() {
    }

    static ChannelCaller currentServerCaller(IPayloadContext context) {
        if (context.connection().isMemoryConnection()) {
            return new ChannelCaller.Local("singleplayer");
        }
        return currentServerCaller();
    }

    static ChannelCaller currentServerCaller() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isLocalServer()) {
            return new ChannelCaller.Local("singleplayer");
        }
        ClientPacketListener listener = mc.getConnection();
        if (listener == null) {
            return new ChannelCaller.Server("not-connected");
        }
        if (listener.getConnection().isMemoryConnection()) {
            return new ChannelCaller.Local("singleplayer");
        }
        ServerData data = listener.getServerData();
        if (data == null || data.ip == null || data.ip.isBlank()) {
            return new ChannelCaller.Server("unknown");
        }
        return new ChannelCaller.Server(data.ip);
    }
}
