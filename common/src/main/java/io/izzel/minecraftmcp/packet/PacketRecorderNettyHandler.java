package io.izzel.minecraftmcp.packet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;

public final class PacketRecorderNettyHandler extends ChannelDuplexHandler {
    public static final String NAME = "minecraft_mcp_packet_recorder";

    private final PacketRecorder recorder;
    private boolean recorderErrorLogged;

    public PacketRecorderNettyHandler(PacketRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet<?> packet) {
            record(PacketDirection.CLIENTBOUND, packet);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet<?> packet) {
            record(PacketDirection.SERVERBOUND, packet);
        }
        super.write(ctx, msg, promise);
    }

    private void record(PacketDirection direction, Packet<?> packet) {
        try {
            recorder.record(direction, packet);
        } catch (RuntimeException e) {
            if (!recorderErrorLogged) {
                recorderErrorLogged = true;
                System.err.println("[Minecraft MCP] packet recorder failed (suppressed, packets keep flowing): " + e);
            }
        }
    }
}
