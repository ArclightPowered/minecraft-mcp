package io.izzel.minecraftmcp.packet;

public final class PacketRecorders {
    public static final PacketRecorder CLIENT = new PacketRecorder();
    public static final PacketRecorder SERVER = new PacketRecorder();

    private PacketRecorders() {
    }
}
