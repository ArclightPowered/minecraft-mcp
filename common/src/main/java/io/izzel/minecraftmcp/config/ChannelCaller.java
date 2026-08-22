package io.izzel.minecraftmcp.config;

import java.util.UUID;

public sealed interface ChannelCaller {
    String label();

    record Local(String label) implements ChannelCaller {
    }

    record Player(UUID id, String name, boolean permitted, String permissionNode) implements ChannelCaller {
        @Override
        public String label() {
            return name + " (" + id + ")";
        }
    }

    record Server(String address) implements ChannelCaller {
        @Override
        public String label() {
            return address;
        }
    }
}
