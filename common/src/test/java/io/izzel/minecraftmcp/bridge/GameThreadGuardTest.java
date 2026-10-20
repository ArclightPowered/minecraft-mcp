package io.izzel.minecraftmcp.bridge;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GameThreadGuardTest {
    @Test
    void aWorkerThreadMayBlock() {
        FakeServerBridge bridge = new FakeServerBridge();

        assertDoesNotThrow(() -> bridge.waitTicks(0));
        assertEquals(0L, bridge.awaitTicks(0));
        assertFalse(bridge.waitUntil("server.running == false", 0));
    }

    @Test
    void blockingInsideTheGameLoopIsRefusedRatherThanSlow() {
        FakeServerBridge bridge = new FakeServerBridge();

        assertThrows(IllegalStateException.class, () -> bridge.onGameThread(() -> bridge.waitTicks(20)));
        assertThrows(IllegalStateException.class, () -> bridge.onGameThread(() -> bridge.awaitTicks(40)));
        assertThrows(IllegalStateException.class,
                () -> bridge.onGameThread(() -> bridge.waitUntil("server.running == true", 30_000)));
    }

    @Test
    void theRefusalNamesTheSideAndTheOperation() {
        FakeServerBridge bridge = new FakeServerBridge();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> bridge.onGameThread(() -> bridge.awaitTicks(40)));

        assertTrue(failure.getMessage().contains("server"), failure.getMessage());
        assertTrue(failure.getMessage().contains("40 server ticks"), failure.getMessage());
    }

    @Test
    void absurdTickWaitsAreRejectedInsteadOfBlockingAWorker() {
        IllegalArgumentException server = assertThrows(IllegalArgumentException.class,
            () -> new FakeServerBridge().awaitTicks(MinecraftBridge.MAX_WAIT_TICKS + 1));
        IllegalArgumentException client = assertThrows(IllegalArgumentException.class,
            () -> new FakeClientBridge().waitTicks(MinecraftBridge.MAX_WAIT_TICKS + 1));

        assertTrue(server.getMessage().contains(String.valueOf(MinecraftBridge.MAX_WAIT_TICKS)), server.getMessage());
        assertTrue(client.getMessage().contains(String.valueOf(MinecraftBridge.MAX_WAIT_TICKS)), client.getMessage());
    }

    @Test
    void submitStillShortCircuitsOnTheGameThread() {
        FakeServerBridge bridge = new FakeServerBridge();
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
        FakeClientBridge bridge = new FakeClientBridge();

        Thread.currentThread().interrupt();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> bridge.waitTicks(1));

        assertTrue(Thread.interrupted(), "the interrupt flag has to be restored");
        assertTrue(failure.getMessage().contains("waiting 1 ticks"), failure.getMessage());
    }

    @Test
    void anInterruptedWaitUntilFailsLoudInsteadOfClaimingTimeout() {
        FakeClientBridge bridge = new FakeClientBridge();
        assertFalse(bridge.waitUntil("client.inWorld == false", 0));

        Thread.currentThread().interrupt();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> bridge.waitUntil("client.inWorld == false", 1000));

        assertTrue(Thread.interrupted(), "the interrupt flag has to be restored");
        assertTrue(failure.getMessage().contains("client.inWorld == false"), failure.getMessage());
    }
}
