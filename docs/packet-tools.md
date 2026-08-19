# Packet tools

minecraft-mcp exposes client packet recording tools for scenario validation and debugging.

Packet recording observes the active Minecraft `Connection` through a Netty pipeline `ChannelDuplexHandler`. It does not use mixins to intercept `Connection` methods.

## Tools

### `mc.packet.recording.start`

Starts recording and installs the Netty handler when the client has an active connection.

Arguments:

```json
{
  "clear": true,
  "maxPackets": 1000,
  "direction": "both",
  "classContains": "",
  "nameContains": "",
  "parseBundlePackets": true,
  "filters": {
    "name_a": "direction == 'serverbound' && contains(packetSimpleName, 'Swing')"
  }
}
```

`direction` may be `both`, `serverbound`, or `clientbound`. `parseBundlePackets` defaults to `true`; when enabled, vanilla `BundlePacket` instances are expanded and their `subPackets()` are recorded/filtered individually. Set it to `false` to record the bundle wrapper itself.

### `mc.packet.recording.stop`

Stops recording and removes the Netty handler when possible.

### `mc.packet.recording.clear`

Clears the in-memory packet buffer.

### `mc.packet.recording.status`

Returns a condition-friendly status object:

```json
{
  "recording": true,
  "count": 12,
  "serverbound": { "count": 8 },
  "clientbound": { "count": 4 },
  "nextSequence": 13,
  "filter": {
    "name_a": { "count": 1 },
    "name_b": { "count": 0, "errors": { "Cannot compare null values": 132 } }
  },
  "last": {
    "direction": "serverbound",
    "packetClass": "net.minecraft.network.protocol.game.ServerboundSwingPacket",
    "packetSimpleName": "ServerboundSwingPacket",
    "sequence": 12,
    "timeMillis": 123456789,
    "summary": {}
  }
}
```

`filter.<name>.errors` groups per-filter evaluation failures by error message (capped at 8 distinct messages per filter; further kinds fall into an `"(other)"` bucket). A filter whose count stays 0 while its errors grow is evaluating against data it cannot compare — usually a misspelled member below `summary`.

### `mc.packet.dump`

Dumps recorded packets with filters.

Arguments:

```json
{
  "direction": "serverbound",
  "classContains": "protocol.game",
  "nameContains": "Swing",
  "sinceSequence": 0,
  "limit": 100,
  "reverse": false,
  "clearAfterDump": false
}
```

The result includes `nextSinceSequence`, so agents can poll incrementally.

### `mc.packet.wait`

Waits until recorded packets matching the same filter fields used by `mc.packet.dump` reach a required count.

Arguments:

```json
{
  "direction": "serverbound",
  "nameContains": "Swing",
  "sinceSequence": 0,
  "count": 1,
  "timeoutMs": 10000
}
```

Result:

```json
{
  "matched": true,
  "count": 1,
  "required": 1
}
```

## Scenario conditions

Packet state is available through the condition property SPI as `packet`. For assertions about specific packets, define a named filter on `mc.packet.recording.start` and wait on that filter's stable cumulative count. Do not rely on `packet.last` for packet existence checks because any later packet can replace it.

```text
packet.recording == true
packet.count > 0
packet.serverbound.count > 0
packet.clientbound.count > 0
packet.filter.name_a.count > 0
```

Use `mc.condition.wait` for packet assertions because packet timing is asynchronous.
