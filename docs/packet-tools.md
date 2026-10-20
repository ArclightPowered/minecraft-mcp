# Packet tools

minecraft-mcp exposes client packet recording tools for scenario validation and debugging.

Packet recording observes the active Minecraft `Connection` through a Netty pipeline
`ChannelDuplexHandler`. It does not use mixins to intercept `Connection` methods.

## Tools

### `mc.client.packet.recording.start`

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

Each entry in `filters` is a condition expression evaluated against every recorded packet. The
available top-level properties are `sequence`, `timeMillis`, `direction`, `packetClass`,
`packetSimpleName`, `protocol`, `phase` and `summary`. Expressions are validated when the filter is
built (`start`, `dump`, `wait`): a misspelled property or function name, a wrong argument count, or
an invalid literal regex fails the tool call immediately, listing everything that is wrong and what
is available. An expression that validates but fails on a particular packet's data (say
`summary.hp > 3` where `hp` is absent, so `null` reaches `>`) simply does not match that packet;
the failure is counted per filter and shown in the recording status.

`direction` may be `both`, `serverbound`, or `clientbound`. `parseBundlePackets` defaults to `true`;
when enabled, vanilla `BundlePacket` instances are expanded and their `subPackets()` are
recorded/filtered individually. Set it to `false` to record the bundle wrapper itself.

### `mc.client.packet.recording.stop`

Stops recording and removes the Netty handler when possible.

### `mc.client.packet.recording.clear`

Clears the in-memory packet buffer.

### `mc.client.packet.recording.status`

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

`filter.<name>.errors` groups per-filter evaluation failures by error message (capped at 8 distinct
messages per filter; further kinds fall into an `"(other)"` bucket). A filter whose count stays 0
while its errors grow is evaluating against data it cannot compare — usually a misspelled member
below `summary`.

### `mc.client.packet.dump`

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

### `mc.client.packet.wait`

Waits until recorded packets matching the same filter fields used by `mc.client.packet.dump` reach a
required count.

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

Packet state is available through the condition property SPI as `packet`. For assertions about
specific packets, define a named filter on `mc.client.packet.recording.start` and wait on that
filter's stable cumulative count. Do not rely on `packet.last` for packet existence checks because
any later packet can replace it.

```text
packet.recording == true
packet.count > 0
packet.serverbound.count > 0
packet.clientbound.count > 0
packet.filter.name_a.count > 0
```

Use `mc.client.condition.wait` for packet assertions because packet timing is asynchronous.

## Recorder isolation and the Netty hot path

Recording runs inside a live connection's Netty pipeline, on the thread that moves real packets. Two
properties follow from that.

The recorder is an observer, and failures are contained at three levels.
`PacketRecorderNettyHandler` wraps the whole `recorder.record(...)` call in a `try`/`catch`: a
`RuntimeException` inside the recorder leaves the packet travelling the pipeline anyway, because a
broken filter must not be able to stall or kill the connection it is watching — `StackOverflowError`
is not caught the way the plugin-channel parse paths catch it, since a filter expression's depth is
its local author's choice, not a peer's. The first failure to escape the recorder prints one line
to stderr and the handler then suppresses the rest, so a recorder failing on every packet cannot
flood the log. A named filter that throws never gets that far: one level down,
`PacketRecorder.record` catches per named filter, so an evaluation failure on a given packet means
that packet does not match *that* filter, and the packet itself and the other filters are
unaffected — the failure is counted into `filter.<name>.errors`, not logged. The recording filter
(`classContains`, `nameContains`, `direction`) is applied before any named filter, so a packet it
rejects skips named-filter evaluation and storage — not the cost of getting there: the `toString`
summary, the snapshot allocation and the recorder lock are all paid before that check.

A named filter evaluates against the packet alone. Every property a packet filter can name —
`sequence`, `timeMillis`, `direction`, `packetClass`, `packetSimpleName`, `protocol`, `phase`,
`summary` — comes out of the packet's own map, so `PacketNamedFilter.matches` uses
`ConditionContext.overValues(packet.toMap())`: no bridge, no registry, nothing per-packet beyond the
map. Earlier revisions built a throwaway property registry for every matched packet, and before that
a stub client bridge, all on the Netty thread. Validation is correspondingly cheap and one-off:
`PacketNamedFilter.from` checks the expression against the fixed `PROPERTY_NAMES` set through the
`Set<String>` overload of `ConditionValidator.validate`, when the filter is built rather than when
it runs.

Recorders are process-wide, one per side (`PacketRecorders.CLIENT` and `PacketRecorders.SERVER`),
and a bridge reaches its own through `packetRecorder()`. Only the client one is ever written to:
there is no server-side equivalent of the Netty handler yet, so the server registry's `packet`
condition property reports a permanently idle recorder. It is a reserved shape, not a feature — do
not write conditions against it.
