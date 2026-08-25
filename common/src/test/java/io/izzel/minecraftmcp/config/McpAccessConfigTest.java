package io.izzel.minecraftmcp.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class McpAccessConfigTest {
    private static final String NODE = "minecraft_mcp:remote.call";

    private static McpConfig with(List<String> disabledTools, List<String> trustedServers) {
        return new McpConfig(MapOptions.of()
            .with(disabledTools, "access", "disabledTools")
            .with(trustedServers, "access", "trustedServers"), "config/test.json");
    }

    private static McpConfig empty() {
        return with(List.of(), List.of());
    }

    @Test
    void disabledToolsMatchExactNamesAndTrailingWildcards() {
        McpConfig config = with(List.of("mc.server.log.tail", "mc.remote.*"), List.of());

        assertTrue(config.toolDisabled("mc.server.log.tail"));
        assertTrue(config.toolDisabled("mc.remote.call"));
        assertTrue(config.toolDisabled("mc.remote.state"));
        assertFalse(config.toolDisabled("mc.server.state"));
        assertFalse(config.toolDisabled("mc.server.log.tail.extra"), "exact entries must not match by prefix");
        assertFalse(config.toolDisabled(null));
    }

    @Test
    void anEmptyPolicyDisablesNothing() {
        assertFalse(empty().toolDisabled("mc.server.shutdown"));
        assertEquals(Set.of(), empty().disabledTools());
        assertEquals(Set.of(), empty().trustedServers());
    }

    @Test
    void playerTrustComesFromTheLoadersPermissionCheck() {
        McpConfig config = empty();

        assertTrue(config.trusts(new ChannelCaller.Player(UUID.randomUUID(), "Dev", true, NODE)));
        assertFalse(config.trusts(new ChannelCaller.Player(UUID.randomUUID(), "Dev", false, NODE)));
    }

    @Test
    void theIntegratedServerIsTrustedWithoutConfiguration() {
        assertTrue(empty().trusts(new ChannelCaller.Local("singleplayer")));
    }

    @Test
    void serverTrustIsALiteralMatchOnWhatTheUserTyped() {
        McpConfig config = with(List.of(), List.of("play.example.com", "127.0.0.1:25566"));

        assertTrue(config.trusts(new ChannelCaller.Server("play.example.com")));
        assertTrue(config.trusts(new ChannelCaller.Server("127.0.0.1:25566")));
        assertFalse(config.trusts(new ChannelCaller.Server("evil.example.com")));
        assertFalse(config.trusts(new ChannelCaller.Server("unknown")));
    }

    @Test
    void anOmittedPortIsADifferentEntry() {
        McpConfig config = with(List.of(), List.of("play.example.com:25565"));

        assertFalse(config.trusts(new ChannelCaller.Server("play.example.com")));
        assertTrue(config.trusts(new ChannelCaller.Server("play.example.com:25565")));
    }

    @Test
    void hostCaseIsFoldedOnBothSides() {
        McpConfig config = with(List.of(), List.of("  Play.EXAMPLE.Com:25566 "));

        assertTrue(config.trusts(new ChannelCaller.Server("play.example.com:25566")));
        assertTrue(config.trusts(new ChannelCaller.Server("PLAY.Example.COM:25566")));
    }

    @Test
    void blankEntriesAreDropped() {
        McpConfig config = with(List.of(), List.of("good.example.com", "", "   "));

        assertEquals(Set.of("good.example.com"), config.trustedServers());
    }

    @Test
    void disabledToolEntriesAreTrimmedButNotCaseFolded() {
        McpConfig config = with(List.of("  mc.server.log.tail  ", "", "   ", " mc.remote.* "), List.of());

        assertEquals(Set.of("mc.server.log.tail", "mc.remote.*"), config.disabledTools());
        assertTrue(config.toolDisabled("mc.server.log.tail"));
        assertTrue(config.toolDisabled("mc.remote.call"), "a trimmed wildcard still matches by prefix");
        assertFalse(config.toolDisabled("mc.Server.Log.Tail"));
    }

    @Test
    void refusingAServerQuotesTheExactStringToAddAndWhereToAddIt() {
        McpConfig config = with(List.of(), List.of("good.example.com"));

        String refusal = config.refusal(new ChannelCaller.Server("MC.Example.Com:25565"));

        assertTrue(refusal.contains("add \"MC.Example.Com:25565\" to trustedServers"), refusal);
        assertTrue(refusal.contains("config/test.json"), refusal);
    }

    @Test
    void refusingAPlayerNamesThePermissionNodeAsThatLoaderSpellsIt() {
        UUID id = UUID.randomUUID();

        String refusal = empty().refusal(new ChannelCaller.Player(id, "Griefer", false, NODE));

        assertTrue(refusal.contains("Griefer"), refusal);
        assertTrue(refusal.contains(id.toString()), refusal);
        assertTrue(refusal.contains(NODE), refusal);
    }

    @Test
    void everyCallerKindHasARefusal() {
        for (ChannelCaller caller : List.of(
            new ChannelCaller.Local("singleplayer"),
            new ChannelCaller.Player(UUID.randomUUID(), "Dev", false, NODE),
            new ChannelCaller.Server("evil.example.com"))) {
            assertFalse(empty().refusal(caller).isBlank(), () -> "no refusal for " + caller);
        }
    }
}
