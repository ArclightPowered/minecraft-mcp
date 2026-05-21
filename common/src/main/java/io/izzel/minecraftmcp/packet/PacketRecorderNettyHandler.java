package io.izzel.minecraftmcp.packet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;

public final class PacketRecorderNettyHandler extends ChannelDuplexHandler {
    public static final String NAME = "minecraft_mcp_packet_recorder";

    private final PacketRecorder recorder;

    public PacketRecorderNettyHandler(PacketRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet<?> packet) {
            recorder.record(PacketDirection.CLIENTBOUND, packet);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet<?> packet) {
            recorder.record(PacketDirection.SERVERBOUND, packet);
        }
        super.write(ctx, msg, promise);
    }
}
