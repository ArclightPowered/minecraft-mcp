package io.izzel.minecraftmcp.bridge;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GameThreadGuardTest {
    @Test
    void aWorkerThreadMayBlock() {
        Bridge bridge = new Bridge();

        assertDoesNotThrow(() -> bridge.waitTicks(0));
        assertEquals(0L, bridge.awaitTicks(0));
        assertFalse(bridge.waitUntil("server.running == false", 0));
    }

    @Test
    void blockingInsideTheGameLoopIsRefusedRatherThanSlow() {
        Bridge bridge = new Bridge();

        assertThrows(IllegalStateException.class, () -> bridge.onGameThread(() -> bridge.waitTicks(20)));
        assertThrows(IllegalStateException.class, () -> bridge.onGameThread(() -> bridge.awaitTicks(40)));
        assertThrows(IllegalStateException.class,
                () -> bridge.onGameThread(() -> bridge.waitUntil("server.running == true", 30_000)));
    }

    @Test
    void theRefusalNamesTheSideAndTheOperation() {
        Bridge bridge = new Bridge();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> bridge.onGameThread(() -> bridge.awaitTicks(40)));

        assertTrue(failure.getMessage().contains("server"), failure.getMessage());
        assertTrue(failure.getMessage().contains("40 server ticks"), failure.getMessage());
    }

    @Test
    void absurdTickWaitsAreRejectedInsteadOfBlockingAWorker() {
        IllegalArgumentException server = assertThrows(IllegalArgumentException.class,
            () -> new Bridge().awaitTicks(MinecraftBridge.MAX_WAIT_TICKS + 1));
        IllegalArgumentException client = assertThrows(IllegalArgumentException.class,
            () -> new ClientBridge().waitTicks(MinecraftBridge.MAX_WAIT_TICKS + 1));

        assertTrue(server.getMessage().contains(String.valueOf(MinecraftBridge.MAX_WAIT_TICKS)), server.getMessage());
        assertTrue(client.getMessage().contains(String.valueOf(MinecraftBridge.MAX_WAIT_TICKS)), client.getMessage());
    }

    @Test
    void submitStillShortCircuitsOnTheGameThread() {
        Bridge bridge = new Bridge();
        AtomicReference<Integer> depth = new AtomicReference<>(0);

        bridge.onGameThread(() -> {
            assertTrue(bridge.isOnGameThread());
            assertEquals("value", bridge.submit(() -> {
                depth.updateAndGet(current -> current + 1);
                return "value";
            }).join());
        });

        assertEquals(1, depth.get());
        assertFalse(bridge.isOnGameThread(), "the flag has to be cleared on the way out");
    }

    @Test
    void anInterruptedWaitTicksFailsLoudInsteadOfClaimingSuccess() {
        ClientBridge bridge = new ClientBridge();

        Thread.currentThread().interrupt();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> bridge.waitTicks(1));

        assertTrue(Thread.interrupted(), "the interrupt flag has to be restored");
        assertTrue(failure.getMessage().contains("waiting 1 ticks"), failure.getMessage());
    }

    @Test
    void anInterruptedWaitUntilFailsLoudInsteadOfClaimingTimeout() {
        ClientBridge bridge = new ClientBridge();
        assertFalse(bridge.waitUntil("client.inWorld == false", 0));

        Thread.currentThread().interrupt();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> bridge.waitUntil("client.inWorld == false", 1000));

        assertTrue(Thread.interrupted(), "the interrupt flag has to be restored");
        assertTrue(failure.getMessage().contains("client.inWorld == false"), failure.getMessage());
    }

    private static final class Bridge implements MinecraftServerBridge {
        private final FakeGameThread gameThread = new FakeGameThread();

        void onGameThread(Runnable task) {
            gameThread.run(task);
        }

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnServerThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public Map<String, Object> serverState() { return Map.of("running", true); }
    }

    private static final class ClientBridge implements MinecraftClientBridge {
        private final FakeGameThread gameThread = new FakeGameThread();

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, null, "Dev", 1, 70, 3, 0, 0); }
    }
}
