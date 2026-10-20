package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.config.ChannelCaller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

final class FabricClientTrust {
    private FabricClientTrust() {
    }

    static ChannelCaller currentServerCaller(Minecraft mc) {
        ClientPacketListener listener = mc.getConnection();
        if (listener == null) {
            return new ChannelCaller.Server("not-connected");
        }
        if (listener.getConnection().isMemoryConnection() || mc.isLocalServer()) {
            return new ChannelCaller.Local("singleplayer");
        }
        ServerData data = listener.getServerData();
        if (data == null || data.ip == null || data.ip.isBlank()) {
            return new ChannelCaller.Server("unknown");
        }
        return new ChannelCaller.Server(data.ip);
    }
}
