package io.izzel.minecraftmcp.fabric;

import io.izzel.minecraftmcp.server.ServerTickCounter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricMinecraftMcpCommonEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ServerTickCounter.onServerTick());
    }
}
