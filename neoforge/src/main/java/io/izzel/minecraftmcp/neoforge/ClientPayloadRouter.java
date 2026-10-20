package io.izzel.minecraftmcp.neoforge;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

final class ClientPayloadRouter {
    private static volatile BiConsumer<IPayloadContext, String> onRequest;
    private static volatile BiConsumer<IPayloadContext, String> onResponse;

    private ClientPayloadRouter() {
    }

    static void set(BiConsumer<IPayloadContext, String> request, BiConsumer<IPayloadContext, String> response) {
        onRequest = request;
        onResponse = response;
    }

    static void dispatchRequest(IPayloadContext context, String payload) {
        BiConsumer<IPayloadContext, String> current = onRequest;
        if (current != null) {
            current.accept(context, payload);
        }
    }

    static void dispatchResponse(IPayloadContext context, String payload) {
        BiConsumer<IPayloadContext, String> current = onResponse;
        if (current != null) {
            current.accept(context, payload);
        }
    }
}
