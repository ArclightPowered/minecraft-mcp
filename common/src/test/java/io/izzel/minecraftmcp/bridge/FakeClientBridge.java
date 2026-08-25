package io.izzel.minecraftmcp.bridge;

import java.nio.file.Path;

public class FakeClientBridge implements MinecraftClientBridge {
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
    public boolean isOnClientThread() {
        return gameThread.isOn();
    }

    @Override
    public void execute(Runnable runnable) {
        gameThread.run(runnable);
    }

    @Override
    public ClientSnapshot snapshot() {
        return new ClientSnapshot(true, true, null, "Dev", 1, 70, 3, 0, 0);
    }
}
