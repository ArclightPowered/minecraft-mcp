package io.izzel.minecraftmcp.packet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CommandSuggestionSync {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_000_000_000);

    private CommandSuggestionSync() {
    }

    public static int nextId() {
        return NEXT_ID.incrementAndGet();
    }

    public static ClientboundCommandSuggestionsPacket await(Connection connection, int id, Runnable send, long timeoutMs) throws Exception {
        Channel channel = channel(connection);
        if (channel == null || !channel.isOpen()) {
            throw new IllegalStateException("not connected");
        }
        String name = "minecraft_mcp_command_suggest_" + id;
        CompletableFuture<ClientboundCommandSuggestionsPacket> future = new CompletableFuture<>();
        AtomicReference<Throwable> installError = new AtomicReference<>();
        Runnable install = () -> {
            try {
                ChannelPipeline pipeline = channel.pipeline();
                ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ClientboundCommandSuggestionsPacket packet && packet.id() == id) {
                            future.complete(packet);
                        }
                        super.channelRead(ctx, msg);
                    }
                };
                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", name, handler);
                } else {
                    pipeline.addLast(name, handler);
                }
            } catch (Throwable t) {
                installError.set(t);
            }
        };
        if (channel.eventLoop().inEventLoop()) install.run(); else channel.eventLoop().submit(install).syncUninterruptibly();
        if (installError.get() != null) {
            throw new IllegalStateException("failed to install command suggestion awaiter", installError.get());
        }
        long started = System.nanoTime();
        try {
            send.run();
            return future.get(Math.max(0, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Timed out waiting for ClientboundCommandSuggestionsPacket id=" + id + " after " + timeoutMs + "ms");
        } finally {
            Runnable remove = () -> {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(name) != null) pipeline.remove(name);
            };
            if (channel.isOpen()) {
                if (channel.eventLoop().inEventLoop()) remove.run(); else channel.eventLoop().submit(remove).syncUninterruptibly();
            }
        }
    }

    public static Map<String, Object> toMap(int id, String command, ClientboundCommandSuggestionsPacket packet, long latencyMs) {
        return Map.of(
                "status", "suggested",
                "command", command,
                "id", id,
                "suggestions", packet.suggestions().size(),
                "start", packet.start(),
                "length", packet.length(),
                "latencyMs", latencyMs
        );
    }

    private static Channel channel(Connection connection) {
        if (connection == null || !connection.isConnected()) return null;
        try {
            for (Field field : Connection.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(connection);
                    return value instanceof Channel channel ? channel : null;
                }
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access Minecraft Connection channel", e);
        }
    }
}
