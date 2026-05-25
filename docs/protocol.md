# Protocol

The first implementation uses MCP-compatible JSON-RPC 2.0 over localhost HTTP POST `/mcp`.

Supported methods:

- `initialize`
- `tools/list`
- `tools/call`

Implemented tools:

`mc.client.state` returns both `inWorld` and `rawInWorld`. `rawInWorld` means the underlying client level/player exists; `inWorld` means the client is in a playable world state with no loading/GUI screen blocking normal controls.

- `mc.client.state`
- `mc.player.state`
- `mc.screen.current`
- `mc.ticks.wait`
- `mc.keyboard.press`
- `mc.keyboard.hold`
- `mc.screenshot.take`
- `mc.packet.recording.start`
- `mc.packet.recording.stop`
- `mc.packet.recording.clear`
- `mc.packet.recording.status`
- `mc.packet.dump`
- `mc.packet.wait`
- `mc.debug.capabilities`
- `mc.scenario.batch.run`
- `mc.scenario.report`
