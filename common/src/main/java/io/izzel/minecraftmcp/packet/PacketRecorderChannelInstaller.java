package io.izzel.minecraftmcp.packet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class PacketRecorderChannelInstaller {
    private PacketRecorderChannelInstaller() {
    }

    public static Map<String, Object> install(Connection connection, PacketRecorder recorder) {
        Channel channel = channel(connection);
        if (channel == null || !channel.isOpen()) {
            return Map.of("packetHandler", "not_connected");
        }
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        Runnable install = () -> {
            ChannelPipeline pipeline = channel.pipeline();
            boolean alreadyInstalled = pipeline.get(PacketRecorderNettyHandler.NAME) != null;
            if (!alreadyInstalled) {
                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", PacketRecorderNettyHandler.NAME, new PacketRecorderNettyHandler(recorder));
                } else {
                    pipeline.addLast(PacketRecorderNettyHandler.NAME, new PacketRecorderNettyHandler(recorder));
                }
            }
            result.set(Map.of("packetHandler", alreadyInstalled ? "already_installed" : "installed"));
        };
        if (channel.eventLoop().inEventLoop()) install.run(); else channel.eventLoop().submit(install).syncUninterruptibly();
        return result.get();
    }

    public static Map<String, Object> remove(Connection connection) {
        Channel channel = channel(connection);
        if (channel == null || !channel.isOpen()) {
            return Map.of("packetHandler", "not_connected");
        }
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        Runnable remove = () -> {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(PacketRecorderNettyHandler.NAME) != null) {
                pipeline.remove(PacketRecorderNettyHandler.NAME);
                result.set(Map.of("packetHandler", "removed"));
            } else {
                result.set(Map.of("packetHandler", "not_installed"));
            }
        };
        if (channel.eventLoop().inEventLoop()) remove.run(); else channel.eventLoop().submit(remove).syncUninterruptibly();
        return result.get();
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
