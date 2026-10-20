package io.izzel.minecraftmcp.serverlink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class RemoteMcpProxies {
    private static final RemoteMcpProxy PROXY = new RemoteMcpProxy();

    private static final Map<UUID, RemoteMcpProxy> CLIENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> CLIENT_NAMES = new ConcurrentHashMap<>();

    private RemoteMcpProxies() {
    }

    public static RemoteMcpProxy toServer() {
        return PROXY;
    }

    private static RemoteMcpProxy forPlayer(UUID player) {
        return CLIENTS.computeIfAbsent(player, ignored -> new RemoteMcpProxy());
    }

    public static RemoteMcpProxy findClient(UUID player) {
        return player == null ? null : CLIENTS.get(player);
    }

    public static void rememberClientName(UUID player, String name) {
        CLIENT_NAMES.put(player, name);
        forPlayer(player);
    }

    public static UUID findClientByName(String name) {
        for (Map.Entry<UUID, String> entry : CLIENT_NAMES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void forgetClient(UUID player) {
        RemoteMcpProxy proxy = CLIENTS.remove(player);
        if (proxy != null) {
            proxy.failPending();
        }
        CLIENT_NAMES.remove(player);
    }

    public static List<Map<String, Object>> clientSummaries(Predicate<UUID> reachable) {
        List<Map<String, Object>> list = new ArrayList<>();
        CLIENTS.forEach((uuid, proxy) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", uuid.toString());
            entry.put("player", CLIENT_NAMES.get(uuid));
            entry.put("available", reachable.test(uuid));
            list.add(entry);
        });
        return list;
    }
}
