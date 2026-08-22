package io.izzel.minecraftmcp.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class McpConfig {
    private record Layer(String label, McpOptions options) {
    }

    private record Hit(String label, Object value) {
    }

    private final List<Layer> layers;
    private final McpOptions configFile;
    private final String mintedAuthToken = UUID.randomUUID().toString();
    private final String configSource;

    public McpConfig(McpOptions configFile, String configSource) {
        this.configSource = Objects.requireNonNull(configSource, "configSource");
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.layers = List.of(
            new Layer("system property", new PropertyOptions()),
            new Layer("environment", new EnvironmentOptions()),
            new Layer("config", this.configFile),
            new Layer("default", new DefaultOptions()));
    }

    public String bindHost() {
        return resolve(String.class, "endpoint", "bind");
    }

    public int port() {
        int port = resolve(Integer.class, "endpoint", "port");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("endpoint.port expected a port from 0 to 65535 but got '" + port + "'");
        }
        return port;
    }

    public String authToken() {
        String configured = resolve(String.class, "endpoint", "authToken");
        return configured.isBlank() ? mintedAuthToken : configured;
    }

    public boolean headless() {
        return resolve(Boolean.class, "endpoint", "headless");
    }

    public String scenarioDir() {
        return resolve(String.class, "scenario", "directory");
    }

    public boolean batchExit() {
        return resolve(Boolean.class, "scenario", "batchExit");
    }

    @SuppressWarnings("unchecked")
    public Set<String> disabledTools() {
        Set<String> cleaned = new LinkedHashSet<>();
        for (String entry : (List<String>) resolve(List.class, "access", "disabledTools")) {
            if (entry != null && !entry.isBlank()) {
                cleaned.add(entry.trim());
            }
        }
        return Set.copyOf(cleaned);
    }

    @SuppressWarnings("unchecked")
    public Set<String> trustedServers() {
        Set<String> folded = new LinkedHashSet<>();
        for (String entry : (List<String>) resolve(List.class, "access", "trustedServers")) {
            if (entry != null && !entry.isBlank()) {
                folded.add(foldAddress(entry));
            }
        }
        return Set.copyOf(folded);
    }

    public boolean toolDisabled(String tool) {
        if (tool == null) {
            return false;
        }
        Set<String> disabled = disabledTools();
        if (disabled.isEmpty()) {
            return false;
        }
        if (disabled.contains(tool)) {
            return true;
        }
        for (String pattern : disabled) {
            if (pattern.endsWith("*") && tool.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    public boolean trusts(ChannelCaller caller) {
        return switch (caller) {
            case ChannelCaller.Local ignored -> true;
            case ChannelCaller.Player player -> player.permitted();
            case ChannelCaller.Server server -> trustedServers().contains(foldAddress(server.address()));
        };
    }

    static String foldAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private <V> V resolve(Class<V> type, String... path) {
        List<String> option = List.of(path);
        return convert(type, option, lookup(type, option).value());
    }

    private Hit lookup(Class<?> type, List<String> path) {
        boolean wantsList = List.class.isAssignableFrom(type);
        for (Layer layer : layers) {
            Optional<?> found = wantsList ? layer.options().getList(path) : layer.options().get(path);
            if (found.isPresent()) {
                return new Hit(layer.label(), found.get());
            }
        }
        throw new IllegalStateException("No default registered for option " + OptionNames.describe(path));
    }

    @SuppressWarnings("unchecked")
    private static <V> V convert(Class<V> type, List<String> path, Object value) {
        if (type == String.class) {
            return (V) String.valueOf(value);
        }
        if (List.class.isAssignableFrom(type)) {
            return (V) value;
        }
        String text = String.valueOf(value).trim();
        String option = OptionNames.describe(path);
        if (type == Integer.class) {
            try {
                return (V) Integer.valueOf(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(option + " expected a number but got '" + text + "'", e);
            }
        }
        if (type == Boolean.class) {
            if (text.equalsIgnoreCase("true")) {
                return (V) Boolean.TRUE;
            }
            if (text.equalsIgnoreCase("false")) {
                return (V) Boolean.FALSE;
            }
            throw new IllegalArgumentException(option + " expected true or false but got '" + text + "'");
        }
        throw new IllegalArgumentException("Unsupported option type " + type.getName());
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (Runnable option : List.<Runnable>of(
            this::bindHost, this::port, this::authToken, this::headless,
            this::scenarioDir, this::batchExit, this::disabledTools, this::trustedServers)) {
            try {
                option.run();
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        return List.copyOf(problems);
    }

    public List<String> report() {
        List<String> lines = new ArrayList<>();
        for (List<String> path : DefaultOptions.paths()) {
            boolean list = DefaultOptions.isList(path);
            Hit hit = lookup(list ? List.class : String.class, path);
            lines.add("Picked " + OptionNames.describe(path) + " from " + hit.label() + ": " + display(path, hit.value()));
            if (masked(hit.label()) && configFileHasValue(list, path)) {
                lines.add("WARNING: " + OptionNames.describe(path) + " comes from " + hit.label()
                    + "; the value in " + configSource + " is ignored");
            }
        }
        return lines;
    }

    private static boolean masked(String label) {
        return "system property".equals(label) || "environment".equals(label);
    }

    private boolean configFileHasValue(boolean list, List<String> path) {
        return list ? configFile.getList(path).isPresent() : configFile.get(path).isPresent();
    }

    private static String display(List<String> path, Object value) {
        if (path.equals(List.of("endpoint", "authToken"))) {
            return "<redacted>";
        }
        return String.valueOf(value);
    }
}
