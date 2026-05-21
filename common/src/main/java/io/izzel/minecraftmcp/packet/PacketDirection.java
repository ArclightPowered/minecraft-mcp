package io.izzel.minecraftmcp.packet;

public enum PacketDirection {
    CLIENTBOUND("clientbound"),
    SERVERBOUND("serverbound");

    private final String id;

    PacketDirection(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PacketDirection parse(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
        if (text.isEmpty() || "both".equals(text) || "all".equals(text)) return null;
        for (PacketDirection direction : values()) {
            if (direction.id.equals(text) || direction.name().equalsIgnoreCase(text)) return direction;
        }
        throw new IllegalArgumentException("Unknown packet direction: " + value);
    }
}
