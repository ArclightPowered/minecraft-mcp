package io.izzel.minecraftmcp.bridge;

public final class FakeGameThread {
    private final ThreadLocal<Boolean> onGameThread = ThreadLocal.withInitial(() -> false);

    public boolean isOn() {
        return onGameThread.get();
    }

    public void run(Runnable task) {
        if (onGameThread.get()) {
            task.run();
            return;
        }
        onGameThread.set(true);
        try {
            task.run();
        } finally {
            onGameThread.set(false);
        }
    }
}
