package io.izzel.minecraftmcp.bridge;

import java.nio.file.Path;
import java.util.Map;

public class FakeServerBridge implements MinecraftServerBridge {
    protected final FakeGameThread gameThread = new FakeGameThread();

    public void onGameThread(Runnable task) {
        gameThread.run(task);
    }

    @Override
    public String loader() {
        return "test";
    }

    @Override
    public String minecraftVersion() {
        return "test";
    }

    @Override
    public Path gameDirectory() {
        return Path.of(".");
    }

    @Override
    public boolean isOnServerThread() {
        return gameThread.isOn();
    }

    @Override
    public void execute(Runnable runnable) {
        gameThread.run(runnable);
    }

    @Override
    public Map<String, Object> serverState() {
        return Map.of("running", true);
    }
}
