package io.izzel.minecraftmcp.config;

import java.util.List;
import java.util.Optional;

public interface McpOptions {
    Optional<String> get(List<String> path);

    Optional<List<String>> getList(List<String> path);
}
