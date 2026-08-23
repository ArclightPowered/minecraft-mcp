# Command suggestion and server sync tools

## `mc.client.command.suggest`

Requests vanilla command suggestions and waits for the matching server response.

Arguments:

```json
{
  "command": "/",
  "timeoutMs": 30000
}
```

Response:

```json
{
  "status": "suggested",
  "command": "/",
  "id": 1000000001,
  "suggestions": 69,
  "start": 1,
  "length": 0,
  "latencyMs": 3
}
```

Implementation notes:

- Sends vanilla `ServerboundCommandSuggestionPacket` with a generated request id.
- Installs a temporary Netty inbound observer and waits for the matching `ClientboundCommandSuggestionsPacket` id.
- The packet is still passed through to vanilla client handling; the tool only observes it.
- The server handler uses `PacketUtils.ensureRunningOnSameThread`, so the response is useful as a server main-thread round trip.

## `mc.client.connection.sync`

Shortcut for a command-suggestion round trip.

Arguments:

```json
{
  "timeoutMs": 30000
}
```

`mc.client.connection.sync` intentionally only accepts `timeoutMs`. It ignores any command-like arguments and internally calls the same bridge with command `/`.

Use after client-to-server setup commands when the next step depends on the server having processed prior packets:

```json
[
  { "tool": "mc.client.chat.send", "args": { "message": "/setblock 1 65 0 minecraft:furnace" } },
  { "tool": "mc.client.connection.sync", "args": { "timeoutMs": 30000 } },
  { "tool": "mc.client.block.state", "args": { "x": 1, "y": 65, "z": 0 } }
]
```

This guarantees a vanilla client->server->client round trip through the server main thread. It does not by itself prove every client-side world update has been rendered, so scenarios should still use `mc.client.condition.wait` or repeated state checks when they need a specific block/screen/item to appear.
