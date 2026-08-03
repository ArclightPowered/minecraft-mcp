package io.izzel.minecraftmcp.packet;

import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionExpression;
import io.izzel.minecraftmcp.condition.ConditionParser;
import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.property.DefaultConditionPropertyRegistry;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class PacketNamedFilter {
    private final ConditionExpression expression;

    private PacketNamedFilter(ConditionExpression expression) {
        this.expression = expression;
    }

    static PacketNamedFilter from(Object value) {
        if (value instanceof String expression) {
            return new PacketNamedFilter(ConditionParser.parse(expression));
        }
        throw new IllegalArgumentException("Packet filter must be an expression string: " + value);
    }

    boolean matches(RecordedPacket packet) {
        Map<String, Object> globals = new LinkedHashMap<>(packet.toMap());
        globals.put("direction", packet.direction().id());
        globals.put("packetClass", packet.packetClass());
        globals.put("packetSimpleName", packet.packetSimpleName());
        globals.put("summary", packet.summary());
        PacketExpressionBridge bridge = new PacketExpressionBridge(globals);
        DefaultConditionPropertyRegistry registry = new DefaultConditionPropertyRegistry();
        registry.registerGlobal("$", ctx -> globals);
        for (String key : globals.keySet()) {
            registry.registerContextProperty(key, ctx -> globals.get(key));
        }
        return ConditionEvaluator.evaluateBoolean(expression, new ConditionContext(bridge, registry));
    }

    private record PacketExpressionBridge(Map<String, Object> globals) implements io.izzel.minecraftmcp.bridge.MinecraftClientBridge {
        public String loader() { return "packet-filter"; }
        public String minecraftVersion() { return "packet-filter"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) { return CompletableFuture.completedFuture(supplier.get()); }
        public io.izzel.minecraftmcp.bridge.ClientSnapshot snapshot() { return new io.izzel.minecraftmcp.bridge.ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
        public Map<String, Object> packetRecordingStatus() { return globals; }
    }
}
