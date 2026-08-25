package io.izzel.minecraftmcp.serverlink;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class RemoteMcpProxiesTest {
    private static final Predicate<UUID> ALL_REACHABLE = uuid -> true;
    private static final Predicate<UUID> NONE_REACHABLE = uuid -> false;

    @Test
    void serverKeepsOneProxyPerPlayerAndDropsItOnDisconnect() {
        UUID player = UUID.randomUUID();

        RemoteMcpProxies.rememberClientName(player, "Dev");
        RemoteMcpProxy proxy = RemoteMcpProxies.findClient(player);
        assertNotNull(proxy);
        RemoteMcpProxies.rememberClientName(player, "Dev");
        assertSame(proxy, RemoteMcpProxies.findClient(player), "same player must reuse its proxy");
        assertEquals(player, RemoteMcpProxies.findClientByName("dev"), "name lookup is case-insensitive");

        RemoteMcpProxies.forgetClient(player);
        assertNull(RemoteMcpProxies.findClientByName("Dev"));
        assertNull(RemoteMcpProxies.findClient(player), "a forgotten client must not come back");
        RemoteMcpProxies.rememberClientName(player, "Dev");
        assertNotSame(proxy, RemoteMcpProxies.findClient(player), "a reconnect starts a fresh proxy");
        RemoteMcpProxies.forgetClient(player);
    }

    @Test
    void lookingUpAnUnknownPlayerDoesNotMintAProxy() {
        UUID stranger = UUID.randomUUID();

        assertNull(RemoteMcpProxies.findClient(stranger));
        assertTrue(RemoteMcpProxies.clientSummaries(ALL_REACHABLE).stream()
                .noneMatch(entry -> stranger.toString().equals(entry.get("uuid"))),
            "a failed lookup must leave nothing behind");
    }

    @Test
    void namingAClientIsEnoughToListIt() {
        UUID player = UUID.randomUUID();
        try {
            RemoteMcpProxies.rememberClientName(player, "Named");

            assertNotNull(RemoteMcpProxies.findClient(player));
            assertTrue(RemoteMcpProxies.clientSummaries(ALL_REACHABLE).stream()
                .anyMatch(entry -> "Named".equals(entry.get("player"))));
            assertTrue(RemoteMcpProxies.clientSummaries(ALL_REACHABLE).stream()
                    .allMatch(entry -> entry.get("player") != null),
                "a name-less entry means the two maps drifted apart");
        } finally {
            RemoteMcpProxies.forgetClient(player);
        }
    }

    @Test
    void reachabilityComesFromTheCallerRatherThanAnyStateHere() {
        UUID player = UUID.randomUUID();
        try {
            RemoteMcpProxies.rememberClientName(player, "Vanilla");

            Map<String, Object> listed = RemoteMcpProxies.clientSummaries(NONE_REACHABLE).stream()
                .filter(entry -> player.toString().equals(entry.get("uuid")))
                .findFirst().orElseThrow();
            assertEquals("Vanilla", listed.get("player"));
            assertEquals(false, listed.get("available"));

            Map<String, Object> reachable = RemoteMcpProxies.clientSummaries(ALL_REACHABLE).stream()
                .filter(entry -> player.toString().equals(entry.get("uuid")))
                .findFirst().orElseThrow();
            assertEquals(true, reachable.get("available"));
        } finally {
            RemoteMcpProxies.forgetClient(player);
        }
    }

    @Test
    void forgettingAClientFailsWhateverWasInFlightToIt() throws Exception {
        UUID player = UUID.randomUUID();
        RemoteMcpProxies.rememberClientName(player, "Dev");
        RemoteMcpProxy proxy = RemoteMcpProxies.findClient(player);
        var failure = new AtomicReference<Exception>();
        var sent = new AtomicReference<String>();
        Thread caller = new Thread(() -> {
            try {
                proxy.call("mc.client.state", Map.of(), sent::set, 30_000);
            } catch (Exception e) {
                failure.set(e);
            }
        });
        caller.setDaemon(true);
        caller.start();
        for (int i = 0; i < 200 && sent.get() == null; i++) {
            Thread.sleep(10);
        }

        RemoteMcpProxies.forgetClient(player);
        caller.join(5_000);

        assertNotNull(failure.get(), "a disconnect has to fail the call, not leave it waiting");
    }
}
